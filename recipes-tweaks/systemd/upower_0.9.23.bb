DESCRIPTION = "UPower is an abstraction for enumerating power devices, listening to device events and querying history and statistics. "
LICENSE = "GPLv2+"
LIC_FILES_CHKSUM = "file://COPYING;md5=0de8fbf1d97a140d1d93b9f14dcfbf08"

DEPENDS = "libusb1 libgudev udev glib-2.0 dbus-glib polkit gobject-introspection-stub"

SRC_URI = "http://upower.freedesktop.org/releases/${BPN}-${PV}.tar.xz"
SRC_URI[md5sum] = "39cfd97bfaf7d30908f20cf937a57634"
SRC_URI[sha256sum] = "433252b0a8e9ab4bed7e17ee3ee5b7cef6d527b1f5401ee32212d82a9682981b"

inherit autotools pkgconfig gettext

PACKAGECONFIG ??= ""
PACKAGECONFIG[idevice] = "--with-idevice,--without-idevice,libimobiledevice libplist"

EXTRA_OECONF = " --with-backend=linux"

do_configure_prepend() {
    sed -i -e s:-nonet:\:g ${S}/doc/man/Makefile.am
    sed -i -e 's: doc : :g' ${S}/Makefile.am
}    


RRECOMMENDS_${PN} += "pm-utils"
FILES_${PN} += "${datadir}/dbus-1/ \
                ${datadir}/polkit-1/ \
                ${base_libdir}/udev/* \
                /lib/systemd \
"

FILES_${PN}-dbg += "${base_libdir}/udev/.debug"



