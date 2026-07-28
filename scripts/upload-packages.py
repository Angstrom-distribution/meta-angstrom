#!/usr/bin/env python3
# MIT Licensed
# Upload built ipk packages from DEPLOY_DIR_IPK to the Angstrom feed server.
#
# Replacement for old/contrib/upload-packages.sh and the 2013
# old/recipes-angstrom/meta/upload-packages.bb. Stdlib only; the actual
# transfer is delegated to rsync over ssh.
#
# Protocol (cooperates with old/contrib/sort.sh on the server):
#   1. Scan DEPLOY_DIR_IPK recursively (per-PACKAGE_ARCH subdirs), skipping
#      morgue/ directories. Hash every candidate (sha256, cached locally by
#      size+mtime so unchanged files are never re-hashed).
#   2. Ask the server, in ONE ssh round trip, which package basenames it
#      already knows: unsorted/files-sorted + the unsorted/ pool + manifests
#      of completed-but-not-yet-sorted staged uploads. Only new names are
#      uploaded (use --force to override).
#   3. rsync the new files into a staging directory
#      <remote-dir>/incoming/<upload-id>/ - never straight into unsorted/ -
#      using --partial-dir so an interrupted transfer can never leave a
#      truncated file under its real name. The upload id is derived from the
#      content hashes, so re-running after a partial failure resumes the same
#      staging directory instead of starting over.
#   4. When every file has transferred, upload MANIFEST.sha256 and create the
#      COMPLETE marker. sort.sh only ingests staging directories that have
#      the marker, and verifies each file against the manifest before moving
#      it into the unsorted/ pool. Corrupt files are rejected server-side and
#      will simply be re-uploaded on the next run.
#
# Configuration comes from the command line, or (when run standalone in an
# initialised build directory) from bitbake-getvar. When invoked from the
# upload-packages recipe every value is passed explicitly, so no bitbake
# server round trips happen inside the task.
#
# A local directory can stand in for the remote server by passing
# --remote '' --remote-dir /some/path (used for testing without credentials).

import argparse
import concurrent.futures
import fnmatch
import hashlib
import json
import logging
import os
import random
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

log = logging.getLogger("upload-packages")

# rsync exit codes that indicate a permanent problem; everything else
# (network drops, partial transfers, ssh failures) is worth retrying.
RSYNC_PERMANENT_ERRORS = {1, 2, 4, 6}

CHUNK_READ_SIZE = 1024 * 1024


def parse_args(argv):
    p = argparse.ArgumentParser(
        description="Upload built ipk packages to the Angstrom feed server")
    p.add_argument("--deploy-dir-ipk", metavar="DIR",
                   help="DEPLOY_DIR_IPK to scan (default: bitbake-getvar DEPLOY_DIR_IPK)")
    p.add_argument("--remote", metavar="USER@HOST",
                   help="remote ssh account; empty string selects local-filesystem "
                        "mode (default: bitbake-getvar ANGSTROM_UPLOAD_REMOTE)")
    p.add_argument("--remote-dir", metavar="DIR",
                   help="feed base directory on the remote, e.g. website/feeds/v2026.06/ipk/glibc/ "
                        "(default: bitbake-getvar ANGSTROM_UPLOAD_REMOTE_DIR, falling back "
                        "to website/${FEED_BASEPATH})")
    p.add_argument("--build-dir", metavar="DIR", default=None,
                   help="build directory to run bitbake-getvar in (default: $BBPATH head or cwd)")
    p.add_argument("--jobs", type=int, default=4, metavar="N",
                   help="parallel rsync streams and hashing threads (default: 4)")
    p.add_argument("--retries", type=int, default=3, metavar="N",
                   help="retries per rsync stream on transient failure (default: 3)")
    p.add_argument("--exclude", action="append", default=[], metavar="GLOB",
                   help="exclude package basenames matching GLOB (repeatable)")
    p.add_argument("--force", action="store_true",
                   help="upload even if the server already knows the file name")
    p.add_argument("--dry-run", action="store_true",
                   help="scan, hash and plan, but do not transfer anything")
    p.add_argument("--skip-remote-check", action="store_true",
                   help="do not query the server for known files (treat everything as new)")
    p.add_argument("--cache-file", metavar="FILE",
                   help="local sha256 cache (default: ~/.cache/angstrom-upload-packages/<id>.json)")
    p.add_argument("--arch-map", metavar="FILE", default=None,
                   help="feed-arch-map.json to deliver to the server inside the "
                        "verified upload (default: the one next to this script "
                        "if present; pass an empty string to disable)")
    p.add_argument("--json-summary", metavar="FILE",
                   help="write a machine-readable summary of the run to FILE")
    p.add_argument("--ssh-opt", action="append", default=[], metavar="OPT",
                   help="extra option passed to ssh, e.g. --ssh-opt='-p 2222' (repeatable)")
    p.add_argument("--rsync", default="rsync", metavar="PATH",
                   help="rsync executable (default: rsync)")
    p.add_argument("--log-level", default="info",
                   choices=["debug", "info", "warning", "error"])
    return p.parse_args(argv)


