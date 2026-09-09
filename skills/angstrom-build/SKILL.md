---
name: angstrom-build
description: Build an Angstrom OpenEmbedded target with kas-container and analyze what changed in buildhistory and deploy. This is the default build workflow for the meta-angstrom layer. Use when asked to build a recipe/image/package for a machine (beaglebone, qemuarm64, riscv, etc.), or to inspect what a build produced or changed.
---

# Angstrom build + buildhistory/deploy analysis

Build a target with `kas-container` and report exactly what the build produced and what changed, using the buildhistory git repo and the deploy directory. **This is the normal workflow for this layer.**

It assumes a host where `kas-container` runs natively and the build output is an ordinary host directory — a Linux box with podman or docker. On Koen's macOS setup the build optionally goes through mackas, a local wrapper that keeps the output inside ext4 volumes the host cannot see; the `mackas-angstrom` skill covers that host. The bitbake/buildhistory concepts are identical in both, only how you reach the files differs.

## Reliability contract

Every verdict this skill actually relies on in *this* checkout is one specific greppable line — full interpretation lives in "Failure modes and what to do" below. This list is scoped to what's verified real here; it omits sentinels described elsewhere that depend on scripts this checkout doesn't have (e.g. a hardened `sort-packages.py` sentinel) — don't add a line without first checking the thing that emits it actually exists.

- `Tasks Summary: Attempted N tasks of which M didn't need to be rerun and all succeeded.` — build success; `M/N` is the sstate reuse ratio. The `... and K failed.` form is failure. **No `Tasks Summary` line at all** means the run died before the task executor started, or is still running — never success.
- The build's own exit code (`wait "$BPID"; rc=$?`) must *agree* with the `Tasks Summary` line; disagreement means a truncated log and neither signal is trustworthy alone.
- The inline `REPO-DIFF`/`REPOS-AUDIT` failure patterns this skill's own snippets use (steps 1 and "Building with local-only commits") — patterns to type when following those sections, not a pre-existing script's output.
- `upload-packages.py`'s own exit codes: `0` success (including a no-op), non-zero on a local or remote failure (see "Publishing to the package feed").

## Layout

Everything below is relative to the **build root** — the directory this repo was cloned *into*, i.e. the parent of the `meta-angstrom` checkout. kas mounts it at `/repo` inside the container, resolves every config path against it, and clones the sibling layers (`openembedded-core`, `bitbake`, `meta-openembedded`, `meta-ti`, ...) alongside. Run every command in this skill from there. `$BR` below stands for that path; substitute your own.

