DESCRIPTION = "Task packages for the Angstrom distribution"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

PR = "r36"

inherit packagegroup

RDEPENDS_${PN} = "\
    matchbox-panel-manager \
    mboxkbd-layouts-gui \
    gpe-su \
    gpe-conf \
    gpe-shield \
    gpe-taskmanager \
    keylaunch \
    minilite \
    minimix \
    xmonobut"
