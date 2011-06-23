DESCRIPTION = "Basic task to get a device booting"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r1"

inherit task

# packages which content depend on MACHINE_FEATURES need to be MACHINE_ARCH
#
PACKAGE_ARCH = "${MACHINE_ARCH}"

#
# those ones can be set in machine config to supply packages needed to get machine booting
#
MACHINE_ESSENTIAL_EXTRA_RDEPENDS ?= ""
MACHINE_ESSENTIAL_EXTRA_RRECOMMENDS ?= ""

DISTRO_UPDATE_ALTERNATIVES ?= "${@base_conditional("ONLINE_PACKAGE_MANAGEMENT", "none", "", "update-alternatives-cworth", d)}"
OPKGCONFIG ?= "${@base_conditional("ONLINE_PACKAGE_MANAGEMENT", "none", "", "opkg opkg-config-base angstrom-feed-configs", d)}"

# Make sure we build the kernel
DEPENDS = "virtual/kernel"

#
# minimal set of packages - needed to boot
#
RDEPENDS_${PN} = "\
    angstrom-version \
    base-files \
    base-passwd \
    busybox \
    netbase \
    ${DISTRO_UPDATE_ALTERNATIVES} \
    ${OPKGCONFIG} \
    ${MACHINE_ESSENTIAL_EXTRA_RDEPENDS} \
    "

RRECOMMENDS_${PN} = "\
    kernel \
    ${MACHINE_ESSENTIAL_EXTRA_RRECOMMENDS} \
    "
