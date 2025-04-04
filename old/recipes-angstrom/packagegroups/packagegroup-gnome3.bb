DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PR = "r6"

inherit packagegroup

RDEPENDS_${PN} = " \
  gnome-settings-daemon gnome-control-center \
  gnome-keyring \
  gdm-systemd angstrom-gdm-autologin-hack \
  gnome-power-manager3 \
  gnome-bluetooth \
  gnome-panel3 \
  gtk-engine-clearlooks gtk-theme-clearlooks \
  upower udisks \
  gnome-disk-utility \
  nautilus3 \
  gpe-scap \
  bash \
  tzdata \
"
