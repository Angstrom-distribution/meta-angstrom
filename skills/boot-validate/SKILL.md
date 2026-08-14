---
name: boot-validate
description: Boot an Angstrom/OpenEmbedded image under emulation and prove it reaches a working login shell, then run functional checks a bitbake pass cannot prove — systemd presets actually disabling units, timers actually armed, networkd config actually applied, sound cards actually usable. Covers all 12 oe-core qemu* machines (qemuarm, qemuarm64, qemuarmv5, qemuloongarch64, qemumips, qemumips64, qemuppc, qemuppc64, qemuriscv32, qemuriscv64, qemux86, qemux86-64) through stock runqemu — individually or as a sequential sweep, `scripts/boot-validate.py --sweep` — and the beaglebone machine through the custom AM335x QEMU fork (koenkooi/qemu, branch beaglebone-black). Use when asked to boot-test an image, validate that a build actually comes up, check boot-time behavior at a serial console, sweep every qemu machine, or run an interactive check on an emulated board. Not for building images (see angstrom-build, or mackas-angstrom on a macOS/mackas host).
---

# Boot validation for Angstrom images

A bitbake pass only proves the rootfs was *assembled* with the right files. Whether a preset really disables a unit, a timer really arms, a networkd drop-in really takes effect, or an ALSA device is really openable by the user that needs it, is only observable on a booted system. This skill boots the image and answers that.

Companion skills: `angstrom-build` (the standard kas-container build flow) and `mackas-angstrom` (the same on a macOS/mackas host — `mackas exec`, `retrieve`). Build there, validate here.

## Two launch shapes, one validator

| | oe-core qemu machines | beaglebone |
|---|---|---|
| Machines | all 12 oe-core `qemu*` machines: `qemuarm`, `qemuarm64`, `qemuarmv5`, `qemuloongarch64`, `qemumips`, `qemumips64`, `qemuppc`, `qemuppc64`, `qemuriscv32`, `qemuriscv64`, `qemux86`, `qemux86-64` (each has a `kas/<machine>.yml` fragment of the same name) | `beaglebone` (real-hardware image) |
| Emulator | stock `runqemu` from oe-core, using the image's own `.qemuboot.conf` | a from-scratch AM335x SoC model in a QEMU fork |
| Where it runs | **inside the build environment** — the qemu binaries and `runqemu` are in the kas container, and so is the deploy dir | on the host, against a retrieved `.wic`/`.wic.xz` |
| Speed | TCG, minutes to a login prompt (64-bit mips/ppc/riscv budgeted longer — see the machine table) | TCG single-core Cortex-A8, several minutes to a login prompt |

Everything downstream of "a console appeared" is identical, which is what `scripts/boot-validate.py` implements once for both. The backend choice (`runqemu` vs. the board fork) is a lookup in a `BACKENDS` table in the script, not an if/elif on machine name — see "Sweeping every qemu machine" below.

The qemu machine list itself is **discovered at runtime** from `openembedded-core/meta/conf/machine/qemu*.conf` (a sibling checkout — see `mackas-angstrom`/`angstrom-build` for the layout), not hardcoded, so it will not silently go stale the next time oe-core adds or drops a machine. `--list-machines` shows what was actually discovered (or says so if it fell back to a static list because no sibling checkout was found).

## The validator: `scripts/boot-validate.py`

Waits for a login prompt, logs in, waits for the shell to actually be *ready* (not merely echoed — an early command gets swallowed during session setup), runs fenced sanity checks, then any extra probes, and prints a single `BOOT-VALIDATION: PASS | PASS(warn) | FAIL` line. Exit code 0 on PASS, non-zero on FAIL. The full console capture goes to a log file for evidence.

Two console backends, same logic:

- `--launch 'CMD'` — spawn CMD and drive its stdin/stdout as the console. This is the emulator path for both shapes. CMD must put the guest console on stdio (`runqemu ... nographic`, or `-nographic` for a hand-built qemu line).
- `--port /dev/ttyUSB0` — a real serial port via pyserial, for validating the same image on real hardware. pyserial is imported only on this path, so the emulator path needs nothing installed.

`--launch`/`--port` plus `--check`/`--host-cmd`/`--timeout`/`--poweroff`/`--password` are implemented and verified end-to-end against a real headless beaglebone boot. `--sweep`/`--machine` (the oe-core qemu*-machine driver described below, which builds inside the container and needs its own machine-discovery logic) is not currently implemented — driving a single machine via `--launch` is the only working path right now.

