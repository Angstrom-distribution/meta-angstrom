DESCRIPTION = "Task packages for the Angstrom distribution"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

PR = "r32"

PACKAGE_ARCH = "${MACHINE_ARCH}"

inherit packagegroup

RDEPENDS_${PN} = "\
    gpe-edit \
    gpe-gallery \
    gpe-calculator \
    gpe-clock \
    gpe-plucker \
    gpe-terminal \
    gpe-watch \
    gpe-what \
    matchbox-panel-hacks \
    ${@bb.utils.contains("COMBINED_FEATURES", "wifi", "gpe-aerial", "",d)} \
    gpe-soundbite \
    ${@bb.utils.contains("MACHINE_FEATURES", "touchscreen", "rosetta", "",d)} \
    gpe-scap \
    gpe-windowlist"

