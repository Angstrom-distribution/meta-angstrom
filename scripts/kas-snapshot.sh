#!/bin/sh
# Emit a fully-resolved snapshot of the kas composition for one build.
# Usage: scripts/kas-snapshot.sh <kas-file-list> <outfile>
#
# The dump goes to a temporary file and is only moved into place once it has
# been checked. A plain '> outfile' creates (and truncates) the output before
# kas-container runs, so a failed or half-written dump would leave behind
# something that still looks like a snapshot -- and a snapshot is only useful
# if it can be trusted to be the whole, resolved composition.
#
# Prints exactly one KAS-SNAPSHOT: SUCCESS / KAS-SNAPSHOT: FAILED line.
# Exit codes: 0 success, 1 bad usage or a failed/unusable dump (the outfile
# is then left exactly as it was).
set -eu

DONE=0
TMPOUT=""

fail() {
	echo "FATAL: $*" >&2
	DONE=1
	echo "KAS-SNAPSHOT: FAILED $*" >&2
	exit 1
}

# Backstop for anything set -eu kills outside a fail() guard (or a signal):
# the run must still end with a sentinel, never a silent nonzero exit.
cleanup() {
	rc=$?
	if [ -n "$TMPOUT" ]; then
		rm -f "$TMPOUT"
	fi
	if [ "$rc" -ne 0 ] && [ "$DONE" -eq 0 ]; then
		echo "KAS-SNAPSHOT: FAILED unexpected exit (rc=$rc)" >&2
	fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

[ $# -eq 2 ] || fail "usage: $0 <kas-file-list> <outfile> (got $# argument(s))"

KAS_FILES="$1"
OUTFILE="$2"
OUTDIR=$(dirname "$OUTFILE")

[ -d "$OUTDIR" ] || fail "output directory does not exist: $OUTDIR"

# Absolutize OUTFILE before changing directories, so all later uses
# refer to the caller's intended path regardless of cwd after the cd
case "$OUTFILE" in
	/*) ;;
	*) OUTFILE="$PWD/$OUTFILE" ;;
esac

command -v kas-container >/dev/null 2>&1 || \
	fail "kas-container is not in PATH; source ~/oe/env.sh first"

WORKDIR="$HOME/oe/work"
[ -d "$WORKDIR" ] || fail "$WORKDIR does not exist"
cd "$WORKDIR" || fail "cannot enter $WORKDIR"

# Every entry of the colon-separated list must exist relative to the work
# directory: kas would otherwise resolve a different composition (or none)
# than the one that was asked for.
OLDIFS="$IFS"
IFS=:
for frag in $KAS_FILES; do
	IFS="$OLDIFS"
	[ -n "$frag" ] || continue
	[ -f "$frag" ] || fail "kas fragment not found: $frag (relative to $WORKDIR)"
	IFS=:
done
IFS="$OLDIFS"

TMPOUT="$OUTFILE.tmp.$$"

RC=0
# --skip repos_checkout --skip repos_apply_patches is mandatory on every
# kas-container invocation in this project, no exceptions: an un-skipped run
# has hard-reset meta-dominion and meta-qcom-3rdparty before by resetting a
# sibling layer to its pinned ref, discarding local-only commits with no
# warning. A snapshot dump is exactly as capable of doing that as a real
# build is -- --resolve-refs/--resolve-local still resolve the composition
# from the current on-disk state of every repo, so the snapshot is no less
# accurate for having skipped a destructive checkout it never needed.
kas-container dump --skip repos_checkout --skip repos_apply_patches \
	--resolve-refs --resolve-local --resolve-env "$KAS_FILES" \
	> "$TMPOUT" || RC=$?
[ "$RC" -eq 0 ] || fail "kas-container dump exited $RC; $OUTFILE left untouched"

# A dump that stops halfway is still valid YAML, so check for the parts that
# make it a usable snapshot rather than just for "some output".
[ -s "$TMPOUT" ] || fail "kas-container dump wrote an empty snapshot"
grep -q '^header:' "$TMPOUT" || \
	fail "the dump has no 'header:' key, it is not a kas file"
grep -q '^repos:' "$TMPOUT" || \
	fail "the dump has no 'repos:' key, the composition resolved to nothing"
# --resolve-refs turns every branch/tag into a commit id: no commit id means
# the snapshot does not actually pin anything.
COMMITS=$(grep -c -E '^ +commit: +[0-9a-f]{40}$' "$TMPOUT" || true)
[ "$COMMITS" -gt 0 ] || \
	fail "the dump pins no commit ids, --resolve-refs did not resolve anything"

mv "$TMPOUT" "$OUTFILE" || fail "cannot move the snapshot into place at $OUTFILE"
TMPOUT=""

[ -s "$OUTFILE" ] || fail "$OUTFILE is empty after being written"
BYTES=$(wc -c < "$OUTFILE" | tr -d ' ')
DONE=1
echo "KAS-SNAPSHOT: SUCCESS $OUTFILE ($BYTES bytes, $COMMITS pinned commit(s)) from $KAS_FILES"
