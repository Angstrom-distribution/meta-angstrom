# library/coretests/ is rustc's own internal test/benchmark suite, not part
# of the runtime stdlib source rust-src-lib exists to ship (that's for
# -Zbuild-std / IDE jump-to-source). coretests/benches/ascii.rs has a
# literal build path baked in, which trips the buildpaths QA check on every
# machine (confirmed live, package sweep 2026-08-16). Not fixed upstream as
# of oe-core master 07a342aa80 (2026-08-17): do_install's plain "cp -r
# ${S}/library ..." still ships coretests/ unconditionally, only *.sh files
# are excluded. do_install itself just calls rust_do_install, so this fires
# after whichever class-native/class-nativesdk/class-target variant ran.
do_install:append() {
    rm -rf ${D}${libdir}/rustlib/src/rust/library/coretests
}
