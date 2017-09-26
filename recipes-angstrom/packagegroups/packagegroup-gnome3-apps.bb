DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PR = "r6"

inherit packagegroup

RDEPENDS_${PN} = " \
  x11vnc \
  matchbox-terminal \
  epiphany \
"
