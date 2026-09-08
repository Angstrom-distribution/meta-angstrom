SUMMARY = "Write /etc/resolv.conf from systemd-networkd's learned DNS"
DESCRIPTION = "Tiny oneshot + path unit that keeps /etc/resolv.conf in sync \
with the DNS servers systemd-networkd learns over DHCP."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://pico-resolv \
           file://pico-resolv.service \
           file://pico-resolv.path \
"

S = "${UNPACKDIR}"

inherit systemd allarch

# Only the .path is auto-enabled; pico-resolv.service ships alongside but
# has no [Install] section, so it stays static and only ever runs when the
# path unit triggers it.
SYSTEMD_SERVICE:${PN} = "pico-resolv.path"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

# networkctl lives in the systemd-networkd package, not the base systemd one.
RDEPENDS:${PN} = "systemd-networkd"

do_install() {
	install -d ${D}${libexecdir}
	install -m 0755 ${UNPACKDIR}/pico-resolv ${D}${libexecdir}/pico-resolv

	install -d ${D}${systemd_system_unitdir}
	install -m 0644 ${UNPACKDIR}/pico-resolv.service ${D}${systemd_system_unitdir}/pico-resolv.service
	install -m 0644 ${UNPACKDIR}/pico-resolv.path ${D}${systemd_system_unitdir}/pico-resolv.path
}

FILES:${PN} = "${libexecdir}/pico-resolv \
               ${systemd_system_unitdir}/pico-resolv.service \
               ${systemd_system_unitdir}/pico-resolv.path \
"
