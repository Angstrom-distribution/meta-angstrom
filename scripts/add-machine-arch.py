#!/usr/bin/env python3
# MIT Licensed
# Add or update machine entries in feed-arch-map.json, the config that
# scripts/sort-packages.py uses to sort uploaded ipks into feeds.
#
# The mapping mirrors what oe-core actually does (bitbake.conf):
#   MACHINE_ARCH  = MACHINE with '-' replaced by '_'   (bitbake.conf:190)
#   PACKAGE_ARCH ??= TUNE_PKGARCH                       (bitbake.conf:189)
#   PACKAGE_ARCHS = all any noarch PACKAGE_EXTRA_ARCHS MACHINE_ARCH (:192)
# so per machine we record machine_arch (computed), feed_arch (TUNE_PKGARCH,
# resolved via bitbake-getvar) and the full PACKAGE_ARCHS list (resolved via
# bitbake-getvar). MACHINE is passed per machine through the environment,
# which works because oe-buildenv-internal puts MACHINE in
# BB_ENV_PASSTHROUGH_ADDITIONS; we extend that variable ourselves for safety.
#
# Usage, inside an initialised build environment (e.g. kas-container shell):
#   add-machine-arch.py                   # current MACHINE
#   add-machine-arch.py beaglebone qemuarm64
#   add-machine-arch.py --from-kas        # every machine fragment in kas/
# Without bitbake, values can be supplied by hand for a single machine:
#   add-machine-arch.py foo --feed-arch armv7at2hf-vfp-neon \
#       --package-archs "all any noarch armv5te armv7at2hf-vfp-neon foo"
#
# Idempotent: an entry that would not change is reported and left alone;
# a changed entry is updated in place. Python 3.7+, stdlib only.

import argparse
import json
import logging
import os
import re
import subprocess
import sys

from pathlib import Path

log = logging.getLogger("add-machine-arch")

ARCH_MAP_NAME = "feed-arch-map.json"


def parse_args(argv):
    p = argparse.ArgumentParser(
        description="Add or update machine entries in %s" % ARCH_MAP_NAME)
    p.add_argument("machines", nargs="*", metavar="MACHINE",
                   help="machine(s) to add/update (default: the build's "
                        "current MACHINE via bitbake-getvar)")
    p.add_argument("--config", metavar="FILE",
                   default=str(Path(__file__).resolve().parent / ARCH_MAP_NAME),
                   help="arch map to update (default: %s next to this script)"
                        % ARCH_MAP_NAME)
    p.add_argument("--from-kas", nargs="?", const="", metavar="DIR",
                   help="also add every 'machine:' found in kas fragments "
                        "under DIR (default: the kas/ directory next to this "
                        "script's layer)")
    p.add_argument("--build-dir", metavar="DIR",
                   help="build directory to run bitbake-getvar in "
                        "(default: $BBPATH head or cwd)")
    p.add_argument("--feed-arch", metavar="ARCH",
                   help="override TUNE_PKGARCH (single machine only; skips "
                        "bitbake-getvar)")
    p.add_argument("--package-archs", metavar="LIST",
                   help="override PACKAGE_ARCHS as a space-separated list "
                        "(single machine only; skips bitbake-getvar)")
    p.add_argument("--dry-run", action="store_true",
                   help="report changes without writing the config")
    p.add_argument("--log-level", default="info",
                   choices=["debug", "info", "warning", "error"])
    return p.parse_args(argv)


def bitbake_getvar(var, build_dir, machine=None):
    env = dict(os.environ)
    if machine:
        env["MACHINE"] = machine
        env["BB_ENV_PASSTHROUGH_ADDITIONS"] = (
            env.get("BB_ENV_PASSTHROUGH_ADDITIONS", "") + " MACHINE").strip()
    cmd = ["bitbake-getvar", "--value", "-q", "--ignore-undefined", var]
    try:
        proc = subprocess.run(cmd, cwd=build_dir, env=env,
                              capture_output=True, text=True, timeout=300)
    except FileNotFoundError:
        log.error("bitbake-getvar not in PATH; run inside a build environment "
                  "or pass --feed-arch/--package-archs by hand")
        return None
    except subprocess.TimeoutExpired:
        log.error("bitbake-getvar %s timed out", var)
        return None
    if proc.returncode != 0:
        log.error("bitbake-getvar %s failed: %s", var, proc.stderr.strip())
        return None
    lines = [l for l in proc.stdout.splitlines() if l.strip()]
    return lines[-1].strip() if lines else None


