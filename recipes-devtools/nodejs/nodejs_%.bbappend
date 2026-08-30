# node+cctest are two independent gyp targets; their final links can run
# concurrently regardless of PARALLEL_MAKE (proven -- still OOM'd at -j4).
# Both are 2GB+ statically-linked V8 binaries with debug info; only bfd ld
# is available (no gold/lld in this sysroot) and its default symbol-table
# handling needs far more RAM than the mackas VM's 42G for two concurrent
# links of this size. --no-keep-memory/--reduce-memory-overheads trade
# link speed for peak RAM, which is the actual fix.
LDFLAGS:append = " -Wl,--no-keep-memory -Wl,--reduce-memory-overheads"

# Separate problem from the link-time one above: do_compile itself OOM-kills
# cc1plus (confirmed live, package-sweep run 2026-08-16 -- "Killed signal
# terminated program cc1plus" compiling V8/libuv/libnode objects) when too
# many object files compile concurrently under the project-wide -j18.
# Throttle just this recipe's own compile parallelism rather than the whole
# VM's -- targeted, not a global PARALLEL_MAKE/BB_NUMBER_THREADS change.
# -j4 confirmed OOM-free on two machines (pocketbeagle, pocketbeagle2);
# bumped to -j8 to trade some of that safety margin back for speed.
PARALLEL_MAKE:pn-nodejs = "-j 8"
