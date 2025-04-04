DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PR = "r15"

inherit packagegroup

RDEPENDS_${PN} = " \
  gsettings-desktop-schemas gnome-common-schemas \
  gnome-settings-daemon gnome-control-center \
  gnome-keyring \
  angstrom-gdm-autologin-hack \
  gnome-power-manager \
  gnome-bluetooth \
  gnome-panel \
  gtk-engine-clearlooks gtk-theme-clearlooks angstrom-clearlooks-theme-enable \
  upower udisks \
  gnome-disk-utility \
  gnome-system-monitor \
  nautilus \
  gpe-scap \
  bash \
  tzdata \
  angstrom-skel-gnome2 \
"

RRECOMMENDS_${PN} = "ofono"
