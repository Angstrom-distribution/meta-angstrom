INHERIT += "package_ipk"

ANGSTROM_FEED_CONFIGS ?= "angstrom-feed-configs"

EXTRAOPKGCONFIG = "opkg-config-base ${ANGSTROM_FEED_CONFIGS}"

ANGSTROM_IPK_UPDATE_ALTERNATIVES ?= "update-alternatives-opkg"

DISTRO_UPDATE_ALTERNATIVES = "${ANGSTROM_IPK_UPDATE_ALTERNATIVES}"

# we need the same provider for opkg and u-a
PREFERRED_PROVIDER_virtual/update-alternatives-native ?= "opkg-utils-native"
PREFERRED_PROVIDER_virtual/update-alternatives = "opkg-utils"
VIRTUAL-RUNTIME_update-alternatives = "${ANGSTROM_IPK_UPDATE_ALTERNATIVES}"

