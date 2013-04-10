DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r13"

inherit packagegroup

RDEPENDS_${PN} = " \
  gsettings-desktop-schemas \
  gnome-settings-daemon gnome-control-center \
  gnome-keyring \
  angstrom-gdm-autologin-hack \
  gnome-power-manager \
  gnome-bluetooth \
  gnome-panel gnome-applets \
  gtk-engine-clearlooks gtk-theme-clearlooks angstrom-clearlooks-theme-enable \
  upower udisks \
  gnome-disk-utility \
  gnome-system-monitor \
  nautilus \
  gpe-scap \
  bash \
  tzdata \
"

RRECOMMENDS_${PN} = "ofono"
