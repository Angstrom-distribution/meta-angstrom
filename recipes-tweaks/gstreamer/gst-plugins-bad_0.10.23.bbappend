DEPENDS += "orc orc-native"
EXTRA_OECONF =  "--disable-directfb --disable-examples --disable-vdpau --disable-apexsink --enable-experimental --enable-orc --disable-eglgles"


SRC_URI = "git://anongit.freedesktop.org/gstreamer/gst-plugins-bad;branch=0.10 \
           file://0001-opencv-fix-warnings-and-build-against-opencv-2.4.x.patch \
           file://0002-configure-opencv-plugin-builds-with-newer-versions-u.patch \
          "
SRCREV = "b8a6a7d1456691a1344a544f8fe6d86d1e0b584f"

require recipes-tweaks/gstreamer/gstupdate.inc
