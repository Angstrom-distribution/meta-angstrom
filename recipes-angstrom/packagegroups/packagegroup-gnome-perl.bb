DESCRIPTION = "Task for images with perl"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r13"

inherit packagegroup

# for backwards compatibility
RPROVIDES_${PN} += "packagegroup-gnome3-perl"

RDEPENDS_${PN} = " \
  perl \
  libxml-parser-perl \
"
