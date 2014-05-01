# Toolchain specific virtuals
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}binutils = "binutils-cross-${TARGET_ARCH}"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}binutils-crosssdk = "binutils-crosssdk-${TARGET_ARCH}"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}gcc-initial = "gcc-cross-initial-${TARGET_ARCH}"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}gcc = "gcc-cross-${TARGET_ARCH}"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}g++ = "gcc-cross-${TARGET_ARCH}"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}compilerlibs = "gcc-runtime"
PREFERRED_PROVIDER_linux-libc-headers = "linux-libc-headers"

PREFERRED_PROVIDER_libgcc ?= "libgcc"
PREFERRED_PROVIDER_libgcc-inital ?= "libgcc-initial"
PREFERRED_PROVIDER_libgcc-nativesdk ?= "libgcc-nativesdk"
