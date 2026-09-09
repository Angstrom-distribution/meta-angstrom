---
name: mackas-angstrom description: Build an Angstrom OpenEmbedded target on macOS via mackas (the optional kas-container wrapper for Apple's container runtime) and analyze what changed in buildhistory and deploy. Use when asked to build a recipe/image/package for a machine (beaglebone, qemuarm64, riscv, etc.) in the meta-angstrom layer on a macOS host running mackas, or to inspect what a mackas build produced or changed. The default workflow for this layer is plain kas-container, documented in angstrom-build — use this skill only when the build actually runs through mackas.
---

# Angstrom build with mackas (macOS) + buildhistory/deploy analysis

**mackas is a macOS-specific convenience layer, not this layer's standard workflow.** The normal path is plain `kas-container` on a Linux host with docker or podman — see the `angstrom-build` skill, which is where the layer's build/analysis procedure actually lives. Use this skill only on a Mac where builds are driven through mackas.

Build a target with mackas's `kas-container` wrapper on Apple's `container` runtime and report exactly what the build produced and what changed, using the buildhistory git repo and the deploy directory. The core bitbake/buildhistory concepts, the kas fragment composition, and the `--skip` rules are the same as in `angstrom-build`; nearly everything else about *how you reach them* is different, because `TMPDIR`/`DL_DIR`/`SSTATE_DIR` live inside ext4 volumes that macOS cannot see.

The mackas repo ships its own project-agnostic skill (`skills/mackas/SKILL.md` in that repo), covering the general mackas mechanics this skill also describes — `mackas exec`, the `--skip` footguns, env.sh staleness, retrieve/clean/monitor, one-VM troubleshooting. It is meant to be symlinked into any project's `.claude/skills/`. The overlap is deliberate duplication; what is genuinely only here is the meta-angstrom-specific material — this project's paths, kas fragments, the non-default `DEPLOY_DIR`, and the buildhistory/deploy analysis conventions.

## Reliability contract

Every sentinel this skill actually produces in *this* checkout, in one place — on any `FAILURE`/`FAILED` form, read the named section first; never retry blind, never route around the check that produced it. This list is scoped to what this checkout genuinely has; it deliberately omits sentinels described in other checkouts' versions of this skill that depend on scripts not present here (e.g. a `mackas-build-and-monitor.sh` wrapper, or a hardened `sort-packages.py` sentinel) — don't add a line here without first verifying the thing that emits it actually exists.

- `Tasks Summary: Attempted N tasks of which M didn't need to be rerun and all succeeded.` — bitbake's own build-success line; the `... and K failed.` form is failure. **No `Tasks Summary` line at all** means the run died before the task executor started, or is still running — never success.
- `mackas check`'s `N pass, N warn, N fail` summary + exit 1 on any `[FAIL]` — the preflight verdict; `mackas status` has no pass/fail signal at all (see "Preflight").
- `+ retrieved <sub> -> ...` per object, `Retrieved to <dest>` header, or `Nothing retrieved.` + exit 1 — `mackas retrieve`'s own sentinels (see "Retrieving files off the build volumes").
- `FATAL: ...` lines from `scripts/publish-feed.sh`, plus `upload-packages.py`'s own exit codes (`0` success/no-op, non-zero on a local or remote failure) — see "Publishing to the package feed".
- `monitor: finished` (the build reached a terminal state) vs `monitor: bridge never attached in ~60s -- stop the build` — the build-watch loop in "Building"; the two are mutually exclusive, so read which one landed rather than assuming completion. Same kind as the next line: a pattern to type, not a pre-existing script's output.
- The inline `REPO-SNAPSHOT: FAILURE -- ...` / sanity-anchor pattern this skill's own repo-update snippet uses (see "3. Report which repos changed") — this is a pattern to type when following that section, not a pre-existing script's output; check it every time you run that snippet, not just when something looks wrong.

## Environment layout

- mackas tool checkout: wherever mackas was cloned. `env.sh` puts it on `$PATH`, so plain `mackas ...` always resolves to the live script; `command -v mackas` finds the checkout when its path is actually needed.
- **Always work from `~/oe/work`** (`$MACKAS_BASE/work`, sourced from `env.sh`). `~/oe` is a short symlink mackas maintains specifically to route around `kas-container` path issues (word-splitting on spaces, case-sensitivity requirements) — it is not one of two equally-valid roots to choose between. mackas owns where the real storage physically lives (the project's configured `MACKAS_ROOT`, behind that link — `mackas status` shows it); this skill never needs to reason about that path directly, only `~/oe`.
- Layer repo: `~/oe/work/meta-angstrom`. Sibling layers (`meta-ti`, `meta-arm`, `meta-beagleboard`, `meta-dominion`, `meta-openembedded`, `openembedded-core`, `bitbake`, ...) live alongside it under the same `work/` dir. These sibling checkouts ARE ordinary macOS-visible directories — only the build output volumes are hidden.
- Build output lives in **three ext4 volumes inside the container VM, invisible from macOS**:
  - `oe-build-tmp` → `/build` (`KAS_BUILD_DIR`/`TOPDIR`) → `TMPDIR` = `/build/tmp`, `BUILDHISTORY_DIR` = `/build/buildhistory`
  - `oe-build-dl` → `/downloads` (`DL_DIR`)
  - `oe-build-sstate` → `/sstate` (`SSTATE_DIR`)

None of `/build`, `/downloads`, `/sstate` are host paths reachable from `~/oe` — there is nowhere to `cd` for them. Every inspection of buildhistory/deploy runs **inside the container**, via `mackas exec CMD` (next section), not via a host-side `cd`.

### `mackas exec` — the safe way to run one-off commands in the checkout

**`mackas exec CMD [ARGS...]` is the standard tool for every one-off read-only query in this skill** — `bitbake-getvar`, git queries in buildhistory, `find`/`ls` in deploy, `cat` of a task log. It runs CMD in a throwaway kas shell with kas's four repo-mutating setup steps (`setup_dir`, `finish_setup_repos`, `repos_checkout`, `repos_apply_patches`) **always skipped, with no flag to add them back**. It exists precisely because a hand-typed `kas-container shell ... -c '...'` without the `--skip` pair has reset real checkouts' local commits more than once (see the `--skip` section below); prefer `exec` over hand-typing that invocation every time the command is a one-off rather than a build.

`exec` takes no kas file list — it composes from `MACKAS_PROJECT_DIR`/`MACKAS_KAS_CONFIG` (and appends `kas/macos-local.yml` itself). **Persist a default pair once** so `exec`/`clean`/`retrieve`/etc. resolve correctly even in a shell with nothing exported — without a resolvable project/config, `mackas clean tmp+deploy` hard-fails rather than guessing a path (see that section below):

```sh
mackas set MACKAS_PROJECT_DIR meta-angstrom
mackas set MACKAS_KAS_CONFIG kas/angstrom.yml:kas/beaglebone.yml
```

Those land in `~/.mackas.conf`. Leave `MACKAS_PROJECT_URL` empty — setting it would pull in mackas's own project-checkout flow, which this project does not use; only the path-resolution config is wanted.

Any machine fragment works as that persisted default: `DEPLOY_DIR`/`TMPDIR` resolve identically regardless of which one is composed (`DEPLOY_DIR = "${TOPDIR}/deploy"` in `conf/distro/angstrom.conf`, independent of `MACHINE`). For anything machine-specific (buildhistory per-arch paths, a real build, etc.), still export explicitly per shell — the environment always wins over the config file:

```sh
export MACKAS_KAS_CONFIG=kas/angstrom.yml:kas/<machine>.yml
```

(`mackas get MACKAS_KAS_CONFIG` reflects the export, not the persisted default, the moment one is set.) Re-export `MACKAS_KAS_CONFIG` when switching machines mid-shell — an already-set value is never overridden by derivation. To chain several commands, wrap them: `mackas exec sh -c '... && ...'`. Each `exec` is a fresh container — nothing persists between calls except what is on the ext4 volumes. Same one-VM rule as everything else: it refuses while a build/shell holds any volume. Two caveats: it is not a sandbox (repos and volumes are mounted read-write — only kas itself is guaranteed hands-off), and because `repos_apply_patches` is always skipped, a freshly added `patches:` entry is never applied by an `exec` run (same blind spot as the `--skip` pair, below). `mackas` itself never goes stale the way `env.sh` does — `env.sh` puts the mackas checkout on PATH, so `mackas` is always the live script.

### DEPLOY_DIR is not the bitbake default

**`DEPLOY_DIR` is NOT `${TMPDIR}/deploy` — do not assume a path.** `conf/distro/angstrom.conf` sets `DEPLOY_DIR = "${TOPDIR}/deploy"`, putting it at `/build/deploy`, a sibling of `tmp/` rather than inside it. A distro conf can change this again, so verify rather than trusting this note:

```sh
mackas exec sh -c 'bitbake-getvar DEPLOY_DIR; bitbake-getvar TMPDIR; bitbake-getvar BUILDHISTORY_DIR'
```

`bitbake-getvar <VAR>` parses the config (not full recipes) and prints the resolved value — cheap enough to run before every buildhistory/deploy inspection rather than trusting a remembered path. Same for `DEPLOY_DIR_IMAGE`/`DEPLOY_DIR_IPK` when a step needs the exact subdirectory.

**`mackas clean tmp+deploy` needs `MACKAS_PROJECT_DIR`/`MACKAS_KAS_CONFIG` resolvable, and hard-fails instead of guessing if they aren't.** Without a resolvable project/config it would otherwise silently substitute the oe-core-default `${TMPDIR}/deploy` path and report success while leaving this project's real, non-default `/build/deploy` uncleared — `clean` refuses (`die_unresolved_guest_var`) rather than guess a path it's about to `rm -rf`, and reports per-path which of TMPDIR/DEPLOY_DIR actually existed and got cleared. With the persisted pair from `mackas exec`'s note above, it resolves correctly with nothing exported; only override `MACKAS_KAS_CONFIG` per-shell when working against a different machine's config matters for the resolution. Re-run `mackas -y setup <MACKAS_ROOT> && source ~/oe/env.sh` if this behavior doesn't match (env.sh staleness, as always).

## Preflight (every session)

```sh
source ~/oe/env.sh     # puts the docker shim + kas-container wrapper on PATH
mackas status          # env.sh puts the mackas checkout on PATH
```

Confirm: container runtime running, `ghcr.io/siemens/kas/kas:5.4` image present, the ext4 volumes present, `docker resolves to` the shim under `~/oe/bin/docker` — not `/usr/local/bin/docker`. If any of that is off, `mackas check` names the fix. `status` also flags real ext4 corruption on any volume (the dirty-bit check, see Troubleshooting) as part of this same routine look — worth reading its output fully after any crash, not just skimming for the daemon/image lines.

**The `container` system daemon does not survive a reboot/crash; mackas auto-starts it.** Every real mackas-driven kas invocation (`build`/`shell`/`exec`/`smoketest`/`retrieve`/`buildstats analyze`/`clean`/`sstate prune`/`lock`/`dump`, plus the hand-typed `kas-container` wrapper script) calls `ensure_container_running()` first and starts the daemon itself if it's down, so `container system start` is not something to run by hand after a crash. `status`/`check` deliberately stay read-only (no auto-start) and instead print one clear "container system is not running" line rather than misleadingly walking every volume/image and reporting them as absent or orphaned when they're simply unreachable. If `status`/`check` report that line, just run any real mackas command rather than treating it as a deeper problem.

**Work from a shell with `env.sh` sourced; do not bypass it.** `env.sh` defines `kas-container` as a shell function that injects `--runtime-args` (the three volumes, `-c`/`-m` limits), blanks `KAS_BUILD_DIR`/`DL_DIR`/`SSTATE_DIR`, auto-appends the `macos-local.yml` tuning fragment, and derives `MACKAS_PROJECT_DIR`/`MACKAS_KAS_CONFIG`. Anything that resolves `kas-container` through `$PATH` instead of the shell function (`command kas-container`, an absolute path, an unsourced shell) loses at least the fragment auto-append and the project-variable derivation, and — depending on whether the protection wrapper described below is installed — potentially the volume mounts as well.

At the pinned kas 5.4, `--runtime-args` OVERWRITES rather than accumulates (`kas-container` ~line 347: `KAS_EXTRA_RUNTIME_ARGS=" $2"` — last flag wins). Never hand-pass `--runtime-args` to a wrapped `kas-container` call; let mackas own the flag, or the three ext4 volume mounts silently disappear. (Unreleased kas master changes this to accumulate — irrelevant until the pin moves past 5.4.)

**Never `nohup kas-container`.** `nohup CMD` resolves `CMD` via `PATH`, not shell-function lookup, so it bypasses the sourced wrapper: zero `--runtime-args`, no ext4 volumes, and `KAS_BUILD_DIR` unset so kas falls back to `TOPDIR` under `KAS_WORK_DIR` on virtiofs. Concrete symptom: `bitbake-server` fails with `OSError: [Errno 95] Operation not supported` on `sock.bind()` (AF_UNIX is unsupported on virtiofs). Background a build with `&` (or the harness's own `run_in_background`) inside a shell that already has `env.sh` sourced instead.

mackas mitigates this at a second layer by installing `kas-container` on `$PATH` as a small **protection wrapper script**, with the real pinned upstream binary moved to `kas-container.real` and never itself on `$PATH`. Where that wrapper is installed, any `$PATH`-resolved call — `nohup`, `env`, an unsourced shell, a Makefile recipe — still gets the volumes, the `-c`/`-m` limits, and a **refuse-rather-than-guess** check that declines to launch at all if the resolved `--runtime-args` is empty or missing a volume mount. It does not restore the auto-appended `macos-local.yml` fragment or the `MACKAS_PROJECT_DIR`/`MACKAS_KAS_CONFIG` derivation, which are shell-function-only conveniences.

**The wrapper is installed by `mackas -y setup <MACKAS_ROOT>`, so its presence tracks env.sh staleness** — check for a `kas-container.real` file alongside `kas-container` in the shim dir (`~/oe/bin/`). A lone `kas-container` with no `.real` sibling means the raw binary is what a bypass reaches, and the Errno 95 hazard above is live. Either way the rule is the same: never `nohup`.

### env.sh staleness

**`~/oe/env.sh` is generated by `mackas setup` and does NOT auto-update when the mackas tool itself changes.** It can sit stale through days of real mackas fixes — including the `--skip STEP <files>` argument-parsing fix and the `MACKAS_PROJECT_DIR`/`MACKAS_KAS_CONFIG` auto-derivation that makes `mackas retrieve`/`buildstats analyze` resolve `DEPLOY_DIR` correctly (both covered below) — with zero error or warning that anything is out of date. `source`-ing a stale `env.sh` silently keeps the old (sometimes broken) wrapper behavior.

**Whenever told "mackas was updated," or when a behavior this skill documents doesn't seem to be happening, re-run setup to refresh it** (get the root from `mackas status` or `~/.mackas.conf`):

```sh
mackas -y setup <MACKAS_ROOT>
source ~/oe/env.sh
```

Idempotent and safe — verify with `--dry-run` first if unsure (a *global* flag, before the subcommand: `mackas --dry-run setup <root>`, not after it). Every step reports "already done" except rewriting `env.sh` itself and harmless volume-ownership `chown`s, as long as `<root>` matches the real existing root. Passing the root explicitly avoids the "no `~/.mackas.conf`" relocation risk entirely — no need to avoid `setup` outright, just never run it bare with no root argument (see Troubleshooting).

## Kas fragment composition

Always compose the base config with a machine fragment, colon-separated — same fragments as the Linux skill, this layer does not change per host:

- Base: `meta-angstrom/kas/angstrom.yml` (distro, repos, local_conf_header)
- Machine: one `meta-angstrom/kas/<machine>.yml` — e.g. `beaglebone.yml`, `qemuarm64.yml`, `riscv.yml`, `radxa-dragon-q8b.yml`. **`ls kas/` in this repo for the authoritative list**; fragments are added and renamed regularly, so do not work from a list memorized anywhere, including this one.

The `macos-local.yml` tuning fragment mackas generates — `BB_NUMBER_THREADS`/`PARALLEL_MAKE` sized to `MACKAS_CPUS`, `BB_DISKMON_DIRS` pointed at the three ext4 volumes, and `BB_HASHSERVE_DB_DIR` pinned into `$SSTATE_DIR` so hash-equivalence survives `mackas clean` — lands at `meta-angstrom/kas/macos-local.yml`. It is **not** in git: mackas writes it the first time any `exec`/`retrieve`-style query runs with `MACKAS_PROJECT_DIR` pointing at this checkout, and adds it to `.git/info/exclude`. Expect it to be absent on a fresh clone until then. mackas's own kas-driving commands (`exec`, `shell`, `smoketest`, retrieve's internal queries) always compose it last automatically. Hand-typed `kas-container build/shell` calls are a different story: the sourced wrapper *can* auto-append it (`MACKAS_KAS_AUTO_FRAGMENT=1`, on by default), but its existence check uses the fragment path baked into `env.sh` at generation time from the *configured* project — unset in this multi-layer checkout, so the auto-append does not fire here. To get the tuning on a hand-typed build, compose it yourself as the last fragment: `...:meta-angstrom/kas/macos-local.yml`. Either way, angstrom.yml's own `diskmon` block (HALT/STOPTASKS on `${TMPDIR}`/`${DL_DIR}`/`${SSTATE_DIR}`) still applies and is enough for correctness; the fragment is a hash-equivalence/perf nicety, not a requirement. Do not block a build on it.

**That `diskmon` block is not just a safety net — it fires for real on `DL_DIR` in normal use, and it means exactly what it says.** A large new fetch (e.g. enabling a class that pulls in multi-GB git-cloned databases) on top of an already-busy `oe-build-dl` genuinely halts mid-task:

```
WARNING: The free space of /downloads (/dev/vdd) is running low (1.975GB left)
ERROR: Immediately halt since the disk space monitor action is "HALT"!
```

This is bitbake sending SIGTERM to the offending fetch tasks before the volume fills, not a mackas or network failure — check real headroom first (`mackas exec df -h /downloads`, or `mackas exec du -sh /downloads/*` for what's actually using it — `git2/` accumulates fast across many recipes/machines and is the usual culprit) rather than assuming a transient fetch error and retrying blind. `mackas volume resize oe-build-dl <bigger size>` (below) is the fix when the volume itself is genuinely too small for what the project needs, not just fragmented — resize doesn't need a build/shell to be stopped first beyond the normal one-VM rule, and preserves everything already downloaded.

## Building

**Before backgrounding any real build, re-read this checklist fresh — do not rely on remembering it from an earlier build in this same session.** Real incident, 2026-09-07: a build was backgrounded with a hand-rolled `&`/`disown` + log-`grep` completion watcher instead of attaching `mackas monitor`, exactly the "log-tailing as a stand-in" this skill already says never to do — caught only because the user asked "are you using the proper monitor?" The rule existed in this file the whole time; it was skipped under time pressure anyway. A checklist, checked explicitly, is the guard against that:

- [ ] `MACKAS_MONITOR=1` exported in the shell that launches the build (confirm with `mackas runtime-args` before backgrounding if in doubt — the `-p 8801:8801` chunk must be present).
- [ ] **An agent never runs bare `mackas monitor` — only `mackas monitor --once`, inside the poll loop below.** No exceptions, no "just to check". Bare `mackas monitor` streams a new line every few seconds for the whole build; wired into any harness tool that notifies per stdout line, it forces a reply per tick (real incident 2026-09-09 — four corrections before the *mechanism*, not the reply wording, was changed). Bare monitor is for a human watching a terminal. Check the literal command string for `--once` before running it.
- [ ] Track completion as its **own** tracked background process — never a log-`grep`/`tail -f` loop as the *primary* mechanism (fine only as the loop's backup break below). One process, one notification, when it exits:
  ```sh
  MON="$SCRATCH/<target>-monitor.log"   # NOT $LOG: the post-build report parses the build log
  : > "$MON"                            # truncate: a stale [success] from the last build breaks out instantly
  n=0
  while :; do
    mackas monitor --once >>"$MON" 2>&1
    grep -qE '\[success\]|\[failed\]' "$MON" && break
    grep -qE 'Tasks Summary:|Summary: There (was|were)' "$LOG" && break
    n=$((n+1))
    [ "$n" -eq 4 ] && ! grep -qE '\[(building|success|failed)\]' "$MON" \
      && { echo "monitor: bridge never attached in ~60s -- stop the build"; exit 0; }
    sleep 15
  done
  echo "monitor: finished"
  exit 0
  ```
  Run it under the harness's "notify me once, when this command exits" background mechanism — never the per-line streaming kind. Each piece earns its place: the second `grep` breaks out when bitbake finished but the bridge never said so (a stale mackas does exactly that — see "Watching one build live"), otherwise the loop spins silently forever and nothing ever notifies. The `n -eq 4` guard is the 5-attempts-in-60s bridge-attach rule from "2. Build"; it tests for a real `[status]` line rather than for an empty `$MON`, because a failed attach writes its own `socket.timeout` noise into the file — an emptiness test never fires and the loop hangs forever, which is the exact failure it exists to prevent. The explicit `exit 0`s keep a no-match `grep` from reporting the watch itself as failed.
- [ ] Reporting cadence while a build runs: real progress (task count/%, sstate coverage, current task) roughly every 10 minutes of wall-clock, and immediately on any error — nothing else, ever. A progress signal arriving — a monitor poll, a log line, a harness notification — is not itself a cue to speak; it only updates internal state. Never reply per-tick, and never reply with filler just because a signal landed. "Filler" means any reply that carries no number the user didn't already have: `(unchanged)`, `still building`, `ok`, an emoji, a one-word acknowledgment. If 10 minutes have passed and the counter genuinely hasn't moved, that is not filler — name the task holding it and for how long (a multi-hour `do_compile` reads as frozen and is not, see "2. Build").
- [ ] For anything likely to run more than ~20-30 minutes (i.e. almost every real build): either get periodic progress pushes going (offer the user `/loop` explicitly at launch time — a plain session cannot self-schedule wakeups outside `/loop`) or say plainly that progress is on-demand only. Don't just go silent and say nothing about the gap — this is about *acknowledging the gap exists*, not license to narrate every tick within it.
- [ ] On completion: the unprompted post-build report — every time, not only when asked for a "detailed report." Before sending it, walk the pre-send gate in "Report"; it lists the exact table header rows the reply must contain.

All commands assume `env.sh` is sourced and run from the kas work dir:

```sh
cd ~/oe/work
```

**The `cd` to `~/oe/work` is not optional, and it must be the `~/oe` short link, not wherever it points.** `kas-container` mounts the current directory at `/repo` and resolves every kas config path relative to it. `~/oe` exists precisely so this skill never has to reason about where mackas physically put the storage — always go through the link.

### 1. Record pre-build state (BEFORE building)

buildhistory auto-commits after every build; capture HEAD from inside the container (there is no host path to `git rev-parse` against):

```sh
mackas exec sh -c 'cd /build/buildhistory 2>/dev/null && git rev-parse HEAD || echo "(no buildhistory yet)"'
```

`mackas exec` never runs kas's checkout/reset step, so this is safe even while sibling layers carry local-only commits. If you reach for a hand-typed `kas-container shell` here instead, know that it resets sibling layers exactly like a `build` would — kas's checkout/reset step runs on *every* `kas-container` invocation, `shell` included, and a read-only query is not exempt. The `--skip repos_checkout --skip repos_apply_patches` pair is then mandatory whenever any sibling layer carries local-only commits (see "The `--skip` flag family" below) — forgetting it on a step that "only reads a value" is exactly how a clean-but-ahead sibling layer gets silently reset before the build even starts.

**Whenever the build will actually update sibling repos** — `--update` passed (see step 2 — `--skip repos_checkout` alone is NOT enough, see the note there), or a manual `git pull` across siblings before building — also snapshot every repo's current HEAD, so it can be reported after the build (step 3). Sibling checkouts under `~/oe/work` are ordinary host-visible directories (only `TMPDIR`/`DL_DIR`/`SSTATE_DIR` are hidden in the ext4 volumes), so this is plain host git, no `mackas exec` needed:

```sh
cd ~/oe/work
> "$SCRATCH/pre-build-shas.txt"
for d in */; do
  d="${d%/}"; [ -d "$d/.git" ] || continue
  echo "$d $(git -C "$d" rev-parse HEAD)" >> "$SCRATCH/pre-build-shas.txt"
done
[ -s "$SCRATCH/pre-build-shas.txt" ] && grep -q '^meta-angstrom' "$SCRATCH/pre-build-shas.txt" \
  || { echo "REPO-SNAPSHOT: FAILURE -- empty/incomplete snapshot, wrong cwd or broken ~/oe link"; exit 1; }
```

**Check the snapshot actually landed before trusting it.** A wrong cwd or a broken `~/oe` link makes the glob match nothing, leaving a 0-byte file with exit 0 from the loop above — and step 3's "nothing moved" table would then render as if genuinely nothing changed, which is a different claim than "the snapshot itself was empty." `meta-angstrom` is the sanity anchor: it is always present in a working `~/oe/work`, so its absence from the snapshot means the snapshot is wrong, not that every repo failed to move.

Skip this when the build is `--skip repos_checkout`'d or every repo is `commit:`-pinned — nothing will move, so there is nothing to snapshot.

### 2. Build

**Export `MACKAS_MONITOR=1` before every real build, by default — not as an optional extra to reach for after a build already looks stuck.** Skipping this is the single easiest way to lose the one live view into a long-running task; see "Watching one build live" below for what it buys and its env.sh-staleness caveat.

**A build must run under the `mackas monitor --once` poll loop (see the Building checklist above — never bare `mackas monitor` from an agent), or it must be stopped — never substitute log-tailing.** `tail`/`grep` on the raw redirected stdout is not an acceptable stand-in, not even temporarily "until the bridge comes up." If `mackas monitor --once` fails to attach right after launch (exits instantly with no output, or hits a `socket.timeout` — the bridge can genuinely take a few seconds to publish its port), **retry at least 5 times over about 60 seconds** before concluding it's really broken; a single failed attempt moments after launch is a timing race, not proof the bridge is dead. If it is still failing after real retries, stop the build rather than watch it any other way. Stopping means killing the actual `container` VM, not just the host-side shell/background-task wrapper — a `TaskStop`/killed shell only ends the local `kas-container` process, the Apple `container` VM keeps building underneath it. Confirm with `container list`; if the VM is still there, `container stop <id>` it, and don't consider the build stopped until that list is empty.

**`--update` is a separate flag from `--skip`, and it's the one that actually matters for "make sure repos are current."** Confirmed against kas 5.4's own source (`kas/repos.py`'s `fetch_async`): for a branch-tracked repo (no `commit:` pin), plain `kas-container build` with **no** `--skip` flags still does *not* re-fetch from upstream if a local clone already satisfies the configured branch name — it only fetches on a genuinely fresh clone, or when `--update` is passed (`kas/libkas.py:656`: *"Pull new upstream changes to the desired branch even if it is already checked out locally"*). Without `--skip`, kas *will* still reset/checkout to whatever it already has locally — safe, but not necessarily current. A build meant to actually pick up new upstream commits needs `--update` explicitly — real incident, 2026-08-18: four consecutive "no `--skip`" builds all silently reused a `meta-qcom` clone that was 146 real upstream commits (a month's worth) stale, because `--update` was never passed.

**Whenever `--update` is used, run a holistic patch test FIRST — before launching the real (possibly hours-long) build.** A repo drifting forward is exactly when one of this layer's own kas-managed patches is most likely to stop applying (upstream renamed/rebased/already-fixed the same thing a local patch works around — three separate real cases hit in one session: 2026-08-18, `meta-qcom`/`meta-openembedded`/`meta-ti` all had patches invalidated by real upstream drift). Discovering this via a failed multi-hour build, one patch at a time, is expensive and easy to misdiagnose as something else. `kas-container checkout` runs the exact same fetch + patch-apply code path as `build`, just stops before bitbake — use it as the pre-flight, and it's naturally shared across every machine in a batch since they all use the same sibling checkouts:

```sh
cd ~/oe/work
kas-container checkout --update \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml
```

Exit 0 with no `Could not apply patch` lines means every patch in the composition applies cleanly against the now-current repos. For each failure: check whether the target file/content already matches what the patch would produce (upstream already carries the equivalent fix — drop the now-redundant patch entry entirely from `kas/angstrom.yml`) before assuming it needs a rebase; only rebase (regenerate the patch against the new source, same edit→commit→`git format-patch`→copy→reset workflow as any other patch) when the underlying issue still genuinely exists. Only proceed to the real build once this exits clean.

Since checkout already ran, the actual build (every machine, if batching) uses `--skip repos_checkout --skip repos_apply_patches` — no need to repeat the fetch:

```sh
export MACKAS_MONITOR=1
kas-container build --skip repos_checkout --skip repos_apply_patches \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml \
  --target <target>
```

Run it in the background and tee to a log — even a fully-cached recipe streams thousands of lines; a from-scratch image build can take hours:

```sh
... > "$SCRATCH/<target>-build.log" 2>&1   # run_in_background: true
```

Then watch it with the `mackas monitor --once` poll loop from the Building checklist — that is the mechanism for watching progress, not re-tailing the log file, and not bare `mackas monitor`.

Watch the tail of the log:
- `Tasks Summary: Attempted N tasks ... all succeeded.` → success
- sstate reuse percentages show how much was cached vs. compiled from scratch
- `ERROR:` lines (grep them) → failure; the failing task's `log.do_<task>` path is printed, but it is a path **inside the container** (`/build/tmp/work/...`) — read it with `mackas exec cat <path>`, not a host `cat`.

The log goes quiet for as long as the longest single task runs — a large `do_compile` (`chromium-ozone-wayland`, `linux-*`, `webkitgtk`) holds the build-wide task counter at the same number for tens of minutes to hours. That reads as frozen and is not. The `mackas monitor --once` poll loop is the way to see inside such a task while it runs; see "Watching one build live" below.

For a one-off task without a full build (e.g. just fetch, or cleansstate):

```sh
mackas exec bitbake -c <task> <target>
```

(Repo-safe by construction. The one time to use a real `kas-container shell ... -c "bitbake -c <task> <target>"` instead is when a freshly added `patches:` entry must be applied first — `exec` always skips patch application, see "When it backfires" below.)

### 3. Report which repos changed (only when repos were actually updated)

**Whenever repos got updated at the start of the build — via kas's normal checkout or a manual `git pull` — show a summary table of what changed per repo, every time, not just on request.** Compare each repo's new HEAD against the snapshot from step 1 (plain host git against `~/oe/work/<repo>`, robust regardless of whether the repo tracks a branch, is `commit:`-pinned, or carries kas-applied patches on top — no need to parse reflog or distinguish repo types):

```sh
cd ~/oe/work
while read -r repo old; do
  new=$(git -C "$repo" rev-parse HEAD)
  [ "$old" = "$new" ] && continue
  n=$(git -C "$repo" rev-list --count "$old..$new" 2>/dev/null)
  echo "$repo|$old|$new|$n"
  git -C "$repo" log --oneline "$old..$new"
done < "$SCRATCH/pre-build-shas.txt"
```

Render as the `| Repo | Old | New | Commits | Summary |` table from the pre-send gate in "Report", with a short **written summary** of what actually changed in the last cell — read the full `git log --oneline` output for that repo and synthesize it (e.g. "podman/cloud-init/yq/upx/lopper version bumps, CVE status updates, vrunner test fixes"), don't paste raw commit subject lines as the summary. Repos where `old == new` don't need a row — if *nothing* moved, say that in one line instead of showing an empty table. A `commit:`-pinned repo never moves regardless of `--skip`; that's expected, not worth flagging as surprising.

### 4. Same target, multiple machines

Only one `kas-container` can hold the ext4 volumes at a time (the one-VM rule), so a same-target/multi-machine batch must be sequential — one shell script looping over machines, backgrounded as a whole, logging each machine to its own file. Every machine in the batch shares the same sibling checkouts, so run the `kas-container checkout --update` holistic patch test (see step 2 above) exactly **once**, before the loop — not per machine:

```sh
cd ~/oe/work
kas-container checkout --update meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/beaglebone.yml
```

(Any machine fragment works for this pre-flight — the repo checkout/patch step doesn't depend on `MACHINE`.) Fix and re-run this until it exits 0 with no `Could not apply patch` lines before starting the batch — see step 2 for the fix procedure. Only then loop over machines, all using `--skip repos_checkout --skip repos_apply_patches` since checkout already ran:

```sh
STATUSFILE="$SCRATCH/batch-status.txt"
> "$STATUSFILE"
for m in beaglebone qemuarm64 rb1-core-kit riscv; do
  LOG="$SCRATCH/<target>-${m}-build.log"
  kas-container build --skip repos_checkout --skip repos_apply_patches \
    meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/${m}.yml \
    --target <target> >> "$LOG" 2>&1
  rc=$?
  if [ "$rc" -eq 0 ] && grep -q "Tasks Summary:.*all succeeded" "$LOG"; then
    echo "${m}: SUCCESS rc=$rc" >> "$STATUSFILE"
  else
    echo "${m}: FAILED rc=$rc" >> "$STATUSFILE"
    echo "BATCH ABORTED after ${m} failure" >> "$STATUSFILE"
    break
  fi
done
mackas volume fstrim all
```

**Check both `rc` AND the positive success marker, and stop the loop on the first failure — never let it silently continue to the next machine.** `rc=0` alone isn't sufficient proof (a kas warning-only exit, or a script bug elsewhere, can still be 0); the marker alone isn't sufficient either if the log was truncated. A loop with no failure check (real incident, 2026-08-18: an earlier version of this exact loop had none) will happily run every remaining machine against a repo state a prior machine's failure left inconsistent, wasting the whole batch's wall-clock on doomed builds and reporting nothing wrong until someone reads the raw logs by hand. Read `$STATUSFILE` for the final per-machine verdict, not just the loop's own exit code.

When repos should NOT be updated for this batch (e.g. a sibling carries local-only commits), skip the `checkout --update` pre-flight entirely and use `--skip repos_checkout --skip repos_apply_patches` on every machine — see "The `--skip` flag family" below.

**Report after each machine finishes, not just once at the end of the whole batch.** The loop only notifies on completion of the *entire* backgrounded script, which can be 30+ minutes of silence across 4 machines. A per-line-notification harness tool is safe here, and only here, because the loop below prints one line per *machine finishing* — a real event, at most 4 of them — not one line per poll. That is the test for wiring anything into such a tool: one line per event, never one line per tick. Poll each machine's log for its own completion marker and emit one line per machine as it lands:

```sh
set -u                    # deliberately NOT -e: see the exit-status note below
declare -a MACHINES=(beaglebone qemuarm64 rb1-core-kit riscv)
declare -a SEEN=()
FAILED=0
while true; do
  for m in "${MACHINES[@]}"; do
    if [[ ! " ${SEEN[*]} " =~ " ${m} " ]]; then
      LOG="$SCRATCH/<target>-${m}-build.log"
      if [ -f "$LOG" ] && grep -qE "Summary: There (was|were)" "$LOG" 2>/dev/null; then
        if grep -q "ERROR" "$LOG"; then
          echo "${m}: FAILED - $(grep -E 'Summary: There|Command .* failed' "$LOG" | tail -3 | tr '\n' ' ')"
          FAILED=$((FAILED + 1))
        else
          echo "${m}: SUCCESS - $(grep 'Tasks Summary' "$LOG" | tail -1)"
        fi
        SEEN+=("$m")
      fi
    fi
  done
  if [ "${#SEEN[@]}" -eq "${#MACHINES[@]}" ]; then
    echo "all machines reported (${FAILED} of ${#MACHINES[@]} failed)"
    break
  fi
  sleep 10
done
exit 0    # the watch ran to completion; per-machine pass/fail is in the lines above
```

Never let a grep-family or test-family command be the last thing a Monitor or backgrounded script runs. `grep -c "^ERROR:" "$LOG"` prints `0` and exits 1 on a fully clean build — the count is right, the exit status is not — so the Monitor tool's completion notification reports the watch as failed on exactly the builds that succeeded. Under `set -e` it is worse than cosmetic: the script dies at that line and every summary line after it is silently dropped, so a clean build reports less than a broken one. Under `set -o pipefail` the same non-zero poisons any pipeline the grep sits in. Same exit convention, same trap: `grep`/`grep -v`/`grep -q`/`grep -c` (no match), `diff`/`cmp` (files differ), `git diff --quiet` (there are changes), `pgrep`/`pkill` (nothing matched), `[ ]`/`[[ ]]` (false), `(( ))` (result is zero). Guard the value with `|| true` and end the script with an explicit `exit 0` — the `|| true` alone only holds while that grep happens to be last.

A monitor's exit status answers "did the watch run correctly", not "did the build pass" — build outcome belongs in the emitted lines, where it can say which machine. A backgrounded build wrapper is the reverse: there the exit code should be the build's, so capture it before doing any reporting (`rc=$?; echo "${m}: rc=$rc, ..."`).

Start this Monitor right after backgrounding the build script, not after it finishes — the whole point is per-machine updates *during* the batch instead of one lump summary at the end.

`mackas monitor`/`MACKAS_MONITOR` cannot itself span a multi-machine batch — each `kas-container` invocation publishes its own bridge on the same port, and `mackas monitor` exits on the first dead port or first terminal status, so it only ever announces one machine. This is a known, tracked gap in mackas (an opt-in reconnect-and-wait flag has been proposed), but the Monitor-tool log-polling loop above is the correct mechanism for this skill's batch reporting regardless of whether that lands.

### Watching one build live (`mackas monitor`)

**Everything below describes the bridge itself. An agent still only ever invokes it as `mackas monitor --once`, inside the poll loop in the Building checklist** — the bare, following form streams a line every few seconds and turns any per-line harness notification into a forced reply per tick. Read that checklist before running anything from this section.

**`mackas monitor` works for this skill's hand-typed `kas-container build` calls too — requires a fresh `env.sh`.** The sourced `kas-container` wrapper recomputes `--runtime-args` live on every call (`mackas runtime-args`, asked fresh each time) rather than freezing it at `mackas setup` generation time, so exporting `MACKAS_MONITOR=1` before a hand-typed build publishes the same progress-bridge port `mackas smoketest` does — confirm with `mackas runtime-args` before/after exporting it, the `-v .../mackasjson.py:ro ... -p 8801:8801` chunk should appear once set. `mackas set MACKAS_MONITOR_NOTIFY 1` persists a native-notification-on-transitions config value (`~/.mackas.conf`) that `mackas monitor --once --notify` picks up once that bridge exists. **Same env.sh-staleness rule as everywhere else** — an `env.sh` generated before the live-recompute behavior landed keeps freezing `--runtime-args` at generation time instead, so `MACKAS_MONITOR=1` exported mid-session does nothing with no error. For this skill's own multi-machine batch reporting, the Monitor-tool loop above remains the mechanism actually used; the native bridge is the tool for one build at a time, and the only one that can see inside a single long-running task.

The bridge Python and the poller are both read live out of the mackas checkout — `mackas-uibridge/mackasjson.py` is bind-mounted into the container at container start, and `mackas monitor` runs `tools/mackas-monitor` straight from that checkout — so an updated mackas needs no extra step beyond the usual `mackas -y setup "$MACKAS_ROOT" && source ~/oe/env.sh` refresh (`MACKAS_ROOT` here is `/Volumes/Angstrom-builds/v2026.06`). A build that is already running keeps whatever bridge it started with, so a mid-build update only takes effect from the next build.

**`MACKAS_MONITOR=1` is safe to trust for pass/fail, not just progress.** The bridge queries the cooker for MACHINE/DISTRO only after `bb.ui.knotty.main()` has handed the real environment to the server (querying earlier would force a premature config parse and crash oe-core's `base.bbclass` event handlers, turning an otherwise-100%-successful build into a non-zero exit with spurious `ERROR` lines), and it lingers ~5s after recording a terminal status so a poller reliably observes `[success]`/`[failed]` rather than just seeing the port vanish mid-`[building]`. If either symptom (crash-on-otherwise-clean-build, or `mackas monitor` never reporting a terminal state) resurfaces, treat it as a signal `mackas` is stale relative to this note — check what the mackas checkout is actually at (`git -C "$(dirname "$(command -v mackas)")" log -3`; uncommitted working-tree fixes are still live, same as any bash-script/bind-mounted-Python change in this tool).

**Each poll prints one line, and it carries progress from *inside* the running task as well as the build-wide counter.** The leading `[status] done/total  recipe:task` substring is a stable contract; percent, elapsed time, per-task progress and a failed-so-far count are appended after it, never inserted into it:

```
[building] 412/3170  busybox_1.36.bb:do_fetch  13%  2:05  (chromium-ozone-wayland_150.0.7871.124.bb:do_compile 42%)
```

The parenthesised part is the task's own progress. It names its own `recipe:task` rather than the build's `current`, because a long instrumented compile is precisely the task that others start and finish around. Up to two reporting tasks are named, then `+N more`; a task that reports "progress is happening but not how much" (git counting objects) reads as `busy` rather than `0%`, and the download fetchers append a rate (`at 1.2M/s`). The underlying N-of-M is not available — bitbake divides it out to a percentage before publishing — so `42%` yes, `762/1814` no.

**Which tasks report it.** This relays bitbake's own progress framework (`bb/progress.py`), which a task opts into with a `progress` varflag on its shell function (or a handler constructed directly in Python), so coverage is per-task rather than universal:

- **ninja-parsing compiles**, the common case for anything expensive: `cmake.bbclass` and `meson.bbclass` set the `outof:^\[(\d+)/(\d+)\]\s+` varflag on `do_compile`, and any recipe that drives `ninja` itself can set the same flag. **`chromium-ozone-wayland:do_compile` is covered this way** — `meta-browser/meta-chromium`'s `chromium-gn.inc` sets that varflag on its own bare `ninja` invocation, so this project's multi-hour Chromium compile does report a real percentage despite being a GN build that inherits neither bbclass. `chromium-gn-native`'s `do_compile` (a Python bootstrap script) does not.
- `cargo`/`cargo_c`/`waf` `do_compile`, `libc-package.bbclass`'s `oe_runmake`, `image.bbclass`'s `do_rootfs`, and `do_fetch` for the `git`/`wget`/`s3`/`perforce` fetchers.
- **Not** plain autotools/make `do_compile`, and not `do_configure`/`do_install`/`do_package`/`do_rm_work` — those carry no varflag at all. An empty parenthesis is the normal case for such a build, not a fault; there, `recipe:task` plus elapsed time is the whole of what can be said.

**Reach for `mackas monitor --once` before the task's own log file.** Reading a running task's log (`mackas exec sh -c 'tail -5 /build/tmp/work/.../temp/log.do_compile'`) is not available *while the build runs at all* — `mackas exec` starts a second container and the one-VM rule refuses it. `mackas monitor` polls a published HTTP port and attaches no ext4 volume and starts no container, so it is the only progress view that works concurrently with the build that produced it. The per-task log stays the fallback for after the fact, and for the tasks the progress framework does not cover.

## Analyzing results

### Buildhistory (what changed in package/image content)

**`mackas retrieve buildhistory` copies the whole buildhistory git repo out to `~/oe/artifacts/buildhistory` — a real host-side git checkout you can `git log`/`git diff`/`git show` directly on macOS**, no `kas-container shell -c` round-trip per query. It resolves `BUILDHISTORY_DIR` via bitbake itself (a sibling of `tmp/`, not inside it — same non-default-path caution as `DEPLOY_DIR`), and skips cleanly with a specific message if the project doesn't `INHERIT buildhistory` (angstrom.yml's `local_conf_header` already does, so this always resolves here). Same one-VM rule and `MACKAS_KAS_CONFIG`/`MACKAS_PROJECT_DIR` requirement as `retrieve deploy` below — export the two variables (see `mackas exec` above) or let a real `kas-container` call in the same shell derive them first. It's a full copy each time (not incremental), so for a single quick lookup the in-container query below is still cheaper; retrieve the whole repo when you want to browse history or diff several commits with normal git.

For a single value without retrieving anything, query inside the container via `mackas exec`. Chain commands with `&&` in one `sh -c` string — each `exec` invocation is a fresh container, nothing persists between calls except what is on the ext4 volumes themselves:

```sh
mackas exec sh -c "
  cd /build/buildhistory &&
  git log --oneline $PRE..HEAD &&
  git diff --stat $PRE..HEAD
"
```

`git log` output prefixed `No changes:` means identical output to the previous build.

Per-recipe metadata lives under `packages/<TUNE_PKGARCH>/<recipe>/`. The arch string varies by machine (beaglebone: `armv7at2hf-neon-angstrom-linux-gnueabi`; qemuarm64: `armv8a-angstrom-linux`) — discover it rather than hard-coding it:

```sh
mackas exec sh -c "
  cd /build/buildhistory &&
  A=\$(git show --name-only --oneline HEAD | grep -oE 'packages/[^/]+/<recipe>' | head -1) &&
  git show HEAD:\$A/latest &&
  git show HEAD:\$A/<recipe>/latest &&
  git show HEAD:\$A/<recipe>/files-in-package.txt
"
```

Report: version (PV-PR), which sub-packages were produced, runtime deps (RDEPENDS), installed size (PKGSIZE), and any notable files (services, configs, binaries). Empty sub-packages (e.g. `-doc`, `-locale`, `-staticdev`) have a zero-line `files-in-package.txt` and produce no ipk — call those out as empty, not missing.

For images, the same pattern against `images/<machine>/glibc/<image>/` — `image-info.txt`, `installed-package-names.txt`, `files-in-image.txt`; `git diff` between `$PRE` and `HEAD` shows added/removed packages.

### Deploy (the shippable artifacts)

Get the real path first (see DEPLOY_DIR above — do not assume `deploy/` sits under `tmp/`). For quick inspection (sizes, filenames, freshness), query inside the container — do not copy multi-GB artifacts to macOS just to `ls` them:

```sh
mackas exec sh -c "
  DEPLOY_DIR=\$(bitbake-getvar --value -q DEPLOY_DIR) &&
  find \$DEPLOY_DIR/ipk -name '<recipe>*.ipk' -newermt '<HH:MM before build>' -printf '%TT  %10s  %p\n' | sort
"
```

- Recipe builds land ipks in `deploy/ipk/<pkgarch>/` (e.g. `armv7at2hf-neon/` for beaglebone, `armv8a/` for qemuarm64; plus `all/` and `<machine>/`). The ipk dir uses the short pkgarch while buildhistory uses the full triplet.
- Image builds land in `deploy/images/<machine>/`: `Angstrom-<image>-<version>-<machine>.{wic.xz,tar.gz,manifest,spdx.json}` plus `.rootfs.*` symlinks, `.wic.bmap`, `testdata.json`. **The filename stem is `IMAGE_BASENAME`, which an image recipe can override away from its own PN** — e.g. `recipes-images/angstrom/console-base-image.bb` sets `IMAGE_BASENAME = "base-image"`, so building `console-base-image` produces `Angstrom-base-image-*` artifacts and a `buildhistory/images/<machine>/glibc/base-image/` dir, not `console-base-image`. Check the recipe for an `IMAGE_BASENAME` override before concluding a target "didn't produce anything." Prefer the buildhistory image dir diff over the `.manifest` for package deltas.

### Retrieving files off the build volumes

**Only when the actual artifact file needs to leave the VM** (attach it, flash it, hand it to the user) — `mackas retrieve deploy`. `retrieve` resolves `DEPLOY_DIR` itself via a `bitbake_getvar()` helper (`bitbake-getvar --value -q DEPLOY_DIR`) rather than assuming the OE-core textbook default, so it works correctly with this distro's non-default `DEPLOY_DIR` (falls back to `/build/tmp/deploy` with a warning only if the query fails).

**`mackas retrieve deploy images [MACHINE]` narrows a full `deploy` retrieval down to just the boot images for one board** (`DEPLOY_DIR_IMAGE`, resolved the same non-guessing way as `DEPLOY_DIR` itself) — worth reaching for instead of bare `deploy` whenever only the `.wic.xz`/`.tar.gz` artifacts are wanted, not the whole ipk/rpm feed and every MACHINE a multiconfig build has ever produced. `MACHINE` defaults to the project's currently configured one; an explicit override lands at `$dest/deploy/images/<MACHINE>` (bare `deploy images` lands at `$dest/deploy/images`) and must be a plain name — `/` or `..` in it is refused rather than spliced into the guest path. Composes with the other objects normally, e.g. `mackas retrieve deploy images beaglebone buildhistory`.

**Every `retrieve` object prints its real measured transfer size** (`<sub>: SIZE to transfer to $dest`, e.g. `deploy: 4.2G to transfer to ~/oe/artifacts`), and warns (never refuses) if `$dest`'s filesystem looks too small for it — a du/df failure never blocks the retrieval, this is a heads-up only. Runs under `--dry-run` too, so a preview shows the real size rather than nothing.

That query needs `MACKAS_KAS_CONFIG`/`MACKAS_PROJECT_DIR` set. **The simplest, safest way is to export them yourself** (see `mackas exec` above) — no container invocation at all, so none of the footguns below apply:

```sh
export MACKAS_PROJECT_DIR=meta-angstrom
export MACKAS_KAS_CONFIG=kas/angstrom.yml:kas/<machine>.yml
```

Alternatively the sourced `kas-container` wrapper **derives both automatically** from whatever file list you actually pass a hand-typed `build`/`shell`/`checkout` call — e.g. `meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/beaglebone.yml` (from `~/oe/work`) derives `MACKAS_PROJECT_DIR=meta-angstrom` and `MACKAS_KAS_CONFIG=kas/angstrom.yml:kas/beaglebone.yml` — and exports them into that shell only (never to a config file, never overriding a value already set). So after a real build in the same shell, `retrieve` works with no extra step. If you rely on derivation rather than explicit exports:

- **It only derives within a single shell session.** A `kas-container` call with the real file list must have run first, in the same shell you're about to run `retrieve`/`clean tmp+deploy`/`buildstats analyze` in — otherwise `MACKAS_PROJECT_DIR`/`MACKAS_KAS_CONFIG` are unset and the query falls back to the wrong default path. Confirm with `mackas retrieve --dry-run deploy` — it should show `test -d /build/deploy`, not `/build/tmp/deploy`.

  ```sh
  kas-container shell --skip repos_checkout --skip repos_apply_patches \
    meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml -c true
  ```

- **A derivation-only call like the above is not exempt from the sibling-layer reset — same rule as "Record pre-build state" above.** kas's checkout/reset step runs on every `kas-container` invocation, so a `shell ... -c true` that exists only to derive two variables will still reset a clean-but-ahead sibling layer to its configured branch head and drop its local-only commits from the working tree (recoverable via reflog, but avoidable). Always pass `--skip repos_checkout --skip repos_apply_patches` here — or skip the whole dance by exporting the two variables directly, which is why that is the preferred route. Easy to forget precisely because the call "just derives a variable."
- **Run the derivation call plain and unpiped, in the foreground — never `| tail`, `| grep`, or any other pipe.** Bash runs the left side of a pipe in a subshell, so the wrapper's `export MACKAS_PROJECT_DIR=.../MACKAS_KAS_CONFIG=...` never reaches the parent shell: both stay unset, with no error, and the next `retrieve`/`clean tmp+deploy`/`buildstats analyze` silently falls back to the wrong OE-core default paths (`/build/tmp/deploy` instead of this distro's `/build/deploy`). Redirecting to a file (`> file.log 2>&1`) is fine — that forks no subshell.
- It explicitly does **not** derive anything for a composition spanning *sibling* layers (e.g. `meta-angstrom/...:meta-ti/...`) — not relevant here since every composition in this project stays within `meta-angstrom`'s own `kas/` fragments.
- **Requires a fresh `env.sh`** (see Preflight) — an `env.sh` generated by an older mackas won't have this derivation logic.
- `logs`/`buildstats`/`buildhistory` and `mackas clean tmp+deploy` resolve the same way and benefit identically — `clean tmp+deploy` runs the same `bitbake_getvar` machinery for `TMPDIR`/`DEPLOY_DIR` and hits the identical wrong-default fallback without the derivation. `buildhistory` is a fourth retrievable object alongside `deploy`/`logs`/`buildstats` — see the Buildhistory section above.

See the `--skip` section below for `mackas retrieve`'s handling of sibling layers with local-only commits.

**Never run `container` (Apple's CLI) directly to work around this or anything else — always go through a `mackas`/`kas-container` command.** mackas's own volume-attachment guard (`volume_in_use` in the mackas script) only runs when an operation goes through `mackas`; bypassing it with a raw `container` invocation skips that safety net. If a file genuinely needs to come out of `/build/deploy` and mackas can't do it, that is a case to raise with the user, not to solve by hand.

### Report

**Every build gets this summary at the end, unprompted — not just when a "detailed report" is asked for, and not skipped for a throwaway iteration/attempt inside a debug loop either.** Task counts and pass/fail alone are NOT the report — they don't say whether the build was cheap (cache-restored) or expensive (recompiled). Standing convention of this repo's owner: every field below is required, not "include if convenient."

**This section owns every chat-facing table in this skill** — build reports and feed-upload reports alike. "Publishing to the package feed" covers upload *mechanics* only and sends you back here for the report.

**Set `MACKAS_MONITOR=1` from before the build starts** — the bridge cannot be attached retroactively, so this is a preflight step, not something to add once the build looks slow. While it runs, give a progress line roughly every 10 minutes with real numbers (`N/M tasks (X%), on recipe:task`), then go quiet once genuinely idle between updates — no filler lines with nothing new to say.

#### Pre-send gate — walk this against the draft, before sending it

Each box names the **literal header row** that must appear in the reply. Search the draft you are about to send for that exact string; do not check a box against your intention to include it. **A sentence describing the numbers is not a substitute for the table** — "68 of 71 packages uploaded across four arches" satisfies nothing below. If a box applies and its string is not in the draft, the draft is not finished.

- [ ] Any build finished → required fields 2-5 below, each carrying a real number (field 1 is conditional).
- [ ] Repos moved during this build → `| Repo | Old | New | Commits | Summary |`
- [ ] More than one machine built → `| Machine | Result | Duration | sstate reuse | Notes |`
- [ ] buildstats analyzed → `| Task type | CPU time (s) | Wall time (s) | Tasks | Parallelism |` **and** `| Recipe | CPU time (s) | Wall time (s) | Tasks |`
- [ ] Packages uploaded → `| Package | Version | Archs |`
- [ ] Exactly one arch uploaded → an **Upload summary** line immediately above that table
- [ ] Something the user asked for is absent from the upload table → `| Package | Reason |`
- [ ] Packages uploaded → count every draft row against its buildhistory recipe; any recipe with more than 3 rows must be one collapsed `<recipe> (<N> packages)` row instead, not listed individually (see "Upload tables").

Skip a box that genuinely doesn't apply — don't narrate it (field 1 says so explicitly for repo changes). The trap is the last box: a package the user named by hand that is missing from the upload table has not made it inapplicable, it has made it required.

**Required fields, every single build:**

1. **Repo changes** — ONLY when repos actually moved (a manual `git pull`, or the rare deliberate un-skipped `repos_checkout`). A normal `--skip`'d build touches no repo content: no table, nothing to report, don't pad the report with "no changes." When they did move: a table, `Repo | Old | New | Commits | Summary`, with a synthesized written summary of the whole range in the last cell — not a raw list of commit subjects, and not just the newest one (see step 3 below for the worked example).
2. **Duration** — wall-clock, not "it finished." On macOS use BSD `date` (`date -d` does not exist here): first vs. last timestamped log line, `date -j -f '%Y-%m-%d %H:%M:%S' "$ts" '+%s'`. Convert to Europe/Amsterdam before reporting — most sources here log UTC.
3. **Build result + sstate reuse %** — bitbake prints no single overall number; compute it from the `Tasks Summary` line as didn't-need-rerun / attempted (`awk 'BEGIN{printf "%.1f%%", (c/a)*100}'`). Quote the per-task-type `X% sstate reuse (...)` lines from the "Build completion summary" block too whenever the headline number hides something (high overall reuse but e.g. `do_package: 0%`).
4. **Buildstats + buildhistory, retrieved and summarised** — `mackas retrieve buildstats buildhistory` (volume-only on macOS, same reasoning as `deploy`), then `mackas buildstats analyze`. buildstats gives real per-task wall-clock/CPU (report the slowest task); buildhistory gives version/size/dependency deltas vs. the previous build — this is what catches a version going backwards *before* it surfaces as a `do_packagedata` QA failure downstream. The analyzer's `by task type` and `top recipes by cpu` blocks go into the reply as the two tables named in the gate above, with the headers relabeled (see "Analyzing buildstats").
5. **Deploy** — which artifacts were written, with sizes and paths.

Flag anything surprising (unexpected new RDEPENDS, size jumps, QA warnings, empty packages, a version going backwards).

**Rendered, single build:**

> `console-pico-image` / qemuarmv5 — SUCCESS
> Duration 18m42s (07:12:03 → 07:30:45 CEST) · sstate reuse 91.4% (3,412 attempted / 3,119 cached)
> buildhistory: busybox 1.37.0-r0 → 1.37.1-r0 · image 41.2 → 41.4 MiB (+204 KiB) · no new/dropped packages
> buildstats: slowest linux-stable:do_compile 6m11s

**Multi-machine sweeps** render as a markdown table **in the chat reply**, not left in a log the user has to open: `Machine | Result | Duration | sstate reuse | Notes`, one row per machine. **Size/RAM** — one before-all/after-all pair per *batch*, never per machine/option: rootfs `IMAGESIZE` delta, `MemAvailable` right after boot, combined RSS of relevant services when applicable.

#### Upload tables

**`upload-packages.py` prints the package table itself, on stdout, after its own log lines** — a `Packages published:` header (`Packages would publish:` under `--dry-run`) followed by `| Package | Version | Archs |`. Copy that block into the reply rather than re-deriving it from the log or the feed listing. It already collapses OE's split subpackages (`-dbg`/`-dev`/`-doc`/`-staticdev`/`-src`/`-ptest`/`-lic`/`-locale*`) into the base recipe name and comma-joins every arch into one cell. Never widen it: a row per `-dbg`/`-dev`/`-src` variant, or per-arch size/timestamp columns, is chaff that stays in the tool's own log.

**Multi-arch** (several machines/tunes uploaded together): the table alone, no summary line above it:

| Package | Version | Archs |
|---|---|---|
| domoticz | 2026.3.18327+git0+abc1234-r0 | armv5e, armv8a, riscv64imafdc, x86-64-v3 |
| samba | 4.21.1-r0 | armv5e, armv7at2hf-neon |

**Single-arch**: an `Upload summary` line above the table. Every number in it comes from the tool's own output — the `UPLOAD-PACKAGES: SUCCESS ...` sentinel carries `scanned=`, `already-present=`, `planned=`, `uploaded=` and `remote-dir=`; the `Uploading N packages (X MiB)` and `Upload <id> complete: N packages in Ys` log lines carry size and elapsed time; parallel streams is `--jobs` (default 4). Exact shape —

> **Upload summary** — 68/71 packages uploaded, 3 already on the server · 1.2 GiB transferred in 4m12s (~4.9 MiB/s, 4 parallel jobs) · target: `/data/www/angstrom/feeds/v2026.06/ipk/glibc`

— then the table (Archs column still present, one arch repeated down every row):

| Package | Version | Archs |
|---|---|---|
| bash | 5.2.32-r0 | armv5e |
| domoticz | 2026.3.18327+git0+abc1234-r0 | armv5e |

**Anything the user asked to have published that is missing from that table gets its own table right after, `Package | Reason`** — a recipe that doesn't exist, a build failure, an arch mismatch. These never reach `upload-packages.py` at all (no ipk was ever built, so they are not even in its `scanned=` count), so nothing but this table reports them. Never silently drop one:

| Package | Reason |
|---|---|
| cdi | recipe not found ("nothing provides cdi") |
| thermald | build failed, x86-only dependency assumption, doesn't fit qemuarmv5 |
| yaffs2-utils | build failed, see `<logpath>` |

Packages the server already had are counted in the summary line, not listed row by row here.

**Collapse a recipe contributing more than 3 rows into one.** `upload-packages.py`'s own collapse only merges suffix variants of the *same* package name (`-dbg`/`-dev`/...) — it can't catch a recipe that fans out into differently-named packages, e.g. glibc's ipks (`ldconfig`, `ldd`, `ldso`, `libc6`, `nscd`, `sln`, `tzcode`, ...) share no common name at all. Real recipe origin is in buildhistory, not derivable from the package name: `packages/<pkgarch>/<recipe>/` lists every package that recipe produced as a direct subdirectory, and `packages/<pkgarch>/<recipe>/latest`'s `PACKAGES` line confirms it. Where more than 3 rows resolve to the same `<recipe>`, replace them with one: `<recipe> (<N> packages)`, using the version already shown on any one of those rows (subpackages of one recipe share it) and the same Archs cell. Example — the glibc-family rows this session actually produced:

| Package | Version | Archs |
|---|---|---|
| glibc (15 packages) | 2.43+git0+1c9988e525-r1 | armv5e |

**The specific failure mode this convention exists to prevent** (flagged twice by Koen): duration/sstate-extraction gets skipped at *script-writing* time, not report time. When writing a build-sweep script, put the duration and sstate extraction in at the same moment as the pass/fail check — not bolted on afterwards as a separate reporting step. A sweep script that greps only `Tasks Summary` is the recorded mistake; don't repeat it in a new script.

A build report says what was *assembled*. To prove an image actually boots and behaves, see the `boot-validate` skill — it drives the oe-core qemu machines and the beaglebone AM335x emulator to a login shell and runs functional checks there.

### Reclaim disk space

Run after every build, once the container has exited (the volumes must be free, same one-VM rule as everywhere else):

```sh
mackas volume fstrim all
```

The ext4 volumes are sparse images that only ever grow — a from-scratch build can add tens of GB to `oe-build-tmp` that deleted intermediate files never give back on their own. `fstrim` reclaims the freed host disk space; it skips any volume still busy rather than failing the step.

**`mackas volume resize <name> <size>` grows a volume** — relevant if `DL_DIR`/`SSTATE_DIR` genuinely need more headroom, without destroy-and-recreate losing the whole cache. It always copies into a fresh, correctly-sized volume and destroys the old one; there is no in-place path — Apple `container` formats volumes with ext4's `sparse_super2` feature, and the guest kernel has no online-resize support for a filesystem carrying it, so growing the ext4 inside an attached (mounted) volume is structurally impossible on this runtime, not just unimplemented. The copy is checksummed, so data integrity across a resize is not a concern. Same confirmation pattern as `sstate prune` — needs `--yes`/`-y` when not running in a terminal, or it reports the real growth-vs-free-space numbers and declines rather than hanging on a prompt nothing will answer. The copy runs at host disk speed — doubling a `DL_DIR` volume holding tens of GB of `git2/` sources takes well under a minute — and file count and `du` size come out identical on the far side.

If the volume being resized (or destroyed) was **relocated** onto another drive (`mackas status` shows whether it was), the destroy step only removes the daemon's *reference* — the image itself lives on the other drive. An older mackas silently left that full-size image behind, "destroyed" but still costing the space, with nothing left pointing at it to explain where it went; current mackas warns with the leftover's size and offers to remove it. If a resize/destroy on a relocated volume doesn't reclaim the space you'd expect, look for that prompt in the output — and for the leftover directory on the relocated drive if it was declined.

### Cleaning

`mackas clean` with no target still does its original job: delete and recreate the **whole** `oe-build-tmp` volume (and clear `~/oe/logs`). That drops everything under `TOPDIR`, not just `tmp/` — deploy, buildhistory, `conf/`, `cache/` all go with it, silently. `DL_DIR`/`SSTATE_DIR` are separate volumes; bare `clean` never touches them — only their own explicit targets (`clean downloads`, `clean sstate`) do.

`mackas clean <target>` (repeatable, order doesn't matter) narrows that to one slice at a time, each independently confirmed:

- **`tmp+deploy`** — clears `TMPDIR` and `DEPLOY_DIR` **in place** inside the live volume, without deleting/recreating it, so buildhistory and `conf/` survive (unlike bare `clean`). Always clears both together — bitbake's stamps live under `TMPDIR` and record what already wrote `DEPLOY_DIR`, so clearing only one leaves the other inconsistent with it; there's no narrower "deploy only" or "tmp only" form. `SSTATE_DIR` survives, so the next build is mostly cheap sstate restores, not recompiles. Being an in-place `rm -rf` rather than a volume swap, it's slower than bare `clean`, and an in-place `rm` alone wouldn't reclaim host disk — so it **auto-fstrims the TMPDIR volume afterward** itself (`MACKAS_FSTRIM_AUTO=0` skips that).
- **`downloads`** — deletes and recreates just the `DL_DIR` volume. Independent of the others; fetched sources have no stamp relationship to `tmp`/`sstate`.
- **`sstate`** — deletes and recreates the whole `SSTATE_DIR` volume. For age-based partial cleanup instead of a full wipe, `mackas sstate prune --older-than N[d]` (below) is the narrower tool.

Same one-VM rule as everywhere else — each target refuses while a running build/shell still holds the volume it needs.

Prefer `mackas retrieve buildhistory` (see above) or `mackas clean tmp+deploy` over bare `clean` whenever the buildhistory record from before the clean still matters — bare `clean` gives no warning that it's taking buildhistory with it.

**Never run `kas-container purge`.** It deletes the build dir, sstate, downloads, AND every repo kas manages — including sibling layers that may carry local-only, unpushed commits. Check current state with `git -C <layer> log --oneline @{u}..` before assuming any layer is safe; this project has repeatedly carried unpushed work in `meta-dominion` / `meta-qcom-3rdparty` / `meta-kodi`. `kas clean` is narrower (only removes `tmp*`) and safe by comparison, but still leaves this distro's non-default `/build/deploy` behind — prefer mackas's own `clean` commands above instead of either.

### Pruning sstate by age

`mackas sstate prune --older-than N[d]` (e.g. `--older-than 90d`) deletes sstate objects bitbake hasn't reused in at least N days, without touching the rest of the cache — the middle ground between doing nothing and `mackas clean sstate`'s full wipe.

"N days old" means *not reused* in N days, not *created* N days ago: sstate.bbclass touches (updates the mtime of) an object every time a build finds and reuses it, so an object from months ago that's still hit on every build stays fresh indefinitely, while one from last week that nothing has needed since is fair game.

Safe to prune aggressively — sstate is hash-addressed, so an object pruned here that turns out still to be needed just gets rebuilt once, at the cost of that one task, never a correctness risk.

Two-phase like the rest of mackas's destructive commands: it always scans for real first (even under `--dry-run` — the count/size has to be real to be worth anything) and reports the object count and reclaimable size, then only deletes after confirmation (or `-y`/`--yes`). Same one-VM rule — refuses while the sstate volume is attached to a running build/shell.

A successful prune deletes in place inside the already-attached volume, so it **auto-fstrims the sstate volume afterward** itself (`MACKAS_FSTRIM_AUTO=0` skips that, same knob `clean tmp+deploy` uses) — reclaim is no longer a separate manual step to remember, and a failing fstrim never turns a successful prune into a reported failure.

For more surgical pruning — keep only what one specific checkout's stamps still reference, or drop older duplicates of the same package/arch/task signature — that's a job for oe-core's own `scripts/sstate-cache-management.py` run inside a project checkout, not this. `mackas sstate prune` solves the coarser "nothing has touched this in months" case; it doesn't replace the finer-grained tool.

## Reproducibility artifacts: `mackas lock` / `mackas dump`

Two thin wrappers around kas's own reproducibility mechanisms, worth reaching for whenever a build's exact inputs need to be pinned down or recorded (e.g. before/after a suspicious result, or alongside a report):

- **`mackas lock`** runs `kas-container lock`, writing a resolved lockfile *into the checkout* next to the kas config it locked (kas's own choice of name/location, not mackas's). `/repo` is a host bind mount, so the lockfile is immediately visible on the Mac — no `retrieve` needed. This one is **not** read-only from the checkout's perspective (kas itself writes the lockfile), so it's not wrapped in the repo-preserving `--skip` flags `mackas exec` always uses — run it only when a fresh lock is actually wanted, not as a casual query.
- **`mackas dump`** runs `kas-container dump --resolve-env --resolve-local --resolve-refs`, saving the fully-resolved YAML (every env var, every local override, every repo ref resolved to its exact value) to `$MACKAS_LOGS/dump-<timestamp>.yml`. Purely read-only — writes nothing into the checkout, only to the logs.

Both refuse under the one-VM rule while a build/shell holds any of the three volumes, same as everything else.

## Publishing to the package feed

The feed-publish pipeline lives in `meta-angstrom/scripts/`: `upload-packages.py` (client, stdlib-only, hashes `DEPLOY_DIR_IPK`, rsyncs new files into a staged `incoming/<upload-id>/` on the server with a checksummed manifest), `sort-packages.py` (server-side: ingests+verifies staged uploads, sorts into the real feed tree, re-indexes), and `publish-feed.sh` (the reusable wrapper around both). Remote target is `koen@beast:/data/www/angstrom/feeds/v2026.06/ipk/glibc` by default (`PUBLISH_FEED_REMOTE`/`PUBLISH_FEED_REMOTE_DIR` to override).

```sh
scripts/publish-feed.sh                        # dry run: retrieve + preview only
scripts/publish-feed.sh --publish               # real upload only, no sort
scripts/publish-feed.sh --publish --sort        # upload + sort, the full pipeline
scripts/publish-feed.sh --sort                  # sort only (ingest a previous upload)
scripts/publish-feed.sh --publish --skip-retrieve  # reuse an existing ~/oe/artifacts/deploy
```

**`--publish` only uploads into `incoming/` on the server; it never sorts.** Sorting
(ingest, verify, move into the real feed dirs, re-index) is the separate, explicit `--sort` flag. **Default to `--publish` alone unless the user asks for a sort too** — a completed upload sits safely in `incoming/` until something ingests it, so nothing is lost by not sorting immediately, and running the sort step uninvited (a ~10+ minute re-index across every touched feed dir) is the wrong default. `--skip-retrieve` reuses an already-populated `~/oe/artifacts/deploy` from an earlier `mackas retrieve deploy` in the same session instead of re-fetching.

**Every upload ends with the report in "Report" → "Upload tables" — walk that section's pre-send gate before writing the reply.** The table shapes live there, not here; this section is mechanics only.

`upload-packages.py` prints that `| Package | Version | Archs |` table itself, on stdout, on real runs and dry runs alike — copy it in. Its arch cell comes from parsing each ipk filename's trailing `_<arch>` (`parse_ipk_filename`: first `_` splits off PN, last `_` splits off arch), **not** from the `DEPLOY_DIR_IPK/<PACKAGE_ARCH>/` subdir the file sits in. An arch that itself contains an underscore (`rb1_core_kit`, `beaglev_ahead`) therefore splits wrong — check those cells against the deploy subdirs before pasting a table from such a machine.

**`sort-packages.py`'s default (no `--feed-dir`) resolution walks up from the current
directory** looking for an `unsorted/` subdir, so it works from the feed base, from `unsorted/` itself (`sort.sh`-compatible), or from anywhere else under the tree — `incoming/`, `incoming/<upload-id>/`, a sorted arch dir, etc. — not just the two exact locations the original rewrite supported. Still errors clearly (`Not in or under a feed directory`) if run somewhere unrelated; pass `--feed-dir` explicitly to sidestep resolution entirely.

**`sort-packages.py` takes an advisory exclusive lock (`<feed-dir>/.sort-packages.lock`)
for the whole ingest+sort run, including `--dry-run`.** A second concurrent invocation against the same feed dir fails fast with a clear error instead of silently racing the first — without the lock, a `--dry-run` diagnostic run and a real run against the same `incoming/<upload-id>/` at the same time can leave the staging directory partially consumed, with confusing "manifest entry missing on disk" warnings and no clear error from either side. If you and the user (or two of your own commands) might touch the feed server around the same time, expect the lock to sometimes reject one side — that is it working, not a bug; just retry after the other run finishes.

**`sort-packages.py --drop NAME` forgets a package** (every version/architecture) from
`unsorted/files-sorted`, the dedup list the server uses to decide it already has a file. It only edits that list — it does not touch ipks already sorted into the feed tree. Use it to force a re-publish of something already on the server: after `--drop`, the next matching upload is treated as new again, and sorting it overwrites the stale file in place via a plain `os.replace`. Matches by the ipk filename's exact `PN_` prefix, so `--drop domoticz` does not also drop `domoticz-dbg`/`domoticz-dev` (pass those separately if intended — the flag is repeatable). Run it server-side: `ssh koen@beast python3 /data/www/angstrom/feeds/v2026.06/ipk/glibc/sort-packages.py --feed-dir /data/www/angstrom/feeds/v2026.06/ipk/glibc --drop <name>`.

**When editing `scripts/sort-packages.py`, it also has to be redeployed to beast** —
it's the server-side copy that actually runs; committing the change in this repo alone does nothing there. `rsync -av -e ssh scripts/sort-packages.py koen@beast:/data/www/angstrom/feeds/v2026.06/ipk/glibc/sort-packages.py`. `upload-packages.py` is client-side (invoked locally via `publish-feed.sh`), so it never needs deploying anywhere.

**Prefer `rsync -av -e ssh` over `scp`/piping through `ssh ... cat` for any file
transfer to beast** — `scp`'s sftp subsystem has been observed refusing the connection there while plain `ssh` works fine; `rsync` is the more robust fallback (resumable, preserves modes) rather than improvising a manual pipe.

**Pulling any built artifact out of the container and installing it on a remote host
(not just feed packages)** follows the same shape: build the `-native` recipe that produces it, `mackas exec cat <path under /build/tmp/...> > localfile`, `rsync` it to the remote, then install it there. Installing as root on a remote host over ssh needs a human at the keyboard — the auto-mode classifier blocks `sudo` over ssh from this side, so hand that step to the user rather than attempting it.

## Debugging a failed build (logs/buildstats)

`tmp/log` and `tmp/buildstats` are inside the same invisible volume. Either read a specific file in place:

```sh
mackas exec cat /build/tmp/work/.../temp/log.do_<task>
```

or pull a bundle out for closer inspection / `buildstats analyze`:

```sh
mackas retrieve buildstats logs        # -> ~/oe/artifacts/{buildstats,logs}
mackas buildstats analyze              # summarize timing from what was fetched
```

Same one-VM rule as `deploy`: stop the build/shell first. Same DEPLOY_DIR-derivation and sibling-layer-reset caveats as `retrieve deploy` above.

That rule is also why none of this is a way to check on a task that is still running — every route to a task log goes through a second container. For live progress on a long task use `mackas monitor --once` instead (see "Watching one build live"); these commands are for after the build has let go of the volumes.

### Analyzing buildstats (task-level timing/resource data)

**Retrieve it after every build, not just when something looks slow or fails** — it's the data source for the timing/bottleneck half of a build report, the same way buildhistory is the source for the content-delta half. `mackas retrieve buildstats` (or `buildstats logs` together) pulls `tmp/buildstats/<BUILDNAME>/<recipe>/<task>` out to `~/oe/artifacts/buildstats/<retrieve-timestamp>/` — a fresh subdirectory per retrieval, so successive retrievals never merge into each other on the host side (they can still merge on the *guest* side; see below).

`mackas buildstats analyze [PATH]` (default `PATH` = `~/oe/artifacts/buildstats`) runs a stdlib-only Python script from the mackas checkout, no dependencies. It accepts a `BUILDNAME` dir directly or any ancestor containing one; with more than one retrieval nested under `PATH`, it picks the lexically greatest (= newest, since both the retrieve timestamp and `BUILDNAME` sort chronologically as digit strings).

```sh
mackas buildstats analyze                                          # human digest, from the last retrieve
mackas buildstats analyze ~/oe/artifacts/buildstats/<timestamp>    # a specific retrieval
```

Key fields and what they mean for a report:

- **`wall` vs `task CPU` vs `parallelism`** — wall is real elapsed time; task CPU sums both the task's own and its children's rusage (a compile's real work happens in `make`/`cc1`/`ld` child processes, so reading only the task's own rusage undercounts massively); `parallelism = CPU / wall`. Low parallelism despite free host cores means something is serializing, not that the VM lacks CPU.
- **`concurrency peak`/`mean`** — how many tasks actually ran at once. A peak far below the VM's core count during a long stretch is direct evidence of one big task (usually a kernel or browser/toolchain compile) blocking everything else in the dependency graph — name the specific recipe from `top recipes by cpu`, not just the aggregate number.
- **`by task type`** and **`top recipes by cpu`** — the two tables the report must quote (see the pre-send gate in "Report"): which *kind* of work dominated, and which *specific recipes* were the most expensive. **Relabel the tool's raw column headers** — `cpu_s`/`wall_s`/`n` are for reading the tool's own terminal output, not a report a person reads. Rendered: `| Task type | CPU time (s) | Wall time (s) | Tasks | Parallelism |` and `| Recipe | CPU time (s) | Wall time (s) | Tasks |`. Never paste a raw header row verbatim.
- **`peak task RSS`** — the single highest per-task memory figure seen. Relevant context for anything OOM-adjacent — a peak close to the container's `-m` limit is the smoking gun.

**`BUILDNAME` genuinely varies per machine and per build** (`conf/distro/angstrom.conf` sets it from `${DISTRO_VERSION}`/`${MACHINE}`/`${DATETIME}`), so distinct `BUILDNAME` dirs pile up side by side in `tmp/buildstats` across a session — because the buildstats-clearing step that would otherwise wipe `tmp/buildstats` before each build only fires on the `mackas build`/`mackas smoketest` code path, and this skill's own hand-typed `kas-container build ...` pattern (needed for the `--skip repos_checkout --skip repos_apply_patches` flags `mackas build` doesn't expose) bypasses it entirely. **Confirmed directly in this checkout**: after building `beaglebone` then `rb1-core-kit` with no intervening `mackas clean`, one `buildstats` retrieval contained both machines' `BUILDNAME` dirs side by side. This doesn't corrupt either build's own numbers, but it means "just analyze the newest retrieved dir" is not a safe shortcut once more than one machine has built in the same session — the newest by lexical sort is not necessarily the one you just built (e.g. `rb1-core-kit` sorts after `beaglebone` regardless of which ran more recently).

**When retrieving buildstats for a specific machine you just built, point the analyzer at that machine's own `BUILDNAME` dir explicitly:**

```sh
mackas retrieve buildstats --dest ~/oe/artifacts/buildstats-<machine>
BSDIR=$(ls -d ~/oe/artifacts/buildstats-<machine>/buildstats/*/Angstrom-*-<machine>-* 2>/dev/null | sort | tail -1)
if [ -n "$BSDIR" ] && [ -d "$BSDIR" ]; then
  echo "analyzing: $BSDIR"
  mackas buildstats analyze "$BSDIR"
else
  echo "NO BUILDSTATS DIR FOR <machine> -- wrong --dest or machine name"
fi
```

Echo `$BSDIR` before analyzing — the glob is silenced with `2>/dev/null`, so a wrong machine name or a `--dest` that doesn't match the retrieve both produce an empty `$BSDIR`, and `mackas buildstats analyze ""` then falls back to its own default `PATH` and happily reports a *different* machine's build.

## The `--skip` flag family

Everything in this section is about **hand-typed `kas-container build/shell` invocations** — `mackas exec` bakes the full four-step skip set in unconditionally, so for one-off commands there is no flag to remember (or to get wrong).

### `-k` is not a synonym, and not bitbake's `-k` either

**kas's own `-k`/`--keep-config-unchanged` is a different flag from the `--skip` pair, and is not what it sounds like.** It skips five kas steps at once, including `write_bbconfig` — so kas stops regenerating `local.conf`/`bblayers.conf`, meaning any fragment in the file list (including an auto-appended `macos-local.yml`) has no effect for that invocation. Use the explicit `--skip repos_checkout --skip repos_apply_patches` pair instead when the goal is protecting sibling-layer commits; it's surgical, `-k` is not. `env.sh`'s `kas-container` wrapper prints a one-line stderr warning naming exactly what gets skipped whenever it sees `-k` — read it rather than filtering it out.

**Separately: kas's `-k` has nothing to do with bitbake's own `-k`/`--continue` (keep building past a task failure).** To pass real `bitbake -k` semantics through `kas build`, use kas's `extra_bitbake_args` positional (`kas build ... --target a --target b -- -k`, `--` separator) — not kas's own `-k` flag, which means something else entirely and would silently skip config regeneration instead.

`-k`/`--keep-config-unchanged` expands to five skipped steps: `setup_dir`, `finish_setup_repos`, `repos_checkout`, `repos_apply_patches`, `write_bbconfig` (`kas/libkas.py:702-714` in kas 5.4). mackas's `exec`/`kas_shell_ro` only replicates the first four — which is why a hand-typed `kas-container ... -k` silently drops `kas/macos-local.yml`'s `BB_NUMBER_THREADS`/`BB_HASHSERVE_DB_DIR` tuning: the fifth skip (`write_bbconfig`) is the one that would have picked it up.

### When it's needed: sibling layers with local-only commits

**kas force-resets clean sibling repos to the configured branch head at build start** (`branch: Reset to <sha>`), same as on Linux — this is kas behavior, not a mackas one, and `angstrom-build` documents the full rule (it hits every `repos:` entry with a `url:`; the self-referencing `angstrom:` entry is never checked out, so local work in this layer itself is not at risk). Uncommitted modified *tracked* files protect a repo ("Repo is dirty - no checkout"), but untracked files do NOT count — a repo with only local commits on the configured branch gets silently reset and your commits vanish from the working tree (recoverable via reflog).

**This bites hardest right after you fix something.** A repo that was dirty a moment ago and protected the build is, the instant you `git commit` or `git am` that fix, clean-but-ahead — and clean-but-ahead is NOT protected, only uncommitted changes are. A freshly `git am`'d fix survives zero `kas-container` invocations before being silently reset back to the tracked branch tip, with no error, no warning beyond an easy-to-miss "Repository X checked out to `<old sha>`" log line. Pass the `--skip` pair proactively on every invocation while any sibling layer carries local-only commits — do not wait to notice the loss first.

When any sibling layer (`meta-beagleboard`, `meta-dominion`, `meta-angstrom` itself, ...) carries local-only commits, build with both skips:

```sh
cd ~/oe/work
kas-container build --skip repos_checkout --skip repos_apply_patches \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml --target <target>
```

`--skip repos_checkout` alone is not enough: kas then tries to re-apply `angstrom.yml`'s oe-core patches onto the already-patched tree and dies with "Could not apply patch". Skipping both steps leaves every repo exactly as-is (oe-core stays patched from the previous run). Verify after the build that each sibling repo's HEAD is still your commit stack — from the host, on their normal paths under `~/oe/work/<layer>`.

`kas-container for-all-repos -k` gives the same check without leaving the container, one line per repo:

```sh
kas-container for-all-repos -k \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml \
  'echo "$KAS_REPO_NAME $(git rev-parse --short HEAD) $(git rev-list --count @{u}..HEAD 2>/dev/null || echo -)"'
```

`-k` is required here — without it, `for-all-repos` runs kas's full setup macro first and **resets** repos before running the command, the opposite of an audit.

### Before the one deliberate un-skipped run: pin everything else first, don't just audit around it

**Knowing the risk and auditing for it after the fact is not the same as preventing it.** The audit pattern above (`for-all-repos -k` before/after, comparing ahead-counts) *detects* a collateral reset — it does not stop kas from doing it. If any sibling layer with local-only commits has no `commit:` pin in the kas config, an otherwise-necessary un-skipped run (to apply a brand-new `patches:` entry, see below) will still reset it, even though you knew going in that this exact thing happens.

**The actual prevention step, do this before the un-skipped run, not after discovering the damage:** for every repo that (a) has real local-only commits and (b) has no `commit:` pin yet, add one — pin it to its current HEAD (`git -C ~/oe/work/<repo> rev-parse HEAD`) in the relevant kas config. A pinned repo's checkout target *is* the pin, so an un-skipped `repos_checkout` cannot reset it away from local work — there is nothing to reset to that isn't already there. **This only works for a sha that already exists on the entry's fetched remote branch**: kas resolves the pin against what it fetched, so pinning a genuinely unpushed HEAD fails the checkout instead of protecting it. Push the commits first, or keep the `--skip` pair and skip the un-skipped run entirely. This is the same pattern `meta-sdr`/`meta-qt5`/`meta-sunxi`/`meta-meson` already use; repos that float on a bare `branch:` with no pin are the ones actually at risk, and are identifiable in advance (`grep -B2 -A1 "branch:" kas/angstrom.yml` and check which entries lack a sibling `commit:` line).

Do this *before* running the deliberate exception below, not as cleanup after. A safety-snapshot branch is a recovery net for the unexpected; a missing pin on a *known*-at-risk repo is not unexpected, and reaching for a recovery net instead of removing the hazard is treating a foreseeable, preventable outcome as a surprise.

### One un-skipped run can silently half-patch unrelated repos too

**kas's multi-repo checkout+patch-apply is not transactional.** If any single repo's `patches:` entry fails to apply — a genuine conflict, e.g. because that repo's own base just moved — kas aborts the *entire* run with a fatal error. But repos processed *earlier* in that same run, whose patches applied fine, are left exactly where that partial run stopped: some patches applied-and-committed, some applied-but-never-committed (the working tree shows real, correct content as an uncommitted modification), some not attempted at all. Nothing reports this partial state as broken — it just silently becomes the checkout's new reality, and only surfaces later as an unrelated-looking failure (a missing `LAYERSERIES_COMPAT` entry breaking bitbake's parse for every machine, a fix that "doesn't seem to have landed" for no visible reason) whenever something else next depends on that repo's full patch set.

If an un-skipped run is ever interrupted (crashes, gets stopped, hits a real conflict on one repo) — after resolving the immediate cause, **check every `patches:`-declared repo the run touched, not just the one that errored.** `git status --short` on each: a modified-but-uncommitted tracked file is that repo's own patch content, applied but not committed — verify it matches the patch (`git stash`, `git apply --check` the patch fresh against the pre-stash HEAD, diff the two) and commit it if so. A `patches:` entry with no trace of its effect at all was never attempted — apply and commit it directly (`git apply` + a `kas: <patch-entry-name>` commit, matching kas's own naming convention) rather than re-running the full un-skipped invocation again, which re-risks every floating-branch repo a second time for no benefit once you know which specific repos and patches are actually missing.

### When it backfires: blocking fresh patches

**Only pass the `--skip` pair when the *active* kas fragment composition actually includes a repo that needs it — do not carry it over reflexively from unrelated work.** `--skip repos_apply_patches` doesn't just protect already-patched repos from re-patching; it blocks kas from applying **any** `patches:` block for **every** repo in the composition, including a brand new entry just added to `angstrom.yml`. The failure mode: add a `patches:` entry for a repo with no local commits, build with the habitual `--skip` pair, and kas silently uses the *pristine, unpatched* checkout — the new patch is never applied, and bitbake fails with exactly the original pre-patch error, with no hint that the skip flags were the cause (it looks identical to "the patch didn't work").

kas only resets/patches repos that are actually declared in the *composed* config's `repos:` block — a fragment that doesn't pull in a given layer was never going to touch it regardless of `--skip`, so passing the pair buys no protection there and costs the new patch. Check which repos the *current* fragment composition actually declares before deciding whether `--skip` is needed at all. After adding a new `patches:` entry, do at least one build **without** any `--skip` flags (or grep the log for `Patch applied.*<new patch name>`) to confirm it actually applied before trusting the result. The same blind spot applies to `mackas exec`: it always skips `repos_apply_patches`, so an `exec` run can never apply a freshly added patch either.

**The same trap catches *existing* patches, not just newly added ones.** A long run of `--skip`-everywhere builds (needed while sibling layers carry local-only commits) leaves patched repos such as `openembedded-core` and `meta-ti` sitting on their plain configured branch instead of kas's generated `patched-<branch>` — so a patch the kas config relies on silently isn't active, and whatever it fixed resurfaces looking like a new, unrelated bug. Fastest fix when this happens: check whether kas has already generated a `patched-<branch>` locally (`git branch -a | grep patch`) and whether it's still current (`git merge-base --is-ancestor $(git merge-base patched-<branch> <branch>) <branch>`, expect 0 commits behind) — if so, `git checkout patched-<branch>` directly is far cheaper than a full non-skip rebuild, and is exactly what a real kas run would have produced anyway.

### `mackas retrieve` / `buildstats analyze` / `exec` do not reset sibling layers

`mackas retrieve` and `mackas buildstats analyze` both resolve variables (`DEPLOY_DIR`, `LOG_DIR`, `BUILDSTATS_BASE`, ...) through an internal `bitbake_getvar`, which unconditionally skips the four repo-mutating kas steps (`setup_dir`, `finish_setup_repos`, `repos_checkout`, `repos_apply_patches`) on every call — the same shared machinery `mackas exec` runs your command through. A read-only variable query never touches a repo checkout, regardless of whether `conf/local.conf`/`conf/bblayers.conf` already exist under `MACKAS_PROJECT_DIR`.

This lives in the mackas script itself, not `env.sh` — and `env.sh` puts the mackas checkout on PATH, so `mackas` commands are always current (only the sourced wrapper/derivation logic can go stale). If a `retrieve`/`buildstats analyze`/`exec` call ever does reset a sibling layer, treat it as a signal the mackas checkout is outdated or the fix regressed, and check `git log <upstream>..HEAD` on the affected layer immediately (`git reflog` has the lost commits as recoverable `cherry-pick`/`am` entries).

## Failure modes and what to do

### VZError: "The storage device attachment is invalid"

`Error Domain=VZErrorDomain Code=2 "The storage device attachment is invalid"` is mackas's own documented, *expected* symptom of the one-VM-per-volume rule being violated (see `volume_in_use()` in the mackas script). Stopping a build mid-run (e.g. killing its background task) can leave the Apple `container` VM instance still "running" and still holding the ext4 volumes, so the next `kas-container` invocation fails this way — the wrapping shell command exiting is no evidence the VM went with it, which is why the next build fails fast rather than queueing. mackas's guard for it only runs when the operation goes through a `mackas` command; a bare `kas-container`/`container` call bypasses that safety net entirely.

**Two distinct root causes produce the identical error message:**

1. **Transient `fstrim`/volume contention** — another `mackas`/`bats` process actively holding a volume. Clears on its own or once that process finishes; `ps aux | grep "container run"` shows it.
2. **A genuinely orphaned/stale `container` VM instance left running** from something unrelated (e.g. `mackas check`'s own "test container booted" verification step). This one does NOT clear on retry.

`container list` (read-only, safe to run directly — it's an inspection, not a mutation) distinguishes them: a real build's instance runs with the project's actual `-c`/`-m` profile from `env.sh`, so a lingering instance with a *different*, small profile (e.g. 4 CPU/1GB — kas-container's bare default) is the orphan, not a real build. Stopping it (`container stop <id>`) is a mutating command — raise it with the user rather than running it yourself. **When the VZError persists across 2-3 retries with no visible contention in `ps aux`, check `container list` before continuing to blindly retry.**

Do not reach for `container list`-then-`container stop` by hand as a routine fix — run `mackas status`/`mackas check` to diagnose, and treat a genuinely stuck volume as something to raise with the user rather than resolve unilaterally with raw `container` commands.

### Missing `~/oe/gitconfig` → mackas refuses cleanly, but the underlying cause is worth knowing

`env.sh` exports `GITCONFIG_FILE=~/oe/gitconfig` unconditionally once set (it only skips exporting when your shell already has `GITCONFIG_FILE` set — it never checks the file is *present*), and this is what stands in for git's own "dubious ownership" refusal: Apple `container`'s virtiofs mounts `/repo` appearing owned by `0:0`, which git otherwise refuses to touch at all. **Every real mackas-driven kas invocation (build/shell/exec) validates the gitconfig — readable and containing `safe.directory = *` — before running kas, and `die()`s with a clear message pointing at `mackas setup` if it isn't**, rather than letting a missing/broken gitconfig surface as a confusing downstream symptom. If you see that die message, `mackas setup <MACKAS_ROOT>` is the fix (it offers to repair a `GITCONFIG_FILE` you set yourself too, not just the one it generates).

This check is bypassed only by going around mackas entirely — a hand-typed `kas-container` call from a shell that never sourced `env.sh` gets no `GITCONFIG_FILE` at all and none of this protection. In that case the old confusing symptom still applies: git hits "dubious ownership" on `/repo`, kas's own root-detection (`git rev-parse --show-toplevel`) silently falls back to the wrong directory instead of erroring, and the actual failure looks like `file /build/../repo/kas/conf/layer.conf not found` or a patch `path:` in `angstrom.yml` resolving one directory off — nothing obviously about git config. Check `ls ~/oe/gitconfig` and `echo $GITCONFIG_FILE` if that happens; recreate the file by hand if missing:

```
[safe]
	directory = *
```

Do not "fix" this by running **bare** `mackas setup` (no arguments) — with no `~/.mackas.conf` present it resolves `MACKAS_ROOT` to the built-in default, a different disk entirely, and will offer to relocate the ext4 volumes there. `mackas setup <MACKAS_ROOT>` (the real root, as an explicit positional argument) is safe — same invocation as the `env.sh`-staleness refresh under Preflight, confirmed idempotent via `--dry-run`.

### `OSError: [Errno 117] Structure needs cleaning` — ext4 corruption after a crash

A host crash (macOS panic, power loss, forced VM teardown) mid-write to one of the ext4 volumes can leave real filesystem corruption behind — surfaces as `[Errno 117] Structure needs cleaning` from bitbake (or any other syscall touching the damaged region), not just from `mackas` itself. This is normal fsck-after-a-crash territory, not a mackas bug.

**`mackas check`/`mackas status` catch this proactively, for free, without running fsck at all.** Both read the ext4 superblock's `EXT2_ERROR_FS` bit straight off the plain `volume.img` file (`tools/mackas-ext4-dirty-bit`, stdlib-only `/usr/bin/python3`) — no mount, no loop device, no container, no daemon required, so it's the one part of `check`/`status` that still answers correctly even with the container system down (exactly the reboot-after-a-crash state). A dirty volume surfaces as a plain `[FAIL]`/inline annotation naming the fix (`mackas volume fsck <name>`) right next to the volume it's about — run `mackas status` after any crash before reaching for `volume fsck` reactively; it names which of the three volumes actually needs it, instead of having to check all three blind. Needs `python3` (macOS ships one at `/usr/bin/python3`) — detected, never required; silently skipped if unavailable.

`mackas volume fsck <name>` (or `all`/`--all`) checks and offers to repair it, safely: it clones the volume image first (APFS copy-on-write, near-free until e2fsck actually writes), then repairs the **clone**, and only promotes the repaired clone over the real volume once an independent second forced check-pass comes back completely clean. The original volume is never written to until that promotion, and the pre-repair image is kept alongside it afterward. Two ways it can run `e2fsck` against that clone: **a host-installed `e2fsck`, if one is available** (`brew install e2fsprogs`) — operates directly on `volume.img` as a plain file, no mount/loop-device/container/network needed, live-verified byte-identical repair output to the container path, just much faster; or, if no working host `e2fsck` is found, **the original container mechanism** — loop-attaching the clone inside a throwaway container (needed for a genuinely unmounted filesystem, since a named-volume mount can never give `e2fsck` that) and installing `e2fsprogs` into it on demand (needs network; set `MACKAS_FSCK_IMAGE` to a prebuilt image that already has it to skip that step, e.g. offline). `mackas` picks whichever is available automatically — its own plan output says which path will run. `brew install python3 e2fsprogs` in one step covers both this and the dirty-bit check above.

Don't assume a crash always leaves real corruption — a plain forced check (`-n`, no repair) can legitimately come back clean if the crash only left a dirty journal that a subsequent normal mount already replayed (which happens automatically the next time anything attaches the volume, including mackas's own auto-start-the-daemon path above). `mackas status`'s dirty-bit check (above) is the cheap first move after any crash — it's free and catches the real `EXT2_ERROR_FS` case across all three volumes in one shot with no daemon/e2fsprogs needed. If it reports clean but something still feels off (or `python3`/the tool isn't available so the check was silently skipped), fall back to running `mackas volume fsck <name>` directly — a clean result costs little and confirms there's nothing to worry about, versus assuming and being wrong.

`--check-only` skips the repair/promotion step if you just want to know the verdict without mackas offering to act on it. For the specific case of the throwaway `oe-build-tmp` (TMPDIR) volume, mackas's own output reminds you that `mackas clean` (full recreate) is usually cheaper than repairing it if the corruption turns out to be real and you don't care about losing buildhistory/`conf/` in the process — `fsck` is the right call specifically when preserving the existing sstate-adjacent build state (or buildhistory) matters more than the extra time repair costs.

**Check (or fsck) all three volumes after a crash, not just whichever one first surfaced an error.** One crash can leave `oe-build-tmp` checking completely clean while `oe-build-sstate` carries real corruption that only surfaces much later — past parsing, well into task execution — as the identical `[Errno 117]` from a different task. `mackas status`'s dirty-bit check covers all three volumes for free in one shot, which is why it is the right first move rather than reactive per-task discovery; `mackas volume fsck all` is the tool when a deeper pass is wanted. Include `oe-build-dl` too, even though it is less likely to be mid-write during a typical crash (fetches are brief relative to `tmp`/`sstate` build activity).

### Sibling-repo branch drift

**Verify each sibling layer's `branch:` in `angstrom.yml` actually matches where its real work lives before treating it as the reset target.** A layer can be checked out locally on a different branch than what `angstrom.yml` pins (e.g. checked out on a fork's `wrynose` branch while the kas config still says `branch: master`) — in that case kas silently re-checks-out to the *configured* branch the moment the repo is clean, discarding the real work from the working tree with no error. If a build behaves as though branch-specific fixes are simply missing, check `git -C ~/oe/work/<layer> branch -r` for a remote branch the current `angstrom.yml` pin does not reference, not just the layer's own working-tree state.

## What NOT to do

The hard rules, collected from where each is argued in full — the pointer is where the *why* lives.

- **Never hand-pass `--runtime-args` to a wrapped `kas-container` call** — at the pinned kas version the flag overwrites rather than accumulates, silently dropping the three ext4 volume mounts (see "Preflight").
- **Never `nohup kas-container`** — it bypasses the sourced wrapper, losing the volumes and landing `KAS_BUILD_DIR` on virtiofs (the Errno 95 `sock.bind()` failure); background with `&` in a sourced shell instead (see "Preflight").
- **Never watch a build by tailing or grepping its log** — a log line cannot show progress inside a running task, and a killed host-side shell can silently stop writing to that log while the build itself keeps running in the container; `mackas monitor` is the only view that survives that (see "2. Build").
- **Never run bare `mackas monitor` from an agent** — it streams a line every few seconds, forcing one reply per tick out of any per-line-notification harness tool; use the `--once` poll loop (see "Building").
- **Never report a build or an upload in prose where a table is required** — a sentence carrying the same numbers does not substitute; walk the pre-send gate against the draft reply and check for the literal header rows (see "Report").
- **Never run Apple's `container` CLI directly for anything mutating** — it bypasses mackas's `volume_in_use` guard; `container list` (read-only) is the one exception (see "Getting files off the volumes" and "VZError" under Failure modes).
- **Never run `kas-container purge`** — it deletes every kas-managed repo, including sibling layers carrying local-only unpushed commits (see "Cleaning").
- **Never treat kas's `-k` as the `--skip` pair, or as bitbake's `-k`** — it skips `write_bbconfig` too, silently dropping fragments; use the explicit `--skip` pair instead (see "The `--skip` flag family").
- **Never run bare `mackas setup` with no root argument** — with no `~/.mackas.conf` it resolves to a default root and can offer to relocate the volumes there; always pass `<MACKAS_ROOT>` explicitly (see "env.sh staleness").

## Notes / gotchas

- `local_conf_header` in `angstrom.yml` already enables `buildhistory`, `buildstats`, and `rm_work` — source/work dirs are wiped after each recipe; inspect results via buildhistory and deploy, not `tmp/work` (which is inside the invisible volume regardless).
- kas prints "Repo <x> is dirty - no checkout" for locally-modified sibling layers; that is expected whenever several sibling repos have uncommitted work, and does not fail the build.
- Only **one VM** may hold an ext4 volume at a time: `mackas retrieve`/`clean`/`volume` operations all refuse while a build or `mackas shell` still has it attached. Stop the build first.
- `DL_DIR`/`SSTATE_DIR` are shared across every machine's build under the same two volumes — never point them at anything layer-specific.
- Machine name normalization: buildhistory dir names use underscores (`qcs6490_thundercomm_rubikpi3`) while deploy/images use hyphens (`qcs6490-thundercomm-rubikpi3`).

## References

- `skills/mackas/SKILL.md` in the mackas repo — the project-agnostic mackas skill this one deliberately overlaps with (see the intro).
- `angstrom-build` skill — the same build/analysis flow on a native Linux kas-container host.
- `boot-validate` skill — proving a built image actually boots and behaves (see "Report").
- `bitbake-task-debug` skill — instrumenting a failing task's own execution, beyond reading its log.