Verdict rules:

- `systemctl is-system-running` → `running` = **PASS**.
- `starting` = **PASS(warn)**, not a bare PASS. A single snapshot can't tell a real transient state (jobs still queued, about to settle) from a permanent one (a crash-looping unit whose start job never completes) — conflating the two let a genuinely broken app (a service stuck in a 30-restart loop, never coming up) report a bare PASS three attempts running before this was caught. Don't trust `starting` alone; the per-image `--check` probes (`is-active`, `journalctl -u <service>`) right below carry the real answer — always add them for any service the image is meant to run.
- `degraded` = **PASS(warn)** on purpose: the board booted; chase the failing units separately with `systemctl --failed`.
- No systemd at all (an `init=/bin/sh` or non-systemd boot) but a real `uname -a` = **PASS(warn)**.
- No login prompt, no shell, or an empty `uname` = **FAIL**, with the last 40 console lines printed for triage.

**Compare the reported `/etc/os-release` fields with the build you meant to test.** A PASS on a stale image validates nothing. The script reports `PRETTY_NAME`, `VERSION` and `BUILD_ID`; Angstrom sets the first two (`PRETTY_NAME="Angstrom <release>"`), so `BUILD_ID` is normally absent and `VERSION` is the field to compare. `--check` probes are the way to add anything image-specific:

```sh
--check 'audio:cat /proc/asound/cards; ls -l /dev/snd'
--check 'presets:systemctl is-enabled busybox-syslog.service busybox-klogd.service'
```

Each runs after login and is reported verbatim under its name. Judge the printed text, not exit codes — see the gotchas.

### Per-image configuration: hostfwd, checks, host-cmd

`--sweep`/`--machine` mode carries three per-image tables in the script, keyed on the image recipe name, so a boot-test config for a specific image lives in the repo instead of being typed at each invocation. All three are additive to whatever the caller passes on the command line, never a replacement for it.

| Table | What it adds | Where it lands |
|---|---|---|
| `IMAGE_HOSTFWD` | extra `hostfwd` host↔guest port pairs | `QB_SLIRP_OPT` prefix on the inner `runqemu` invocation |
| `IMAGE_CHECKS` | extra `--check NAME:CMD` guest probes | appended after the caller's own `--check` args |
| `IMAGE_HOST_CMDS` | extra `--host-cmd NAME:CMD` host commands | appended after the caller's own `--host-cmd` args |

**`IMAGE_HOSTFWD`** — `{image: [(host_port, guest_port), ...]}`. `get_extra_qemu_args()` composes the full `QB_SLIRP_OPT` string from it, keeping runqemu's own default `127.0.0.1:2222->22`/`127.0.0.1:2323->23` forwards (an override replaces the whole hostfwd list, not just adds to it — see the code comment). A row's extra qemu args come from `IMAGE_HOSTFWD[image]` when the image has an entry, falling back to the machine row's own `extra_qemu_args` (`MACHINE_OVERRIDES`) otherwise. **Take the guest port from the recipe's own config/env file and record where you got it** — a guessed port produces a forward that silently reaches nothing. Where an httpd or reverse proxy fronts the app, forward *its* port and probe through it: that is the path a real client takes.

The tables ship two worked examples rather than any real image — a REST-shaped `myapp-image` (8080 → 8080) and a SOAP-shaped `mysoap-image` (8090 → 80 for the fronting httpd, 9090 → 9090 for the app itself). Replace them when adding a real image; they exist to show the mechanism, not to be kept.

**`IMAGE_CHECKS`** — `{image: ["NAME:CMD", ...]}`, threaded through exactly like `--check`: run on the **guest**, after the sanity checks. Write real probes, not just `systemctl --failed` + `is-active` — `is-active` proves only that a process forked. The example entries show the shape worth copying:

- a **REST** cycle — `GET` an endpoint, extract a token from the response body, send it as a header on a second `POST`/`GET`, and assert on both status codes *and* the fields that must be present;
- a **SOAP** cycle — fetch `?wsdl` and confirm it is one, then `POST` an XML envelope with a `SOAPAction` header, assert the expected element is in the response, and assert a `Fault` is *not* (a SOAP Fault is a perfectly valid HTTP 200, so status alone never catches it);
- per-app diagnostics either way: `journalctl -u <unit>` (last, so it also covers what the probes above provoked), a state/DB-file `ls`, an HTTP-status probe per web route.

