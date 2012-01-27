# uclibc:
require conf/distro/include/angstrom-uclibc.inc

USE_NLS ?= "no"
USE_NLS_glib-2.0 = "yes"
USE_NLS_glib-2.0-native = "yes"
USE_NLS_gcc-cross = "no"

TARGET_OS_UC = "uclinux${@['-uclibc','-uclibceabi'][bb.data.getVar('TARGET_ARCH',d,1) in ['arm', 'armeb']]}"

#Gcc will die with 'internal consistency error when using the above optimizations
FULL_OPTIMIZATION_avr32 = ""
BUILD_OPTIMIZATION_avr32 = ""

CXXFLAGS += "-fvisibility-inlines-hidden"

