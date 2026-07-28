SUMMARY = "First-boot rootfs grow service"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# This recipe is intended for OpenEmbedded-Core wrynose or newer.

SRC_URI = "file://firstboot-grow-rootfs-auto.sh \
           file://firstboot-grow-rootfs.service \
"

S = "${UNPACKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "firstboot-grow-rootfs.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    install -d ${D}${sbindir}
    install -m 0755 ${UNPACKDIR}/firstboot-grow-rootfs-auto.sh ${D}${sbindir}/firstboot-grow-rootfs-auto.sh

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/firstboot-grow-rootfs.service ${D}${systemd_system_unitdir}/firstboot-grow-rootfs.service
    sed -i -e 's|@SBINDIR@|${sbindir}|' ${D}${systemd_system_unitdir}/firstboot-grow-rootfs.service
}

FILES:${PN} += "${sbindir}/firstboot-grow-rootfs-auto.sh ${systemd_system_unitdir}/firstboot-grow-rootfs.service"

RDEPENDS:${PN} += " \
    util-linux-sfdisk \
    util-linux-findmnt \
    util-linux-lsblk \
    util-linux-blockdev \
    e2fsprogs-resize2fs \
    xfsprogs \
    btrfs-tools \
"

