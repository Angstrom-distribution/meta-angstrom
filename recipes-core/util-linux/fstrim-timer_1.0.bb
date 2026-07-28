SUMMARY = "Weekly fstrim timer for flash-backed filesystems"
DESCRIPTION = "Ships the fstrim.timer/fstrim.service units and enables the \
timer so unused blocks are discarded weekly. util-linux itself is built \
--without-systemd in oe-core (enabling it creates a util-linux<->systemd \
dependency cycle), so its bundled units are never installed; this recipe \
provides standalone copies that call the util-linux fstrim binary."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://fstrim.service \
           file://fstrim.timer \
"

S = "${UNPACKDIR}"

inherit systemd allarch

SYSTEMD_SERVICE:${PN} = "fstrim.timer"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

# the units call ${sbindir}/fstrim, provided by util-linux
RDEPENDS:${PN} = "util-linux-fstrim"

do_install() {
	install -d ${D}${systemd_system_unitdir}
	install -m 0644 ${UNPACKDIR}/fstrim.service ${D}${systemd_system_unitdir}/fstrim.service
	install -m 0644 ${UNPACKDIR}/fstrim.timer ${D}${systemd_system_unitdir}/fstrim.timer
}

FILES:${PN} = "${systemd_system_unitdir}/fstrim.service \
               ${systemd_system_unitdir}/fstrim.timer \
"
