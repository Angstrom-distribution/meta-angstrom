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
#
# Every run ends with exactly one greppable sentinel line:
#   UPLOAD-PACKAGES: SUCCESS upload=<id|none> scanned=N planned=N uploaded=N ...
#   UPLOAD-PACKAGES: FAILED  <same fields> reason=<what went wrong>
# Exit codes: 0 success (including "nothing to upload" and --dry-run),
# 1 local/configuration failure, bad command line included (nothing was
# sent), 2 the remote side failed
# (query, transfer, staging or finalisation). An upload is only marked
# COMPLETE after the staged file count on the server has been verified, so
# the server never ingests a short upload.

import argparse
import concurrent.futures
import fnmatch
import hashlib
import json
import logging
import os
import random
import re
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

# rsync's own I/O timeout: a stalled socket (a half-open connection survives
# indefinitely otherwise) exits 30, which is transient and so gets retried.
# The wall-clock cap is only a backstop for an rsync that wedges without I/O.
RSYNC_IO_TIMEOUT = 300
RSYNC_STREAM_TIMEOUT = 4 * 3600

CHUNK_READ_SIZE = 1024 * 1024


class SentinelArgumentParser(argparse.ArgumentParser):
    """argparse's own usage errors exit 2, but exit 2 is documented above as
    "the remote side failed" -- a typo'd flag must not be misdiagnosed as a
    remote failure. Usage errors are a local/configuration problem: print the
    sentinel and exit 1 like every other one."""

    def error(self, message):
        self.print_usage(sys.stderr)
        print("UPLOAD-PACKAGES: FAILED reason=bad usage: %s" % message,
              file=sys.stderr)
        sys.exit(1)


