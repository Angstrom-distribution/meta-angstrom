#Angstrom image to test systemd

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

IMAGE_PREPROCESS_COMMAND = "rootfs_update_timestamp"

IMAGE_INSTALL += " \
	angstrom-version angstrom-feed-configs \
	base-files base-passwd \
	netbase \
	rsyslog \
	opkg opkg-config-base \
	update-alternatives-cworth \
	busybox \
	dropbear openssh-sftp \
	e2fsprogs-e2fsck e2fsprogs-blkid \
	avahi-daemon avahi-utils avahi-systemd \
	connman connman-plugin-loopback connman-plugin-ethernet connman-plugin-wifi connman-systemd \
	systemd-compat-units \
	cpufrequtils \
    htop \
"

IMAGE_DEV_MANAGER   = "udev"
IMAGE_INIT_MANAGER  = "systemd"
IMAGE_INITSCRIPTS   = " "
IMAGE_LOGIN_MANAGER = "tinylogin shadow"

export IMAGE_BASENAME = "systemd-image"

inherit image
