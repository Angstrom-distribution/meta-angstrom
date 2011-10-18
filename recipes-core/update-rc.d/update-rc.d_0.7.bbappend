FILESEXTRAPATHS := "${THISDIR}/${PN}"

SRC_URI += "file://0001-update-rc.d-make-s-a-noop-when-systemd-is-present.patch"
 
PRINC = "1"

