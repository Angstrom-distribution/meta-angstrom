DESCRIPTION = "Task packages for the Angstrom distribution"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

PR = "r32"

inherit packagegroup

PACKAGE_ARCH = "${MACHINE_ARCH}"

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
    ${@base_contains("COMBINED_FEATURES", "wifi", "gpe-aerial", "",d)} \
    gpe-soundbite \
    ${@base_contains("MACHINE_FEATURES", "touchscreen", "rosetta", "",d)} \
    gpe-scap \
    gpe-windowlist"

