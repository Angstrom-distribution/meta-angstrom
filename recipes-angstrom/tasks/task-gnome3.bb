DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r6"

inherit task

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
