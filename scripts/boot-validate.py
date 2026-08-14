#!/usr/bin/env python3
"""Boot an Angstrom/OpenEmbedded image under emulation and prove it reaches
a working login shell, then run functional checks. See skills/boot-validate/
SKILL.md for the full design this implements.

This is a rebuild of a script that existed earlier in this project but was
never committed (untracked) and was lost between sessions. Implements the
single-machine --launch/--port console-driving path documented in the
skill; --sweep/--machine (the oe-core qemu* multi-machine driver, which
needs to run inside the build container) is not reimplemented here.
"""
import argparse
import re
import select
import shlex
import subprocess
import sys
import time
import os
import pty
import signal

ANSI_RE = re.compile(rb'\x1b(?:\[[0-9;?]*[a-zA-Z]|\][^\x07\x1b]*(?:\x07|\x1b\\)|[()][A-Za-z0-9]|[=>])')

LOGIN_RE = re.compile(rb'(?:^|\n)[\w.-]*\s*(?:login|Login):\s*$')
PASSWORD_RE = re.compile(rb'(?:^|\n)Password:\s*$')
PROMPT_RE = re.compile(rb'[#$]\s*$')


def strip_ansi(data: bytes) -> bytes:
    return ANSI_RE.sub(b'', data)


class Console:
    def __init__(self, cmd, logfile):
        self.master_fd, slave_fd = pty.openpty()
        self.proc = subprocess.Popen(
            cmd, shell=True, stdin=slave_fd, stdout=slave_fd, stderr=slave_fd,
            preexec_fn=os.setsid, close_fds=True,
        )
        os.close(slave_fd)
        self.buf = b''
        self.logfile = logfile

    def send(self, s: str):
        os.write(self.master_fd, s.encode())

    def sendline(self, s: str = ''):
        self.send(s + '\n')

    def read_available(self, timeout=0.5) -> bytes:
        r, _, _ = select.select([self.master_fd], [], [], timeout)
        if not r:
            return b''
        try:
            data = os.read(self.master_fd, 65536)
        except OSError:
            return b''
        self.logfile.write(data)
        self.logfile.flush()
        return data

    def expect(self, patterns, timeout):
        """Wait until one of the (compiled) regex patterns matches the
        accumulated, ANSI-stripped buffer. Returns the matched pattern index,
        or None on timeout. Does not consume/reset the buffer -- the caller
        decides what to do with it (see expect_nth for why: a fast command's
        entire echo+output+marker cycle can arrive in a single read, before
        expect() is even called once, so trimming eagerly on the first
        match can discard real data that already arrived alongside it)."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self.proc.poll() is not None:
                return None
            chunk = self.read_available(timeout=min(1.0, deadline - time.time()))
            if chunk:
                self.buf = strip_ansi(self.buf + chunk)
                if len(self.buf) > 200_000:
                    self.buf = self.buf[-100_000:]
                for i, pat in enumerate(patterns):
                    if pat.search(self.buf):
                        return i
        return None

    def expect_bare_line(self, marker: str, timeout):
        """Wait until `marker` appears alone on its own line (only whitespace
        around it) and return that match's span. The pty echoes the command
        line we just sent (which contains the marker as literal text) back
        one or more times before it actually executes -- how many times
        varies (a plain local-echo pass, then sometimes a second redraw with
        the shell prompt prepended) so counting occurrences is unreliable.
        But only the real, post-execution `echo MARKER` ever produces a line
        containing *just* the marker -- every echoed-input occurrence has the
        rest of the command text (and often a prompt) on the same line."""
        pat = re.compile(rb'(?:^|\n)\r*' + re.escape(marker).encode() + rb'\r*(?:\n|$)')
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self.proc.poll() is not None:
                return None
            chunk = self.read_available(timeout=min(1.0, deadline - time.time()))
            if chunk:
                self.buf = strip_ansi(self.buf + chunk)
                if len(self.buf) > 200_000:
                    self.buf = self.buf[-100_000:]
                m = pat.search(self.buf)
                if m:
                    return m.span()
        return None

    def drain(self, seconds=1.0):
        end = time.time() + seconds
        out = b''
        while time.time() < end:
            out += self.read_available(timeout=max(0, end - time.time()))
        return strip_ansi(out)

    def terminate(self):
        if self.proc.poll() is None:
            try:
                os.killpg(os.getpgid(self.proc.pid), signal.SIGTERM)
                self.proc.wait(timeout=10)
            except Exception:
                try:
                    os.killpg(os.getpgid(self.proc.pid), signal.SIGKILL)
                except Exception:
                    pass


def dq_escape(s: str) -> str:
    return s.replace('\\', '\\\\').replace('"', '\\"')


def run_check(con: Console, name: str, cmd: str, timeout: int, results: list):
    endmarker = f'BVEND-{name}-{time.time_ns()}'
    con.buf = b''
    con.sendline(f'{cmd}; echo {endmarker}')
    span = con.expect_bare_line(endmarker, timeout)
    if span is None:
        print(f'CHECK {name}: TIMED OUT')
        results.append((name, 'TIMED OUT', con.buf.decode('utf-8', 'replace')))
        con.buf = b''
        return
    out = con.buf[:span[0]].decode('utf-8', 'replace')
    con.buf = b''
    # out still holds the echoed input line(s); the real output starts right
    # after the LAST occurrence of the command text we sent.
    idx = out.rfind(cmd)
    if idx >= 0:
        nl = out.find('\n', idx)
        out = out[nl + 1:] if nl >= 0 else ''
    body = out.strip('\r\n').strip()
    print(f'CHECK {name}:\n{body}')
    results.append((name, 'ran', body))


def run_host_cmd(name: str, cmd: str, results: list):
    try:
        r = subprocess.run(cmd, shell=True, capture_output=True, timeout=120, text=True)
        out = (r.stdout or '') + (r.stderr or '')
        print(f'HOST-CMD {name}: rc={r.returncode}\n{out}')
        results.append((name, r.returncode, out))
    except Exception as e:
        print(f'HOST-CMD {name}: EXCEPTION {e}')
        results.append((name, -1, str(e)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--launch', help='command to spawn; its stdio is the guest console')
    ap.add_argument('--port', help='real serial port device (pyserial), alternative to --launch')
    ap.add_argument('--timeout', type=int, default=900)
    ap.add_argument('--poweroff', action='store_true')
    ap.add_argument('--password', default='')
    ap.add_argument('--check', action='append', default=[], help='NAME:CMD, repeatable')
    ap.add_argument('--host-cmd', action='append', default=[], help='NAME:CMD, repeatable, runs on host after checks')
    ap.add_argument('--logfile', default=None)
    args = ap.parse_args()

    if not args.launch and not args.port:
        print('BOOT-VALIDATION: FAIL (no --launch or --port given)')
        sys.exit(2)

    ts = int(time.time())
    logpath = args.logfile or f'/tmp/boot-validate-{ts}.log'
    logfile = open(logpath, 'wb')

    if args.port:
        print('BOOT-VALIDATION: FAIL (--port/pyserial path not implemented in this rebuild)')
        sys.exit(2)

    con = Console(args.launch, logfile)
    deadline = time.time() + args.timeout
    verdict = None
    fail_reason = ''
    check_results = []
    host_results = []

    try:
        idx = con.expect([LOGIN_RE], timeout=max(10, deadline - time.time()))
        if idx is None:
            verdict, fail_reason = 'FAIL', 'no login prompt seen within timeout'
        else:
            con.sendline('root')
            idx2 = con.expect([PASSWORD_RE, PROMPT_RE], timeout=30)
            if idx2 == 0:
                con.sendline(args.password)
            # nudge and wait for a real, settled prompt (skill's documented gotcha:
            # commands sent right after login can be swallowed during session setup)
            time.sleep(2)
            con.sendline('')
            ridx = con.expect([PROMPT_RE], timeout=30)
            if ridx is None:
                verdict, fail_reason = 'FAIL', 'no shell prompt after login'
            else:
                con.drain(1.0)

                def run_marked(cmd, timeout):
                    """Like run_check but returns the captured text directly --
                    a generic prompt regex false-matches on stray '#'/'$' bytes
                    inside command output (e.g. a kernel version string like
                    '#1 SMP'), so every command here brackets its output with
                    a unique marker instead of waiting for the prompt. See
                    Console.expect_bare_line for why a bare-own-line match is
                    used instead of counting marker occurrences."""
                    marker = f'BVCMD-{time.time_ns()}'
                    con.buf = b''
                    con.sendline(f'{cmd}; echo {marker}')
                    span = con.expect_bare_line(marker, timeout)
                    if span is None:
                        con.buf = b''
                        return None
                    out = con.buf[:span[0]].decode('utf-8', 'replace')
                    con.buf = b''
                    idx = out.rfind(cmd)
                    if idx >= 0:
                        nl = out.find('\n', idx)
                        out = out[nl + 1:] if nl >= 0 else ''
                    return out.strip('\r\n').strip()

                uname_out = run_marked('uname -a', 15) or ''
                osrelease_out = run_marked('cat /etc/os-release', 15) or ''
                sysstate = run_marked('systemctl is-system-running 2>&1', 30) or ''

                print('--- uname -a ---')
                print(uname_out)
                print('--- /etc/os-release ---')
                print(osrelease_out)
                print('--- systemctl is-system-running ---')
                print(sysstate)

                state = sysstate.strip().splitlines()[0].strip() if sysstate.strip() else ''
                if not uname_out:
                    verdict, fail_reason = 'FAIL', 'empty uname'
                elif 'running' in state:
                    verdict = 'PASS'
                elif state in ('starting', 'degraded'):
                    verdict = 'PASS(warn)'
                    fail_reason = f'systemctl is-system-running: {state}'
                elif 'command not found' in sysstate.lower() or 'No such file' in sysstate:
                    verdict = 'PASS(warn)'
                    fail_reason = 'no systemd on this image'
                else:
                    verdict = 'PASS(warn)'
                    fail_reason = f'unrecognized systemctl state: {state!r}'

                for c in args.check:
                    name, _, cmd = c.partition(':')
                    run_check(con, name, cmd, timeout=60, results=check_results)

                bvfail = [r for r in check_results if 'BV-FAIL:' in r[2] or r[1] == 'TIMED OUT']
                if bvfail:
                    verdict = 'FAIL'
                    fail_reason = '; '.join(f'{n}: {("TIMED OUT" if s=="TIMED OUT" else "BV-FAIL")}' for n, s, _ in bvfail)

                for hc in args.host_cmd:
                    name, _, cmd = hc.partition(':')
                    run_host_cmd(name, cmd, host_results)

                if args.poweroff:
                    con.sendline('poweroff')
                    con.drain(10)
    finally:
        con.terminate()
        logfile.close()

    if verdict is None:
        verdict, fail_reason = 'FAIL', 'unknown error'

    line = f'BOOT-VALIDATION: {verdict}'
    if fail_reason:
        line += f' ({fail_reason})'
    print(line)
    print(f'log: {logpath}')
    sys.exit(0 if verdict != 'FAIL' else 1)


if __name__ == '__main__':
    main()
