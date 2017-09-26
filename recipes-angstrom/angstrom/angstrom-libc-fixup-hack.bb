DESCRIPTION = "Fixup some miscompiled apps by making an extra symlink"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PACKAGE_ARCH = "all"
ALLOW_EMPTY_${PN} = "1"

pkg_postinst_${PN}() {
if [ "x$D" != "x" ]; then
	exit 1
fi
ln -sf /lib/libc.so.6 /lib/libc.so
}
