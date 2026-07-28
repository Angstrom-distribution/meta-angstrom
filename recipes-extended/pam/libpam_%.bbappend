# Skip pam_unix's 2 second fail delay on failed authentication; on slow machines
# every failed attempt (including sshd's empty-password 'none' probe when
# PermitEmptyPasswords is enabled) stalls logins otherwise.
do_install:append () {
	sed -i -e '/pam_unix.so/s/$/ nodelay/' ${D}${sysconfdir}/pam.d/common-auth
}
