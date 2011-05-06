# glibc:
require conf/distro/include/glibc-${TOOLCHAIN_TYPE}.inc

ANGSTROM_BLACKLIST_pn-libiconv = "the glibc provided iconv library is used"

TARGET_OS = "linux"
TARGET_OS .= "${@['','-gnueabi'][bb.data.getVar('TARGET_ARCH',d,1) in ['arm', 'armeb']]}"

#mess with compiler flags to use -Os instead of -O2
#Please see http://free-electrons.com/doc/embedded_linux_optimizations/img47.html for some more info
# perl has some problems, see http://bugs.openembedded.net/show_bug.cgi?id=1616
# [23:00] oxo: glibc (any version) on sparc does not like (repeat me) "-Os"

FULL_OPTIMIZATION_pn-perl = "-O1"
FULL_OPTIMIZATION_ep9312 = "-O -fno-signed-zeros -pipe ${DEBUG_FLAGS}"

# This is only used for -native packages
BUILD_OPTIMIZATION_pn-perl-native = "-O1"

TARGET_LINK_HASH_STYLE = "${@['-Wl,--hash-style=gnu',''][bb.data.getVar('TARGET_ARCH',d,1) in ['mips', 'mipsel', 'mips64', 'mips64el']]}"

# Disable -Wl,as-needed for now till all the test come back OK
#require conf/distro/include/angstrom-ldflags.inc

CXXFLAGS += "-fvisibility-inlines-hidden"

LIBC_DEPENDENCIES = "\
    libsegfault \
    glibc \
    glibc-dbg \
    glibc-dev \
    glibc-utils \
    glibc-thread-db \
    glibc-localedata-i18n \
    glibc-gconv-ibm850 \
    glibc-gconv-cp1252 \
    glibc-gconv-iso8859-1 \
    glibc-gconv-iso8859-15 \
    locale-base-en-gb \
    "

