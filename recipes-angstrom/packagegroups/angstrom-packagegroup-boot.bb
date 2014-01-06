DESCRIPTION = "Basic packagegroup to get an Angstrom powered device booting"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

PR = "r5"

inherit packagegroup

# packages which content depend on MACHINE_FEATURES need to be MACHINE_ARCH
#
PACKAGE_ARCH = "${MACHINE_ARCH}"

DEPENDS = "packagegroup-boot"

#
# minimal set of packages - needed to boot
#
RDEPENDS_${PN} = "\
	packagegroup-boot \
	angstrom-version \
    "

