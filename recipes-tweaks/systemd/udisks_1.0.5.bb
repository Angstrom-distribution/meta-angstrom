DESCRIPTION = "A storage daemon that implements well-defined D-Bus interfaces that can be used to query and manipulate storage devices."
LICENSE = "GPLv2+"
LIC_FILES_CHKSUM = "file://COPYING;md5=73d83aebe7e4b62346afde80e0e94273"

DEPENDS = "libatasmart sg3-utils polkit udev dbus-glib glib-2.0 intltool-native lvm2"
# optional dependencies: device-mapper parted

DEPENDS += "${@base_contains('DISTRO_FEATURES', 'systemd', 'systemd libgudev', '', d)}"

SRCREV = "9829152b12a8924d2e091a00133ed1a3a7ba75c0"
SRC_URI = "git://anongit.freedesktop.org/git/udisks.git;protocol=http;branch=udisks1 \
           file://optional-depends.patch"

S = "${WORKDIR}/git"

inherit autotools-brokensep systemd gtk-doc

PACKAGECONFIG ??= "parted"
PACKAGECONFIG[parted] = "--enable-parted,--disable-parted,parted"

EXTRA_OECONF = "--disable-man-pages"

FILES_${PN} += "${libdir}/polkit-1/extensions/*.so \
                ${datadir}/dbus-1/ \
                ${datadir}/polkit-1 \
                ${base_libdir}/udev/* \
"

FILES_${PN}-dbg += "${base_libdir}/udev/.debug"

RPROVIDES_${PN} += "${PN}-systemd"
RREPLACES_${PN} += "${PN}-systemd"
RCONFLICTS_${PN} += "${PN}-systemd"
SYSTEMD_SERVICE_${PN} = "udisks.service"
SYSTEMD_AUTO_ENABLE = "disable"
