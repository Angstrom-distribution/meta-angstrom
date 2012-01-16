FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://angstrom.repo"

do_install_append() {
	install -d ${D}${sysconfdir}/zypp/repos.d
	install -m 0644 ${WORKDIR}/angstrom.repo ${D}${sysconfdir}/zypp/repos.d/
}

FILES_${PN} += "${sysconfdir}/zypp/repos.d/angstrom.repo"
