DESCRIPTION = "Transmission is a BitTorrent client w/ a built-in Ajax-Powered Webif GUI."
SECTION = "network"

HOMEPAGE = "http://www.transmissionbt.com/"

DEPENDS = "libevent gnutls openssl libtool intltool-native curl"

LICENSE = "MIT & GPLv2"
LIC_FILES_CHKSUM = "file://COPYING;md5=0dd9fcdc1416ff123c41c785192a1895"

PV = "2.92"
SRCREV = "67ef7b888c37fdb85a6d0733c1cc2f0f7c585f66"
SRC_URI = "git://github.com/transmission/transmission.git;protocol=https;branch=master \
           file://0862099d0bf5a3ec8b2e9d538458d612897741a2.patch \
           "

S = "${WORKDIR}/git"

inherit cmake gettext useradd systemd

PACKAGECONFIG = "${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'gtk', '', d)} \
                 ${@bb.utils.contains('DISTRO_FEATURES','systemd','systemd','',d)}"

PACKAGECONFIG[gtk] = " -DENABLE_GTK=1,-DENABLE_GTK=0,gtk+3,"
PACKAGECONFIG[systemd] = " -DWITH_SYSTEMD=1,-DWITH_SYSTEMD=0,systemd,"

EXTRA_OECMAKE += " \
                  -DUSE_SYSTEM_EVENT2=1 \
                 "

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


