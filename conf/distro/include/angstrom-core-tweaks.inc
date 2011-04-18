# This include is to import changes from OE that are hard to overlay into core

# Providers

# Prefer the one from meta-oe over the one in oe-core
PREFERRED_PROVIDER_virtual/libx11 = "libx11"
PREFERRED_PROVIDER_virtual/xserver-xf86 = "xserver-xorg"
PREFERRED_PROVIDER_virtual/xserver = "xserver-xorg"

# bitbake.conf stuff:

TOOLCHAIN_PATH ?= "${STAGING_DIR_NATIVE}${prefix_native}/bin/${BASE_PACKAGE_ARCH}${HOST_VENDOR}-${HOST_OS}"
TOOLCHAIN_SYSPATH ?= "${TOOLCHAIN_PATH}/${TARGET_SYS}"

# Angstrom cares for uclibc and eglibc
STAGING_DIR_TARGET = "${STAGING_DIR}/${MACHINE}-${ANGSTROMLIBC}"
STAGING_DIR_HOST = "${STAGING_DIR}/${MACHINE}-${ANGSTROMLIBC}"
SSTATE_MANIFESTS = "${TMPDIR}/sstate-control-${ANGSTROMLIBC}"
# Image.bbclass stuff:

#
# udev, devfsd, busybox-mdev (from busybox) or none
#
IMAGE_DEV_MANAGER ?= "${@base_contains("MACHINE_FEATURES", "kernel26",  "udev","",d)} "
#
# sysvinit, upstart
#
IMAGE_INIT_MANAGER ?= "sysvinit sysvinit-pidof"
IMAGE_INITSCRIPTS ?= "initscripts"
#
# tinylogin, getty
#
IMAGE_LOGIN_MANAGER ?= "tinylogin"

# set sane default for the SPLASH variable
SPLASH ?= ""

IMAGE_KEEPROOTFS ?= ""
IMAGE_KEEPROOTFS[doc] = "Set to non-empty to keep ${IMAGE_ROOTFS} around after image creation."

IMAGE_BOOT ?= "${IMAGE_INITSCRIPTS} \
               ${IMAGE_DEV_MANAGER} \
               ${IMAGE_INIT_MANAGER} \
               ${IMAGE_LOGIN_MANAGER}"

# some default locales
IMAGE_LINGUAS ?= "de-de fr-fr en-gb"

IMAGE_INSTALL += "${IMAGE_BOOT}"


# SDK stuff
SDK_NAME = "${DISTRO}-${DISTRO_VERSION}-${SDK_ARCH}-${BASE_PACKAGE_ARCH}-${TARGET_OS}-${ANGSTROMLIBC}"
TOOLCHAIN_OUTPUTNAME ?= "${SDK_NAME}-toolchain"
