#!/usr/bin/env python3
# MIT Licensed
# Server-side Angstrom feed sorter. Python 3.7+, stdlib only.
#
# Full rewrite of old/contrib/sort.sh. Runs on the feed server (cron or
# post-upload hook) against a feed base directory laid out as:
#
#   <feed-dir>/incoming/<upload-id>/   staged uploads from upload-packages.py
#   <feed-dir>/unsorted/               flat pool of not-yet-sorted ipks
#   <feed-dir>/<archdir>/base/
#   <feed-dir>/<archdir>/machine/<machine_arch>/
#   <feed-dir>/all/  <feed-dir>/sdk/
#
# Matching rule: bitbake names machine-specific package arch dirs and ipk
# suffixes after MACHINE with '-' replaced by '_' (bitbake.conf:190), so the
# map's machine_arch field (normalized once at config-write time) is the only
# key matched here; hyphenated spellings never appear in ipk filenames.
#
# Differences from sort.sh, on purpose:
#  - Staged uploads are ingested with sha256 verification against their
#    MANIFEST.sha256 (hashlib, no sha256sum dependency); corrupt files are
#    rejected and logged so the client re-uploads them.
#  - The machine/arch table is no longer hardcoded: it is read from
#    feed-arch-map.json (see scripts/add-machine-arch.py), which can also be
#    delivered through the same verified upload channel as the packages.
#  - Files with an arch the map does not know stay in unsorted/ with a
#    warning instead of silently rotting there while still being added to
#    files-sorted (which made the old script never upload them again).
#  - files-sorted gains only names that were actually sorted into a feed.
#  - Only feeds that actually received files are re-indexed.
#  - The upload ticker records the feed name, not the literal "unsorted".
#
# Run it from <feed-dir>/unsorted (sort.sh compatible) or point --feed-dir
# at the feed base.

import argparse
import copy
import gzip
import hashlib
import json
import logging
import os
import re
import shutil
import subprocess
import sys
import time

from pathlib import Path

log = logging.getLogger("sort-packages")

ARCH_MAP_NAME = "feed-arch-map.json"
ALL_ARCHES = ("all", "any", "noarch")
TICKER_EXCLUDE = re.compile(r"-dbg|-dev|-doc|-static|angstrom-version|locale")


def parse_args(argv):
    p = argparse.ArgumentParser(
        description="Sort uploaded ipk packages into the Angstrom feed tree")
    p.add_argument("--feed-dir", metavar="DIR",
                   help="feed base directory (default: parent of the current "
                        "directory when run from unsorted/, else the current "
                        "directory if it contains unsorted/)")
    p.add_argument("--config", metavar="FILE",
                   help="feed arch map (default: <feed-dir>/%s)" % ARCH_MAP_NAME)
    p.add_argument("--opkg-make-index", default="opkg-make-index", metavar="PATH",
                   help="opkg-make-index executable (default: from PATH)")
    p.add_argument("--skip-sorted-list", action="store_true",
                   help="do not update the files-sorted duplicate list")
    p.add_argument("--skip-index", action="store_true",
                   help="sort only, do not run opkg-make-index")
    p.add_argument("--post-command", metavar="CMD",
                   help="shell command to run after a successful sort "
                        "(replaces the old hardcoded repo-updater step)")
    p.add_argument("--dry-run", action="store_true",
                   help="report what would happen without changing anything")
    p.add_argument("--log-level", default="info",
                   choices=["debug", "info", "warning", "error"])
    return p.parse_args(argv)


def sha256_file(path):
    h = hashlib.sha256()
    with open(str(path), "rb") as f:
        while True:
            block = f.read(1024 * 1024)
            if not block:
                break
            h.update(block)
    return h.hexdigest()


def atomic_write_text(path, text):
    path = Path(path)
    tmp = path.parent / (path.name + ".tmp.%d" % os.getpid())
    tmp.write_text(text)
    os.replace(str(tmp), str(path))


def resolve_feed_dir(arg):
    if arg:
        feed_dir = Path(arg)
        if not (feed_dir / "unsorted").is_dir():
            log.error("%s does not contain an unsorted/ directory", feed_dir)
            return None
        return feed_dir.resolve()
    cwd = Path.cwd()
    if cwd.name == "unsorted":
        return cwd.parent.resolve()
    if (cwd / "unsorted").is_dir():
        return cwd.resolve()
    log.error("Not in a feed directory (no unsorted/ here); use --feed-dir")
    return None


