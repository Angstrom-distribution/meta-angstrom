FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:${THISDIR}/files:"
PRINC := "${@int(PRINC) + 5}"

SRC_URI += " \
           file://remove-potcdate.sed \
           file://remove-potcdate.sin \
           file://common.tar.xz \
          "
SRC_URI[md5sum] = "15389c73e091b1dda915279c388b9cb2"
SRC_URI[sha256sum] = "9151aa108c177054387885763fa0e433e76780f7c5655c70a5390f2a6c6871da"

S = "${WORKDIR}/git"

# Yes, that breaks offline builds
do_configure_prepend() {
	( cd ${S}
	mkdir -p ${S}/common
        cp -Rf ${WORKDIR}/common/* ${S}/common/
	cp ${WORKDIR}/remove-pot* ${S}/po/ )
}

do_configure_append() {
	sed -i -e 's:POTFILES.in remove-potcdate.sed:POTFILES.in:' ${B}/po/Makefile
}

ALLOW_EMPTY_${PN}-apps = "1"
ALLOW_EMPTY_${PN}-glib = "1"
