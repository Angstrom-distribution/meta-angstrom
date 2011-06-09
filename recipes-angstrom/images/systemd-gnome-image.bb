require systemd-image.bb

XSERVER ?= "xserver-xorg \
           xf86-input-evdev \
           xf86-input-mouse \
           xf86-video-fbdev \
           xf86-input-keyboard \
"

IMAGE_INSTALL += " \
	gdm-systemd \
	gnome-panel \
	gnome-icon-theme \
	gnome-themes \
    upower udisks \
	${XSERVER} \
"
export IMAGE_BASENAME = "systemd-GNOME-image"

