DESCRIPTION = "Task for a full GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r13"

inherit packagegroup

RDEPENDS_${PN} = " \
  packagegroup-gnome-fonts \
  packagegroup-gnome \
  packagegroup-gnome-gstreamer \
  packagegroup-gnome-perl \
  packagegroup-gnome-themes \
  packagegroup-gnome-xserver-base \
"

RRECOMMENDS_${PN} = " \
   packagegroup-core-x11-xserver \
"

