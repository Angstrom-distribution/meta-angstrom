# Small image that launches the Enlightenment desktop without a display manager

require console-image.bb

XSERVER ?= "xserver-xorg \
            xf86-video-fbdev \
            xf86-input-evdev \
           "

IMAGE_INSTALL += " \
    e-wm-config-angstrom e-wm-config-default \
    xserver-nodm-init \
    xserver-common \
    ttf-dejavu-sans ttf-dejavu-sans-mono ttf-dejavu-common \
    ${XSERVER} \
"

export IMAGE_BASENAME = "efl-nodm-image"
