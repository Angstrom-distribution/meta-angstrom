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
#  - --drop NAME forgets a package (all versions/architectures) from
#    files-sorted, so the next upload re-delivers it and sorting overwrites
#    the stale ipk already in the feed tree.
#
# Run it from <feed-dir>/unsorted (sort.sh compatible) or point --feed-dir
# at the feed base.
#
# Every run ends with exactly one greppable sentinel line:
#   SORT-PACKAGES: SUCCESS feed-dir=... ingested=N rejected=N unusable=N
#                          sorted=N feeds=N unknown=N dry-run=0|1
#   SORT-PACKAGES: FAILED  <same fields>, preceded by one "reason:" line per
#                          distinct problem.
# Exit codes: 0 success, 1 setup/config/lock failure, bad command line
# included (nothing was attempted),
# 2 the run did something but not all of it (rejected or un-ingestable
# uploads, a failed index rebuild, files-sorted not updated, post command
# failed). Never exits 0 after skipping a step.

import argparse
import copy
import fcntl
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


class SentinelArgumentParser(argparse.ArgumentParser):
    """argparse's own usage errors exit 2, but exit 2 is documented above as
    "the run did something but not all of it" -- a typo'd flag must not look
    like a partial sort. Usage errors attempt nothing: print the sentinel and
    exit 1 like the other setup/config failures."""

    def error(self, message):
        self.print_usage(sys.stderr)
        print("SORT-PACKAGES: FAILED bad usage: %s" % message, file=sys.stderr)
        sys.exit(1)


