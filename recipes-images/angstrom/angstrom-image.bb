#Angstrom image to test systemd

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

IMAGE_PREPROCESS_COMMAND = "rootfs_update_timestamp ;"

DISTRO_UPDATE_ALTERNATIVES ??= ""
ROOTFS_PKGMANAGE_PKGS ?= '${@base_conditional("ONLINE_PACKAGE_MANAGEMENT", "none", "", "${ROOTFS_PKGMANAGE} ${DISTRO_UPDATE_ALTERNATIVES}", d)}'

IMAGE_INSTALL += " \
	angstrom-packagegroup-boot \
	packagegroup-basic \
	${ROOTFS_PKGMANAGE_PKGS} \
	update-alternatives-opkg \
	systemd-analyze \
	cpufreq-tweaks \
"

IMAGE_DEV_MANAGER   = "udev"
IMAGE_INIT_MANAGER  = "systemd"
IMAGE_INITSCRIPTS   = " "
IMAGE_LOGIN_MANAGER = "busybox shadow"

export IMAGE_BASENAME = "Angstrom-base-image"

IMAGE_PREPROCESS_COMMAND += "do_systemd_network ; "

do_systemd_network () {
	install -d ${IMAGE_ROOTFS}${sysconfdir}/systemd/network
	cat << EOF > ${IMAGE_ROOTFS}${sysconfdir}/systemd/network/10-en.network
[Match]
Name=en*

[Network]
DHCP=yes
EOF

	cat << EOF > ${IMAGE_ROOTFS}${sysconfdir}/systemd/network/11-eth.network
[Match]
Name=eth*

[Network]
DHCP=yes
EOF
}

inherit image
