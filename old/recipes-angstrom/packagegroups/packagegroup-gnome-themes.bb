DESCRIPTION = "Task for GNOME themes"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PR = "r13"

inherit packagegroup

# for backwards compatibility
RPROVIDES_${PN} += "packagegroup-gnome3-themes"

RDEPENDS_${PN} = " \
  angstrom-gnome-icon-theme-enable \
  gnome-icon-theme \
  gnome-themes \
  gnome-theme-crux \
  gnome-theme-highcontrast \
  gnome-theme-highcontrastinverse \
  gnome-theme-highcontrastlargeprint \
  gnome-theme-highcontrastlargeprintinverse \
  gnome-theme-largeprint \
  gnome-theme-mist \
  gtk-engine-clearlooks \
  gtk-engine-crux-engine \
  gtk-engine-glide \
  gtk-engine-hcengine \
  gtk-engine-thinice \
  gtk-engine-redmond95 \
  gtk-theme-clearlooks \
  gtk-theme-crux \
  gtk-theme-mist \
  gtk-theme-thinice \
  gtk-theme-redmond \
  hicolor-icon-theme \
"
