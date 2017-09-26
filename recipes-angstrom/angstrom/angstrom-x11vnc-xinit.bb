DESCRIPTION = "Script to start a passwordless vnc of the current X session"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://x11vnc.desktop"

PR = "r2"

do_install() {
	install -d ${D}${sysconfdir}/X11/Xinit.d
	echo "#!/bin/sh" > ${D}${sysconfdir}/X11/Xinit.d/02vnc
	echo "x11vnc  -q -bg -display :0 -forever -avahi" >> ${D}${sysconfdir}/X11/Xinit.d/02vnc
	chmod 0755 ${D}${sysconfdir}/X11/Xinit.d/02vnc

	install -d ${D}${sysconfdir}/xdg/autostart
	install -m 0644 ${WORKDIR}/x11vnc.desktop ${D}${sysconfdir}/xdg/autostart/ 
}

RDEPENDS_${PN} = "x11vnc"
CONFFILES_${PN} += "${sysconfdir}/X11/Xinit.d/02vnc ${sysconfdir}/xdg/autostart/x11vnc.desktop"
PACKAGE_ARCH = "all"

