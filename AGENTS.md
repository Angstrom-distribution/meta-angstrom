# Agent Guide for meta-angstrom

meta-angstrom is the distro layer for the Ångström Distribution: distro config (`conf/distro/angstrom.conf`), the kas composition that pulls in every other layer (`kas/angstrom.yml` + a machine fragment), and Angstrom-specific recipes/images.

## Building / testing

Builds run through `kas-container` (docker or podman), composing `kas/angstrom.yml` with one machine fragment. Run every command from the **build root** — the directory this repo was cloned into, the parent of the `meta-angstrom` checkout, where kas clones the sibling layers (`openembedded-core`, `bitbake`, `meta-openembedded`, `meta-ti`, ...). kas mounts that directory at `/repo` and resolves every config path against it.

```sh
kas-container build \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml \
  --target <target>
```

Full procedure — fragment composition, buildhistory/deploy analysis, feed publishing, the `--skip` flag family — is in `skills/angstrom-build/SKILL.md`. Read it before building.

**`--skip repos_checkout --skip repos_apply_patches` is mandatory whenever any sibling layer carries local-only commits**, which several here routinely do — they sit ahead of their pushed branches for long stretches. kas force-resets a *clean* sibling repo to the configured branch head at the start of every invocation (`shell` and `for-all-repos` included, not just `build`); uncommitted tracked changes protect a repo, local-only commits do not, and they silently disappear from the working tree (recoverable via reflog). `--skip repos_checkout` alone is not enough — kas then re-applies `angstrom.yml`'s oe-core patches onto an already-patched tree and dies with "Could not apply patch". The flip side: `--skip repos_apply_patches` blocks *every* `patches:` block in the composition, so a newly added patch entry needs one deliberate un-skipped run — pin at-risk sibling repos with `commit:` first so that run cannot reset them. A `commit:` pin only works for a sha that already exists on that entry's fetched remote branch; it cannot pin local-only work. The reset hits every `repos:` entry with a `url:` — the self-referencing `angstrom:` entry is never checked out, so local commits in this repo are safe. `git reflog` in the reset layer recovers the lost head (`git reset --hard <sha>`).

When adding a layer or moving a pin onto a newer oe-core, check `LAYERSERIES_COMPAT_<collection>` in each layer's `conf/layer.conf` first — a missing series is the usual first parse failure, and where upstream lags the fix goes in `patches/` wired in from `angstrom.yml`.

Only one build at a time may use a given `TMPDIR`/`SSTATE_DIR`/`DL_DIR`. Plain `kas-container` enforces nothing here: if these are pointed at shared locations, concurrent builds (including a second machine of the same target) will corrupt each other. Keep multi-machine sweeps strictly sequential.

Machine fragments tracked in `kas/`: `beaglebone`, `qemuarm64`, `qemuarmv5`, `riscv` — `ls kas/` for the authoritative list, fragments get added and renamed, and untracked ones (a local `rb1-core-kit.yml`, the mackas-generated `macos-local.yml`) show up there too. `DEPLOY_DIR` is `${TOPDIR}/deploy` (a sibling of `tmp/`, not the oe-core-textbook default under `tmp/deploy`), except under the `k3r5` multiconfig, where `conf/distro/include/fix-k3r5.inc` gives it a private `${TMPDIR}/deploy` — verify with `bitbake-getvar` rather than assuming a path.

### mackas (macOS only, optional)

On macOS, Koen's local setup drives kas through `mackas`, a `kas-container` wrapper for Apple's `container` runtime that keeps `TMPDIR`/`DL_DIR`/`SSTATE_DIR` on ext4 volumes the host cannot see. It is a convenience layer for that host, not part of this layer's required workflow — everything above still applies underneath it. Details, and the footguns that only exist there (stale `env.sh`, the one-VM rule, `mackas exec` for repo-safe one-off queries, retrieving artifacts off the invisible volumes), are in `skills/mackas-angstrom/SKILL.md`.

```sh
source ~/oe/env.sh
export MACKAS_PROJECT_DIR=meta-angstrom
export MACKAS_KAS_CONFIG=kas/angstrom.yml:kas/<machine>.yml
cd ~/oe/work
mackas exec bitbake-getvar -r <recipe> <VAR>          # safe one-off query
kas-container build --skip repos_checkout --skip repos_apply_patches \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/<machine>.yml \
  --target <target>                                    # real build
```

Check `container list` (read-only, the one raw `container` command that is safe to run) is empty before starting anything — only one VM can hold the ext4 build volumes at a time.

## Never push without asking

Commit locally; never run `git push` on this repo (or any sibling layer) without asking first, even mid-task. Report what is ready to push and let Koen decide when.

## Commit messages

This is the authoritative convention for this layer — follow it exactly, it intentionally differs from other projects' style guides:

- Subject: `recipe-or-file: summary of the change`.
- `kas/*.yml` and `conf/layer.conf`-style config changes almost always get their own commit, split out from whatever recipe/feature change motivated them.
- Body: at most a short paragraph or two of *why* (the problem being solved, what was verified against a real build), then a flat bullet list of *what* changed. Do not narrate the debugging journey — how many attempts something took, what was tried and discarded, blow-by-blow iteration — that belongs in conversation, not in the permanent log.
- Say only what the reader doesn't already know. Don't explain that a higher version number is newer, that an old value is "stale," or restate what the diff already shows — every sentence should carry information a reviewer couldn't get faster by reading the diff itself. If a sentence would be obvious to anyone who can read the surrounding code, cut it.
- Describe the change against the tree as it stands, never against where it was ported from. No "wrynose carried X", no "this originally also fixed Y, already upstream" -- what another branch used to do is not part of this commit.
- End with `Signed-off-by: <name> <email>`, read from real `git config` (`git config user.name`/`user.email`) — never fabricated — and, when AI-assisted, `Assisted-by: Claude:claude-sonnet-5`: agent name, colon, model id, no email and no URL.

Reference example of the target shape, including both trailers: commit `0e70d62` (`linux-firmware: package the TI variant per machine arch`).

## Recipe/patch style

- Keep in-file comments (`.bb`/`.bbappend`/`.inc`/patches) terse — a line or two by default, ~6-line cap if it genuinely needs more, every line earning its place.
- Avoid `INSANE_SKIP`/other QA-suppressions unless the underlying complaint genuinely cannot be fixed — chase the real root cause first. Prefer `git://` fetches over tarballs where the downstream build system can actually consume a plain checkout.
- `LICENSE` is parsed as a strict SPDX expression at parse time: no `&`/`|`, no `GPLv2+`-style names, non-standard licenses need a `LicenseRef-<name>` plus a matching generic license file or `NO_GENERIC_LICENSE` mapping. `INSANE_SKIP` cannot silence the resulting `license-format` error (it is parse-time and reads `ERROR_QA`, not a `do_package_qa` check) — use `ERROR_QA:remove = "license-format"` in that one recipe when the license genuinely isn't worth modelling.
- A `_%` bbappend only matches a recipe whose filename carries a version (`foo_1.0.bb`, `foo_git.bb`). A versionless recipe (`linux-mainline.bb`) needs a bare `foo.bbappend` — otherwise the append silently never applies.
- Never write a bare `WKS_SEARCH_PATH:append` into a `layer.conf`: bitbake counts any applied operator as "already set", which kills `image_types_wic.bbclass`'s weak default for every layer. Seed the class default explicitly, then append.

- Never say "Yocto" (except the literal phrase "Yocto Project"); never "Poky" — say "oe-core" / "nodistro".

More traps of this kind, with the worked examples this layer already carries in `patches/`, are in `skills/angstrom-build/SKILL.md`.
