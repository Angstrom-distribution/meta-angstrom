FILESEXTRAPATHS_prepend := "${THISDIR}/angstrom:"

SRC_URI += "file://settings"

do_install_append() {
	install -d ${D}${localstatedir}/lib/connman/
	install -m 0644 ${WORKDIR}/settings ${D}${localstatedir}/lib/connman/
}

PACKAGES =+ "${PN}-angstrom-settings"

FILES_${PN}-angstrom-settings = "${localstatedir}/lib/connman/settings"
RDEPENDS_${PN}-angstrom-settings = "${PN}"
LICENSE_FLAGS_${PN}-angstrom-settings = "non-commercial"
