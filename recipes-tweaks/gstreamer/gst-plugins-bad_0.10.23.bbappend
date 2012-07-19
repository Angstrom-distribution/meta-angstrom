PRINC := "${@int(PRINC) + 1}"
DEPENDS += "orc"
EXTRA_OECONF += "--enable-orc"