class ArchMap(object):
    """Classification data loaded from feed-arch-map.json."""

    def __init__(self, aliases, machine_dirs, base_archs):
        self.aliases = aliases            # arch -> archdir
        self.machine_dirs = machine_dirs  # machine_arch -> archdir
        self.base_archs = base_archs      # set of base package arches
        # Arch is matched as a filename suffix against the known names,
        # longest first - machine arches routinely contain underscores
        # (rb1_core_kit, beaglev_ahead), so splitting the filename on '_'
        # cannot work. sort.sh did the same with find -name "*_$machine.ipk".
        self._machines_by_len = sorted(machine_dirs, key=len, reverse=True)
        self._bases_by_len = sorted(base_archs, key=len, reverse=True)

    @classmethod
    def load(cls, path):
        try:
            with open(str(path)) as f:
                data = json.load(f)
        except (OSError, ValueError) as exc:
            log.error("Cannot read arch map %s: %s", path, exc)
            return None
        aliases = dict(data.get("arch_aliases") or {})
        machines = data.get("machines") or {}
        machine_dirs = {}
        base_archs = set(data.get("extra_base_archs") or [])
        for name in sorted(machines):
            entry = machines[name]
            machine_arch = entry.get("machine_arch") or name.replace("-", "_")
            feed_arch = entry.get("feed_arch")
            if not feed_arch:
                log.warning("arch map entry %s has no feed_arch, skipped", name)
                continue
            archdir = aliases.get(feed_arch, feed_arch)
            if machine_arch in machine_dirs and machine_dirs[machine_arch] != archdir:
                log.warning("arch map: %s maps to both %s and %s; keeping %s",
                            machine_arch, machine_dirs[machine_arch], archdir,
                            archdir)
            machine_dirs[machine_arch] = archdir
            for arch in entry.get("package_archs") or []:
                base_archs.add(arch)
        # machine arches and the all-arches are never base feeds
        base_archs.difference_update(machine_dirs)
        base_archs.difference_update(ALL_ARCHES)
        log.info("Arch map: %d machines, %d base arches, %d aliases",
                 len(machine_dirs), len(base_archs), len(aliases))
        return cls(aliases, machine_dirs, base_archs)

    def classify(self, filename):
        """Return a path relative to the feed dir, or None if unknown."""
        stem = filename[:-4]  # strip .ipk
        if "_" not in stem:
            return None
        for arch in ALL_ARCHES:
            if stem.endswith("_" + arch):
                return Path("all")
        if filename.endswith("sdk.ipk"):
            return Path("sdk")
        for arch in self._machines_by_len:
            if stem.endswith("_" + arch):
                return Path(self.machine_dirs[arch]) / "machine" / arch
        for arch in self._bases_by_len:
            if stem.endswith("_" + arch):
                archdir = self.aliases.get(arch, arch)
                return Path(archdir) / "base"
        return None


def merge_arch_map(target, incoming):
    """Merge a delivered arch map into <feed-dir>/feed-arch-map.json.
    Machine entries and aliases are updated per key; extra_base_archs are
    unioned. Returns True if the target changed."""
    try:
        with open(str(incoming)) as f:
            new = json.load(f)
    except (OSError, ValueError) as exc:
        log.warning("Delivered arch map is unreadable, ignored: %s", exc)
        return False
    current = {}
    if Path(target).exists():
        try:
            with open(str(target)) as f:
                current = json.load(f)
        except (OSError, ValueError):
            log.warning("Existing arch map %s is corrupt, replacing", target)
    # deep copy: the no-op comparison below must not see our own mutations
    merged = copy.deepcopy(current) if current else {"version": 1}
    merged.setdefault("arch_aliases", {}).update(new.get("arch_aliases") or {})
    merged["extra_base_archs"] = sorted(
        set(merged.get("extra_base_archs") or []) |
        set(new.get("extra_base_archs") or []))
    merged.setdefault("machines", {}).update(new.get("machines") or {})
    if "_comment" in new:
        merged["_comment"] = new["_comment"]
    if merged == current:
        log.info("Delivered arch map matches the current one")
        return False
    atomic_write_text(target, json.dumps(merged, indent=2, sort_keys=True) + "\n")
    log.info("Arch map %s updated from upload (%d machines)",
             target, len(merged.get("machines", {})))
    return True


def parse_manifest(path):
    """Return [(sha256, relpath)] from a MANIFEST.sha256 file."""
    entries = []
    with open(str(path)) as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            parts = line.split("  ", 1)
            if len(parts) != 2 or len(parts[0]) != 64:
                log.warning("Malformed manifest line ignored: %r", line)
                continue
            entries.append((parts[0].lower(), parts[1]))
    return entries


