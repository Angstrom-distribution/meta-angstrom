require recipes-graphics/xorg-driver/xorg-driver-video.inc
DESCRIPTION = "X.Org X server -- OpenGL based 2D rendering acceleration library"

DEPENDS += "libdrm"

LIC_FILES_CHKSUM = "file://COPYING;md5=c7f5e33031114ad132cb03949d73a8a8"

PV = "0.6.0+gitr${SRCPV}"
SRCREV = "081a53703128d8aa2368856bab289759c593ccff"

SRC_URI = "git://anongit.freedesktop.org/xorg/driver/glamor"

S = "${WORKDIR}/git"

PACKAGECONFIG ??= "gl"

PACKAGECONFIG[gl] = "--disable-glamor-gles2,,virtual/libgl"
PACKAGECONFIG[gles2] = "--enable-glamor-gles2,,virtual/libgles2"

FILES_${PN} += "${datadir}/X11 ${libdir}/xorg"
FILES_${PN}-dbg += "${libdir}/xorg/modules/.debug"

