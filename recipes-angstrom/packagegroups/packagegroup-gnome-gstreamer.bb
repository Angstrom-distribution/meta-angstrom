DESCRIPTION = "Task for a image with gstreamer"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r13"

inherit packagegroup

# for backwards compatibility
RPROVIDES_${PN} += "packagegroup-gnome3-gstreamer"

DEPENDS_${PN} = " \
  gst-plugins-base \
  gst-plugins-good \
  gst-plugins-bad \
  gst-plugins-ugly \
"

RDEPENDS_${PN} = " \
  gst-ffmpeg \
  gst-plugins-base-meta \
"
