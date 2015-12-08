DEPENDS += "libxml2 python-lxml"

FILESEXTRAPATHS_prepend := "${THISDIR}/mesa:"
SRC_URI_append = " file://mesa-fstat.patch"
