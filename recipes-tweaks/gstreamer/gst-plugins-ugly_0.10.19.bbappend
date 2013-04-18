PRINC := "${@int(PRINC) + 2}"
DEPENDS += "x264 orc orc-native"
EXTRA_OECONF += "--enable-orc"

FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI = "git://anongit.freedesktop.org/gstreamer/gst-plugins-ugly;branch=0.10 \
           file://remove-potcdate.sed \
           file://remove-potcdate.sin \
          "
SRCREV = "9afc696e5fa9fb980e02df5637f022796763216f"

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
