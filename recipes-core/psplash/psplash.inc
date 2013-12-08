DESCRIPTION = "Userspace framebuffer boot logo based on usplash."
HOMEPAGE = "http://git.yoctoproject.org/cgit/cgit.cgi/psplash"
SECTION = "base"
LICENSE = "GPLv2+"
LIC_FILES_CHKSUM = "file://psplash.h;md5=89004674feba6dcfe7fbb8baccb58205"

PROVIDES = "virtual/psplash"
RPROVIDES_${PN} = "virtual-psplash"
RCONFLICTS_${PN} = "exquisite"

SRCREV = "afd4e228c606a9998feae44a3fed4474803240b7"
PV = "0.1+git${SRCPV}"
PR = "r1"

SRC_URI = "git://git.yoctoproject.org/psplash;protocol=git"

# You can create your own pslash-poky-img.h by doing
# ./make-image-header.sh <file>.png POKY
# and rename the resulting .h to pslash-poky-img.h (for the logo)
# respectively psplash-bar-img.h (BAR) for the bar.
# You might also want to patch the colors (see patch)

inherit autotools pkgconfig update-rc.d

do_configure_append() {
	install -m 0644 ${WORKDIR}/psplash-poky-img.h ${S}/
	install -m 0644 ${WORKDIR}/psplash-bar-img.h ${S}/

	if [ -e "${WORKDIR}/psplash.h" ]; then
		install -m 0644 ${WORKDIR}/psplash.h ${S}/
	fi
}

do_install_prepend() {
	install -d ${D}/mnt/.splash/
	install -d ${D}${sysconfdir}/default/
	install -m 0644 ${WORKDIR}/psplash-default ${D}${sysconfdir}/default/psplash
	install -d ${D}${sysconfdir}/init.d/
	install -m 0755 ${WORKDIR}/psplash-init ${D}${sysconfdir}/init.d/psplash
	install -d ${D}${bindir}
	ln -s psplash-write ${D}${bindir}/splash-write
	install -d ${D}${sysconfdir}/default
	install -m 0755 ${WORKDIR}/splashfuncs ${D}${sysconfdir}/default/splashfuncs
}

INITSCRIPT_NAME = "psplash"
INITSCRIPT_PARAMS = "start 0 S . stop 20 0 1 6 ."

PACKAGES =+ "${PN}-support"

RDEPENDS_${PN} += "${PN}-support"
FILES_${PN}-support += "/mnt/.splash ${sysconfdir} ${bindir}/psplash-write ${bindir}/splash-write"
