#allow empty password
do_install_append () {
	sed -i -e 's:nullok_secure:nullok:g' ${D}${sysconfdir}/pam.d/common-auth
}