def bitbake_getvar(var, build_dir):
    """Resolve a single bitbake variable via bitbake-getvar; None if unavailable."""
    cmd = ["bitbake-getvar", "--value", "-q", "--ignore-undefined", var]
    try:
        proc = subprocess.run(cmd, cwd=build_dir, capture_output=True,
                              text=True, timeout=300)
    except FileNotFoundError:
        log.debug("bitbake-getvar not in PATH")
        return None
    except subprocess.TimeoutExpired:
        log.warning("bitbake-getvar %s timed out", var)
        return None
    if proc.returncode != 0:
        log.debug("bitbake-getvar %s failed: %s", var, proc.stderr.strip())
        return None
    # Be defensive about stray server log lines: take the last non-empty line.
    lines = [l for l in proc.stdout.splitlines() if l.strip()]
    return lines[-1].strip() if lines else None


def resolve_config(args):
    """Fill in missing configuration from bitbake-getvar."""
    build_dir = args.build_dir
    if build_dir is None:
        bbpath = os.environ.get("BBPATH", "")
        build_dir = bbpath.split(":")[0] if bbpath else os.getcwd()

    def need(name):
        return getattr(args, name) is None

    if need("deploy_dir_ipk") or need("remote") or need("remote_dir"):
        log.info("Resolving configuration with bitbake-getvar in %s", build_dir)

    if need("deploy_dir_ipk"):
        args.deploy_dir_ipk = bitbake_getvar("DEPLOY_DIR_IPK", build_dir)
    if need("remote"):
        args.remote = bitbake_getvar("ANGSTROM_UPLOAD_REMOTE", build_dir)
    if need("remote_dir"):
        args.remote_dir = bitbake_getvar("ANGSTROM_UPLOAD_REMOTE_DIR", build_dir)
        if args.remote_dir is None:
            basepath = bitbake_getvar("FEED_BASEPATH", build_dir)
            if basepath:
                args.remote_dir = "website/" + basepath

    errors = []
    if not args.deploy_dir_ipk:
        errors.append("--deploy-dir-ipk (or DEPLOY_DIR_IPK via bitbake-getvar)")
    if args.remote is None:
        errors.append("--remote (or ANGSTROM_UPLOAD_REMOTE via bitbake-getvar); "
                      "use --remote '' for local-filesystem mode")
    if not args.remote_dir:
        errors.append("--remote-dir (or ANGSTROM_UPLOAD_REMOTE_DIR/FEED_BASEPATH via bitbake-getvar)")
    if errors:
        for e in errors:
            log.error("missing configuration: %s", e)
        return False
    args.remote_dir = args.remote_dir.rstrip("/")
    return True


def scan_deploy_dir(deploy_dir, excludes):
    """Return {relpath: (size, mtime_ns)} for every candidate ipk."""
    root = Path(deploy_dir)
    if not root.is_dir():
        log.error("DEPLOY_DIR_IPK does not exist: %s", root)
        return None
    found = {}
    skipped = 0
    for path in sorted(root.rglob("*.ipk")):
        rel = path.relative_to(root)
        # never touch opkg's morgue directories or rsync partial dirs
        if any(part in ("morgue", ".rsync-partial") for part in rel.parts):
            continue
        if any(fnmatch.fnmatch(path.name, pat) for pat in excludes):
            skipped += 1
            continue
        st = path.stat()
        found[str(rel)] = (st.st_size, st.st_mtime_ns)
    log.info("Found %d ipk files under %s (%d excluded by pattern)",
             len(found), root, skipped)
    return found


def default_cache_file(deploy_dir):
    cache_home = Path(os.environ.get("XDG_CACHE_HOME",
                                     Path.home() / ".cache"))
    tag = hashlib.sha256(str(Path(deploy_dir).resolve()).encode()).hexdigest()[:16]
    return cache_home / "angstrom-upload-packages" / (tag + ".json")