**Wait on the port, not on the unit.** `Type=simple` reports `active` the instant `ExecStart` forks, long before an app framework finishes bootstrap and calls `listen()`. An `is-active` gate therefore races every API probe behind it. Poll the real port instead — `for i in $(seq 1 40); do curl -s -o /dev/null --max-time 2 http://127.0.0.1:PORT/ && break; sleep 2; done`.

**A probe can fail the verdict: print a line starting with `BV-FAIL:`.** The run then ends `BOOT-VALIDATION: FAIL` with every such line named in the verdict. Policy lives in the probe because only the probe knows what its own output means — `test "$C" = 200 || echo BV-FAIL: GET / HTTP $C`. Without it the verdict reads *only* systemd's own state, which is how an image whose every HTML route returned 500 reported a clean PASS: nothing in the check set read an HTTP status, and the one contradicting signal was a screenshot, i.e. a report-only `--host-cmd`. A `TIMED OUT` check fails the run too — no evidence gathered is not a pass.

**Two probe-authoring traps, both found the hard way:**

- **The whole probe is one console line, sharing it with the run's own end marker.** `boot-validate.py` sends `echo BVSTART; <your probe>; echo BVEND`, so a *shell syntax error anywhere in the probe* takes `BVEND` down with it and the harness reports `TIMED OUT` rather than anything about the syntax. An unquoted `|| echo (no such file)` did exactly this and hid a probe's output for two full attempts. Quote anything containing `(`, `)`, `&`, `;` or `|`.
- **Fetching a stack trace: grep the message, don't `tail` the file.** A web-framework trace is easily ~90 frames, so `tail -n 60` returns middleware plumbing and never the exception that names the fault. Grep the log's `LEVEL: message` header lines instead, and `cut -c1-300` — a single untruncated frame takes minutes to clock out over the serial console.

**`--host-cmd NAME:CMD`** (repeatable) — runs on the **emulator host** (wherever `boot-validate.py` itself runs — inside the kas-container for the `runqemu` backend), after the `--check` probes and before poweroff, guest still up. This is the way to drive a hostfwd'd port from the host side: `curl` against a forwarded API port, a headless-Chromium screenshot of a webUI, anything that needs to reach the guest over the network rather than through the console. Captured stdout/stderr and rc are mirrored into the log file and printed as `HOST-CMD <name>: rc=<n>` plus the output. **Report-only**: a failing `--host-cmd` never flips `BOOT-VALIDATION` from PASS to FAIL — it diagnoses the app sitting on top of a working boot, not the boot itself. `IMAGE_HOST_CMDS` threads per-image entries through `--sweep`/`--machine` the same table-driven way as `IMAGE_CHECKS`; the example entry is a headless-Chromium screenshot per web route (`chromium --headless=new --no-sandbox --disable-gpu --virtual-time-budget=10000 --window-size=1280,1024 --screenshot=/repo/artifacts-boot-validate/<name>.png http://127.0.0.1:<hostfwd-port>/`). It installs chromium on demand because it runs host-side, outside the read-only container mount; output lands in `artifacts-boot-validate/` at the meta-angstrom repo root — add that to `.git/info/exclude`. **Address the host-side `IMAGE_HOSTFWD` port here, not the guest port the `--check` probes use.**

**A screenshot is evidence only once you open it.** Every failure mode here produces a PNG of the right size at the right time: a Chromium `ERR_CONNECTION_RESET` page when nothing is listening, a framework's own `500 | Server Error` page when the app is up but the view layer is broken. Read the file, and cross-check that two shots of *different* routes are not byte-identical — identical hashes mean a global fault, not two working pages.

`--check`/`--host-cmd` values embedded into the generated `kas-container shell -c '...'` payload go through `_dq_escape()` (one level of `\"`-escaping for the container shell) — not the two-level `\\\"` dance `QB_SLIRP_OPT` needs, because that string is additionally re-parsed a third time (`Popen shell=True` inside the *inner* `boot-validate.py --launch` invocation) and `--check`/`--host-cmd` are plain sibling arguments that never go through that third parse. Don't hand-quote a `--check`/`--host-cmd` command through three shell levels yourself — put it in the table and let `_dq_escape()` do it. `_dq_escape()` also escapes `$` and `` ` ``, so a probe can use shell variables and `$(...)`: the value is meant to be evaluated by the *guest*, but it arrives inside a double-quoted container-shell argument, which would otherwise substitute them away before the guest ever saw them. A **single quote** in a probe still breaks the enclosing `-c '...'` — use double quotes inside probe bodies.

