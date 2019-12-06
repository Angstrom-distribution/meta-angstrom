FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

PR = "r2"

PACKAGECONFIG   = " \
                   ${@bb.utils.filter('DISTRO_FEATURES', 'efi ldconfig pam selinux usrmerge polkit', d)} \
                   ${@bb.utils.contains('DISTRO_FEATURES', 'wifi', 'rfkill', '', d)} \
                    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xkbcommon', '', d)} \
                   acl \
                   backlight \
                   ${@bb.utils.contains('TCLIBC', 'glibc', 'myhostname sysusers', '', d)} \
                   binfmt \
                   efi \
                   firstboot \
                   gshadow \
                   hibernate \
                   hostnamed \
                   idn \
                   ima \
                   kmod \
                   ldconfig \
                   localed \
                   logind \
                   machined \
                   myhostname \
                   networkd \
                   nss \
                   nss-mymachines \
                   nss-resolve \
                   polkit \
                   quotacheck \
                   randomseed \
                   resolved \
                   set-time-epoch \
                   smack \
                   sysusers \
                   timedated \
                   timesyncd \
                   utmp \
                   vconsole \
                   xz \
                   \
                   networkd \
                   resolved \
                   iptc \
                   libidn \
                   lz4 \
"
# fix pager corruption with busybox less/more
RRECOMMENDS_${PN} += "less"
