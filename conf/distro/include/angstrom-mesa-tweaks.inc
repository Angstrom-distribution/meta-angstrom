# Support ATI cards for x86-64

PACKAGECONFIG_pn-llvm3.3 = "r600"
PACKAGECONFIG_pn-llvm3.3-native = "r600"
PACKAGECONFIG_pn-mesa_x86-64 = " egl gles dri x11 wayland r600 gallium gallium-llvm"