### Credentials

| Image | user | password | notes |
|---|---|---|---|
| `console-base-image` | `root` | empty | `IMAGE_FEATURES += "empty-root-password"`; **console/pubkey only** — `allow-empty-password` is deliberately not set, so the empty password does not work over ssh |
| `dominion-image`, `transmission-image` | `root` | empty | `empty-root-password allow-empty-password` — empty password works over ssh too |
| anything with a real root password | `root` | that password | pass it with `--password` |

Empty is the default, so `--password` is usually unnecessary. The script handles both shapes (password prompt or straight to a shell) without being told which to expect.

## Sweeping every qemu machine

`scripts/boot-validate.py` also drives the whole build-then-boot cycle, not just the console, so a full sweep or a single machine is one command:

```sh
python3 scripts/boot-validate.py --list-machines            # the discovered table; touches nothing
python3 scripts/boot-validate.py --sweep --dry-run           # the ordered plan, one line per machine; touches nothing
python3 scripts/boot-validate.py --sweep                     # build + boot-test every supported machine
python3 scripts/boot-validate.py --machine qemuriscv64        # just that one
python3 scripts/boot-validate.py --machine qemuarm64 --dry-run --image kodi-image   # override the target image
```

For each machine this runs a single composite shell command: `kas-container build ... --target <image> && kas-container shell ... -c 'zstd -d ...; python3 boot-validate.py --launch "runqemu ..." ...'`. `--dry-run` prints that exact command instead of running it — use it to check a machine-table change (or this skill) before spending real build time; that is what a reviewing agent should run first, since it never touches the build environment.

**THE SWEEP IS STRICTLY SEQUENTIAL, and must stay that way.** A build environment's `TMPDIR`/`DL_DIR`/`SSTATE_DIR` may only be held by one process at a time; a second concurrent `kas-container` invocation corrupts or deadlocks on them (on a mackas host that is enforced by the three ext4 volumes' exclusive attachment, elsewhere it is not enforced at all). `run_sweep()` in the script uses a plain blocking `subprocess.run()` per machine for exactly this reason — no threads, no `&`, no multiprocessing, ever, no matter how tempting parallelizing 12 TCG boots looks.

Machines that cannot boot-test at all would show up as `SKIPPED (unsupported): <reason>`, both in `--list-machines` and in the sweep's own output — the table has an explicit `supported`/`reason` pair for this, it does not silently drop a machine. None of the 12 currently need it (see the research this was built from: no missing kernel provider, fstype, or QEMU system target across any of them). `beaglebone` shows up in the table too, `excluded from sweep` rather than unsupported — it is a fully valid board-fork machine, just not part of the oe-core-qemu* sweep (see Shape 2 below for how to run it).

Per-machine quirks (timeout, whether the deploy artifact needs `zstd -d` first, extra `runqemu`/qemu args) live in a short `MACHINE_OVERRIDES` dict in the script — only exceptions to the defaults belong there. `qemuloongarch64` is the one machine that already deploys a plain (uncompressed) `ext4`; every other machine deploys `ext4.zst` and the sweep decompresses it in the container before handing it to `runqemu`. `qemumips64`, `qemuppc64` and `qemuriscv64` get a 1200s timeout instead of the 900s default — 64-bit targets are slower under TCG.

## Shape 1: oe-core qemu machines (`runqemu`)

`--sweep`/`--machine` (above) generate exactly the commands below for whichever machine they're pointed at — this section is what they expand to, and the manual form to reach for when driving `runqemu` for something the table doesn't cover (a non-default image, an extra qemu flag, debugging a single boot interactively).

`runqemu` and the qemu binaries live in the build container, and so does `DEPLOY_DIR`, so the validator runs **inside** the build environment and spawns `runqemu` there itself:

```sh
# Linux (plain kas-container), from the build root
kas-container shell --skip repos_checkout --skip repos_apply_patches \
  meta-angstrom/kas/angstrom.yml:meta-angstrom/kas/qemuarm64.yml \
  -c 'python3 /repo/meta-angstrom/scripts/boot-validate.py \
        --launch "runqemu /build/deploy/images/qemuarm64/<image>-qemuarm64.rootfs.ext4 nographic slirp" \
        --timeout 900 --poweroff'
```

