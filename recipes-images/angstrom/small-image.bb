#Angstrom image to test systemd

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

IMAGE_PREPROCESS_COMMAND = "rootfs_update_timestamp ;"

DISTRO_UPDATE_ALTERNATIVES ??= ""
ROOTFS_PKGMANAGE_PKGS ?= '${@base_conditional("ONLINE_PACKAGE_MANAGEMENT", "none", "", "${ROOTFS_PKGMANAGE} ${DISTRO_UPDATE_ALTERNATIVES}", d)}'

CONMANPKGS ?= ""
CONMANPKGS_libc-uclibc = ""

IMAGE_INSTALL += " \
	base-files \
	${ROOTFS_PKGMANAGE_PKGS} update-alternatives-cworth \
	dropbear \
"

IMAGE_DEV_MANAGER   = "udev"
IMAGE_INIT_MANAGER  = "systemd"
IMAGE_INITSCRIPTS   = " "
IMAGE_LOGIN_MANAGER = "busybox shadow"

# Don't drag in X11 and fsck
BAD_RECOMMENDATIONS += "dbus-launch udev-hwdb util-linux-fsck e2fsprogs-e2fsck busybox-syslog"
export IMAGE_BASENAME = "small-image"

IMAGE_PREPROCESS_COMMAND += "do_delete_kernel ; do_delete_ldconfig ;"

do_delete_kernel () {
        rm -f ${IMAGE_ROOTFS}/boot/*
}

do_delete_ldconfig () {
        rm -f ${IMAGE_ROOTFS}/${base_sbindir}/ldconfig
}

inherit image image-mklibs
