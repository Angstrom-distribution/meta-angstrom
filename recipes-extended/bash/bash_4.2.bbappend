PRINC := "${@int(PRINC) + 1}"
FILESEXTRAPATHS := "${THISDIR}/${PN}"

SRC_URI += "file://0001-17-16-panto-Apparently-it-s-a-combination-of-kernel-.patch"