```sh
# macOS (mackas)
cd ~/oe/work
mackas exec python3 /repo/scripts/boot-validate.py \
  --launch 'runqemu /build/deploy/images/qemuarm64/<image>-qemuarm64.rootfs.ext4 nographic slirp' \
  --timeout 900 --poweroff
```

- Container-side paths differ between the two hosts because what gets mounted at `/repo` differs: a plain `kas-container` call from a Linux build root puts the build root there (`/repo/meta-angstrom/scripts/boot-validate.py`); under mackas, `MACKAS_PROJECT_DIR=meta-angstrom` puts *this layer* there (`/repo/scripts/boot-validate.py`). `KAS_BUILD_DIR` is `/build` in both. Resolve `DEPLOY_DIR` with `bitbake-getvar` rather than assuming it — this distro sets `DEPLOY_DIR = "${TOPDIR}/deploy"`, not `${TMPDIR}/deploy`.
- **The validator must spawn `runqemu`, not be piped into it.** `mackas exec` does not forward the host's stdin into the container (verified), so the older "`feed | runqemu`" pattern only works from a script that is itself already running inside the container. Having the validator own the child process removes that constraint and replaces blind `sleep` timing with real prompt detection.
- Point `--launch` at an **uncompressed** rootfs with a `.qemuboot.conf` beside it. Deploy usually holds `.ext4.zst`; `IMAGE_FSTYPES:append = " ext4"` in the image recipe is what produces a plain one (`kodi-image.bb` does this for exactly this reason).
- `slirp` is user-mode NAT and needs no privileges. It does **not** relay custom DHCP options — see the timezone test.
- `--poweroff` shuts the guest down cleanly at the end instead of just logging out; without it the emulator is killed when the script exits.
- Budget generously: `--timeout 900` is a reasonable default under TCG, and a heavyweight graphical image can need more.

## Shape 2: beaglebone on the custom AM335x fork

- Source: `https://github.com/koenkooi/qemu.git`, branch `beaglebone-black` (QEMU 10.0.0 base). Build it like any QEMU; only `arm-softmmu` is needed, and the wrapper expects the binary at the checkout's own `build/qemu-system-arm`:

  ```sh
  git clone -b beaglebone-black https://github.com/koenkooi/qemu.git
  cd qemu && ./configure --target-list=arm-softmmu && make -j"$(sysctl -n hw.ncpu)"
  ```

  `$QEMU_BBB` below stands for that checkout; `run-emulator.sh` also honors a `QEMU_BB` env var pointing at a binary built elsewhere.
- **This is a from-scratch AM335x SoC model** — own boot ROM, LCDC, CPSW ethernet MAC, WDT/PRCM emulation. It is NOT upstream QEMU's BeagleBone support; do not consult upstream docs for board behavior.
- TCG only: a software-emulated single-core Cortex-A8. U-Boot appears within seconds; a login prompt typically takes several minutes (allow ~10 before suspecting a hang, and only if serial output has also stopped).
- Machine types (`-M`): `beaglebone-black` (default), `beaglebone` (White), `beaglebone-green-eco`, `beaglebone-green-wireless`, `beaglebone-enhanced`. All 512 MiB RAM.
- The SD card model **rejects any image whose size is not an exact power of two**. A real wic build never lands on one naturally, so padding is mandatory every run — `run-emulator.sh` does it automatically.
- The board auto-binds its onboard CPSW NIC to whatever `-nic` backend is given (`qemu_configure_nic_device()` in `hw/arm/am335x_soc.c`); there is never a separate `-device` to pass.

Always go through the wrapper, which handles `.wic.xz` decompression, power-of-two padding, `-snapshot`, networking modes and console wiring:

```sh
"$QEMU_BBB"/run-emulator.sh --help
```

Validated the same way as shape 1, just with a different launch command — and on the host, since this emulator is not in the build container:

```sh
python3 scripts/boot-validate.py \
  --launch "$QEMU_BBB/run-emulator.sh ~/oe/artifacts/deploy/images/Angstrom-base-image-*-beaglebone.rootfs.wic.xz" \
  --timeout 900 --poweroff
```

Interactively instead, with the serial console (U-Boot → kernel → login) in the terminal:

```sh
# Throwaway boot with NAT networking (default; guest writes discarded on exit):
run-emulator.sh Angstrom-base-image-...-beaglebone.rootfs.wic.xz

# Real-LAN DHCP test (user-mode slirp does NOT relay custom DHCP options):
sudo run-emulator.sh -n vmnet-bridged --mac 52:54:00:be:ef:01 my-image.wic

# ssh into the guest through user-mode NAT:
run-emulator.sh --hostfwd tcp:127.0.0.1:2222-:22 my-image.wic
```

