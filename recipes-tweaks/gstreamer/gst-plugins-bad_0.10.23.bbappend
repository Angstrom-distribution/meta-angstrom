PRINC := "${@int(PRINC) + 3}"
DEPENDS += "orc orc-native"
EXTRA_OECONF += "--enable-orc"

