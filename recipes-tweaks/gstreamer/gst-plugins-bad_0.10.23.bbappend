PRINC := "${@int(PRINC) + 1}"
DEPENDS += "orc orc-native"
EXTRA_OECONF =  "--disable-vdpau --disable-apexsink --enable-experimental --enable-orc"

