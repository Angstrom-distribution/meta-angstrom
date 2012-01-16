INHERIT += "package_ipk"

EXTRAOPKGCONFIG =  "opkg-config-base angstrom-feed-configs"

DISTRO_UPDATE_ALTERNATIVES = "update-alternatives-cworth"

# we need the same provider for opkg and u-a
PREFERRED_PROVIDER_virtual/update-alternatives-native ?= "opkg-native"
PREFERRED_PROVIDER_virtual/update-alternatives = "opkg"
VIRTUAL-RUNTIME_update-alternatives = "opkg"

