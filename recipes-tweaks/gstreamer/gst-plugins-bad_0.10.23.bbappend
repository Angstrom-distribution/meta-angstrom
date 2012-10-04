PRINC := "${@int(PRINC) + 4}"
DEPENDS += "orc orc-native"
EXTRA_OECONF =  "--disable-vdpau --disable-apexsink --enable-experimental --enable-orc"

