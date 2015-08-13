DESCRIPTION = "Drivers for the MT7601 wifi chipset"

LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://mt7601u.h;beginline=2;endline=12;md5=46082c720a31a197c059066ce021f118"

inherit module

PV = "1.0+${SRCPV}"

SRCREV = "ae46ece59d517cbb7c2e7478cefdb41ba23a8311"
SRC_URI = "git://github.com/kuba-moo/mt7601u.git;protocol=https"

S = "${WORKDIR}/git"

EXTRA_OEMAKE = "KDIR=${STAGING_KERNEL_DIR}" 

do_install() {
	install -d ${D}/lib/modules/${KERNEL_VERSION}/kernel/drivers/net/wireless/mediatek/mt7601u/
	install -m0644 mt7601u.ko ${D}/lib/modules/${KERNEL_VERSION}/kernel/drivers/net/wireless/mediatek/mt7601u/
}
