DESCRIPTION = "Task for a full GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PR = "r13"

inherit packagegroup

RDEPENDS_${PN} = " \
  packagegroup-gnome-fonts \
  packagegroup-gnome \
  packagegroup-gnome-gstreamer \
  packagegroup-gnome-perl \
  packagegroup-gnome-themes \
  packagegroup-gnome-xserver-base \
  angstrom-skel-gnome2 \
"

RRECOMMENDS_${PN} = " \
   packagegroup-core-x11-xserver \
"

