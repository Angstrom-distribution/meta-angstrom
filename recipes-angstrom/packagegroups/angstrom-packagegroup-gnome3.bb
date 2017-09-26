DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PR = "r6"

inherit packagegroup

RDEPENDS_${PN} = " \
  packagegroup-gnome3-fonts \
  packagegroup-gnome3 \
  packagegroup-gnome3-gstreamer \
  packagegroup-gnome3-perl \
  packagegroup-gnome3-themes \
  packagegroup-gnome3-xserver-base \
"

RRECOMMENDS_${PN} = " \
   packagegroup-core-x11-xserver \
"

