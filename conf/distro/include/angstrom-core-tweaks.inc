# This include is to import changes from OE that are hard to overlay into core

# Default hash policy for distro
BB_SIGNATURE_HANDLER ?= "OEBasicHash"

# The network based PR service host and port
# Uncomment the following lines to enable PRservice.
# Set PRSERV_HOST to 'localhost' and PRSERV_PORT to '0' to automatically
# start local PRService.
# Set to other values to use remote PRService.
#PRSERV_HOST ?= "localhost"
#PRSERV_PORT ?= "0"
 

# Empty commercial license, we want to build a superset for the default config
COMMERCIAL_LICENSE = ""
LICENSE_FLAGS_WHITELIST += "commercial"

ACCEPT_QCOM_EULA = "1"

ACCEPT_EULA_dragonboard-410c-32 = "1"
ACCEPT_EULA_dragonboard-410c = "1"
ACCEPT_EULA_dragonboard-820c = "1"
ACCEPT_EULA_ifc6410 = "1"
ACCEPT_EULA_sd-600eval = "1"


# Unbreak multimachine builds
LICENSE_DIRECTORY = "${DEPLOY_DIR}/licenses/${MACHINE_ARCH}"

# Preset set this or bbappends break
PRINC ??= "0"

# Shared state fixups
MACHINEOVERRIDES[vardepsexclude] += "SOC_FAMILY"

# Tuning and package arch compatibility

# Turn armv7a-vfp-neon into armv7a for compatibility reasons
# In the long term the switch to the longer arch needs to be made,
# but that needs a coordinated effort
#PKGARCHCOMPAT_ARMV7A = "True"

# Providers

# opkg
PREFERRED_PROVIDER_opkg-collateral = "angstrom-feed-configs"
EXTRA_IMAGE_FEATURES ?= "package-management empty-root-password"

PREFERRED_PROVIDER_os-release = "angstrom-version"

# Prefer libjpeg-turbo
PREFERRED_PROVIDER_jpeg = "libjpeg-turbo"
PREFERRED_PROVIDER_jpeg-native = "libjpeg-turbo-native"

# Prefer the one from meta-oe over the one in oe-core
PREFERRED_PROVIDER_virtual/libx11 = "libx11"
PREFERRED_PROVIDER_virtual/xserver-xf86 = "xserver-xorg"
PREFERRED_PROVIDER_virtual/xserver = "xserver-xorg"

# bitbake.conf stuff:

TOOLCHAIN_PATH ?= "${STAGING_DIR_NATIVE}${prefix_native}/bin/${TUNE_PKGARCH}${HOST_VENDOR}-${HOST_OS}"
TOOLCHAIN_SYSPATH ?= "${TOOLCHAIN_PATH}/${TARGET_SYS}"

# Package manager selection, angstrom uses ANGSTROM_PKG_FORMAT

PACKAGE_CLASSES = "package_ipk"

# Image.bbclass stuff:

# Override default fs-perms.txt that messes with our /var setup:
FILESYSTEM_PERMS_TABLES = "fs-perms-angstrom.txt"
 
#
# udev, devfsd, busybox-mdev (from busybox) or none
#
IMAGE_DEV_MANAGER ?= "udev"
#
# sysvinit, upstart, systemd
#
IMAGE_INIT_MANAGER ?= "systemd"
IMAGE_INITSCRIPTS ?= "systemd-compat-units"
#
# busybox, getty
#
IMAGE_LOGIN_MANAGER ?= "busybox"

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

# Layer tweaks

# meta-opie
include conf/distro/include/preferred-opie-git-versions.inc 
PREFERRED_PROVIDER_virtual/libsdl = "libsdl"

# SDK stuff

PREFERRED_PROVIDER_libltdl-dev = "libtool"
PREFERRED_PROVIDER_libtool = "libtool"

SDK_NAME = "${DISTRO}-${TCLIBC}-${SDK_ARCH}-${FEED_ARCH}"
TOOLCHAIN_OUTPUTNAME ?= "${SDK_NAME}-${DISTRO_VERSION}-toolchain"

FREESMARTPHONE_GIT = "git://git.freesmartphone.org"
E_MIRROR = "http://download.enlightenment.org/releases"
FREEBSD_MIRROR = "ftp://ftp.freebsd.org/pub/FreeBSD/"
E_SVN = "svn://svn.enlightenment.org/svn/e"
