DESCRIPTION = "Task for a full GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

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

