FILESEXTRAPATHS_prepend := "${THISDIR}/files:"

SRC_URI_append = " file://auth2-none.c-avoid-authenticate-empty-passwords-to-m.patch "

do_install_append () {
	sed -i -e 's:^#PasswordAuthentication.*$:PasswordAuthentication no:g' ${D}${sysconfdir}/ssh/sshd_config
}
