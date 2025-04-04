DESCRIPTION = "Task packages for the Angstrom distribution"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PR = "r45"

PACKAGE_ARCH = "${MACHINE_ARCH}"

inherit packagegroup

XSERVER ?= "xserver-xorg \
            xf86-input-evdev \
            xf86-input-tslib \
            xf86-input-mouse \
            xf86-video-fbdev \
            xf86-input-keyboard"

DEPENDS = "virtual/xserver"

RDEPENDS_${PN} = "\
    ${XSERVER} \
    dbus-x11 \
    ttf-dejavu-sans \
    ttf-dejavu-sans-mono \
    "
