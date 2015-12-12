DEPENDS += "libxml2 python-lxml"

FILESEXTRAPATHS_prepend := "${THISDIR}/mesa:"
SRC_URI += "file://mesa-fstat.patch"