def ingest_incoming(feed_dir, arch_map_path, dry_run):
    """Verify and absorb completed staged uploads from <feed-dir>/incoming.
    Returns (ingested, rejected) counts."""
    incoming = feed_dir / "incoming"
    unsorted_dir = feed_dir / "unsorted"
    ingested = 0
    rejected = 0
    if not incoming.is_dir():
        return 0, 0
    for stage in sorted(p for p in incoming.iterdir() if p.is_dir()):
        if not (stage / "COMPLETE").exists():
            log.info("Skipping incomplete upload %s", stage.name)
            continue
        manifest = stage / "MANIFEST.sha256"
        if not manifest.exists():
            log.warning("Ingesting unverified upload %s (no manifest)", stage.name)
            if not dry_run:
                for ipk in sorted(stage.rglob("*.ipk")):
                    os.replace(str(ipk), str(unsorted_dir / ipk.name))
                    ingested += 1
                shutil.rmtree(str(stage))
            continue
        log.info("Ingesting verified upload %s", stage.name)
        for digest, relpath in parse_manifest(manifest):
            src = stage / relpath
            name = src.name
            if not src.exists():
                log.warning("%s: manifest entry missing on disk: %s",
                            stage.name, relpath)
                continue
            actual = sha256_file(src)
            if actual != digest:
                rejected += 1
                log.error("%s: checksum mismatch, rejecting %s", stage.name, relpath)
                if not dry_run:
                    with open(str(incoming / "rejected.log"), "a") as f:
                        f.write("%d REJECTED %s from %s\n" %
                                (int(time.time()), relpath, stage.name))
                continue
            if name == ARCH_MAP_NAME:
                if not dry_run:
                    merge_arch_map(arch_map_path, src)
                continue
            if not name.endswith(".ipk"):
                log.warning("%s: ignoring non-package file %s", stage.name, relpath)
                continue
            if not dry_run:
                os.replace(str(src), str(unsorted_dir / name))
            ingested += 1
        if not dry_run:
            shutil.rmtree(str(stage))
    return ingested, rejected


def flatten_pool(unsorted_dir, dry_run):
    """Match sort.sh: drop morgue dirs and stale indexes, pull nested ipks up."""
    for morgue in sorted(unsorted_dir.rglob("morgue")):
        if morgue.is_dir():
            log.info("Removing morgue directory %s", morgue)
            if not dry_run:
                shutil.rmtree(str(morgue))
    for stale in sorted(unsorted_dir.glob("Packages*")):
        if not dry_run:
            stale.unlink()
    for ipk in sorted(unsorted_dir.rglob("*.ipk")):
        if ipk.parent != unsorted_dir:
            log.debug("Flattening %s", ipk)
            if not dry_run:
                os.replace(str(ipk), str(unsorted_dir / ipk.name))
    # clear out emptied subdirectories (deepest first)
    for sub in sorted((p for p in unsorted_dir.rglob("*") if p.is_dir()),
                      key=lambda p: len(p.parts), reverse=True):
        try:
            if not dry_run:
                sub.rmdir()
        except OSError:
            pass  # not empty


def read_sorted_list(unsorted_dir):
    path = unsorted_dir / "files-sorted"
    if not path.exists():
        return set()
    return set(l.strip() for l in path.read_text().splitlines() if l.strip())


def dedupe_pool(unsorted_dir, sorted_names, dry_run):
    removed = 0
    for ipk in sorted(unsorted_dir.glob("*.ipk")):
        if ipk.name in sorted_names:
            log.info("Removing duplicate %s (already sorted)", ipk.name)
            if not dry_run:
                ipk.unlink()
            removed += 1
    return removed


def sort_pool(feed_dir, unsorted_dir, archmap, dry_run):
    """Move pool files into their feeds. Returns (touched dirs, moved names,
    unknown names)."""
    touched = set()
    moved = []
    unknown = []
    for ipk in sorted(unsorted_dir.glob("*.ipk")):
        rel = archmap.classify(ipk.name)
        if rel is None:
            unknown.append(ipk.name)
            continue
        dest_dir = feed_dir / rel
        log.debug("%s -> %s/", ipk.name, rel)
        if not dry_run:
            dest_dir.mkdir(parents=True, exist_ok=True)
            os.replace(str(ipk), str(dest_dir / ipk.name))
        touched.add(dest_dir)
        moved.append(ipk.name)
    if unknown:
        log.warning("%d packages with unknown arch left in unsorted/ "
                    "(is feed-arch-map.json up to date?):", len(unknown))
        for name in unknown[:20]:
            log.warning("  %s", name)
        if len(unknown) > 20:
            log.warning("  ... and %d more", len(unknown) - 20)
    return touched, moved, unknown


def update_sorted_list(unsorted_dir, sorted_names, moved, dry_run):
    merged = sorted(sorted_names | set(moved))
    if not dry_run:
        atomic_write_text(unsorted_dir / "files-sorted",
                          "".join(n + "\n" for n in merged))
    log.info("files-sorted now lists %d packages", len(merged))


