DESCRIPTION = "Append Autologin to GDM config (custom.conf)"
SUMMARY = "A hack to enable automatic login for the root using when using the GDM display manager"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PR = "r1"

PACKAGE_ARCH = "all"
ALLOW_EMPTY_${PN} = "1"

pkg_postinst_${PN}() {
#!/bin/sh
grep "TimedLogin" $D/etc/gdm/custom.conf
if [ $? -eq 0 ]; then
    echo "NOTE:: custom.conf already has 'TimedLogin' configured - not patching"
else
    cat >> $D/etc/gdm/custom.conf <<EOF

[daemon]
TimedLoginEnable=true
TimedLogin=root
TimedLoginDelay=10

EOF
fi
}

RDEPENDS_${PN} = "gdm"
