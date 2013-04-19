PRINC := "${@int(PRINC) + 4}"
DEPENDS += "orc orc-native"
EXTRA_OECONF =  "--disable-directfb --disable-examples --disable-vdpau --disable-apexsink --enable-experimental --enable-orc --disable-eglgles"

FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI = "git://anongit.freedesktop.org/gstreamer/gst-plugins-bad;branch=0.10 \
           file://0001-opencv-fix-warnings-and-build-against-opencv-2.4.x.patch \
           file://0002-configure-opencv-plugin-builds-with-newer-versions-u.patch \
           file://remove-potcdate.sed \
           file://remove-potcdate.sin \
          "
SRCREV = "b8a6a7d1456691a1344a544f8fe6d86d1e0b584f"

S = "${WORKDIR}/git"

# Yes, that breaks offline builds
do_configure_prepend() {
	( cd ${S}
	git submodule init
	git submodule update
	cp ${WORKDIR}/remove-pot* ${S}/po/ )
}

do_configure_append() {
	sed -i -e 's:POTFILES.in remove-potcdate.sed:POTFILES.in:' ${B}/po/Makefile
}

ALLOW_EMPTY_${PN}-glib = "1"
