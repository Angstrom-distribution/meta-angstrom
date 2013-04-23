DEPENDS += "x264 orc orc-native"
EXTRA_OECONF += "--enable-orc"

SRC_URI = "git://anongit.freedesktop.org/gstreamer/gst-plugins-ugly;branch=0.10 \
          "
SRCREV = "9afc696e5fa9fb980e02df5637f022796763216f"

require recipes-tweaks/gstreamer/gstupdate.inc