def parse_args(argv):
    p = SentinelArgumentParser(
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
    p.add_argument("--drop", action="append", default=[], metavar="NAME",
                   help="remove all files-sorted entries for package NAME "
                        "(every version/architecture), so the next upload "
                        "of it is treated as new and sorting overwrites "
                        "the old ipk in place (repeatable)")
    p.add_argument("--skip-index", action="store_true",
                   help="sort only, do not run opkg-make-index")
    p.add_argument("--ensure-archs", metavar="ARCH[,ARCH...]",
                   help="comma/space-separated base arches to guarantee a valid "
                        "(possibly empty) Packages/Packages.gz index for -- e.g. "
                        "a machine's full PACKAGE_EXTRA_ARCHS compatibility "
                        "ladder -- so a client configured to look at a "
                        "less-specific arch with no content yet gets an empty "
                        "index instead of a 404. Runs independent of, and "
                        "before, the normal ingest/sort")
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
    """Replace path atomically, and only once the new content is really on
    disk. A short write (ENOSPC on the feed server is a real possibility)
    must never be renamed over a good files-sorted/Packages/arch map: that
    would silently truncate state nothing else can reconstruct."""
    path = Path(path)
    tmp = path.parent / (path.name + ".tmp.%d" % os.getpid())
    data = text.encode("utf-8")
    try:
        with open(str(tmp), "wb") as f:
            f.write(data)
            f.flush()
            os.fsync(f.fileno())
        written = tmp.stat().st_size
        if written != len(data):
            raise IOError("short write to %s: %d of %d bytes"
                          % (tmp, written, len(data)))
        os.replace(str(tmp), str(path))
    except BaseException:
        try:
            tmp.unlink()
        except OSError:
            pass
        raise


def resolve_feed_dir(arg):
    if arg:
        feed_dir = Path(arg)
        if not (feed_dir / "unsorted").is_dir():
            log.error("%s does not contain an unsorted/ directory", feed_dir)
            return None
        return feed_dir.resolve()
    cwd = Path.cwd().resolve()
    if cwd.name == "unsorted":
        return cwd.parent
    # Walk up so this also works from anywhere under the feed tree (e.g.
    # incoming/, or a sorted arch subdir), not just the feed base itself.
    for candidate in (cwd, *cwd.parents):
        if (candidate / "unsorted").is_dir():
            return candidate
    log.error("Not in or under a feed directory (no unsorted/ found); use --feed-dir")
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
    unioned. Returns True if the target changed, False if it did not need
    to, None if the delivered map could not be used at all (the caller
    counts that as a failed ingest: it passed its checksum, so the client
    shipped something broken and will not know unless we say so)."""
    try:
        with open(str(incoming)) as f:
            new = json.load(f)
    except (OSError, ValueError) as exc:
        log.error("Delivered arch map is unreadable, not merged: %s", exc)
        return None
    if not isinstance(new, dict):
        log.error("Delivered arch map is not a JSON object, not merged")
        return None
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
    # Read the merged map back: everything downstream sorts by it, so an
    # unparseable result must be reported here, not discovered as "unknown
    # arch" on every package of the next run.
    try:
        with open(str(target)) as f:
            written = json.load(f)
    except (OSError, ValueError) as exc:
        log.error("Arch map %s is unreadable after being written: %s", target, exc)
        return None
    if written != merged:
        log.error("Arch map %s does not match what was written", target)
        return None
    log.info("Arch map %s updated from upload (%d machines)",
             target, len(merged.get("machines", {})))
    return True


def move_into_pool(src, dest, stage_name):
    """Move one verified file into unsorted/ and confirm it landed."""
    try:
        os.replace(str(src), str(dest))
    except OSError as exc:
        log.error("%s: cannot move %s into the pool: %s", stage_name, src.name, exc)
        return False
    if not dest.exists():
        log.error("%s: %s is not in the pool after being moved there",
                  stage_name, dest.name)
        return False
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
    Returns (ingested, rejected, unusable) counts: `rejected` is a failed
    sha256, `unusable` is anything else that stopped a manifested file from
    reaching the pool. Both are hard failures for the run -- the client is
    told the upload was consumed, so nothing else would ever notice.

    A consumed staging directory is always removed, even when part of it
    failed: query_remote_known() on the client counts a COMPLETE stage's
    manifest as "the server has these", so leaving one behind would
    permanently suppress the re-upload that fixes the problem."""
    incoming = feed_dir / "incoming"
    unsorted_dir = feed_dir / "unsorted"
    ingested = 0
    rejected = 0
    unusable = 0
    if not incoming.is_dir():
        return 0, 0, 0
    for stage in sorted(p for p in incoming.iterdir() if p.is_dir()):
        if not (stage / "COMPLETE").exists():
            log.info("Skipping incomplete upload %s", stage.name)
            continue
        manifest = stage / "MANIFEST.sha256"
        if not manifest.exists():
            # upload-packages.py always uploads the manifest before creating
            # COMPLETE, so a marker with no manifest can only be a damaged
            # stage -- ingesting it would put unverified (possibly truncated)
            # files in the feed. Left in place for inspection; the client's
            # query_remote_known() only reads manifests, so leaving it does
            # not suppress the re-upload.
            count = sum(1 for _ in stage.rglob("*.ipk"))
            log.error("%s: COMPLETE marker but no MANIFEST.sha256; %d file(s) "
                      "NOT ingested, stage left in place", stage.name, count)
            unusable += max(count, 1)
            continue
        log.info("Ingesting verified upload %s", stage.name)
        try:
            entries = parse_manifest(manifest)
        except OSError as exc:
            log.error("%s: cannot read MANIFEST.sha256: %s", stage.name, exc)
            unusable += 1
            continue
        if not entries:
            log.error("%s: MANIFEST.sha256 has no usable entries, nothing "
                      "ingested from this upload", stage.name)
            unusable += 1
            continue
        for digest, relpath in entries:
            src = stage / relpath
            name = src.name
            if not src.exists():
                # COMPLETE was set, so the client believes it sent this.
                log.error("%s: manifest entry missing on disk: %s",
                          stage.name, relpath)
                unusable += 1
                continue
            try:
                actual = sha256_file(src)
            except OSError as exc:
                log.error("%s: cannot read %s: %s", stage.name, relpath, exc)
                unusable += 1
                continue
            if actual != digest:
                rejected += 1
                log.error("%s: checksum mismatch, rejecting %s", stage.name, relpath)
                if not dry_run:
                    with open(str(incoming / "rejected.log"), "a") as f:
                        f.write("%d REJECTED %s from %s\n" %
                                (int(time.time()), relpath, stage.name))
                continue
            if name == ARCH_MAP_NAME:
                if not dry_run and merge_arch_map(arch_map_path, src) is None:
                    unusable += 1
                continue
            if not name.endswith(".ipk"):
                log.warning("%s: ignoring non-package file %s", stage.name, relpath)
                continue
            if dry_run:
                ingested += 1
            elif move_into_pool(src, unsorted_dir / name, stage.name):
                ingested += 1
            else:
                unusable += 1
        if not dry_run:
            shutil.rmtree(str(stage))
    return ingested, rejected, unusable


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


PACKAGE_NAME_RE = re.compile(r"^([^_]+)_")


def package_name(ipk_filename):
    m = PACKAGE_NAME_RE.match(ipk_filename)
    return m.group(1) if m else None


def drop_packages(unsorted_dir, names, dry_run):
    """Remove every files-sorted entry for the given package name(s), across
    all versions/architectures. Does not touch already-sorted ipks in the
    feed tree: the next matching upload is treated as new again, and sorting
    it overwrites the old file in place via the normal os.replace."""
    names = set(names)
    sorted_names = read_sorted_list(unsorted_dir)
    dropped = sorted(n for n in sorted_names if package_name(n) in names)
    if not dropped:
        log.info("--drop %s: no files-sorted entries matched",
                 ", ".join(sorted(names)))
        return 0
    log.info("--drop %s: removing %d files-sorted entries",
             ", ".join(sorted(names)), len(dropped))
    for n in dropped:
        log.info("  %s", n)
    if not dry_run:
        atomic_write_text(unsorted_dir / "files-sorted",
                          "".join(n + "\n" for n in sorted(sorted_names - set(dropped))))
    return len(dropped)


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
    """Rewrite the dedupe list and read it back. Nothing reconstructs this
    file: if it silently lost entries the next upload would re-deliver
    packages that are already in the feed. Returns True on success."""
    merged = sorted(sorted_names | set(moved))
    if not dry_run:
        atomic_write_text(unsorted_dir / "files-sorted",
                          "".join(n + "\n" for n in merged))
        readback = read_sorted_list(unsorted_dir)
        if readback != set(merged):
            log.error("files-sorted is wrong after writing it: %d entries on "
                      "disk, %d expected", len(readback), len(merged))
            return False
    log.info("files-sorted now lists %d packages", len(merged))
    return True


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
    (same post-treatment sort.sh applied to every index). Returns True only if
    the feed really ends up with a matching Packages/Packages.gz pair -- an
    index a client cannot read is a failure even though nothing raised."""
    packages = directory / "Packages"
    if not packages.exists():
        log.error("%s: indexing reported success but wrote no Packages file",
                  directory)
        return False
    lines = [l for l in packages.read_text(encoding="utf-8").splitlines()
             if not l.startswith("Source:")]
    text = "".join(l + "\n" for l in lines)
    atomic_write_text(packages, text)
    gz = directory / "Packages.gz"
    tmp = directory / ("Packages.gz.tmp.%d" % os.getpid())
    try:
        with open(str(tmp), "wb") as f:
            with gzip.GzipFile(filename="Packages", mode="wb", fileobj=f,
                               compresslevel=9, mtime=0) as z:
                z.write(text.encode("utf-8"))
            f.flush()
            os.fsync(f.fileno())
        os.replace(str(tmp), str(gz))
    except BaseException:
        try:
            tmp.unlink()
        except OSError:
            pass
        raise
    # opkg fetches Packages.gz, not Packages: verify it decompresses back to
    # exactly what we indexed rather than trusting that it was written.
    try:
        with gzip.open(str(gz), "rb") as z:
            roundtrip = z.read().decode("utf-8")
    except (OSError, ValueError, UnicodeDecodeError) as exc:
        log.error("%s: Packages.gz is not readable after writing it: %s",
                  directory, exc)
        return False
    if roundtrip != text:
        log.error("%s: Packages.gz does not match Packages (%d vs %d bytes)",
                  directory, len(roundtrip), len(text))
        return False
    (directory / "Packages.sig").touch()
    return True


def index_feeds(feed_dir, touched, tool):
    """Re-index every feed dir that received files. Returns the number of
    failures."""
    failures = 0
    for directory in sorted(touched):
        if not run_index(tool, directory):
            failures += 1
        elif not postprocess_index(directory):
            failures += 1
    return failures


def ensure_placeholder_archs(feed_dir, archs, tool, dry_run):
    """Guarantee a valid (possibly empty) Packages/Packages.gz under
    <feed-dir>/<arch>/base/ for every arch in `archs` -- typically a
    machine's full PACKAGE_EXTRA_ARCHS compatibility ladder, not just its
    exact tune. A client's opkg.conf can list a less-specific compatible
    arch (its arch.conf already ranks it at a real priority) long before any
    package is ever actually built for it; without a placeholder index,
    `opkg update` 404s on that feed line instead of just seeing zero
    packages. Once real content lands (normal sort/index path), it
    overwrites the placeholder the ordinary way -- this only fills a gap
    that would otherwise 404, never removes or shadows real content.
    Returns (created, failed): the number of placeholders created (or that
    dry-run would create) and the number that could not be."""
    created = 0
    failed = 0
    if not dry_run and archs and shutil.which(tool) is None:
        log.error("%s not found; cannot create placeholder indexes for %s",
                  tool, ", ".join(archs))
        return 0, len(archs)
    for arch in archs:
        directory = feed_dir / arch / "base"
        if (directory / "Packages").exists():
            continue
        log.info("Placeholder index: %s (no content yet)", directory)
        if dry_run:
            created += 1
            continue
        directory.mkdir(parents=True, exist_ok=True)
        if run_index(tool, directory) and postprocess_index(directory):
            created += 1
        else:
            log.error("Failed to create placeholder index at %s", directory)
            failed += 1
    return created, failed


def report_outcome(feed_dir, args, stats, reasons):
    """Emit the one line a caller (or a cron mail) should have to read, and
    turn it into the process exit code. Every terminal state prints exactly
    one SORT-PACKAGES: SUCCESS / FAILED line -- no run of this script ends
    without one."""
    fields = ("feed-dir=%s ingested=%d rejected=%d unusable=%d sorted=%d "
              "feeds=%d unknown=%d dry-run=%d"
              % (feed_dir, stats["ingested"], stats["rejected"],
                 stats["unusable"], stats["sorted"], stats["feeds"],
                 stats["unknown"], 1 if args.dry_run else 0))
    if reasons:
        for reason in reasons:
            log.error("SORT-PACKAGES: reason: %s", reason)
        log.error("SORT-PACKAGES: FAILED %s", fields)
        return 2
    if stats["unknown"]:
        # Not a failure (the files stay in unsorted/ and sort once the map
        # knows their arch) but it must not vanish into the log either.
        log.warning("SORT-PACKAGES: %d package(s) left unsorted with an "
                    "unknown arch -- update feed-arch-map.json", stats["unknown"])
    log.info("SORT-PACKAGES: SUCCESS %s", fields)
    return 0


def _run_sort(args, feed_dir):
    unsorted_dir = feed_dir / "unsorted"
    arch_map_path = Path(args.config) if args.config else feed_dir / ARCH_MAP_NAME

    stats = {"ingested": 0, "rejected": 0, "unusable": 0,
             "sorted": 0, "feeds": 0, "unknown": 0}
    reasons = []

    if args.drop:
        drop_packages(unsorted_dir, args.drop, args.dry_run)

    if args.ensure_archs:
        archs = [a for a in re.split(r"[,\s]+", args.ensure_archs.strip()) if a]
        created, failed = ensure_placeholder_archs(
            feed_dir, archs, args.opkg_make_index, args.dry_run)
        if created:
            log.info("%s placeholder index(es) for %d arch(es)",
                     "Would create" if args.dry_run else "Created", created)
        if failed:
            reasons.append("%d placeholder index(es) could not be created "
                           "(--ensure-archs)" % failed)

    ingested, rejected, unusable = ingest_incoming(feed_dir, arch_map_path,
                                                   args.dry_run)
    stats["ingested"] = ingested
    stats["rejected"] = rejected
    stats["unusable"] = unusable
    if ingested or rejected:
        log.info("Ingest: %d files accepted, %d rejected", ingested, rejected)
    if rejected:
        reasons.append("%d staged file(s) failed sha256 verification and were "
                       "rejected" % rejected)
    if unusable:
        reasons.append("%d staged file(s) could not be ingested" % unusable)

    flatten_pool(unsorted_dir, args.dry_run)

    pool = sorted(unsorted_dir.glob("*.ipk"))
    if not pool:
        log.info("No unsorted packages, nothing to do")
        return report_outcome(feed_dir, args, stats, reasons)

    archmap = ArchMap.load(arch_map_path)
    if archmap is None:
        log.error("No usable arch map at %s; deliver one with "
                  "upload-packages.py --arch-map or pass --config", arch_map_path)
        # Configuration failure, not a sorting failure: keep exit 1 for it.
        log.error("SORT-PACKAGES: FAILED feed-dir=%s no usable arch map at %s",
                  feed_dir, arch_map_path)
        return 1

    sorted_names = read_sorted_list(unsorted_dir)
    dedupe_pool(unsorted_dir, sorted_names, args.dry_run)

    touched, moved, unknown = sort_pool(feed_dir, unsorted_dir, archmap,
                                        args.dry_run)
    stats["sorted"] = len(moved)
    stats["feeds"] = len(touched)
    stats["unknown"] = len(unknown)
    log.info("Sorted %d packages into %d feed directories, %d unknown",
             len(moved), len(touched), len(unknown))

    if args.dry_run:
        return report_outcome(feed_dir, args, stats, reasons)

    if moved and not args.skip_sorted_list:
        if not update_sorted_list(unsorted_dir, sorted_names, moved, args.dry_run):
            reasons.append("files-sorted could not be updated; the next upload "
                           "would re-deliver packages already in the feed")
    update_ticker(feed_dir, moved, args.dry_run)

    indexed_ok = True
    if moved and not args.skip_index:
        if shutil.which(args.opkg_make_index) is None:
            log.error("%s not found; indexes not rebuilt (--skip-index to "
                      "silence)", args.opkg_make_index)
            reasons.append("%s not found, %d feed index(es) not rebuilt"
                           % (args.opkg_make_index, len(touched)))
            indexed_ok = False
        else:
            failures = index_feeds(feed_dir, touched, args.opkg_make_index)
            if failures:
                reasons.append("%d of %d feed index(es) failed to rebuild; "
                               "those feeds are stale" % (failures, len(touched)))
                indexed_ok = False

    # Unchanged rule: the post command only runs on a clean sort+index, so it
    # never publishes a half-updated feed.
    if args.post_command and indexed_ok and not reasons and moved:
        log.info("Running post command: %s", args.post_command)
        proc = subprocess.run(args.post_command, shell=True, cwd=str(feed_dir))
        if proc.returncode != 0:
            log.error("Post command exited %d", proc.returncode)
            reasons.append("post command exited %d" % proc.returncode)
    elif args.post_command and moved:
        log.error("Post command not run: the sort did not complete cleanly")

    return report_outcome(feed_dir, args, stats, reasons)


def main(argv=None):
    args = parse_args(argv)
    logging.basicConfig(
        level=getattr(logging, args.log_level.upper()),
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S")

    feed_dir = None
    lock_fh = None
    try:
        feed_dir = resolve_feed_dir(args.feed_dir)
        if feed_dir is None:
            log.error("SORT-PACKAGES: FAILED no feed directory resolved")
            return 1

        unsorted_dir = feed_dir / "unsorted"
        if not unsorted_dir.is_dir():
            log.error("SORT-PACKAGES: FAILED feed-dir=%s has no unsorted/ "
                      "directory", feed_dir)
            return 1

        # Advisory exclusive lock so two ingest/sort runs against the same
        # feed dir never race each other -- observed live: a concurrent run
        # left a just-staged upload partially consumed (files moved
        # mid-check) with no error from either side. Fail fast instead of
        # racing.
        lock_path = feed_dir / ".sort-packages.lock"
        try:
            lock_fh = open(str(lock_path), "a")
        except OSError as exc:
            log.error("SORT-PACKAGES: FAILED cannot open the lock file %s: %s",
                      lock_path, exc)
            return 1
        try:
            fcntl.flock(lock_fh, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            log.error("Another sort-packages.py is already running against %s "
                      "(holds %s) -- not racing it, try again shortly",
                      feed_dir, lock_path)
            log.error("SORT-PACKAGES: FAILED feed-dir=%s locked by another run",
                      feed_dir)
            return 1

        return _run_sort(args, feed_dir)
    except Exception as exc:
        # Never let a traceback be the only record: an unexpected failure
        # gets the same greppable sentinel as an expected one.
        log.exception("Unhandled error during sort: %s", exc)
        log.error("SORT-PACKAGES: FAILED feed-dir=%s unhandled error: %s",
                  feed_dir if feed_dir is not None else "(unresolved)", exc)
        return 2
    finally:
        if lock_fh is not None:
            fcntl.flock(lock_fh, fcntl.LOCK_UN)
            lock_fh.close()


if __name__ == "__main__":
    sys.exit(main())
