require recipes-graphics/xorg-driver/xorg-driver-video.inc

LIC_FILES_CHKSUM = "file://COPYING;md5=aabff1606551f9461ccf567739af63dc"

SUMMARY = "X.Org X server -- ATI Radeon video driver"

DESCRIPTION = "Open-source X.org graphics driver for ATI Radeon graphics"

DEPENDS += "virtual/libx11 libxvmc drm xf86driproto glproto \
            virtual/libgl xineramaproto libpciaccess"
RDEPENDS_${PN} += "xserver-xorg-module-exa"

COMPATIBLE_HOST = '(i.86|x86_64).*-linux'

PV = "7.5.0+git${SRCPV}"
SRC_URI = "git://anongit.freedesktop.org/xorg/driver/xf86-video-ati"
SRCREV = "068a59e010ce6bfcd54f5a18cc08c55c54b8618d"

S = "${WORKDIR}/git"

