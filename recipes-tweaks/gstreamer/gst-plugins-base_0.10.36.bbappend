PRINC := "${@int(PRINC) + 3}"
DEPENDS += "pango orc orc-native"
EXTRA_OECONF += "--enable-orc --enable-pango"

FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI = "git://anongit.freedesktop.org/gstreamer/gst-plugins-base;branch=0.10 \
           file://remove-potcdate.sed \
           file://remove-potcdate.sin \
          "
SRCREV = "f97c9d5213f14f12dc056b9eaa2253325bc3a18c"

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

ALLOW_EMPTY_${PN}-apps = "1"
ALLOW_EMPTY_${PN}-glib = "1"
