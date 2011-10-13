# This include is to import changes from OE that are hard to overlay into core

# Empty commercial license, we want to build a superset for the default config
COMMERCIAL_LICENSE = ""

# Tuning and package arch compatibility

# Turn armv7a-vfp-neon into armv7a for compatibility reasons
# In the long term the switch to the longer arch needs to be made,
# but that needs a coordinated effort
PKGARCHCOMPAT_ARMV7A = True

# Providers

# Prefer the one from meta-oe over the one in oe-core
PREFERRED_PROVIDER_virtual/libx11 = "libx11"
PREFERRED_PROVIDER_virtual/xserver-xf86 = "xserver-xorg"
PREFERRED_PROVIDER_virtual/xserver = "xserver-xorg"

XSERVER_qemux86 = "xserver-xorg \
                   mesa-dri-driver-swrast \
                   xf86-input-vmmouse \
                   xf86-input-keyboard \
                   xf86-input-evdev \
                   xf86-video-vmware \
                   qemugl"

XSERVER_qemux86-64 = "xserver-xorg \
                      mesa-dri-driver-swrast \
                      xf86-input-vmmouse \
                      xf86-input-keyboard \
                      xf86-input-evdev \
                      xf86-video-vmware \
                      qemugl"


# bitbake.conf stuff:

TOOLCHAIN_PATH ?= "${STAGING_DIR_NATIVE}${prefix_native}/bin/${TUNE_PKGARCH}${HOST_VENDOR}-${HOST_OS}"
TOOLCHAIN_SYSPATH ?= "${TOOLCHAIN_PATH}/${TARGET_SYS}"

# Image.bbclass stuff:

# Override default fs-perms.txt that messes with our /var setup:
FILESYSTEM_PERMS_TABLES = "fs-perms-angstrom.txt"
 
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

PREFERRED_PROVIDER_libltdl-dev = "libtool"
PREFERRED_PROVIDER_libtool = "libtool"

SDK_NAME = "${DISTRO}-${TCLIBC}-${SDK_ARCH}-${FEED_ARCH}"
TOOLCHAIN_OUTPUTNAME ?= "${SDK_NAME}-${DISTRO_VERSION}-toolchain"
