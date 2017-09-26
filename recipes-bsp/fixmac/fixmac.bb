SUMMARY = "Provide random MAC address for devices with 00:00:00:00:00 as MAC address"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"


SRC_URI = "file://fixmac.sh \
           file://fixmac.rules"


do_install() {
	install -d ${D}${sysconfdir}/udev/rules.d
	install -m 0644  ${WORKDIR}/fixmac.rules ${D}${sysconfdir}/udev/rules.d

	install -d ${D}${bindir}
	install -m 0755 ${WORKDIR}/fixmac.sh ${D}${bindir}

	sed -i -e s:/sbin:${base_sbindir}:g -e s:/etc:${sysconfdir}:g ${D}${bindir}/fixmac.sh

}

RDEPENDS_${PN} = "ranpwd"