- Layer repo (this repo): `$BR/meta-angstrom`
- Build output (`TMPDIR`, shared `DL_DIR`/`SSTATE_DIR`): `$BR/build` (kas's default `KAS_BUILD_DIR`, `TOPDIR`)
- Buildhistory (a **git repo**, auto-commits each build): `$BR/build/buildhistory`
- Deploy artifacts: `$BR/build/deploy` (`ipk/`, `images/<machine>/`, `licenses/`, `spdx/`) — **not** `build/tmp/deploy`, the oe-core-textbook default. `conf/distro/angstrom.conf` sets `DEPLOY_DIR = "${TOPDIR}/deploy"`, a sibling of `tmp/` rather than nested inside it. A distro conf can change this again, so resolve it with `bitbake-getvar DEPLOY_DIR` rather than trusting this note.

`kas-container` itself is whatever the host has installed (a venv, `pipx install kas`, a distro package); the invocations below assume it is on `$PATH`. It needs a working podman or docker.

## Kas fragment composition

Always compose a base config with a machine fragment, colon-separated:

- Base: `meta-angstrom/kas/angstrom.yml` (distro, repos, local_conf_header)
- Machine: one `meta-angstrom/kas/<machine>.yml` — e.g. `beaglebone.yml`, `qemuarm64.yml`, `qemux86-64.yml`, `riscv.yml` (beaglev-ahead, adds meta-riscv), `rb1-core-kit.yml`. **`ls kas/` in this repo for the authoritative list**; fragments are added and renamed regularly, so do not work from a list memorized anywhere, including this one.

## Procedure

### 1. Record pre-build state (do this BEFORE building)

The buildhistory repo auto-commits after every build, so capture the current HEAD to diff against later:

```sh
cd "$BR/build/buildhistory"
PRE=$(git rev-parse HEAD); echo "pre-build HEAD: $PRE"
```

**Whenever the build will actually update sibling repos** — `--update` passed (see step 2 — `--skip repos_checkout` alone is NOT enough, see the note there), or a manual `git pull` across siblings before building — also snapshot every repo's current HEAD, so step 3 below can report exactly what moved:

```sh
cd "$BR"
> "$SCRATCH/pre-build-shas.txt"
for d in */; do
  d="${d%/}"; [ -d "$d/.git" ] || continue
  echo "$d $(git -C "$d" rev-parse HEAD)" >> "$SCRATCH/pre-build-shas.txt"
done
[ -s "$SCRATCH/pre-build-shas.txt" ] && grep -q '^meta-angstrom' "$SCRATCH/pre-build-shas.txt" \
  || { echo "REPO-SNAPSHOT: FAILURE -- empty/incomplete snapshot, wrong cwd (\$BR)"; exit 1; }
```

**Check the snapshot actually landed before trusting it.** A wrong `$BR` makes the glob match nothing, leaving a 0-byte file with exit 0 — and step 3's "nothing moved" table would then read as if genuinely nothing changed, a different claim than "the snapshot itself was empty." `meta-angstrom` is the sanity anchor: it is always present at `$BR`, so its absence from the snapshot means the snapshot is wrong, not that every repo failed to move.

Skip this when the build is `--skip repos_checkout`'d or every repo is `commit:`-pinned — nothing will move, so there is nothing to snapshot.

### 2. Build

Run from the build root. `<machine>` picks the fragment; `<target>` is a recipe (`nginx`), image (`console-base-image`), or `-c <task>` via a shell (see below).

**`--update` is a separate flag from `--skip`, and it's the one that actually matters for "make sure repos are current."** Confirmed against kas 5.4's own source (`kas/repos.py`'s `fetch_async`): for a branch-tracked repo (no `commit:` pin), plain `kas-container build` with **no** `--skip` flags still does *not* re-fetch from upstream if a local clone already satisfies the configured branch name — it only fetches on a genuinely fresh clone, or when `--update` is passed (`kas/libkas.py:656`: *"Pull new upstream changes to the desired branch even if it is already checked out locally"*). Without `--skip`, kas *will* still reset/checkout to whatever it already has locally — safe, but not necessarily current.

**Whenever `--update` is used, run a holistic patch test FIRST — before launching the real (possibly hours-long) build.** A repo drifting forward is exactly when one of this layer's own kas-managed patches is most likely to stop applying (upstream renamed/rebased/already-fixed the same thing a local patch works around — three separate real cases hit in one session: 2026-08-18, `meta-qcom`/`meta-openembedded`/`meta-ti` all had patches invalidated by real upstream drift). Discovering this via a failed multi-hour build, one patch at a time, is expensive and easy to misdiagnose as something else. `kas-container checkout` runs the exact same fetch + patch-apply code path as `build`, just stops before bitbake — use it as the pre-flight:

```sh
cd "$BR"
kas-container checkout --update \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml
```

Exit 0 with no `Could not apply patch` lines means every patch in the composition applies cleanly against the now-current repos. For each failure: check whether the target file/content already matches what the patch would produce (upstream already carries the equivalent fix — drop the now-redundant patch entry entirely) before assuming it needs a rebase; only rebase (regenerate the patch against the new source) when the underlying issue still genuinely exists. Only proceed to the real build once this exits clean. Since checkout already ran (and, for a multi-machine batch, is shared across every machine), the actual build(s) can then use `--skip repos_checkout --skip repos_apply_patches` — no need to repeat the fetch:

```sh
kas-container build --skip repos_checkout --skip repos_apply_patches \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml \
  --target <target>
```

Add `--skip repos_checkout --skip repos_apply_patches` whenever any sibling layer carries local-only commits — routine in this project (and generally incompatible with `--update` on the same repo, since forcing a fetch on a repo you're also protecting from checkout defeats the purpose — use one or the other per repo's actual state). See "Building with local-only commits in sibling layers" below for why, and for when the pair backfires.

Run it in the background and tee to a log — even a fully-cached recipe streams thousands of lines; a from-scratch image build can take hours:

```sh
... > "$SCRATCH/<target>-build.log" 2>&1   # run_in_background: true
```

Watch for the tail of the log:
- `Tasks Summary: Attempted N tasks ... all succeeded.` → success
- `sstate reuse` percentages show how much was cached vs. compiled from scratch
- `ERROR:` lines (grep them) → failure; the failing task's `log.do_<task>` path is printed

For a one-off task without a full build (e.g. just fetch, or cleansstate):

```sh
kas-container shell --skip repos_checkout \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml \
  -c "bitbake -c <task> <target>"
```

### 3. Report which repos changed (only when repos were actually updated)

**Whenever repos got updated at the start of the build — via kas's normal checkout or a manual `git pull` — show a summary table of what changed per repo, every time, not just on request.** Compare each repo's new HEAD against the snapshot from step 1 (host git, robust regardless of whether the repo tracks a branch, is `commit:`-pinned, or carries kas-applied patches on top — no need to parse reflog or distinguish repo types):

```sh
cd "$BR"
while read -r repo old; do
  new=$(git -C "$repo" rev-parse HEAD)
  [ "$old" = "$new" ] && continue
  n=$(git -C "$repo" rev-list --count "$old..$new" 2>/dev/null)
  echo "$repo|$old|$new|$n"
  git -C "$repo" log --oneline "$old..$new"
done < "$SCRATCH/pre-build-shas.txt"
```

Render as a table: repo, commit range, commit count, and a short **written summary** of what actually changed — read the full `git log --oneline` output for that repo and synthesize it (e.g. "podman/cloud-init/yq/upx/lopper version bumps, CVE status updates, vrunner test fixes"), don't paste raw commit subject lines as the summary. Repos where `old == new` don't need a row — if *nothing* moved, say that in one line instead of showing an empty table. A `commit:`-pinned repo never moves regardless of `--skip`; that's expected, not worth flagging as surprising.

### 4. Analyze buildhistory (what changed in package/image content)

```sh
cd "$BR/build/buildhistory"
git log --oneline $PRE..HEAD                 # new build commit(s); "No changes:" prefix = identical output
git diff --stat $PRE..HEAD                    # every file that changed this build
```

Per-recipe metadata lives under `packages/<TUNE_PKGARCH>/<recipe>/`. The arch string varies by machine — e.g. beaglebone is `armv7at2hf-neon-angstrom-linux-gnueabi`, qemuarm64 is `armv8a-angstrom-linux`. Don't hard-code it; discover it (below). Find it, then read the key files:

```sh
A=$(git show --name-only --oneline HEAD | grep -oE 'packages/[^/]+/<recipe>' | head -1)
git show HEAD:$A/latest                        # PV/PR, DEPENDS, PACKAGES, LICENSE, CONFIG, SRC_URI
git show HEAD:$A/<recipe>/latest               # per-package: RDEPENDS, PKGSIZE, FILES, FILELIST
git show HEAD:$A/<recipe>/files-in-package.txt # exact installed files + perms + sizes
```

Report: version (PV-PR), which sub-packages were produced, runtime deps (RDEPENDS), installed size (PKGSIZE), and any notable files (services, configs, binaries). Empty sub-packages (e.g. `-doc`, `-locale`, `-staticdev`) have a zero-line `files-in-package.txt` and produce no ipk — call those out as empty, not missing.

For images, look under `images/<machine>/glibc/<image>/` for `image-info.txt`, `installed-package-names.txt`, and `files-in-image.txt`; `git diff` shows added/removed packages between builds.

### 5. Analyze deploy (the shippable artifacts)

```sh
cd "$BR/build/deploy"
# ipks written this build (adjust the timestamp to just before the build started):
find ipk -name '<recipe>*.ipk' -newermt '<HH:MM before build>' -printf '%TT  %10s  %p\n' | sort
```

- Recipe builds land ipks in `deploy/ipk/<pkgarch>/` (e.g. `armv7at2hf-neon/` for beaglebone, `armv8a/` for qemuarm64; plus `all/` for arch-independent and `<machine>/` for machine-specific). Note the ipk dir uses the short pkgarch (`armv8a`) while buildhistory uses the full triplet (`armv8a-angstrom-linux`).
- Image builds land in `deploy/images/<machine>/`: `Angstrom-<image>-<version>-<machine>.{wic.xz,tar.gz,manifest,spdx.json}` plus `.rootfs.*` symlinks, `.wic.bmap`, and `testdata.json`. The `.manifest` is the full installed package list; `git diff` of the buildhistory image dir is the cleaner way to see package deltas.

### 6. Report

**Every build gets this summary at the end, unprompted — not just when a "detailed report" is asked for, and not skipped for a throwaway iteration/attempt inside a debug loop either.** Task counts and pass/fail alone are NOT the report — they don't say whether the build was cheap (cache-restored) or expensive (recompiled). This is Koen's own standing convention (confirmed directly 2026-09-07); same required fields as the `mackas-angstrom` skill's Report section, host-side commands instead of `mackas retrieve`/`mackas monitor`:

1. **Repo changes** — ONLY when repos actually moved (a manual `git pull`, or a deliberate un-skipped `repos_checkout`). A normal `--skip`'d build touches no repo content: no table, nothing to report. When they did move: `Repo | Old | New | Commits | Summary`, every commit subject in the range joined by `<br>` in one cell.
2. **Duration** — wall-clock, not "it finished." GNU `date -d` is available on a normal Linux host; first vs. last timestamped log line. Convert to Europe/Amsterdam before reporting if the log is in UTC.
3. **Build result + sstate reuse %** — compute from the `Tasks Summary` line as didn't-need-rerun / attempted (`awk 'BEGIN{printf "%.1f%%", (c/a)*100}'`), not eyeballed. Quote per-task-type `X% sstate reuse (...)` lines from the "Build completion summary" block when the headline hides something.
4. **Buildstats + buildhistory** — resolve paths rather than assuming them (same non-default-`DEPLOY_DIR` caution as everywhere in this layer):

   ```sh
   BS=$(bitbake-getvar --value -q BUILDSTATS_BASE)   # run inside the kas env, e.g. via a shell -c
   ls "$BS" | tail -1                                 # most recent build's stats dir
   ```

   buildstats gives real per-task wall-clock/CPU (report the slowest task); buildhistory (`cd "$BR/build/buildhistory"`, step 4 above) gives version/size/dependency deltas vs. the previous build — catches a version going backwards before it surfaces as a `do_packagedata` QA failure.
5. **Deploy** — which artifacts were written, with sizes and paths.

Flag anything surprising (unexpected new RDEPENDS, size jumps, QA warnings, empty packages, a version going backwards).

**Multi-machine sweeps** render as a markdown table in the chat reply (not left in a log): `Machine | Result | Duration | sstate reuse | Notes`. **Size/RAM**: one before-all/after-all pair per *batch*, never per machine. **Package uploads**: one row per base package, subpackages collapsed, archs comma-joined — see `mackas-angstrom`'s Report section for the exact table shape.

**The specific failure mode this exists to prevent**: duration/sstate-extraction gets skipped at *script-writing* time. Put it in at the same moment as the pass/fail check when writing a build-sweep script, not bolted on afterwards — a sweep script that greps only `Tasks Summary` is the recorded mistake.

A build report says what was *assembled*. To prove an image actually boots and behaves, see the `boot-validate` skill.

## Building with local-only commits in sibling layers

**Two distinct footguns live under this one heading — don't conflate them.** One is driven by whether any sibling carries local-only commits; the other is driven purely by whether the active `patches:` set changed since a sibling's tree was last patched, and has nothing to do with local commits at all. On `master` as of 2026-08-16, every sibling layer audits clean (zero local-only commits anywhere, via `for-all-repos -k` below) — all fixes are captured as kas-managed `patches:` entries in this layer's own git history instead, so the first footgun is currently dormant. The second is not, and isn't reduced by that at all: it fired for real this same session (`kas-container build --skip repos_checkout` alone, without also skipping patches, after adding a new `patches:` entry — exactly the case line below warns about) and aborted the build before a single task ran. Before reaching for either skip flag, ask which condition actually applies right now, not just whether siblings are clean.

**kas force-resets clean sibling repos to the configured branch head at build start** (`branch: Reset to <sha>`). Uncommitted modified *tracked* files protect a repo ("Repo is dirty - no checkout"), but untracked files do NOT count — a repo with only local commits on the configured branch gets silently reset and your commits vanish from the working tree.

This applies to every `repos:` entry that has a `url:` — i.e. every sibling layer kas clones. The self-referencing entry for this layer (`angstrom:` in `angstrom.yml`, no `url:`) is the checkout kas was invoked from and is never checked out or reset, which is why local work in `meta-angstrom` itself survives what the same work in `meta-dominion` would not.

Recovery, when it happens anyway: the commits are still in the reflog of the reset repo. `git -C <layer> reflog` shows the pre-reset head; `git reset --hard <sha>` puts it back. Record the sha *before* any un-skipped run rather than relying on finding it afterwards.

When any sibling layer carries local-only commits, build with both skips:

```sh
kas-container build --skip repos_checkout --skip repos_apply_patches \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml --target <target>
```

`--skip repos_checkout` alone is not enough: kas then tries to re-apply `angstrom.yml`'s oe-core patches onto the already-patched tree and dies with "Could not apply patch". Skipping both steps leaves every repo exactly as-is (oe-core stays patched from the previous run). Verify after the build that the sibling repo's HEAD is still your commit stack.

**Pass the pair only while the active composition actually needs it.** `--skip repos_apply_patches` suppresses *every* `patches:` block in the composition, including one just added: kas then builds from the pristine checkout, bitbake fails with exactly the pre-patch error, and nothing points at the skip flags as the cause. After adding a `patches:` entry, do one deliberate run without the skips (or grep the log for `Patch applied`) to confirm it landed — and pin every repo carrying unpushed work first, since that run resets anything unpinned.

**A `commit:` pin is not a substitute for the skips while work is unpushed.** kas resolves a pin against what it fetched from the entry's remote, so the sha must already exist on that branch upstream — a pin at a local-only HEAD fails the checkout outright instead of protecting it. Pinning is the right move for a repo whose commits *are* pushed (it makes an un-skipped run safe); for genuinely local work, push first or keep using the `--skip` pair.

`kas-container for-all-repos -k` gives the same check without a shell, one line per repo:

```sh
kas-container for-all-repos -k \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml \
  'echo "$KAS_REPO_NAME $(git rev-parse --short HEAD) $(git rev-list --count @{u}..HEAD 2>/dev/null || echo -)"'
```

`-k` is required here — without it, `for-all-repos` runs kas's full setup macro first and **resets** repos before running the command, the opposite of an audit.

**Never run `kas-container purge`.** It deletes the build dir, sstate, downloads, AND every repo kas manages — including sibling layers that may carry local-only, unpushed commits. Check current state with `git -C <layer> log --oneline @{u}..` before assuming any layer is safe; this project has repeatedly carried unpushed work in `meta-dominion` / `meta-qcom-3rdparty` / `meta-kodi`. `kas clean` is narrower (only removes `tmp*`) and safe by comparison, but still leaves this distro's non-default `DEPLOY_DIR` behind — prefer targeted cleanup of `TMPDIR`/`DEPLOY_DIR` over either.

`-k`/`--keep-config-unchanged` expands to five skipped steps: `setup_dir`, `finish_setup_repos`, `repos_checkout`, `repos_apply_patches`, `write_bbconfig` (`kas/libkas.py:702-714` in kas 5.4) — different from, and not a substitute for, the explicit `--skip repos_checkout --skip repos_apply_patches` pair used above: `-k` also skips `write_bbconfig`, so kas stops regenerating `local.conf`/`bblayers.conf` and any fragment in the file list has no effect for that invocation.

At the pinned kas 5.4, `--runtime-args` OVERWRITES rather than accumulates (`kas-container` ~line 347: `KAS_EXTRA_RUNTIME_ARGS=" $2"` — last flag wins), so passing it more than once on the same command line silently drops all but the last occurrence. (Unreleased kas master changes this to accumulate — irrelevant until the pin moves past 5.4.)

## Layer and recipe metadata traps

Each of these fails far from what caused it, and none of them says so.

- **`LAYERSERIES_COMPAT` is the usual first failure when adding a layer or moving a pin onto a newer oe-core.** Every layer's `conf/layer.conf` must name the current series in `LAYERSERIES_COMPAT_<collection>`, or bitbake refuses the whole configuration — including layers pulled in only as a dependency. Check `conf/layer.conf` in every layer touched or added; where upstream has not caught up, the one-line fix belongs in `patches/` and gets wired in via `angstrom.yml` (`patches/meta-meson/0002-layer.conf-add-blacksail-to-LAYERSERIES_COMPAT.patch` is the worked example).
- **`LICENSE` is parsed as a strict SPDX expression, at parse time.** `check_license_format()` runs from base.bbclass's anonymous python, so a bad value kills the build before any task runs. Informal shorthand is a hard error: `&`/`|` for `AND`/`OR`, `GPLv2+`-style names, any identifier not in the SPDX list. Non-standard licenses need a `LicenseRef-<name>` identifier.
- **A `LicenseRef-` with nothing behind it is a *separate*, equally fatal error** — "for which no generic license was found" (nothing under `COMMON_LICENSE_DIR`/`LICENSE_PATH`) or "not defined in `NO_GENERIC_LICENSE`" (a file exists, no mapping). Both report as `license-format`, which oe-core ships in the default `ERROR_QA` (via `CHECKLAYER_REQUIRED_TESTS`). **`INSANE_SKIP` does not silence it** — that only affects package-level `do_package_qa` checks, so adding it looks reasonable and changes nothing. When a license genuinely isn't worth modelling fully, the tool is `ERROR_QA:remove = "license-format"` in that single recipe.
- **A `_%` bbappend only matches a recipe whose filename has a version.** `foo_%.bbappend` matches `foo_1.0.bb` and `foo_git.bb`; a recipe with no version component at all (e.g. meta-linux-mainline's `linux-mainline.bb`) needs a bare `foo.bbappend`. The wildcard silently never matches otherwise — no warning, just an append that never applies. This layer has both forms in `recipes-kernel/linux/` for exactly that reason.
- **`WKS_SEARCH_PATH:append` in a `layer.conf` breaks wic for every other layer.** bitbake treats a variable that has had *any* operator applied to it — `:append` included — as already set, which defeats `image_types_wic.bbclass`'s own weak `WKS_SEARCH_PATH ?= ...` default, so the class-provided search paths disappear globally. Seed the class default explicitly first, then append the layer's own dir (`patches/meta-meson/0005-layer.conf-add-wic-to-WKS_SEARCH_PATH.patch`).

## Publishing to the package feed

`meta-angstrom/scripts/publish-feed.sh` wraps `upload-packages.py` (client, hashes `DEPLOY_DIR_IPK`, rsyncs new files into a staged `incoming/<upload-id>/` on the feed server) and `sort-packages.py` (server-side ingest/sort/re-index). The remote target defaults to `koen@beast:/data/www/angstrom/feeds/v2026.06/ipk/glibc`, overridable with `PUBLISH_FEED_REMOTE`/`PUBLISH_FEED_REMOTE_DIR`. On a Linux kas host (no mackas, so no `~/oe/artifacts/deploy` retrieve step) run `upload-packages.py` directly against the real `DEPLOY_DIR_IPK`:

```sh
python3 meta-angstrom/scripts/upload-packages.py \
  --deploy-dir-ipk "$BR/build/deploy/ipk" \
  --remote koen@beast --remote-dir /data/www/angstrom/feeds/v2026.06/ipk/glibc
```

Add `--dry-run` to preview; the real run also uploads by default, unlike `publish-feed.sh` which stays dry-run unless you pass `--publish`. **Uploading does not sort** — sorting on the server (ingest, verify, move into feed dirs, re-index) is a separate step (`sort-packages.py` run on the feed server, or `publish-feed.sh --sort` from a mackas host); default to upload-only unless a sort is actually wanted, since a completed upload sits safely in `incoming/` until something ingests it.

After a real upload it logs a grouped summary of what was actually sent, filtering out side packages (`locale`/`kernel` in the name, `-dbg`/`-dev`/`-doc`/`-src`/`-staticdev` suffix, or a `lib` prefix) and grouping the rest by `(name, version)` with the architectures built, e.g. `domoticz 2026.02-r0 (armv7at2hf-neon, armv8a)`. **Render this as a table in the chat reply**, same as any multi-machine build report — don't leave it as raw log lines the user has to parse.

`sort-packages.py --drop NAME` removes every `files-sorted` entry for that package name (all versions/architectures) so the next upload of it is treated as new and overwrites the stale ipk on next sort. It also takes an advisory lock for the whole run (including `--dry-run`) so two concurrent invocations against the same feed dir fail fast instead of racing — expect an occasional "already running" error if you and the user touch the feed server at the same time; just retry. The `mackas-angstrom` skill's "Publishing to the package feed" section has the full writeup; the scripts and remote layout are identical on both hosts, only how `DEPLOY_DIR_IPK` gets reached differs (no `mackas retrieve` needed here).

## Failure modes and what to do

Every sentinel from the "Reliability contract" section, what it means, and what to do — and NOT do — about it:

- **No `Tasks Summary` line, no exit code yet** — still running. Wait; a log quiet for an hour is a long `do_compile`, not a hang. Never declare success or failure.
- **No `Tasks Summary` line, non-zero rc** — died before the task executor ever started: a parse error, a bad kas fragment, a missing layer. Read the `Summary: There were N ERROR messages` block and the `^ERROR:` lines; fix the config. Don't re-run blind hoping it was transient.
- **`... and K failed.`** — read each failed task's `log.do_<task>` (paths printed under `Summary: K task(s) failed:`); the log is the diagnosis, the one-line summary is not.
- **rc and `Tasks Summary` disagree** — truncated log; neither signal is trustworthy on its own. Re-derive both before concluding anything; don't pick whichever one says success.
- **`REPO-SNAPSHOT: FAILURE`** — the pre-update snapshot was empty or missing `meta-angstrom`; fix `$BR`/cwd and re-snapshot *before* the update, not after.
- **A repo moved during a `--skip`-ped build** — the build itself is untrustworthy: something reset a sibling layer it should not have touched. Recover local-only commits via that repo's `git reflog` before doing anything else; don't re-run the build first.
- **Empty `$PRE` in buildhistory analysis** — first-ever build, or the recipe didn't change this build. Use the non-range forms; never fall back to a stale value or conclude from the resulting `git show` path errors.
- **`upload-packages.py` exit 1** — local precondition failure (no `rsync`/`ssh`, unreadable deploy dir or arch map); nothing reached the server. Fix locally.
- **`upload-packages.py` exit 2** — remote/transport failure; the staging dir is deliberately left in place and a re-run resumes it. Do not clean it up.

## What NOT to do

- **Never run `kas-container purge`.** It deletes the build dir, sstate, downloads, AND every repo kas manages — including sibling layers that may carry local-only, unpushed commits. Check current state with `git -C <layer> log --oneline @{u}..` before assuming any layer is safe; this project has repeatedly carried unpushed work in `meta-dominion` / `meta-qcom-3rdparty` / `meta-kodi`. `kas clean` is narrower (only removes `tmp*`) and safe by comparison, but still leaves this distro's non-default `DEPLOY_DIR` behind — prefer targeted cleanup of `TMPDIR`/`DEPLOY_DIR` over either.
- **Never grep an unanchored `ERROR`** — it matches compiler output and package names; use `^ERROR:`.
- **Never trust a stale `$PRE`** — it must be a real sha from *this* session or deliberately empty, or the buildhistory diff quietly reports the wrong range.
- **Never assume `DEPLOY_DIR`** — resolve it with `bitbake-getvar` and check non-empty *and* an existing directory (Layout section) before building any path on it.
- **Never pass `--runtime-args` more than once** — at the pinned kas version it OVERWRITES rather than accumulates, so repeating it silently drops all but the last occurrence.
- **Never route around a failed check** — an empty snapshot, a missing verdict line, or a disagreeing rc is a stop condition, not something to retry past or explain away.

## References

- The `mackas-angstrom` skill — the macOS/mackas companion (same bitbake concepts, different file access); its "Publishing to the package feed" section is the full feed writeup, and its "Analyzing buildstats" section covers the standalone buildstats analyzer in more depth.
- The `boot-validate` skill — proving an image actually boots, beyond what a build report says.
- oe-core: `buildstats.bbclass`, `buildhistory.bbclass`.

## Notes / gotchas

- `local_conf_header` in `angstrom.yml` already enables `buildhistory`, `buildstats`, and `rm_work` — so source/work dirs are wiped after each recipe; inspect results via buildhistory and deploy, not `tmp/work`.
- kas prints "Repo <x> is dirty - no checkout" for locally-modified sibling layers; that is expected whenever sibling repos carry uncommitted work, and does not fail the build.
- `DL_DIR`/`SSTATE_DIR` are shared across machines under `build/` — never point them inside a layer checkout.
- **One build at a time per `TMPDIR`/`SSTATE_DIR`/`DL_DIR`.** Nothing in plain `kas-container` locks them: two concurrent invocations sharing a build root (or sharing hand-configured `DL_DIR`/`SSTATE_DIR` across build roots) corrupt each other's state. Keep multi-machine sweeps strictly sequential — one loop, one `kas-container` at a time, a log per machine. Separate `DL_DIR`/`SSTATE_DIR` per concurrent build is the only safe parallel setup, and gives up all cache sharing.
- Machine name normalization: buildhistory dir names use underscores (`qcs6490_thundercomm_rubikpi3`) while deploy/images use hyphens (`qcs6490-thundercomm-rubikpi3`).
