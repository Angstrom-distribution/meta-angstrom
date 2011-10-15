DESCRIPTION = "Set XFCE session as default in GDM config (custom.conf)"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

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
