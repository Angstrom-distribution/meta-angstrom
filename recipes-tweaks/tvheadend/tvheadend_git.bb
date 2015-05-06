SUMMARY = "Tvheadend TV streaming server"
HOMEPAGE = "https://www.lonelycoder.com/redmine/projects/tvheadend"

inherit pythonnative

DEPENDS = "dvb-apps avahi zlib openssl python-native libav"

LICENSE = "GPLv3+"
LIC_FILES_CHKSUM = "file://LICENSE.md;md5=9cae5acac2e9ee2fc3aec01ac88ce5db"

SRC_URI = "git://github.com/tvheadend/tvheadend.git;name=tvh \
           git://linuxtv.org/dtv-scan-tables.git;name=dvb;destsuffix=git/data/dvb-scan \
           file://0001-Move-tvheadend-specific-LD-CFLAGS-into-a-helper-vari.patch \
"
SRCREV_tvh = "d9ca9404c0cb246ea884193127b86ba59495f054"
SRCREV_dvb = "f2053b34a5ac6f263edc22888b0db4b1fa563fe4"
SRCREV_FORMAT = "tvh"

PV = "3.9.2809+git${SRCPV}"

S = "${WORKDIR}/git"

do_configure() {
    # The fetcher ensures the mux list is up to date
    sed -i -e 's:exit 1:exit 0:g' ${S}/support/getmuxlist

    # Enabling mmx when the compiler supports instead of checking the CFLAGS is a very bad idea
    sed -i '/check_cc_option mmx/d' configure

    ./configure --prefix=${prefix} \
                --libdir=${libdir} \
                --bindir=${bindir} \
                --datadir=${datadir} \
                --arch=${TARGET_ARCH} \
                --disable-bundle
}

do_install() {
    oe_runmake install DESTDIR=${D}
}

FILES_${PN} += "${datadir}/${BPN}"
# builtin archival code chokes on busybox stuff
RDEPENDS_${PN} = "tar bzip2"
