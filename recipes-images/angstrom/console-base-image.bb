#Angstrom base image

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit core-image

DISTRO_UPDATE_ALTERNATIVES ??= ""
ROOTFS_PKGMANAGE_PKGS ?= '${@oe.utils.conditional("ONLINE_PACKAGE_MANAGEMENT", "none", "", "${ROOTFS_PKGMANAGE} ${DISTRO_UPDATE_ALTERNATIVES}", d)}'

# Debug features, disable if wanted
# allow-empty-password (PermitEmptyPasswords yes) is left out on purpose: with
# UsePAM it turns every ssh 'none' auth probe into an empty-password PAM
# attempt, adding pam_unix's fail delay to each login. Root's blank password
# stays usable on the console, ssh needs a key.
IMAGE_FEATURES += "empty-root-password"

# Debug tools, leave in
IMAGE_FEATURES += "package-management nfs-client ssh-server-openssh"

CORE_IMAGE_EXTRA_INSTALL += " \
	${ROOTFS_PKGMANAGE_PKGS} ${DISTROFEEDCONFIGS} \
	systemd-networkd systemd-analyze udev-hwdb systemd-zram-generator \
	tzdata cronie \
        bash \
        avahi-daemon avahi-utils \
	net-tools lldpd iproute2-tc ethtool \
        wget curl \
        vim \
        git \
	kernel-modules \
        util-linux-fstrim util-linux-blkdiscard fstrim-timer \
        e2fsprogs-resize2fs \
        htop \
        usb-modeswitch \
        iwd \
	libgpiod \
	bc \
"

export IMAGE_BASENAME = "base-image"

IMAGE_PREPROCESS_COMMAND += "do_systemd_network ; "

do_systemd_network () {
	install -d ${IMAGE_ROOTFS}${sysconfdir}/systemd/network
	cat << EOF > ${IMAGE_ROOTFS}${sysconfdir}/systemd/network/10-en.network
[Match]
Name=en*

[Network]
DHCP=yes
LLDP=yes
EmitLLDP=yes

[DHCPv4]
UseTimezone=yes
RouteMetric=10

[IPv6AcceptRA]
RouteMetric=10

[Route]
Metric=10
EOF

	cat << EOF > ${IMAGE_ROOTFS}${sysconfdir}/systemd/network/11-eth.network
[Match]
Name=eth*

[Network]
DHCP=yes
LLDP=yes
EmitLLDP=yes

[DHCPv4]
UseTimezone=yes
RouteMetric=10

[IPv6AcceptRA]
RouteMetric=10

[Route]
Metric=10
EOF

	cat << EOF > ${IMAGE_ROOTFS}${sysconfdir}/systemd/network/12-wlan.network
[Match]
Name=wlan*

[Network]
DHCP=yes
LLDP=yes
EmitLLDP=yes

[DHCPv4]
UseTimezone=yes
RouteMetric=100

[IPv6AcceptRA]
RouteMetric=100

[Route]
Metric=100
EOF
}

