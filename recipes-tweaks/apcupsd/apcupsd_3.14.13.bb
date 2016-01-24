SUMMARY = "Apcupsd a daemon for controlling APC UPSes"

LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=c12853cc7fdf20d17b4fddefd26b7802"

SRC_URI = "${SOURCEFORGE_MIRROR}/project/apcupsd/apcupsd%20-%20Stable/3.14.13/apcupsd-${PV}.tar.gz"
SRC_URI[md5sum] = "c291d9d3923b4d9c0e600b755ad4f489"
SRC_URI[sha256sum] = "57ecbde01d0448bf8c4dbfe0ad016724ae66ab98adf2de955bf2be553c5d03f9"

inherit autotools-brokensep

LD = "${CXX}"

EXTRA_OECONF = "--without-x \
                --enable-usb \
                --with-distname=${DISTRO}"

do_configure() {
    export topdir=${S}
    cp -a ${S}/autoconf/configure.in ${S}

    if ! [ -d ${S}/platforms/${DISTRO} ] ; then
        cp -a ${S}/platforms/unknown ${S}/platforms/${DISTRO} 
    fi

    gnu-configize --force
    # install --help says '-c' is an ignored option, but it turns out that the argument to -c isn't ignored, so drop the complete '-c path/to/strip' line
    sed -i -e 's:$(INSTALL_PROGRAM) $(STRIP):$(INSTALL_PROGRAM):g' ${S}/autoconf/targets.mak
    # Searching in host dirs triggers the QA checks
    sed -i -e 's:-I/usr/local/include::g' -e 's:-L/usr/local/lib64::g' -e 's:-L/usr/local/lib::g' ${S}/configure

    # m4 macros are missing, using autotools_do_configure leads to linking errors with gethostname_re
    oe_runconf
}

# 'col' nowadays parses LC_CTYPE and will error out with:
#     col: Invalid or incomplete multibyte or wide character
# with LANG=C. Set LC_CTYPE to force UTF-8 char handling since mandb requires it.
export LANG = "en_US.UTF-8"
export LC_CTYPE = "en_US.UTF-8"

do_compile_prepend() {
    env
}

do_install_append() {
    rm ${D}${datadir}/hal -rf
}