def parse_args(argv):
    p = SentinelArgumentParser(
        description="Upload built ipk packages to the Angstrom feed server")
    p.add_argument("--deploy-dir-ipk", metavar="DIR",
                   help="DEPLOY_DIR_IPK to scan (default: bitbake-getvar DEPLOY_DIR_IPK)")
    p.add_argument("--remote", metavar="USER@HOST",
                   help="remote ssh account; empty string selects local-filesystem "
                        "mode (default: bitbake-getvar ANGSTROM_UPLOAD_REMOTE)")
    p.add_argument("--remote-dir", metavar="DIR",
                   help="feed base directory on the remote, e.g. /data/www/angstrom/feeds/v2026.06/ipk/glibc/ "
                        "- must be absolute when uploading over ssh "
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
    # A relative path resolves under the ssh account's home directory, not the
    # feed root -- the whole pipeline would then succeed against a private
    # directory nothing serves. The FEED_BASEPATH fallback above is exactly how
    # that happens, so refuse instead of publishing into nowhere.
    if args.remote and not os.path.isabs(args.remote_dir):
        log.error("remote directory %r is not absolute; over ssh it would "
                  "resolve under %s's home directory instead of the feed root",
                  args.remote_dir, args.remote)
        return False
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
        try:
            st = path.stat()
        except OSError as exc:
            # A build writing into DEPLOY_DIR_IPK underneath us, or an
            # unreadable file: either way the scan is not a true picture of
            # what should be published, so do not upload a partial view.
            log.error("Cannot stat %s: %s", path, exc)
            return None
        found[str(rel)] = (st.st_size, st.st_mtime_ns)
    log.info("Found %d ipk files under %s (%d excluded by pattern)",
             len(found), root, skipped)
    return found


# OE's default split-package suffixes (package.bbclass / bitbake.conf).
# Collapsing on these lets the chat-facing report show one row per recipe
# instead of one row per -dbg/-dev/-src/... variant -- see
# skills/dominion-publish/SKILL.md, "Reporting uploaded packages".
_SUBPACKAGE_SUFFIX_RE = re.compile(
    r"-(dbg|dev|doc|staticdev|src|ptest|lic|locale(?:-.+)?)$")


def base_package_name(pkg_name):
    """Collapse an OE split-package name to its recipe base name for
    reporting -- domoticz-dbg/-dev/-src all report as domoticz. Best-effort:
    a recipe whose real PN happens to end in one of these tokens is rare and
    not specially handled."""
    m = _SUBPACKAGE_SUFFIX_RE.search(pkg_name)
    return pkg_name[:m.start()] if m else pkg_name


def parse_ipk_filename(basename):
    """Split '<name>_<version>_<arch>.ipk' into (name, version, arch).
    PN never contains '_' (it's hyphen-separated), but a git-SRCREV version
    string can -- so split on the FIRST '_' for name and the LAST '_' for
    arch, not a naive 3-way split. Returns None if it doesn't look like an
    ipk filename at all."""
    if not basename.endswith(".ipk"):
        return None
    stem = basename[:-4]
    if "_" not in stem:
        return None
    name, rest = stem.split("_", 1)
    if "_" not in rest:
        return None
    version, arch = rest.rsplit("_", 1)
    return name, version, arch


def group_packages_for_report(relpaths):
    """Group ipk relpaths into {base_name: {"version": v, "archs": {a, ...}}}
    for the chat-facing report -- one entry per base package, not per split
    subpackage. Files that don't parse as '<name>_<version>_<arch>.ipk' are
    silently skipped (this is a report, not a validation pass; scan_deploy_dir
    already validated the files themselves)."""
    groups = {}
    for rel in relpaths:
        parsed = parse_ipk_filename(os.path.basename(rel))
        if parsed is None:
            continue
        name, version, arch = parsed
        base = base_package_name(name)
        entry = groups.setdefault(base, {"version": version, "archs": set()})
        entry["archs"].add(arch)
        if entry["version"] != version:
            # Same recipe, two versions in one run (e.g. a mid-sweep bump) --
            # surface the mismatch rather than silently keeping whichever
            # subpackage was grouped first.
            entry["version"] = "%s, %s" % (entry["version"], version)

    return groups


def format_package_report(groups, verb):
    """Render the collapsed package table: one row per base package,
    version once, every arch comma-joined in one cell. 'verb' is
    'would publish' (--dry-run) or 'published' (a real run)."""
    if not groups:
        return ""
    lines = ["", "Packages %s:" % verb,
             "| Package | Version | Archs |", "| --- | --- | --- |"]
    for name in sorted(groups):
        entry = groups[name]
        lines.append("| %s | %s | %s |" %
                     (name, entry["version"], ", ".join(sorted(entry["archs"]))))
    return "\n".join(lines)


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
    """Persist the hash cache. Purely an optimisation: a failure here is
    reported and the run continues, it must never fail an otherwise good
    upload -- but it must not be silent either, or the next run silently
    re-hashes everything for no visible reason."""
    cache_file = Path(cache_file)
    try:
        cache_file.parent.mkdir(parents=True, exist_ok=True)
        tmp = cache_file.with_suffix(".tmp.%d" % os.getpid())
        with open(tmp, "w") as f:
            json.dump(cache, f)
        os.replace(tmp, cache_file)
    except OSError as exc:
        log.warning("Could not write the hash cache %s: %s (harmless; the "
                    "next run just re-hashes everything)", cache_file, exc)


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
    """Return {relpath: sha256}, reusing cache entries whose size+mtime match.
    Returns None if any file could not be hashed: the digests end up in the
    manifest the server verifies against, so an incomplete hash set must not
    turn into an upload."""
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

        def hash_one(rel):
            try:
                return sha256_file(root / rel)
            except OSError as exc:
                log.error("Cannot hash %s: %s", rel, exc)
                return None

        with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, jobs)) as ex:
            for rel, digest in zip(todo, ex.map(hash_one, todo)):
                if digest is None:
                    continue
                hashes[rel] = digest
                size, mtime_ns = files[rel]
                cache[rel] = {"size": size, "mtime_ns": mtime_ns, "sha256": digest}
        missing = len(todo) - sum(1 for rel in todo if rel in hashes)
        if missing:
            log.error("%d of %d file(s) could not be hashed; refusing to build "
                      "a manifest that does not cover the whole upload", missing,
                      len(todo))
            return None
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
        cmd = [self.rsync, "--perms", "--times", "--partial-dir=.rsync-partial",
               "--timeout=%d" % RSYNC_IO_TIMEOUT]
        if self.remote:
            cmd += ["-e", " ".join(self.ssh_cmd)]
        return cmd + extra


