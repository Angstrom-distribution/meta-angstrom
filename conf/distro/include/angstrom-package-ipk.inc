INHERIT += "package_ipk"

ANGSTROM_FEED_CONFIGS ?= "angstrom-feed-configs"

EXTRAOPKGCONFIG = "opkg-config-base angstrom-feed-configs"

DISTRO_UPDATE_ALTERNATIVES ?= "update-alternatives-cworth"

# we need the same provider for opkg and u-a
PREFERRED_PROVIDER_virtual/update-alternatives-native ?= "opkg-native"
PREFERRED_PROVIDER_virtual/update-alternatives ?= "update-alternatives-cworth"
VIRTUAL-RUNTIME_update-alternatives ?= "update-alternatives-cworth"

