PRINC := "${@int(PRINC) + 1}"

FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://desktop_gnome_background.schemas \
            file://background-default.jpg \
           "

do_install_append() {
	install -m 0644 ${WORKDIR}/desktop_gnome_background.schemas ${D}${sysconfdir}/gconf/schemas/
	install -m 0644 ${WORKDIR}/background-default.jpg ${D}${datadir}/pixmaps/backgrounds/gnome/angstrom-default.jpg
}
