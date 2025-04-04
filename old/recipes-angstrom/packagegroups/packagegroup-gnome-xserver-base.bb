DESCRIPTION = "Task for a full GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

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
