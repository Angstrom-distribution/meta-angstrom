SUMMARY = "ZNC, an advanced IRC bouncer"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3b83ef96387f14655fc854ddc3c6bd57"

DEPENDS = "openssl zlib icu"

PV = "1.4+git"

SRCREV = "60367fb2bf173e0002dde4cf7dac0d00dcf5502d"
SRC_URI = "git://github.com/znc/znc.git \
          "

S = "${WORKDIR}/git"

inherit autotools-brokensep pkgconfig

# ZNC has a custom autogen.sh that states that this command is needed *and* expected to fail
do_configure_prepend() {
    automake --add-missing || true
}