Exit: `Ctrl-A X` (immediate, safe under the default `-snapshot`), or log in, `poweroff`, then `Ctrl-A X`; `Ctrl-A C` toggles the QEMU monitor. From another terminal: `pkill -f qemu-system-arm` (with sudo if launched via sudo).

Images come out of a build (see `angstrom-build`, or `mackas-angstrom` on a macOS/mackas host). Note `console-base-image` deploys as `Angstrom-base-image-*-beaglebone*.wic.xz` because of its `IMAGE_BASENAME` override, and on macOS the deploy dir is inside an invisible ext4 volume — `mackas retrieve deploy images beaglebone` first.

## Functional test recipes

Worked examples against `console-base-image`. The unit and file names come from this layer's own recipes (`recipes-core/systemd/systemd/10-angstrom.preset`, `recipes-core/util-linux/fstrim-timer/`, `recipes-core/systemd/systemd-conf/timezone.conf` + `systemd-conf_%.bbappend`) — re-check there if a result surprises you. Each can be run as a `--check` probe or typed at the console.

### 1. Distro preset disables busybox syslog/klogd

**A correct preset file is not sufficient on its own, and this test exists because of that.** `systemd.bbclass` defaults `SYSTEMD_AUTO_ENABLE ??= "enable"`, and each package's postinst runs `systemctl preset <service>` **at that package's own install time during rootfs construction**, evaluating whatever preset files exist on disk *at that moment*. If `busybox-syslog` installs before meta-angstrom's own systemd package (carrying the preset) lands, it gets enabled right then under systemd's no-matching-preset-means-enable default, and the preset arriving afterward never retroactively re-runs that enablement. The layer therefore also forces `SYSTEMD_AUTO_ENABLE:${PN}-syslog = "disable"` in `recipes-core/busybox/busybox_%.bbappend`, independent of the preset (there is no separate `busybox-klogd` package — `busybox-klogd.service` rides `busybox-syslog`'s enable state via that unit's own `Also=busybox-klogd.service`). **If this fails, suspect install ordering and that bbappend before suspecting the preset file.**

```sh
cat /usr/lib/systemd/system-preset/10-angstrom.preset
systemctl is-enabled busybox-syslog.service busybox-klogd.service
systemctl is-active  busybox-syslog.service busybox-klogd.service
systemctl is-active  systemd-journald.service
journalctl -b -n 5 --no-pager
```

- PASS: preset file contains both `disable` lines; `is-enabled` prints `disabled` twice; `is-active` prints `inactive` twice; journald `active` with recent entries.
- FAIL: `enabled`/`active` for either busybox unit, or the preset file is missing.
- Vacuous case: `Failed to get unit file state ... No such file` means busybox-syslog is not installed at all — check `opkg list-installed | grep busybox` and report the preset untested rather than passed.

### 2. Weekly fstrim timer armed

The standalone `fstrim-timer` recipe ships `fstrim.timer` (`OnCalendar=weekly`, `Persistent=true`, `WantedBy=timers.target`) and `fstrim.service` (oneshot, `fstrim --listed-in /etc/fstab:/proc/self/mountinfo --verbose --quiet-unsupported`). Only the timer is enabled; the service is `static` by design (no `[Install]` section).

```sh
systemctl is-enabled fstrim.timer          # expect: enabled
systemctl is-active  fstrim.timer          # expect: active
systemctl list-timers --all --no-pager | grep fstrim
systemctl start fstrim.service
systemctl show -p Result,ExecMainStatus fstrim.service
journalctl -u fstrim.service --no-pager
```

- PASS: `enabled` + `active`, a `list-timers` row with a NEXT elapse, and the manual run ends `Result=success` / `ExecMainStatus=0`.
- Expected quirk: an emulated disk usually does not support discard, so the manual run trims nothing — `--quiet-unsupported` makes that exit 0 silently. Success with no output is a PASS.
- FAIL: timer `disabled`/`inactive`, or `Result=failed`. (`is-enabled fstrim.service` printing `static` is correct, not a failure.)

### 3. DHCP-supplied timezone (`UseTimezone=yes`, option 101)

`UseTimezone=yes` is set in **two** places in this layer, deliberately overlapping so it applies regardless of which image is booted:

