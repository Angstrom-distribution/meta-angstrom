DEPENDS += "orc orc-native"
EXTRA_OECONF += "--enable-orc"

SRC_URI = "git://anongit.freedesktop.org/gstreamer/gst-plugins-good;branch=0.10 \
          "
SRCREV = "7691f6d0c2dcaa900da8e08a2301302611e70810"

require recipes-tweaks/gstreamer/gstupdate.inc
