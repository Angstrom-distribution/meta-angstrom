#Angstrom image to test systemd

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

IMAGE_PREPROCESS_COMMAND = "rootfs_update_timestamp"

DISTRO_UPDATE_ALTERNATIVES ??= ""
ROOTFS_PKGMANAGE_PKGS ?= '${@base_conditional("ONLINE_PACKAGE_MANAGEMENT", "none", "", "${ROOTFS_PKGMANAGE} ${DISTRO_UPDATE_ALTERNATIVES}", d)}'

CONMANPKGS ?= "connman connman-plugin-loopback connman-plugin-ethernet connman-plugin-wifi connman"
CONMANPKGS_libc-uclibc = ""

IMAGE_INSTALL += " \
	angstrom-packagegroup-boot \
	packagegroup-basic \
	${CONMANPKGS} \
	${ROOTFS_PKGMANAGE_PKGS} update-alternatives-cworth \
	systemd-analyze \
"

IMAGE_DEV_MANAGER   = "udev"
IMAGE_INIT_MANAGER  = "systemd"
IMAGE_INITSCRIPTS   = " "
IMAGE_LOGIN_MANAGER = "tinylogin shadow"

export IMAGE_BASENAME = "systemd-image"

# For some unknown reason some initscripts break PAM, so just delete them
# This is a HACK, the offending script(s) need to get fixed, not deleted.
# And yes, this breaks on package upgrade

delete_sysvinit_scripts() {
	if [ -d ${IMAGE_ROOTFS}${sysconfdir}/init.d ] ; then
		echo "Deleting sysv scripts from ${IMAGE_ROOTFS}"
		# known offenders
		for sysv in networking ofono syslog syslog.busybox banner.sh bootmisc.sh checkroot.sh devpts.sh fuse halt hostname.sh keymap.sh mountall.sh mountall.sh mountnfs.sh populate-volatile.sh rmnologin.sh save-rtc.sh sendsigs sysfs.sh umountfs umountnfs.sh urandom ; do
			rm -f ${IMAGE_ROOTFS}${sysconfdir}/init.d/$sysv
		done 
		# the rest
		find ${IMAGE_ROOTFS}${sysconfdir}/init.d/* | sed 's:${IMAGE_ROOTFS}${sysconfdir}/init.d/::g' | grep -v function | xargs echo
	fi
}

ROOTFS_POSTPROCESS_COMMAND += "delete_sysvinit_scripts ; "

inherit image
