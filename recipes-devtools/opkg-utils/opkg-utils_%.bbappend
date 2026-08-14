# Feed-server fixes for opkg-make-index/opkg.py, verified against upstream
# SRCREV 68a969f0e867ace0d94faf8ebe7c7bb67f59d386 (the revision oe-core's
# opkg-utils_0.7.0.bb builds):
#  0001: don't rescan morgue/ (and -L locales) - a morgue copy could
#        displace the live package and publish Filename: morgue/...
#  0002: skip corrupt packages / tolerate corrupt Packages.stamps instead
#        of crashing the whole index run; write stamps atomically
#  0003: read data.tar.zst (oe-core builds ipks with opkg-build -Z zstd,
#        so -l Packages.filelist was silently empty); keep stdout clean
FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI += " \
    file://0001-opkg-make-index-never-rescan-the-morgue-or-locales-d.patch \
    file://0002-opkg-make-index-survive-corrupt-packages-and-stamp-f.patch \
    file://0003-opkg.py-support-data.tar.zst-keep-stdout-clean.patch \
"
