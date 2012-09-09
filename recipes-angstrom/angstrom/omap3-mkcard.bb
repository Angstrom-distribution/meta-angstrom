DESCRIPTION="Format a card for omap3 booting"
LICENSE = "GPLv2"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/files/common-licenses/GPL-2.0;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI = "file://omap3-mkcard.sh"

FILESPATH =. "${FILE_DIRNAME}/../../contrib:"

PR = "r2"

do_install() {
	install -d ${D}${bindir}/
	install -m 755 ${WORKDIR}/omap3-mkcard.sh ${D}${bindir}/
}

PACKAGE_ARCH_${PN} = "all"
RDEPENDS_${PN} = "bc dosfstools e2fsprogs-mke2fs util-linux util-linux-sfdisk"

