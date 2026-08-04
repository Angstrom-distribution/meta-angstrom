# node+cctest are two independent gyp targets; their final links can run
# concurrently regardless of PARALLEL_MAKE (proven -- still OOM'd at -j4).
# Both are 2GB+ statically-linked V8 binaries with debug info; only bfd ld
# is available (no gold/lld in this sysroot) and its default symbol-table
# handling needs far more RAM than the mackas VM's 42G for two concurrent
# links of this size. --no-keep-memory/--reduce-memory-overheads trade
# link speed for peak RAM, which is the actual fix.
LDFLAGS:append = " -Wl,--no-keep-memory -Wl,--reduce-memory-overheads"
