DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

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
"

RRECOMMENDS_${PN} = "ofono"
