DESCRIPTION = "Task for GNOME applications"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

PR = "r13"

inherit packagegroup

RDEPENDS_${PN} = " \
  x11vnc \
  gnome-terminal \
  epiphany \
  gedit \
  cheese \
  abiword \
  gnumeric \
  gimp \
"