def load_cache(cache_file):
    try:
        with open(cache_file) as f:
            data = json.load(f)
        if isinstance(data, dict):
            return data
    except (OSError, ValueError):
        pass
    return {}


def save_cache(cache_file, cache):
    cache_file = Path(cache_file)
    cache_file.parent.mkdir(parents=True, exist_ok=True)
    tmp = cache_file.with_suffix(".tmp.%d" % os.getpid())
    with open(tmp, "w") as f:
        json.dump(cache, f)
    os.replace(tmp, cache_file)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            block = f.read(CHUNK_READ_SIZE)
            if not block:
                break
            h.update(block)
    return h.hexdigest()


def hash_files(deploy_dir, files, cache, jobs):
    """Return {relpath: sha256}, reusing cache entries whose size+mtime match."""
    root = Path(deploy_dir)
    hashes = {}
    todo = []
    for rel, (size, mtime_ns) in files.items():
        entry = cache.get(rel)
        if entry and entry.get("size") == size and entry.get("mtime_ns") == mtime_ns:
            hashes[rel] = entry["sha256"]
        else:
            todo.append(rel)
    if todo:
        log.info("Hashing %d new/changed files (%d cached)", len(todo), len(hashes))
        with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, jobs)) as ex:
            for rel, digest in zip(todo, ex.map(
                    lambda r: sha256_file(root / r), todo)):
                hashes[rel] = digest
                size, mtime_ns = files[rel]
                cache[rel] = {"size": size, "mtime_ns": mtime_ns, "sha256": digest}
    else:
        log.info("All %d file hashes served from cache", len(hashes))
    # drop cache entries for files that no longer exist
    for stale in set(cache) - set(files):
        del cache[stale]
    return hashes


class Transport:
    """ssh/rsync against user@host, or plain sh/rsync against a local dir."""

    def __init__(self, remote, remote_dir, ssh_opts, rsync):
        self.remote = remote          # "" means local mode
        self.remote_dir = remote_dir
        self.rsync = rsync
        self.ssh_cmd = ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=30"]
        for opt in ssh_opts:
            self.ssh_cmd.extend(shlex.split(opt))

    def run_shell(self, snippet, timeout=300):
        """Run a POSIX sh snippet remotely (or locally in local mode)."""
        if self.remote:
            cmd = self.ssh_cmd + [self.remote, snippet]
        else:
            cmd = ["sh", "-c", snippet]
        log.debug("shell: %s", " ".join(shlex.quote(c) for c in cmd))
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)

    def rsync_dest(self, subpath):
        path = "%s/%s" % (self.remote_dir, subpath)
        return "%s:%s" % (self.remote, path) if self.remote else path

    def rsync_cmd(self, extra):
        cmd = [self.rsync, "--perms", "--times", "--partial-dir=.rsync-partial"]
        if self.remote:
            cmd += ["-e", " ".join(self.ssh_cmd)]
        return cmd + extra


def query_remote_known(transport):
    """One round trip: names the server already has (sorted list, unsorted
    pool, completed-but-unsorted staged uploads). Also pre-creates the
    directories we are about to use. Returns a set of basenames, or None on
    failure."""
    rd = shlex.quote(transport.remote_dir)
    snippet = """
set -e
mkdir -p {rd}/unsorted {rd}/incoming
cat {rd}/unsorted/files-sorted 2>/dev/null || :
find {rd}/unsorted -maxdepth 1 -name '*.ipk' 2>/dev/null | while read -r f; do
    basename "$f"
done
for marker in {rd}/incoming/*/COMPLETE; do
    [ -f "$marker" ] || continue
    stage=$(dirname "$marker")
    if [ -f "$stage/MANIFEST.sha256" ]; then
        sed -e 's/^[0-9a-fA-F]*  //' -e 's:.*/::' "$stage/MANIFEST.sha256"
    fi
done
""".format(rd=rd)
    try:
        proc = transport.run_shell(snippet)
    except subprocess.TimeoutExpired:
        log.error("Remote state query timed out")
        return None
    if proc.returncode != 0:
        log.error("Remote state query failed (%d): %s",
                  proc.returncode, proc.stderr.strip())
        return None
    known = {line.strip() for line in proc.stdout.splitlines() if line.strip()}
    log.info("Server already knows %d package names", len(known))
    return known


