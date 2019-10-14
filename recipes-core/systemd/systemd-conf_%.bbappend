FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://journald.conf"

do_install_append() {
        install -d ${D}${sysconfdir}/systemd
        cp ${WORKDIR}/journald.conf ${D}${sysconfdir}/systemd
}

FILES_${PN} += "${sysconfdir}/systemd"
