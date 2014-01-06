DESCRIPTION = "Task packages for the Angstrom distribution"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

PR = "r33"

inherit packagegroup

RDEPENDS_${PN} = "\
    nmap \
    ettercap-ng \
    stunnel \
    curl \
    prismstumbler \
    kismet \
    hydra \
    miniclipboard"