def machines_from_kas(kas_dir):
    """Collect top-level 'machine:' values from kas fragments (no yaml
    parser needed for this one key)."""
    found = []
    pattern = re.compile(r"^machine:\s*(\S+)\s*$", re.M)
    for frag in sorted(Path(kas_dir).glob("*.yml")):
        for machine in pattern.findall(frag.read_text()):
            if machine not in ("unset", "~", "null") and machine not in found:
                found.append(machine)
                log.debug("%s: machine %s", frag.name, machine)
    return found


def resolve_entry(machine, args, build_dir):
    """Build the config entry for one machine; None on failure."""
    if args.feed_arch:
        feed_arch = args.feed_arch
    else:
        feed_arch = bitbake_getvar("TUNE_PKGARCH", build_dir, machine)
        if not feed_arch:
            return None
    if args.package_archs:
        package_archs = args.package_archs.split()
    else:
        raw = bitbake_getvar("PACKAGE_ARCHS", build_dir, machine)
        if raw is None:
            return None
        package_archs = raw.split()
    return {
        "machine_arch": machine.replace("-", "_"),  # bitbake.conf:190
        "feed_arch": feed_arch,
        "package_archs": package_archs,
    }


def load_config(path):
    path = Path(path)
    if not path.exists():
        log.warning("%s does not exist yet, starting a new one", path)
        return {"version": 1, "arch_aliases": {}, "extra_base_archs": [],
                "machines": {}}
    with open(str(path)) as f:
        return json.load(f)


def save_config(path, config):
    path = Path(path)
    tmp = path.parent / (path.name + ".tmp.%d" % os.getpid())
    with open(str(tmp), "w") as f:
        json.dump(config, f, indent=2, sort_keys=True)
        f.write("\n")
    os.replace(str(tmp), str(path))


def main(argv=None):
    args = parse_args(argv)
    logging.basicConfig(
        level=getattr(logging, args.log_level.upper()),
        format="%(levelname)s: %(message)s")

    build_dir = args.build_dir
    if build_dir is None:
        bbpath = os.environ.get("BBPATH", "")
        build_dir = bbpath.split(":")[0] if bbpath else os.getcwd()

    machines = list(args.machines)
    if args.from_kas is not None:
        kas_dir = args.from_kas or str(Path(__file__).resolve().parent.parent / "kas")
        if not Path(kas_dir).is_dir():
            log.error("kas fragment directory not found: %s", kas_dir)
            return 1
        for machine in machines_from_kas(kas_dir):
            if machine not in machines:
                machines.append(machine)
        log.info("kas fragments name %d machine(s)", len(machines))
    if not machines:
        current = bitbake_getvar("MACHINE", build_dir)
        if not current:
            log.error("No machines given and MACHINE could not be resolved")
            return 1
        machines = [current]

    if (args.feed_arch or args.package_archs) and len(machines) != 1:
        log.error("--feed-arch/--package-archs apply to exactly one machine")
        return 1

    try:
        config = load_config(args.config)
    except (OSError, ValueError) as exc:
        log.error("Cannot read %s: %s", args.config, exc)
        return 1
    entries = config.setdefault("machines", {})

    changed = 0
    failed = 0
    for machine in machines:
        entry = resolve_entry(machine, args, build_dir)
        if entry is None:
            log.error("%s: could not resolve arch data", machine)
            failed += 1
            continue
        old = entries.get(machine)
        if old == entry:
            log.info("%s: unchanged (feed_arch %s)", machine, entry["feed_arch"])
            continue
        # machine_arch is the identity actually matched at sort time
        # (bitbake.conf:190 underscore form); drop any entry stored under
        # another spelling of the same machine so we never keep conflicting
        # duplicates (e.g. seed key 'rb1_core_kit' vs MACHINE 'rb1-core-kit').
        for other in [k for k in entries
                      if k != machine and
                      entries[k].get("machine_arch") == entry["machine_arch"]]:
            log.info("%s: superseding entry %r with the same machine_arch %s",
                     machine, other, entry["machine_arch"])
            del entries[other]
        entries[machine] = entry
        changed += 1
        log.info("%s: %s -> feed_arch %s, machine_arch %s, %d package arches",
                 machine, "updated" if old else "added", entry["feed_arch"],
                 entry["machine_arch"], len(entry["package_archs"]))

    if changed and not args.dry_run:
        save_config(args.config, config)
        log.info("Wrote %s (%d machines total)", args.config, len(entries))
    elif changed:
        log.info("Dry run: %d change(s) not written", changed)
    else:
        log.info("No changes")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
