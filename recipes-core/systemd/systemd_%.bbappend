FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

PACKAGECONFIG = "xz ldconfig networkd resolved iptc libidn \
                 ${@bb.utils.contains('DISTRO_FEATURES', 'pam', 'pam', '', d)} \
                 ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xkbcommon', '', d)} \
                 ${@bb.utils.contains('DISTRO_FEATURES', 'selinux', 'selinux', '', d)} \
                "

SRC_URI += "file://journald.conf"

do_install_append() {
	cp ${WORKDIR}/journald.conf ${D}${sysconfdir}/systemd
}
