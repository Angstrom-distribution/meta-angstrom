
# Symlink utils into gdm dir so gdm-greeter can find them

do_install_append() {
    ln -sf ${libdir}/consolekit/ck-get-x11-display-device ${D}${libdir}/gdm/
    ln -sf ${libdir}/consolekit/ck-get-x11-server-pid ${D}${libdir}/gdm/
    ln -sf ${libdir}/polkit-gnome/polkit-gnome-authentication-agent-1 ${D}${libdir}/gdm/
}

RRECOMMENDS_${PN} += "iso-codes"
