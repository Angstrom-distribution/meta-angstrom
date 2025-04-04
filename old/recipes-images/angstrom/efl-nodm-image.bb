# Small image that launches the Enlightenment desktop without a display manager

require systemd-image.bb

EXTRA_IMAGE_FEATURES += "splash"

XSERVER ?= "xserver-xorg \
            xf86-video-fbdev \
            xf86-input-evdev \
           "

IMAGE_INSTALL += " \
    e-wm-config-angstrom e-wm-config-default e-wm-config-standard e-wm-config-illume2 xterm \
    xserver-nodm-init-systemd \
    formfactor \
    xserver-common \
    ttf-dejavu-sans ttf-dejavu-sans-mono ttf-dejavu-common \
    ${XSERVER} \
"

export IMAGE_BASENAME = "efl-nodm-image"
