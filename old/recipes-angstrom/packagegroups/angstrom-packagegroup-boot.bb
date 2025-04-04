DESCRIPTION = "Basic packagegroup to get an Angstrom powered device booting"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PR = "r5"

# packages which content depend on MACHINE_FEATURES need to be MACHINE_ARCH
#
PACKAGE_ARCH = "${MACHINE_ARCH}"

inherit packagegroup

DEPENDS = "packagegroup-boot"

#
# minimal set of packages - needed to boot
#
RDEPENDS_${PN} = "\
	packagegroup-boot \
	angstrom-version \
    "

