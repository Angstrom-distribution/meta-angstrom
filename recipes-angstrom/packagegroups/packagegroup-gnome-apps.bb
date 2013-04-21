DESCRIPTION = "Task for GNOME applications"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r13"

inherit packagegroup

RDEPENDS_${PN} = " \
  x11vnc \
  xfce4-terminal \
  epiphany \
  gedit \
  cheese \
  abiword \
"
