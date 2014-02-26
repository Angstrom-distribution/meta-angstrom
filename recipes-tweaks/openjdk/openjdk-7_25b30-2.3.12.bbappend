# Make hotspot the default VM on x86

EXTRA_OECONF_append_x86-64 = " --enable-zero=no"
EXTRA_OECONF_append_x86 = " --enable-zero=no"

#WITH_ADDITIONAL_VMS_x86-64 = ""
#WITH_ADDITIONAL_VMS_x86-64 = "--with-additional-vms=shark,cacao,jamvm"
#WITH_ADDITIONAL_VMS_x86 = ""



