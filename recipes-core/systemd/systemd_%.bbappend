FILESEXTRAPATHS:prepend:angstrom := "${THISDIR}/${PN}:"
SRC_URI:append:angstrom = "file://journald.conf"

do_install:append:angstrom() {
	# This disables the 'mac' policy for pni-names
	# We do not want MAC address based naming, for example the wifi on RB1
	# gets a new MAC address every boot *and* doesn't support any of the
	# naming features. This leads to a new, unpredictable interface name on
	# every boot
	sed -i -e 's: mac::g' ${D}${nonarch_libdir}/systemd/network/99-default.link

	# Journald config snipping to limit IO and storage
	install -d ${D}${sysconfdir}/systemd/journald.conf.d/
	install -m -644 ${UNPACKDIR}/journald.conf ${D}${sysconfdir}/systemd/journald.conf.d/angstrom.conf
} 
