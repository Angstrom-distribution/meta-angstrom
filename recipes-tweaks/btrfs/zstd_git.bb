SUMMARY = "Zstandard - Fast real-time compression algorithm http://www.zstd.net"

LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=39bba7d2cf0ba1036f2a6e2be52fe3f0"

PV = "1.3.3+git${SRCPV}"
SRCREV = "f3a8bd553a865c59f1bd6e1f68bf182cf75a8f00"
SRC_URI = "git://github.com/facebook/zstd.git;protocol=https;branch=master"

S = "${WORKDIR}/git"

inherit cmake

EXTRA_OECMAKE = "-DTHREADS_PTHREAD_ARG=2"

OECMAKE_SOURCEPATH = "${S}/build/cmake"

BBCLASSEXTEND = "native"
