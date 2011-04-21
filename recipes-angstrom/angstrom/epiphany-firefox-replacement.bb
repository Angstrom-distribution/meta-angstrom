# This is a hack because $*#($*($# e17 doesn't check if firefox is present and puts it in the menu blindly
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

RDEPENDS_${PN} = "epiphany"
RCONFLICTS_${PN} = "firefox"
PR = "r1"

do_install() {
	install -d ${D}/${bindir}
	ln -sf ${bindir}/epiphany ${D}/${bindir}/firefox
} 

