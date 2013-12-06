DESCRIPTION = "Transmission is a BitTorrent client w/ a built-in Ajax-Powered Webif GUI."
SECTION = "network"

HOMEPAGE = "www.transmissionbt.com/"

DEPENDS = "libevent gnutls openssl libtool intltool-native curl"

LICENSE = "MIT & GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=7ee657ac1dce0e7353033fc06c8087d2"

SRC_URI = "http://download.transmissionbt.com/files/transmission-${PV}.tar.xz"
SRC_URI[md5sum] = "a5ef870c0410b12d10449c2d36fa4661"                                                                   
SRC_URI[sha256sum] = "3996651087df67a85f1e1b4a92b1b518ddefdd84c654b8df6fbccb0b91f03522" 

inherit autotools gettext useradd systemd

PACKAGECONFIG = "${@base_contains('DISTRO_FEATURES', 'x11', 'gtk', '', d)} \
                 ${@base_contains('DISTRO_FEATURES','systemd','systemd','',d)}"

PACKAGECONFIG[gtk] = " --with-gtk,--without-gtk,gtk+,"
PACKAGECONFIG[systemd] = "--with-systemd-daemon,--without-systemd-daemon,systemd,"

# Configure aborts with:
# config.status: error: po/Makefile.in.in was not created by intltoolize.
do_configure_prepend() {
    sed -i /AM_GLIB_GNU_GETTEXT/d ${S}/configure.ac
    intltoolize --copy --force --automake
}

do_install_append() {
	install -d ${D}${nonarch_base_libdir}/systemd/system
	install -m 0644 ${S}/daemon/transmission-daemon.service ${D}${nonarch_base_libdir}/systemd/system
}

PACKAGES += "${PN}-gui ${PN}-client"

FILES_${PN}-client = "${bindir}/transmission-remote ${bindir}/transmission-cli ${bindir}/transmission-create ${bindir}/transmission-show ${bindir}/transmission-edit"
FILES_${PN}-gui += "${bindir}/transmission-gtk ${datadir}/icons ${datadir}/applications ${datadir}/pixmaps"

FILES_${PN} = "${bindir}/transmission-daemon ${datadir}/transmission ${sysconfdir}"

SYSTEMD_SERVICE_${PN} = "transmission-daemon.service"

USERADD_PACKAGES = "${PN}"
GROUPADD_PARAM_${PN} = "--system transmission"
USERADD_PARAM_${PN} = "--home ${localstatedir}/lib/transmission-daemon --create-home \
                       --gid transmission \
                       --shell ${base_bindir}/false \
                       --system \
                       transmission"


