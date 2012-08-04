DESCRIPTION = "Task for a full GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r13"

inherit task

# Most of these tasks are arch independant
PACKAGE_ARCH = "all"

PACKAGES += "task-gnome-xserver-base "

RDEPENDS_task-gnome-xserver-base = " \
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

RDEPENDS_${PN} = " \
  task-gnome-fonts \
  task-gnome \
  task-gnome-gstreamer \
  task-gnome-perl \
  task-gnome-themes \
  task-gnome-xserver-base \
"

RRECOMMENDS_${PN} = " \
   task-xserver \
"

