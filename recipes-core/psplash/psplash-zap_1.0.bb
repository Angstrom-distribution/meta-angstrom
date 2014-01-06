LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

SRC_URI = "file://zzapsplash-init"
PR = "r7"

do_install_prepend() {
        install -d "${D}${sysconfdir}/init.d/"
        install -m 0755 "${WORKDIR}/zzapsplash-init" "${D}${sysconfdir}/init.d/zzapsplash"
}

inherit update-rc.d

RRECOMMENDS_${PN} = "psplash-angstrom"
INITSCRIPT_NAME = "zzapsplash"
INITSCRIPT_PARAMS = "start 99 5 S ."

