SUMMARY = "Utilities for managing LZMA compressed files"
HOMEPAGE = "http://tukaani.org/xz/"
SECTION = "base"

# The source includes bits of PD, GPLv2, GPLv3, LGPLv2.1+, but the only file
# which is GPLv3 is an m4 macro which isn't shipped in any of our packages,
# and the LGPL bits are under lib/, which appears to be used for libgnu, which
# appears to be used for DOS builds. So we're left with GPLv2+ and PD.
LICENSE = "GPLv2+ & GPLv3+ & LGPLv2.1+ & PD"
LICENSE_${PN} = "GPLv2+"
LICENSE_${PN}-dev = "GPLv2+"
LICENSE_${PN}-staticdev = "GPLv2+"
LICENSE_${PN}-doc = "GPLv2+"
LICENSE_${PN}-dbg = "GPLv2+"
LICENSE_${PN}-locale = "GPLv2+"
LICENSE_liblzma = "PD"
LICENSE_liblzma-dev = "PD"
LICENSE_liblzma-staticdev = "PD"
LICENSE_liblzma-dbg = "PD"

LIC_FILES_CHKSUM = "file://COPYING;md5=c475b6c7dca236740ace4bba553e8e1c \
                    file://COPYING.GPLv2;md5=b234ee4d69f5fce4486a80fdaf4a4263 \
                    file://COPYING.GPLv3;md5=d32239bcb673463ab874e80d47fae504 \
                    file://COPYING.LGPLv2.1;md5=4fbd65380cdd255951079008b364516c \
                    file://lib/getopt.c;endline=23;md5=2069b0ee710572c03bb3114e4532cd84 "

SRC_URI = "http://tukaani.org/xz/xz-${PV}.tar.gz"
SRC_URI[md5sum] = "be585bdf8672e4406632eda3d819e284"
SRC_URI[sha256sum] = "231ef369982240bb20ed7cffa52bb12a4a297ce6871f480ab85e8a7ba98bf3d6"

inherit autotools gettext

EXTRA_OECONF_x86 = " --enable-assembler=x86"
EXTRA_OECONF_x86-84 = " --enable-assembler=x86_64"

PACKAGES =+ "liblzma liblzma-dev liblzma-staticdev liblzma-dbg"

FILES_liblzma = "${libdir}/liblzma*${SOLIBS}"
FILES_liblzma-dev = "${includedir}/lzma* ${libdir}/liblzma*${SOLIBSDEV} ${libdir}/liblzma.la ${libdir}/pkgconfig/liblzma.pc"
FILES_liblzma-staticdev = "${libdir}/liblzma.a"
FILES_liblzma-dbg = "${libdir}/.debug/liblzma*"

BBCLASSEXTEND = "native nativesdk"

export CONFIG_SHELL="/bin/sh"
