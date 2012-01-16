INHERIT += "package_ipk"

ROOTFS_PKGMANAGE = "opkg opkg-config-base angstrom-feed-configs"

DISTRO_UPDATE_ALTERNATIVES = "update-alternatives-cworth"

PREFERRED_PROVIDER_virtual/update-alternatives-native ?= "opkg-native"
PREFERRED_PROVIDER_virtual/update-alternatives = "update-alternatives-cworth"
