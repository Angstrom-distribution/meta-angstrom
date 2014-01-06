DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

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
