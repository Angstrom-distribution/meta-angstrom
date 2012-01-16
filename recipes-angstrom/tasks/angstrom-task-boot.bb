DESCRIPTION = "Basic task to get an Angstrom powered device booting"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r4"

inherit task

# packages which content depend on MACHINE_FEATURES need to be MACHINE_ARCH
#
PACKAGE_ARCH = "${MACHINE_ARCH}"

DEPENDS = "task-boot"

#
# minimal set of packages - needed to boot
#
RDEPENDS_${PN} = "\
	task-boot \
	angstrom-version \
    "

