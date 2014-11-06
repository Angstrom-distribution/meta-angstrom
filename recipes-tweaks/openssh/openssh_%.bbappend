FILESEXTRAPATHS_prepend := "${THISDIR}/files:"

do_install_append () {
	sed -i -e 's:^#PasswordAuthentication.*$:PasswordAuthentication no:g' ${D}${sysconfdir}/ssh/sshd_config
}
