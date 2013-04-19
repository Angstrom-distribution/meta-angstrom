PRINC := "${@int(PRINC) + 1}"


SRC_URI = "git://anongit.freedesktop.org/gstreamer/gstreamer;branch=0.10 \
          "
SRCREV = "1bcabb9a23afb25dcd059bd827aa35b8ee7e5043"

S = "${WORKDIR}/git"

# Yes, that breaks offline builds
do_configure_prepend() {
	( cd ${S}
	git submodule init
	git submodule update )
}

ALLOW_EMPTY_${PN}-glib = "1"

