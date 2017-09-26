DESCRIPTION = "Set XFCE session as default in GDM config (custom.conf)"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit allarch
ALLOW_EMPTY_${PN} = "1"

pkg_postinst_${PN}() {
#!/bin/sh
grep "xfce" $D/etc/gdm/custom.conf
if [ $? -eq 0 ]; then
    echo "NOTE:: custom.conf already has 'xfce' configured - not patching"
else
	sed -i -e 's:\[daemon\]:\[daemon\]\nDefaultSession=xfce:' $D/etc/gdm/custom.conf
fi
}

RDEPENDS_${PN} = "gdm"