def remote_parent_exists(transport):
    """Check that the parent of the remote directory is already there.

    Everything below it is created with mkdir -p (that is how a brand-new feed
    subtree is bootstrapped), which means a typo'd or transposed --remote-dir
    would get a complete feed tree built under it and every later step --
    upload, verify, sort -- would succeed against the wrong path. Requiring the
    parent to pre-exist still allows the intended bootstrap and refuses a path
    that was never real. Returns True, False (the parent is really absent) or
    None (the remote could not be asked)."""
    parent = os.path.dirname(transport.remote_dir) or "."
    snippet = ("if [ -d %s ]; then echo PARENT-OK; else echo PARENT-MISSING; fi"
               % shlex.quote(parent))
    try:
        proc = transport.run_shell(snippet, timeout=60)
    except subprocess.TimeoutExpired:
        log.error("Checking %s on the remote timed out", parent)
        return None
    if proc.returncode != 0:
        log.error("Could not check %s on the remote (%d): %s", parent,
                  proc.returncode, proc.stderr.strip())
        return None
    out = proc.stdout.split()
    if "PARENT-OK" in out:
        return True
    if "PARENT-MISSING" in out:
        log.error("%s does not exist, so %s is not an existing feed tree; "
                  "refusing to create one there (check --remote-dir for a "
                  "typo)", parent, transport.remote_dir)
        return False
    log.error("Unexpected reply while checking %s: %r", parent, proc.stdout)
    return None


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
        try:
            proc = subprocess.run(cmd, capture_output=True, text=True,
                                  timeout=RSYNC_STREAM_TIMEOUT)
        except subprocess.TimeoutExpired:
            log.warning("stream %s: rsync did not finish within %ds, killed it",
                        tag, RSYNC_STREAM_TIMEOUT)
            continue
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


def verify_staged_upload(transport, upload_id, expected_ipks):
    """Confirm the staging directory really holds the manifest and every ipk
    before COMPLETE is created. Once the marker exists the server ingests the
    directory and deletes it, so a short upload marked complete loses packages
    with nothing anywhere reporting a problem -- rsync's own exit status is
    the only other evidence, and it cannot see files a chunk never listed."""
    stage = "%s/incoming/%s" % (transport.remote_dir, upload_id)
    snippet = (
        "set -e\n"
        "cd %s\n"
        "test -f MANIFEST.sha256\n"
        "find . -type f -name '*.ipk' -not -path '*/.rsync-partial/*' | wc -l\n"
        % shlex.quote(stage))
    try:
        proc = transport.run_shell(snippet, timeout=300)
    except subprocess.TimeoutExpired:
        log.error("Verifying staged upload %s timed out", upload_id)
        return False
    if proc.returncode != 0:
        log.error("Staged upload %s is not verifiable (%d): %s", upload_id,
                  proc.returncode, proc.stderr.strip())
        return False
    try:
        staged = int(proc.stdout.strip().splitlines()[-1])
    except (IndexError, ValueError):
        log.error("Unexpected reply while verifying staged upload %s: %r",
                  upload_id, proc.stdout)
        return False
    if staged != expected_ipks:
        log.error("Staged upload %s holds %d ipk file(s) but %d were sent; "
                  "NOT marking it COMPLETE -- re-run to finish the transfer",
                  upload_id, staged, expected_ipks)
        return False
    log.info("Staged upload %s verified on the server: %d ipk file(s) plus "
             "the manifest", upload_id, staged)
    return True


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
    # Order matters: the marker is what licenses the server to ingest and
    # delete this directory, so nothing may create it before the contents
    # have been confirmed to be all there.
    if not verify_staged_upload(transport, upload_id,
                                sum(1 for rel in upload if rel.endswith(".ipk"))):
        return False
    marker = "%s/incoming/%s/COMPLETE" % (transport.remote_dir, upload_id)
    # The marker is the one write that licenses the server to ingest and
    # delete the stage, so its existence is confirmed independently (same
    # pattern as remote_parent_exists), not inferred from touch's exit code.
    qm = shlex.quote(marker)
    try:
        proc = transport.run_shell(
            "touch %s && test -f %s && echo MARKER-OK" % (qm, qm), timeout=60)
    except subprocess.TimeoutExpired:
        log.error("Creating COMPLETE marker timed out")
        return False
    if proc.returncode != 0:
        log.error("Creating COMPLETE marker failed: %s", proc.stderr.strip())
        return False
    if "MARKER-OK" not in proc.stdout.split():
        log.error("COMPLETE marker %s is not there after touch said it "
                  "succeeded: %r", marker, proc.stdout)
        return False
    return True