- `console-base-image`'s own `do_systemd_network()` writes `10-en.network`/`11-eth.network`/`12-wlan.network` directly into the rootfs (matching `en*`/`eth*`/`wlan*`); each carries `[DHCPv4] UseTimezone=yes` in the recipe (`recipes-images/angstrom/console-base-image.bb`). This is what wins for this image, since those sort before anything `systemd-conf` installs.
- `systemd-conf`'s bbappend (`recipes-core/systemd/systemd-conf_%.bbappend`) separately adds a drop-in onto oe-core's `80-wired.network` (`Type=ether`, image-independent) plus a new `80-wireless.network` (`Type=wlan`, which oe-core does not ship) — the fallback covering any other image that pulls in `systemd-conf` without duplicating its own network files.

For `console-base-image` specifically, expect `networkctl status end0` to show `Network File: /etc/systemd/network/10-en.network` (not an `80-wired.network.d` drop-in) with `UseTimezone` effectively on — that is correct, not a sign the config landed in the wrong place. **The interface is `end0`, not `eth0`** on the beaglebone board: its CPSW comes up under systemd's predictable naming as `end0`, matching the `en*` pattern.

This test **requires bridged networking**: user-mode slirp (`runqemu ... slirp`, and the emulator's default `-n user`) only hands out IP/gateway/DNS and never relays option 100/101. It also requires the LAN's DHCP server to actually send option 101 — most consumer routers do not. Pre-check from the host before burning a slow boot: `ipconfig getpacket en0` shows what the host's own lease carried; if 101 is absent, configure the DHCP server first (dnsmasq: `dhcp-option=option:tzdb,Europe/Amsterdam`).

```sh
# host side — confirm the bridge interface, then boot:
route -n get default | awk '/interface:/{print $2}'    # typically en0
sudo run-emulator.sh -n vmnet-bridged --mac 52:54:00:be:ef:01 my-image.wic
```

vmnet requires root or the `com.apple.vm.networking` entitlement; a self-built qemu has no entitlement, so **sudo is effectively mandatory** — an unprivileged run fails at netdev creation. macOS may also raise a Local Network permission prompt for the terminal app.

At the guest console, in order:

```sh
timedatectl show -p Timezone --value      # baseline, before the lease: Universal
networkctl                                # wait for end0 "routable  configured"
ip -4 addr show end0                      # a real LAN address, not 10.0.2.x
grep -H TIMEZONE /run/systemd/netif/leases/*   # option 101 actually received?
networkctl status end0 --no-pager         # which .network file matched + drop-ins
timedatectl show -p Timezone --value      # expect the DHCP-supplied zone
ls -l /etc/localtime
journalctl -u systemd-timedated -u systemd-networkd --no-pager | grep -i -e timezone -e denied
```

- PASS: the lease file shows `TIMEZONE=<zone>`, `networkctl status` shows the matched network file with the timezone drop-in listed, and `timedatectl` reports that zone (journal: `Changed time zone to '<zone>'`). **Two independent things must both be present**: `UseTimezone=yes` on the matched `.network` file, *and* polkit in the image (`angstrom.conf` carries it in `DISTRO_FEATURES`). With only the first, the result is "FAIL (authorization)" below, not a partial pass.
- FAIL (config not in effect): `timedatectl` still reports the baseline zone and the journal shows no timezone attempt. The classic cause is file ordering — an image writing its own `10-`/`11-`/`12-` network files shadows anything dropped onto `80-wired.network`, which is why `console-base-image.bb` carries `UseTimezone=yes` inline. Check that recipe and the file `networkctl status` says actually matched, in that order.
- FAIL (authorization): journal shows `Could not set timezone: Access denied` (usually alongside `Could not set hostname: Access denied`) from `systemd-networkd`. Not a config bug: `systemd-networkd` runs as the unprivileged `systemd-network` user, and its D-Bus call to `timedated`'s `SetTimezone` needs a polkit authority to grant it — without one the call is denied outright, permanently (a device in this state never eventually succeeds). Confirm with `which polkitd` and `systemctl status polkit`. On a running system `opkg install polkit` + `systemctl start polkit` recovers it; for a real build, ensure polkit is in `DISTRO_FEATURES`. Follow either with a full `systemctl restart systemd-networkd` — a plain `networkctl renew <if>` does not reliably force a fresh enough DHCP cycle to retrigger the attempt.
- INCONCLUSIVE: no `TIMEZONE=` in the lease — the DHCP server never sent option 101; fix the server, not the image.

If the interface comes up (`Gained carrier`) but `journalctl -u systemd-networkd | grep -i dhcp4` shows no client activity at all under `vmnet-bridged`, suspect a MAC mismatch: vmnet filters host-side traffic against the MAC declared on the QEMU command line, so a guest driver using a different, self-generated MAC never receives the replies. Confirm with `info network` on the QEMU monitor versus `ip link show end0` in the guest. The fork propagates a command-line `--mac` into `am335x-cpsw` properly, so an older binary is the first thing to check.

### 4. Sound card present *and* usable by the right user

No qemu machine gets a sound card by default, and this needs **two** independent pieces — a lesson worth carrying into any audio-adjacent check, because each half looks fine on its own:

- **Guest driver**: `recipes-kernel/linux/files/angstrom-qemu-sound.cfg` (`CONFIG_SOUND`, `CONFIG_SND`, `CONFIG_SND_PCM`, `CONFIG_SND_VIRTIO`). Without it the guest has no card at all.
- **Emulated device**: `QB_OPT_APPEND:append:qemuall = " -audiodev none,id=snd0 -device virtio-sound-pci,audiodev=snd0"` (see `recipes-images/angstrom/kodi-image.bb`). `none` is a discard backend — enough for ALSA to enumerate a card, and it avoids depending on host audio inside the build container.
- **Permissions**: ALSA nodes are `root:audio` mode 660, so a service running as a non-root user needs to be *in* that group. `recipes-graphics/wayland/weston-init.bbappend` adds `audio` to the weston user's supplementary groups for exactly this reason.

The permission half is the one that hides: `/proc/asound/cards` and `aplay -l` show a perfectly working virtio-snd device while the application still fails to open any sink. **Probe both layers, not just the device**:

```sh
--check 'snd-device:cat /proc/asound/cards; ls -l /dev/snd; aplay -l' \
--check 'snd-perms:id weston; getent group audio'
```

Only a boot-log inspection surfaces this class of bug — the kernel driver probes cleanly either way.

## Gotchas

- **Judge printed text, not exit status.** `systemctl is-enabled`/`is-active` exit non-zero for `disabled`/`inactive`, which several tests above *expect*. The same trap applies to `grep`/`diff`/`[ ]` at the end of any wrapper script.
- **Slow boot is normal.** TCG, no acceleration, on either shape. Give a booting guest ~10 minutes before calling it hung, and only if console output has also stopped.
- **Wait for the shell to be ready before typing.** Commands sent between the login prompt and session setup are silently swallowed. The validator nudges with a bare newline and waits for a real prompt; do the same by hand.
- **Strip escape sequences before matching.** OE images enable bash's semantic prompt, which sprays OSC sequences around the prompt and command echo — the raw log looks far noisier than the parsed report. The validator strips ANSI/OSC/DCS before parsing.
- **Power-of-two SD padding is mandatory** for the AM335x fork, every image, every time; the wrapper does it, a hand-rolled qemu line must do it first.
- **`-snapshot` is the wrapper's default**: every run is throwaway, so re-running against the same `.wic` is always safe. Pass `--persist` only when a test needs writes to survive, then re-decompress a fresh image afterwards for clean-state tests.
- **User-mode networking cannot test DHCP options.** slirp/`-n user` hands out IP/gateway/DNS only. Anything option-driven needs bridged networking and a real DHCP server.
- vmnet-bridged over Wi-Fi can be flaky (some APs drop foreign MACs); prefer a wired host interface and pass a fixed `--mac` so the lease is stable across runs.
- **Freeze the VM to inspect device config without booting.** `-S` constructs the machine and all devices but never starts the guest vCPU, so `info network`, `info pci` etc. answer immediately:

  ```sh
  sudo "$QEMU_BBB"/build/qemu-system-arm \
    -M beaglebone-black -S -sd my-image.wic -snapshot \
    -nic vmnet-bridged,ifname=en0,mac=52:54:00:be:ef:01 \
    -display none -monitor unix:/tmp/qemu-mon.sock,server,nowait -serial none &
  sudo chmod 666 /tmp/qemu-mon.sock    # socket is root-owned when qemu ran under sudo
  ```

  Then talk to `/tmp/qemu-mon.sock` with any AF_UNIX client. `sudo pkill -f qemu-system-arm` to clean up — an unprivileged `pkill` silently does nothing to a root-owned process.