def make_upload_id(upload, hashes):
    ident = hashlib.sha256()
    for rel in sorted(upload):
        ident.update(("%s %s\n" % (hashes[rel], rel)).encode())
    return ident.hexdigest()[:16]


def balance_chunks(upload, files, jobs):
    """Greedy longest-processing-time split of the upload set into at most
    `jobs` lists, balanced by file size."""
    n = max(1, min(jobs, len(upload)))
    chunks = [[] for _ in range(n)]
    weights = [0] * n
    for rel in sorted(upload, key=lambda r: files[r][0], reverse=True):
        i = weights.index(min(weights))
        chunks[i].append(rel)
        weights[i] += files[rel][0]
    return [c for c in chunks if c]


def rsync_chunk(transport, deploy_dir, chunk, dest, scratch, tag, retries):
    """rsync one list of relative paths, with retry and backoff. Returns True
    on success."""
    files_from = Path(scratch) / ("files-from.%s" % tag)
    files_from.write_text("".join(rel + "\n" for rel in chunk))
    cmd = transport.rsync_cmd([
        "--files-from=%s" % files_from,
        str(Path(deploy_dir)) + "/",
        dest,
    ])
    for attempt in range(retries + 1):
        if attempt:
            delay = min(60, 5 * (2 ** (attempt - 1))) + random.uniform(0, 2)
            log.warning("stream %s: retrying in %.1fs (attempt %d/%d)",
                        tag, delay, attempt + 1, retries + 1)
            time.sleep(delay)
        log.debug("stream %s: %s", tag, " ".join(shlex.quote(c) for c in cmd))
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode == 0:
            log.info("stream %s: %d files transferred", tag, len(chunk))
            return True
        log.warning("stream %s: rsync exited %d: %s", tag, proc.returncode,
                    proc.stderr.strip().splitlines()[-1] if proc.stderr.strip() else "")
        if proc.returncode in RSYNC_PERMANENT_ERRORS:
            log.error("stream %s: permanent rsync error %d, not retrying",
                      tag, proc.returncode)
            return False
    log.error("stream %s: giving up after %d attempts", tag, retries + 1)
    return False


ARCH_MAP_NAME = "feed-arch-map.json"


def resolve_arch_map(arg):
    """Which feed-arch-map.json (if any) to ship with the upload."""
    if arg == "":
        return None
    if arg is not None:
        path = Path(arg)
        if not path.is_file():
            log.error("--arch-map %s does not exist", path)
            return False
        return path
    default = Path(__file__).resolve().parent / ARCH_MAP_NAME
    return default if default.is_file() else None


def finalize_upload(transport, scratch, upload, hashes, upload_id, retries,
                    arch_map):
    """Upload MANIFEST.sha256 (and the arch map, if any) and create the
    COMPLETE marker. The arch map rides inside the verified upload: it is
    listed in the manifest, so the server ingests it with the same checksum
    guarantee as the packages."""
    entries = dict((rel, hashes[rel]) for rel in upload)
    finalize_files = ["MANIFEST.sha256"]
    if arch_map is not None:
        staged_map = Path(scratch) / ARCH_MAP_NAME
        shutil.copyfile(str(arch_map), str(staged_map))
        entries[ARCH_MAP_NAME] = sha256_file(staged_map)
        finalize_files.append(ARCH_MAP_NAME)
        log.info("Including %s in the upload", arch_map)
    manifest = Path(scratch) / "MANIFEST.sha256"
    manifest.write_text("".join(
        "%s  %s\n" % (entries[rel], rel) for rel in sorted(entries)))
    dest = transport.rsync_dest("incoming/%s/" % upload_id)
    if not rsync_chunk(transport, scratch, finalize_files, dest,
                       scratch, "manifest", retries):
        return False
    marker = "%s/incoming/%s/COMPLETE" % (transport.remote_dir, upload_id)
    try:
        proc = transport.run_shell("touch %s" % shlex.quote(marker), timeout=60)
    except subprocess.TimeoutExpired:
        log.error("Creating COMPLETE marker timed out")
        return False
    if proc.returncode != 0:
        log.error("Creating COMPLETE marker failed: %s", proc.stderr.strip())
        return False
    return True


def write_summary(path, summary):
    if not path:
        return
    with open(path, "w") as f:
        json.dump(summary, f, indent=2, sort_keys=True)
        f.write("\n")


