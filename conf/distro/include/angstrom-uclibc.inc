# uclibc:

PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}binutils = "binutils-cross"
PREFERRED_PROVIDER_linux-libc-headers = "linux-libc-headers"

# The things uclibc can provide.  We default to wanting uclibc to provide them.
PREFERRED_PROVIDER_virtual/libiconv = "mini-iconv"
PREFERRED_PROVIDER_virtual/libintl = "proxy-libintl"
PREFERRED_PROVIDER_virtual/gettext = "proxy-libintl"
#fix some iconv issues, needs to be adjusted when doing uclibc builds
PREFERRED_PROVIDER_virtual/libintl_avr32 = "proxy-libintl"
PREFERRED_PROVIDER_virtual/gettext_avr32 = "proxy-libintl"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}libc-initial = "uclibc-initial"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}libc-for-gcc = "uclibc"
PREFERRED_PROVIDER_virtual/libiconv-nativesdk = "nativesdk-eglibc"
PREFERRED_PROVIDER_virtual/libc-nativesdk = "nativesdk-eglibc"
PREFERRED_PROVIDER_virtual/${SDK_PREFIX}libc-initial-nativesdk  = "nativesdk-eglibc-initial"
PREFERRED_PROVIDER_virtual/${SDK_PREFIX}libc-nativesdk  = "nativesdk-eglibc"
PREFERRED_PROVIDER_virtual/${SDK_PREFIX}libc-for-gcc-nativesdk  = "nativesdk-eglibc"
# libc settings
DISTRO_FEATURES += "${@['', ' nls'][bb.data.getVar('ENABLE_BINARY_LOCALE_GENERATION', d, 1) == '1']}"
# FIXME: We enable IPv4 per default to avoid alot of breakage.
# FIXME: Figure out an acceptable way to negotiate either
# FIXME: desired features or hard requirements, either top-down or bottom-up.
DISTRO_FEATURES += " ipv4"

USE_NLS_glib-2.0 = "yes"
USE_NLS_glib-2.0-native = "yes"
USE_NLS_gcc-cross = "no"

TARGET_OS_bfin = "uclinux-uclibc"

#Gcc will die with 'internal consistency error when using the above optimizations
#with gcc-4.2.1-atmel.1.0.3 (and probably most other avr32 gcc ports).
#However, some packages require optimizations to compile (e.g. libmad).
#It appears the guilty optimization is "-frename-registers", leaving that one 
#out allows the build to proceed normally.  -fexpensive-optimizations may be OK,
#it was removed while debugging an issue that ultimately turned out to be due
#to the ICE fixed by gcc-pr32889.patch.  It needs to be tested again. 
#Note that this testing was done without the gcc-pr32889.patch.
FULL_OPTIMIZATION_avr32 = "-Os -pipe ${DEBUG_FLAGS}"

TARGET_LINK_HASH_STYLE = "${@['-Wl,--hash-style=gnu',''][bb.data.getVar('TARGET_ARCH',d,1) in ['mips', 'mipsel', 'mips64', 'mips64el', 'avr32']]}"
