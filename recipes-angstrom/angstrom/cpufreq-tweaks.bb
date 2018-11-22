DESCRIPTION = "CPU-freq tweaks for angstrom"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit allarch systemd

#PV = "${DISTRO_VERSION}"

SRC_URI = "file://cpu-schedutil.timer \
           file://cpu-schedutil.service \
          "	

do_compile() {
	:
}


do_install () {

	install -d ${D}/${base_libdir}/systemd/system
	install -m 0644 ${WORKDIR}/cpu-schedutil.* ${D}/${base_libdir}/systemd/system/
}

NATIVE_SYSTEMD_SUPPORT = "1"
SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE_${PN} = "cpu-schedutil.timer"

FILES_${PN} += "${base_libdir}/systemd"
RDEPENDS_${PN} = "cpufrequtils"

