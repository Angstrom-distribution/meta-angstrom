SUMMARY = "Gummiboot is a simple UEFI boot manager which executes configured EFI images."
HOMEPAGE = "http://freedesktop.org/wiki/Software/gummiboot"

LICENSE = "LGPLv2.1"
LIC_FILES_CHKSUM = "file://LICENSE;md5=4fbd65380cdd255951079008b364516c"

DEPENDS = "gnu-efi util-linux"

inherit autotools
inherit deploy

PV = "45"
SRCREV = "eb3daf2ca4cb1657cf1f780957485d690a552bf6"
SRC_URI = "git://anongit.freedesktop.org/gummiboot"

S = "${WORKDIR}/git"

EXTRA_OECONF = "--disable-biostest --with-efi-includedir=${STAGING_INCDIR} \
                --with-efi-ldsdir=${STAGING_LIBDIR} \
                --with-efi-libdir=${STAGING_LIBDIR} \
                --disable-manpages"

do_deploy () {
        install ${S}/gummiboot*.efi ${DEPLOYDIR}/
}
addtask deploy before do_build after do_compile
