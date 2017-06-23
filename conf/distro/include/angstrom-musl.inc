# musl:

# The things uclibc can provide.  We default to wanting uclibc to provide them.
# libc settings
DISTRO_FEATURES += "${@['', ' nls'][d.getVar('ENABLE_BINARY_LOCALE_GENERATION', 1) == '1']}"
# FIXME: We enable IPv4 per default to avoid alot of breakage.
# FIXME: Figure out an acceptable way to negotiate either
# FIXME: desired features or hard requirements, either top-down or bottom-up.
DISTRO_FEATURES += " ipv4"

USE_NLS_glib-2.0 = "yes"
USE_NLS_glib-2.0-native = "yes"
USE_NLS_gcc-cross = "no"

TARGET_LINK_HASH_STYLE = "${@['-Wl,--hash-style=gnu',''][d.getVar('TARGET_ARCH',1) in ['mips', 'mipsel', 'mips64', 'mips64el', 'avr32']]}"
