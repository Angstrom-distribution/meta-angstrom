DESCRIPTION = "Transmission is a BitTorrent client w/ a built-in Ajax-Powered Webif GUI."
SECTION = "network"

HOMEPAGE = "http://www.transmissionbt.com/"

DEPENDS = "libevent gnutls openssl libtool intltool-native curl"

LICENSE = "MIT & GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=a1923fe8f8ff37c33665716af0ec84f1"

SRC_URI = "http://download.transmissionbt.com/files/transmission-${PV}.tar.xz"
SRC_URI[md5sum] = "411aec1c418c14f6765710d89743ae42"
SRC_URI[sha256sum] = "a9fc1936b4ee414acc732ada04e84339d6755cd0d097bcbd11ba2cfc540db9eb"

inherit autotools gettext useradd systemd

PACKAGECONFIG = "${@base_contains('DISTRO_FEATURES', 'x11', 'gtk', '', d)} \
                 ${@base_contains('DISTRO_FEATURES','systemd','systemd','',d)}"

PACKAGECONFIG[gtk] = " --with-gtk,--without-gtk,gtk+3,"
PACKAGECONFIG[systemd] = "--with-systemd-daemon,--without-systemd-daemon,systemd,"

# Configure aborts with:
# config.status: error: po/Makefile.in.in was not created by intltoolize.
# So let's run a subset of autogen.sh in do_configure_prepend
do_configure() {
  ( cd ${S} 
    # Systemd libs are now concentrated in libsystemd.so
    sed -i -e s:libsystemd-daemon:libsystemd:g ${S}/configure.ac
    autoreconf -fi || true
    touch aclocal.m4
    echo no | glib-gettextize --force --copy 
    chmod u+w aclocal.m4
    intltoolize --copy --force --automake
  )
  oe_runconf
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


