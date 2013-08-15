DESCRIPTION = "GNOME configuration system"
SECTION = "x11/gnome"
LICENSE = "LGPLv2+"
LIC_FILES_CHKSUM = "file://COPYING;md5=55ca817ccb7d5b5b66355690e9abc605"

DEPENDS = "gtk+ glib-2.0 dbus dbus-glib libxml2 polkit intltool-native gobject-introspection-stub"

inherit gnomebase gtk-doc

SRC_URI = "${GNOME_MIRROR}/GConf/${@gnome_verdir("${PV}")}/GConf-${PV}.tar.bz2;name=archive \
	   file://backenddir.patch"

SRC_URI[archive.md5sum] = "f80329173cd9d134ad07e36002dd2a15"
SRC_URI[archive.sha256sum] = "52008a82a847527877d9e1e549a351c86cc53cada4733b8a70a1123925d6aff4"

S = "${WORKDIR}/GConf-${PV}"

POLKIT_OECONF = "--enable-defaults-service"
POLKIT_OECONF_virtclass-native = "--disable-defaults-service"
POLKIT_OECONF_libc-uclibc = "--disable-default-service"

EXTRA_OECONF = "--enable-shared --disable-static --enable-debug=yes \
                --disable-introspection --disable-orbit --with-openldap=no ${POLKIT_OECONF} --enable-gtk"

do_install_append() {
	rm -rf ${D}${sysconfdir}
	rm -rf ${D}${bindir} ${D}${libdir} ${D}${includedir} ${D}${datadir}
	rm -f ${D}${libexecdir}/*defaults* rm -f ${D}${libexecdir}/gconfd*
}

# disable dbus-x11 when x11 isn't in DISTRO_FEATURES
RDEPENDS_${PN} += "gconf"

ALLOW_EMPTY_${PN} = "1"
FILES_${PN} += "${libexecdir}/*sanity* \
               "