def write_summary(path, summary):
    """Write the --json-summary file. Returns False if one was asked for and
    could not be written: a run that did not produce the artefact it was told
    to produce has not fully succeeded, and whatever reads that file must not
    be left with a stale copy from an earlier run instead."""
    if not path:
        return True
    try:
        with open(path, "w") as f:
            json.dump(summary, f, indent=2, sort_keys=True)
            f.write("\n")
    except OSError as exc:
        log.error("Cannot write the JSON summary %s: %s", path, exc)
        return False
    return True


def finish(args, summary, rc, reason=None):
    """Single exit point: fill in the summary, write it, and print the one
    sentinel line that says how the run ended."""
    summary["success"] = (rc == 0)
    if not write_summary(args.json_summary, summary) and rc == 0:
        summary["success"] = False
        rc = 1
        reason = ("the upload itself succeeded but the --json-summary file "
                  "could not be written")
    remote = summary.get("remote")
    line = ("UPLOAD-PACKAGES: %s upload=%s remote=%s remote-dir=%s scanned=%d "
            "already-present=%d planned=%d uploaded=%d dry-run=%d"
            % ("SUCCESS" if rc == 0 else "FAILED",
               summary.get("upload_id", "none"),
               "(unset)" if remote is None else (remote or "(local)"),
               summary.get("remote_dir") or "(unset)",
               summary.get("scanned", 0), summary.get("already_present", 0),
               summary.get("planned", 0), summary.get("uploaded", 0),
               1 if args.dry_run else 0))
    if rc == 0:
        log.info(line)
    else:
        log.error("%s reason=%s", line, reason or "see the errors above")
    return rc


