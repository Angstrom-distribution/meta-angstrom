DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

PR = "r6"

inherit packagegroup

RDEPENDS_${PN} = " \
  x11vnc \
  matchbox-terminal \
  epiphany \
"