def update_ticker(feed_dir, moved, dry_run):
    """Maintain upload-full.txt / upload.txt (the 'recently uploaded' feed)."""
    interesting = [n for n in moved if not TICKER_EXCLUDE.search(n)]
    if not interesting or dry_run:
        return
    now = int(time.time())
    full = feed_dir / "upload-full.txt"
    with open(str(full), "a") as f:
        for name in interesting:
            f.write("%d %s %s\n" % (now, name, feed_dir.name))
    tail = full.read_text().splitlines()[-100:]
    atomic_write_text(feed_dir / "upload.txt", "".join(l + "\n" for l in tail))


def run_index(tool, directory):
    # -m matches sort.sh; emit both checksums so modern opkg clients can
    # verify sha256 (upstream default is md5 only)
    cmd = [tool, "-m", "-p", "Packages", "-l", "Packages.filelist",
           "--checksum", "md5", "--checksum", "sha256", "."]
    log.info("Indexing %s", directory)
    try:
        proc = subprocess.run(cmd, cwd=str(directory), capture_output=True,
                              text=True, timeout=1800)
    except OSError as exc:
        log.error("Cannot run %s: %s", tool, exc)
        return False
    except subprocess.TimeoutExpired:
        log.error("Indexing %s timed out", directory)
        return False
    if proc.returncode != 0:
        log.error("opkg-make-index failed in %s: %s", directory,
                  proc.stderr.strip().splitlines()[-1] if proc.stderr.strip() else "")
        return False
    return True


def postprocess_index(directory):
    """Strip Source: lines from Packages, write Packages.gz, touch Packages.sig
    (same post-treatment sort.sh applied to every index)."""
    packages = directory / "Packages"
    if not packages.exists():
        return
    lines = [l for l in packages.read_text().splitlines()
             if not l.startswith("Source:")]
    text = "".join(l + "\n" for l in lines)
    atomic_write_text(packages, text)
    gz = directory / "Packages.gz"
    tmp = directory / ("Packages.gz.tmp.%d" % os.getpid())
    with open(str(tmp), "wb") as f:
        with gzip.GzipFile(filename="Packages", mode="wb", fileobj=f,
                           compresslevel=9, mtime=0) as z:
            z.write(text.encode("utf-8"))
    os.replace(str(tmp), str(gz))
    (directory / "Packages.sig").touch()


def index_feeds(feed_dir, touched, tool):
    """Re-index every feed dir that received files. Returns the number of
    failures."""
    failures = 0
    for directory in sorted(touched):
        if run_index(tool, directory):
            postprocess_index(directory)
        else:
            failures += 1
    return failures


def main(argv=None):
    args = parse_args(argv)
    logging.basicConfig(
        level=getattr(logging, args.log_level.upper()),
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S")

    feed_dir = resolve_feed_dir(args.feed_dir)
    if feed_dir is None:
        return 1
    unsorted_dir = feed_dir / "unsorted"
    arch_map_path = Path(args.config) if args.config else feed_dir / ARCH_MAP_NAME

    ingested, rejected = ingest_incoming(feed_dir, arch_map_path, args.dry_run)
    if ingested or rejected:
        log.info("Ingest: %d files accepted, %d rejected", ingested, rejected)

    flatten_pool(unsorted_dir, args.dry_run)

    pool = sorted(unsorted_dir.glob("*.ipk"))
    if not pool:
        log.info("No unsorted packages, nothing to do")
        return 0

    archmap = ArchMap.load(arch_map_path)
    if archmap is None:
        log.error("No usable arch map at %s; deliver one with "
                  "upload-packages.py --arch-map or pass --config", arch_map_path)
        return 1

    sorted_names = read_sorted_list(unsorted_dir)
    dedupe_pool(unsorted_dir, sorted_names, args.dry_run)

    touched, moved, unknown = sort_pool(feed_dir, unsorted_dir, archmap,
                                        args.dry_run)
    log.info("Sorted %d packages into %d feed directories, %d unknown",
             len(moved), len(touched), len(unknown))

    if args.dry_run:
        return 0

    if moved and not args.skip_sorted_list:
        update_sorted_list(unsorted_dir, sorted_names, moved, args.dry_run)
    update_ticker(feed_dir, moved, args.dry_run)

    failures = 0
    if moved and not args.skip_index:
        if shutil.which(args.opkg_make_index) is None:
            log.error("%s not found; indexes not rebuilt (--skip-index to "
                      "silence)", args.opkg_make_index)
            failures = 1
        else:
            failures = index_feeds(feed_dir, touched, args.opkg_make_index)

    if args.post_command and not failures and moved:
        log.info("Running post command: %s", args.post_command)
        proc = subprocess.run(args.post_command, shell=True, cwd=str(feed_dir))
        if proc.returncode != 0:
            log.error("Post command exited %d", proc.returncode)
            failures += 1

    return 2 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
