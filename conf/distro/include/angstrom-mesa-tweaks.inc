
# Enable R600 support in llvm for ATI GPUs

PACKAGECONFIG_pn-llvm3.3 = "r600"
PACKAGECONFIG_pn-llvm3.3-native = "r600"

PACKAGECONFIG_pn-llvm3.7.1 = "r600"
PACKAGECONFIG_pn-llvm3.7.1-native = "r600"

# Enable more tools in mesa-demos

PACKAGECONFIG_pn-mesa-demos = " drm osmesa freetype2 gbm egl gles1 gles2 glu x11 glew"


PACKAGECONFIG_pn-mesa_x86-64 = " egl gles gbm dri dri3 x11 xa r600 gallium gallium-llvm "
PACKAGECONFIG_pn-mesa_x86    = " egl gles gbm dri dri3 x11 xa r600 gallium gallium-llvm "

GALLIUMDRIVERS_LLVM33 = "${@bb.utils.contains('PACKAGECONFIG', 'r600', 'r600', '', d)}"

GALLIUMDRIVERS_append_armv7a  = ",freedreno"
GALLIUMDRIVERS_append_aarch64 = ",freedreno"

PACKAGECONFIG_pn-mesa_append_armv7a = " gallium \
                         ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xa', '', d)} \
                       "
PACKAGECONFIG_pn-mesa_append_aarch64 = " gallium \
                         ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xa', '', d)} \
                       "
