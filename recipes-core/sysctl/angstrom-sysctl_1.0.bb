SUMMARY = "Angstrom distro-wide sysctl defaults"
DESCRIPTION = "sysctl.d drop-in applied by systemd-sysctl.service at boot. \
Enables TCP packetization-layer PMTU discovery so a path that blackholes \
ICMP fragmentation-needed recovers instead of stalling."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://60-angstrom-network.conf"

S = "${UNPACKDIR}"

inherit allarch

do_install() {
	install -d ${D}${nonarch_libdir}/sysctl.d
	install -m 0644 ${UNPACKDIR}/60-angstrom-network.conf \
		${D}${nonarch_libdir}/sysctl.d/60-angstrom-network.conf
}

FILES:${PN} = "${nonarch_libdir}/sysctl.d/60-angstrom-network.conf"
