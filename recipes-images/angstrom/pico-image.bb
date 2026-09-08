# Minimal production image for 200MHz ARMv4-class hardware (64 MiB RAM /
# 32 MiB flash). Keeps glibc and systemd.

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit core-image
export IMAGE_BASENAME = "pico-image"

DISTRO_UPDATE_ALTERNATIVES ??= ""
ROOTFS_PKGMANAGE_PKGS ?= '${@oe.utils.conditional("ONLINE_PACKAGE_MANAGEMENT", "none", "", "${ROOTFS_PKGMANAGE} ${DISTRO_UPDATE_ALTERNATIVES}", d)}'

# IMAGE_FEATURES_REPLACES_ssh-server-openssh will silently drop dropbear if
# ssh-server-openssh is ever added; only ssh-server-dropbear is safe.
IMAGE_FEATURES += "package-management ssh-server-dropbear"

# systemd-zram-generator is the binary behind pico-systemd.inc's zram
# drop-in; without it installed, that config file is a no-op.
CORE_IMAGE_EXTRA_INSTALL += " \
    ${ROOTFS_PKGMANAGE_PKGS} ${DISTROFEEDCONFIGS} \
    systemd-networkd \
    systemd-zram-generator \
    tzdata-core tzdata-europe \
    pico-resolv \
"

# Root credential story on pico: no PAM, so dropbear refuses blank
# passwords -- a blank root password is console-only. Temporary for boot
# testing; real deployments must set a password or authorized_keys.
IMAGE_FEATURES += "empty-root-password"

# oe-core's dropbear.default ships DROPBEAR_EXTRA_ARGS="-w" (disallow root
# logins), which also blocks pubkey root login, not just password -- drop
# it so authorized_keys works. Blank-password root login stays refused by
# dropbear's own separate default (no -B).
IMAGE_PREPROCESS_COMMAND += "do_dropbear_allow_root_pubkey ; "

do_dropbear_allow_root_pubkey () {
	sed -i 's/^DROPBEAR_EXTRA_ARGS=.*/DROPBEAR_EXTRA_ARGS=""/' \
		${IMAGE_ROOTFS}${sysconfdir}/default/dropbear
}

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

# boot-validate.py needs an uncompressed rootfs beside its .qemuboot.conf
# to extract files for test verification. Default IMAGE_FSTYPES has
# .tar.gz/.wic but not .ext4.
IMAGE_FSTYPES:append = " ext4"
