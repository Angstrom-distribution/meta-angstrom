FILESEXTRAPATHS:prepend:angstrom := "${THISDIR}/${PN}:"

SRC_URI:append:angstrom = "\
                           file://angstrom-timezone.conf \
                           file://wireless.network \
"

do_install:append:angstrom() {
	# DHCP-supplied IANA timezone (RFC 4833 option 101) via systemd-timedated,
	# for both interface classes systemd-conf configures: a drop-in onto
	# oe-core's own 80-wired.network, plus an 80-wireless.network oe-core does
	# not ship at all. Package-level, so any image pulling in systemd-conf
	# gets it.
	install -d ${D}${systemd_unitdir}/network/80-wired.network.d
	install -m 0644 ${UNPACKDIR}/angstrom-timezone.conf \
		${D}${systemd_unitdir}/network/80-wired.network.d/angstrom-timezone.conf

	install -m 0644 ${UNPACKDIR}/wireless.network \
		${D}${systemd_unitdir}/network/80-wireless.network
}

FILES:${PN} += "\
    ${systemd_unitdir}/network/80-wired.network.d \
    ${systemd_unitdir}/network/80-wireless.network \
"
