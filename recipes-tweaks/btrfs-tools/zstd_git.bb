SUMMARY = "Zstandard - Fast real-time compression algorithm http://www.zstd.net"

LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=39bba7d2cf0ba1036f2a6e2be52fe3f0"

PV = "1.3.8+git${SRCPV}"
SRCREV = "470344d33e1d52a2ada75d278466da8d4ee2faf6"
SRC_URI = "git://github.com/facebook/zstd.git;protocol=https;branch=master"

S = "${WORKDIR}/git"

inherit cmake

OECMAKE_SOURCEPATH = "${S}/build/cmake"

BBCLASSEXTEND = "native"
