DEPENDS += "libxml2 python-lxml"

FILESEXTRAPATHS_prepend := "${THISDIR}/mesa:"
SRC_URI += "file://mesa-fstat.patch"


GALLIUMDRIVERS_append_armv7a = ",freedreno"
GALLIUMDRIVERS_append_aarch64 = ",freedreno"

PACKAGECONFIG_append_armv7a = " gallium \
                         ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xa', '', d)} \
                       "
PACKAGECONFIG_append_aarch64 = " gallium \
                         ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xa', '', d)} \
                       "