def main(argv=None):
    args = parse_args(argv)
    logging.basicConfig(
        level=getattr(logging, args.log_level.upper()),
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S")

    if not resolve_config(args):
        return 1

    if shutil.which(args.rsync) is None:
        log.error("rsync not found in PATH; inside a bitbake task this needs "
                  "HOSTTOOLS_NONFATAL to include rsync and rsync installed on the host")
        return 1
    if args.remote and shutil.which("ssh") is None:
        log.error("ssh not found in PATH")
        return 1

    started = time.time()
    files = scan_deploy_dir(args.deploy_dir_ipk, args.exclude)
    if files is None:
        return 1

    cache_file = args.cache_file or default_cache_file(args.deploy_dir_ipk)
    cache = load_cache(cache_file)
    hashes = hash_files(args.deploy_dir_ipk, files, cache, args.jobs)
    save_cache(cache_file, cache)

    transport = Transport(args.remote, args.remote_dir, args.ssh_opt, args.rsync)

    arch_map = resolve_arch_map(args.arch_map)
    if arch_map is False:
        return 1

    known = set()
    if args.skip_remote_check:
        log.info("Skipping remote state query (--skip-remote-check)")
    else:
        # The query is read-only apart from mkdir -p, so it is safe (and most
        # informative) to run it for --dry-run too.
        known = query_remote_known(transport)
        if known is None:
            if args.dry_run:
                log.warning("Continuing dry run as if the server knew nothing")
                known = set()
            else:
                return 2

    if args.force:
        upload = sorted(files)
    else:
        upload = sorted(rel for rel in files if Path(rel).name not in known)

    summary = {
        "deploy_dir_ipk": str(args.deploy_dir_ipk),
        "remote": args.remote,
        "remote_dir": args.remote_dir,
        "scanned": len(files),
        "already_present": len(files) - len(upload),
        "planned": len(upload),
        "uploaded": 0,
        "dry_run": args.dry_run,
        "success": False,
    }

    if not upload:
        log.info("Nothing to upload: all %d packages already on the server", len(files))
        summary["success"] = True
        write_summary(args.json_summary, summary)
        return 0

    total_bytes = sum(files[rel][0] for rel in upload)
    upload_id = make_upload_id(upload, hashes)
    summary["upload_id"] = upload_id
    log.info("Uploading %d packages (%.1f MiB) as upload %s, %d already present",
             len(upload), total_bytes / 1048576.0, upload_id,
             len(files) - len(upload))

    if args.dry_run:
        for rel in upload:
            log.info("would upload: %s", rel)
        summary["success"] = True
        write_summary(args.json_summary, summary)
        return 0

    dest = transport.rsync_dest("incoming/%s/" % upload_id)
    chunks = balance_chunks(upload, files, args.jobs)
    log.info("Transferring in %d parallel stream(s) to %s", len(chunks), dest)

    # Pre-create the staging subdirectories in one round trip so parallel
    # rsync receivers do not race each other creating the same directory.
    stage_dirs = sorted({
        "%s/incoming/%s/%s" % (transport.remote_dir, upload_id,
                               os.path.dirname(rel))
        for rel in upload})
    proc = transport.run_shell(
        "mkdir -p " + " ".join(shlex.quote(p.rstrip("/")) for p in stage_dirs))
    if proc.returncode != 0:
        log.error("Creating staging directories failed: %s", proc.stderr.strip())
        write_summary(args.json_summary, summary)
        return 2

    ok = True
    with tempfile.TemporaryDirectory(prefix="upload-packages.") as scratch:
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(chunks)) as ex:
            futures = {
                ex.submit(rsync_chunk, transport, args.deploy_dir_ipk, chunk,
                          dest, scratch, str(i), args.retries): chunk
                for i, chunk in enumerate(chunks)}
            for fut in concurrent.futures.as_completed(futures):
                if fut.result():
                    summary["uploaded"] += len(futures[fut])
                else:
                    ok = False

        if not ok:
            log.error("Upload incomplete; staging directory %s is left in place "
                      "and a re-run will resume it", dest)
            write_summary(args.json_summary, summary)
            return 2

        if not finalize_upload(transport, scratch, upload, hashes,
                               upload_id, args.retries, arch_map):
            log.error("Upload transferred but could not be finalized; "
                      "re-run to finish publishing %s", upload_id)
            write_summary(args.json_summary, summary)
            return 2

    summary["success"] = True
    write_summary(args.json_summary, summary)
    log.info("Upload %s complete: %d packages in %.1fs; the server will ingest "
             "it on the next sort run", upload_id, len(upload),
             time.time() - started)
    return 0


if __name__ == "__main__":
    sys.exit(main())
