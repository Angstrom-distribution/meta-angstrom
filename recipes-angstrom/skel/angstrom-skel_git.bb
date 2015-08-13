SUMMARY = "/etc/skel for the Ångström Distribution"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=2f6ec892a474b38b927af638da43b05c"

SRCREV = "56805c82ef9666b49e9c629f5031053d280bebe2"
SRC_URI = "git://github.com/Angstrom-distribution/Angstrom-skel.git;protocol=https"

S = "${WORKDIR}/git"

inherit allarch

do_install() {
	install -d ${D}${sysconfdir}
	cp -a ${S}/etc/skel ${D}${sysconfdir}
}

PACKAGES =+ "angstrom-skel-htop \
             angstrom-skel-gnome2 \
            "

FILES_angstrom-skel-htop = "${sysconfdir}/skel/.config/htop" 
FILES_angstrom-skel-gnome2 = "${sysconfdir}/skel/.config/gconf" 

