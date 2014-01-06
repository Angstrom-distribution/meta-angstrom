DESCRIPTION = "Task for a full GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

PR = "r13"

inherit packagegroup

# for backwards compatibility
RPROVIDES_${PN} += "packagegroup-gnome3-xserver-base"

RDEPENDS_${PN} = " \
  dbus-x11 \
  iso-codes \
  mime-support \
  xauth \
  xdg-utils \
  xhost \
  xinetd \
  xinit \
  xrandr \
  xrdb \
  xset \
  xvinfo \
"
