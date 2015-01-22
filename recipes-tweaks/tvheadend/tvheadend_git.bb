SUMMARY = "Tvheadend TV streaming server"
HOMEPAGE = "https://www.lonelycoder.com/redmine/projects/tvheadend"

DEPENDS = "avahi zlib openssl python-native libav"

LICENSE = "GPLv3+"
LIC_FILES_CHKSUM = "file://LICENSE.md;md5=9cae5acac2e9ee2fc3aec01ac88ce5db"

SRC_URI = "git://github.com/tvheadend/tvheadend.git;name=tvh \
           git://linuxtv.org/dtv-scan-tables.git;name=dvb;destsuffix=git/data/dvb-scan \
           file://0001-Move-tvheadend-specific-LD-CFLAGS-into-a-helper-vari.patch \
"
SRCREV_tvh = "3077ffa4d38ac4ab9a5e3f26a5ab5848da8b546f"
SRCREV_dvb = "b46a22ca4f51a43143ab0618ee1b3b0067fa4fd3"
SRCREV_FORMAT = "tvh"

PV = "3.9.2339+git${SRCPV}"

S = "${WORKDIR}/git"

do_configure() {
    # The fetcher ensures the mux list is up to date
    sed -i -e 's:exit 1:exit 0:g' ${S}/support/getmuxlist

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
