PRINC := "${@int(PRINC) + 1}"
DEPENDS += "orc orc-native"
EXTRA_OECONF += "--enable-orc"
