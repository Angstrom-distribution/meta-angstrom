FILESEXTRAPATHS:prepend := "${THISDIR}/${BPN}:"

# armv5e (and other archs lacking hardware 8-byte atomics) fail linking the
# main fio binary: "undefined reference to __atomic_store_8/__atomic_load_8".
# GCC provides the libatomic fallback but fio's Makefile doesn't link it.
# The fix goes in LIBS via the Makefile itself, not TARGET_LDFLAGS from
# local.conf -- fio's own link rule is "$(CC) $(LDFLAGS) -o $@ $(FIO_OBJS)
# $(LIBS)", so anything added to LDFLAGS lands before the object files that
# need the symbols and the linker never resolves against it (confirmed
# live). A command-line "LIBS=-latomic" override was tried and also
# confirmed live to silently break t/fio-genzipf and t/fio-dedupe's
# existing -lm/-lz linkage -- appending inside the Makefile itself is the
# safe, additive way. Confirmed live building fio for qemuarmv5
# (2026-08-17).
SRC_URI:append = " file://0001-Makefile-link-latomic-for-archs-lacking-hardware-ato.patch"
