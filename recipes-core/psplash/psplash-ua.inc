inherit update-alternatives

do_install_append() {
	mv ${D}${bindir}/psplash ${D}${bindir}/psplash.${PN}
}

ALTERNATIVE_${PN} = "psplash"
ALTERNATIVE_LINK_NAME[psplash] = "${bindir}/psplash"
ALTERNATIVE_TARGET[psplash] = "${bindir}/psplash.${PN}"
