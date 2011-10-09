INHERIT += "package_ipk"

DISTRO_UPDATE_ALTERNATIVES = "update-alternatives-cworth"

PREFERRED_PROVIDER_virtual/update-alternatives-native ?= "opkg-native"
PREFERRED_PROVIDER_virtual/update-alternatives = "update-alternatives-cworth"