def _run_upload(args):
    summary = {
        "deploy_dir_ipk": args.deploy_dir_ipk,
        "remote": args.remote,
        "remote_dir": args.remote_dir,
        "scanned": 0,
        "already_present": 0,
        "planned": 0,
        "uploaded": 0,
        "dry_run": args.dry_run,
        "success": False,
    }

    if not resolve_config(args):
        return finish(args, summary, 1,
                      "incomplete configuration, nothing was scanned or sent")
    summary["deploy_dir_ipk"] = str(args.deploy_dir_ipk)
    summary["remote"] = args.remote
    summary["remote_dir"] = args.remote_dir

    if shutil.which(args.rsync) is None:
        log.error("rsync not found in PATH; inside a bitbake task this needs "
                  "HOSTTOOLS_NONFATAL to include rsync and rsync installed on the host")
        return finish(args, summary, 1, "rsync not found, nothing was sent")
    if args.remote and shutil.which("ssh") is None:
        log.error("ssh not found in PATH")
        return finish(args, summary, 1, "ssh not found, nothing was sent")

    transport = Transport(args.remote, args.remote_dir, args.ssh_opt, args.rsync)

    # Before anything creates directories out there: everything from here on
    # would succeed just as well against a wrong path.
    parent_ok = remote_parent_exists(transport)
    if parent_ok is False:
        return finish(args, summary, 1,
                      "the parent of %s does not exist on the remote, so this "
                      "is not an existing feed tree; nothing was sent"
                      % args.remote_dir)
    if parent_ok is None:
        if args.dry_run:
            log.warning("Continuing dry run without confirming the remote path")
        else:
            return finish(args, summary, 2,
                          "the remote could not be asked whether %s is a real "
                          "feed tree" % args.remote_dir)

    started = time.time()
    files = scan_deploy_dir(args.deploy_dir_ipk, args.exclude)
    if files is None:
        return finish(args, summary, 1,
                      "DEPLOY_DIR_IPK could not be scanned, nothing was sent")
    summary["scanned"] = len(files)

    cache_file = args.cache_file or default_cache_file(args.deploy_dir_ipk)
    cache = load_cache(cache_file)
    hashes = hash_files(args.deploy_dir_ipk, files, cache, args.jobs)
    if hashes is None:
        return finish(args, summary, 1,
                      "not every package could be hashed, nothing was sent")
    save_cache(cache_file, cache)

    arch_map = resolve_arch_map(args.arch_map)
    if arch_map is False:
        return finish(args, summary, 1,
                      "--arch-map file does not exist, nothing was sent")

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
                return finish(args, summary, 2,
                              "the server could not be queried for what it "
                              "already has")

    if args.force:
        upload = sorted(files)
    else:
        upload = sorted(rel for rel in files if Path(rel).name not in known)

    summary["already_present"] = len(files) - len(upload)
    summary["planned"] = len(upload)

    if not upload:
        log.info("Nothing to upload: all %d packages already on the server", len(files))
        return finish(args, summary, 0)

    total_bytes = sum(files[rel][0] for rel in upload)
    upload_id = make_upload_id(upload, hashes)
    summary["upload_id"] = upload_id
    log.info("Uploading %d packages (%.1f MiB) as upload %s, %d already present",
             len(upload), total_bytes / 1048576.0, upload_id,
             len(files) - len(upload))

    if args.dry_run:
        for rel in upload:
            log.info("would upload: %s", rel)
        report = format_package_report(group_packages_for_report(upload), "would publish")
        if report:
            print(report)
        return finish(args, summary, 0)

    dest = transport.rsync_dest("incoming/%s/" % upload_id)
    chunks = balance_chunks(upload, files, args.jobs)
    log.info("Transferring in %d parallel stream(s) to %s", len(chunks), dest)

    # Pre-create the staging subdirectories in one round trip so parallel
    # rsync receivers do not race each other creating the same directory.
    stage_dirs = sorted({
        "%s/incoming/%s/%s" % (transport.remote_dir, upload_id,
                               os.path.dirname(rel))
        for rel in upload})
    try:
        proc = transport.run_shell(
            "mkdir -p " + " ".join(shlex.quote(p.rstrip("/")) for p in stage_dirs))
    except subprocess.TimeoutExpired:
        log.error("Creating staging directories timed out")
        return finish(args, summary, 2, "creating the staging directories timed out")
    if proc.returncode != 0:
        log.error("Creating staging directories failed: %s", proc.stderr.strip())
        return finish(args, summary, 2, "the staging directories could not be created")

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
            # No COMPLETE marker was created, so the server will not ingest
            # the half-transferred staging directory.
            return finish(args, summary, 2,
                          "%d of %d package(s) transferred; the upload was NOT "
                          "marked complete and nothing has been published"
                          % (summary["uploaded"], len(upload)))

        if not finalize_upload(transport, scratch, upload, hashes,
                               upload_id, args.retries, arch_map):
            log.error("Upload transferred but could not be finalized; "
                      "re-run to finish publishing %s", upload_id)
            return finish(args, summary, 2,
                          "upload %s was not finalised; it is staged but not "
                          "marked complete, so the server will not ingest it"
                          % upload_id)

    log.info("Upload %s complete: %d packages in %.1fs; the server will ingest "
             "it on the next sort run", upload_id, len(upload),
             time.time() - started)
    report = format_package_report(group_packages_for_report(upload), "published")
    if report:
        print(report)
    return finish(args, summary, 0)


def main(argv=None):
    args = parse_args(argv)
    logging.basicConfig(
        level=getattr(logging, args.log_level.upper()),
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S")

    try:
        # Nothing may read an earlier run's summary as this one's result, not
        # even if this run dies before it can write its own.
        if args.json_summary:
            try:
                os.unlink(args.json_summary)
            except OSError:
                pass

        return _run_upload(args)
    except Exception as exc:
        # Never let a traceback be the only record: an unexpected failure gets
        # the same greppable sentinel as an expected one.
        log.exception("Unhandled error during upload: %s", exc)
        log.error("UPLOAD-PACKAGES: FAILED remote-dir=%s unhandled error: %s",
                  args.remote_dir, exc)
        return 2


if __name__ == "__main__":
    sys.exit(main())
