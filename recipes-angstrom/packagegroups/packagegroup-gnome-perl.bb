DESCRIPTION = "Task for images with perl"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

PR = "r13"

inherit packagegroup

# for backwards compatibility
RPROVIDES_${PN} += "packagegroup-gnome3-perl"

RDEPENDS_${PN} = " \
  perl \
  libxml-parser-perl \
"
