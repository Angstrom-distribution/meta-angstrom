# This is a hack because $*#($*($# e17 doesn't check if firefox is present and puts it in the menu blindly
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

RDEPENDS_${PN} = "epiphany"
RCONFLICTS_${PN} = "firefox"
PR = "r1"

do_install() {
	install -d ${D}/${bindir}
	ln -sf ${bindir}/epiphany ${D}/${bindir}/firefox
} 

