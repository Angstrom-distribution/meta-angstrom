PRINC := "${@int(PRINC) + 2}"
DEPENDS += "orc orc-native"
EXTRA_OECONF =  "--disable-directfb --disable-examples --disable-vdpau --disable-apexsink --enable-experimental --enable-orc"

