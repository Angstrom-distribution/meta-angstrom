DEPENDS += "pango orc orc-native"
EXTRA_OECONF += "--enable-orc --enable-pango"

SRC_URI = "git://anongit.freedesktop.org/gstreamer/gst-plugins-base;branch=0.10 \
          "
SRCREV = "f97c9d5213f14f12dc056b9eaa2253325bc3a18c"

require recipes-tweaks/gstreamer/gstupdate.inc
