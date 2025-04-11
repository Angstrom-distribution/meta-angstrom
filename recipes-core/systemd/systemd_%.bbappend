# This disables the 'mac' policy for pni-names
# We do not want MAC address based naming, for example the wifi on RB1
# gets a new MAC address every boot *and* doesn't support any of the
# naming features. This leads to a new, unpredictable interface name on
# every boot

do_install:append:angstrom() {
	sed -i -e 's: mac::g' ${D}${nonarch_libdir}/systemd/network/99-default.link
} 
