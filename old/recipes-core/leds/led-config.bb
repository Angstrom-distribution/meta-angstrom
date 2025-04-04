DESCRIPTION = "Configuration files for runtime LED configuration" 
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit systemd

#PV = "${DISTRO_VERSION}"
PR = "r12"
PACKAGE_ARCH = "${MACHINE_ARCH}"

SRC_URI = "file://led-config \
           file://leds \
           file://leds.service \
          "	

do_compile() {
	:
}


do_install () {
	install -d ${D}/${sysconfdir}/default
	install -d ${D}/${bindir}

	install -m 0644 ${WORKDIR}/leds ${D}/${sysconfdir}/default/
	install -m 0755 ${WORKDIR}/led-config ${D}/${bindir}

	install -d ${D}/${base_libdir}/systemd/system
	install -m 0644 ${WORKDIR}/leds.service ${D}/${base_libdir}/systemd/system/
}

NATIVE_SYSTEMD_SUPPORT = "1"
SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE_${PN} = "leds.service"

FILES_${PN} += "${base_libdir}/systemd"
CONFFILES_${PN} += "${sysconfdir}/default/leds \
                   "

