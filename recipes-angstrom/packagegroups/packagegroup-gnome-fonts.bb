DESCRIPTION = "Task for a GNOME fonts"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r13"

inherit packagegroup

# for backwards compatibility
RPROVIDES_${PN} += "packagegroup-gnome3-fonts"

RDEPENDS_${PN} = " \
  fontconfig fontconfig-utils font-util \
  ttf-liberation-sans \
  ttf-liberation-serif \
  ttf-liberation-mono \
"
