FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://journald.conf"

PACKAGECONFIG   = " \
                   ldconfig \
                   ${@bb.utils.contains('DISTRO_FEATURES', 'pam', 'pam', '', d)} \
                   ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xkbcommon', '', d)} \
                   ${@bb.utils.contains('DISTRO_FEATURES', 'selinux', 'selinux', '', d)} \
                   ${@bb.utils.contains('DISTRO_FEATURES', 'wifi', 'rfkill', '', d)} \
                   ${@bb.utils.contains('MACHINE_FEATURES', 'efi', 'efi', '', d)} \
                   binfmt \
                   randomseed \
                   machined \
                   backlight \
                   quotacheck \
                   hostnamed \
                   ${@bb.utils.contains('TCLIBC', 'glibc', 'myhostname sysusers', '', d)} \
                   hibernate \
                   timedated \
                   timesyncd \
                   localed \
                   ima \
                   smack \
                   logind \
                   firstboot \
                   utmp \
                   polkit \
                   \
                   networkd \
                   resolved \
                   iptc \
                   libidn \
                   lz4 \
"

do_install_append() {
        cp ${WORKDIR}/journald.conf ${D}${sysconfdir}/systemd
}
