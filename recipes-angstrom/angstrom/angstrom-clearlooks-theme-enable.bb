DESCRIPTION = "Enable clearlooks theme in gtkrc"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

inherit allarch

RDEPENDS_${PN} = "gtk-theme-clearlooks"

ALLOW_EMPTY_${PN} = "1"

pkg_postinst() {
#!/bin/sh
mkdir -p $D${sysconfdir}/gtk-2.0
touch $D${sysconfdir}/gtk-2.0/gtkrc
sed -i /gtk-theme-name/d $D${sysconfdir}/gtk-2.0/gtkrc
echo 'gtk-theme-name = "Clearlooks"' >> $D${sysconfdir}/gtk-2.0/gtkrc
}

