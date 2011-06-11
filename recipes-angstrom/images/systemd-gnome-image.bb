require systemd-image.bb

XSERVER ?= "xserver-xorg \
           xf86-input-evdev \
           xf86-input-mouse \
           xf86-video-fbdev \
           xf86-input-keyboard \
"

IMAGE_INSTALL += " \
	gnome-settings-daemon gnome-control-center \
	gdm-systemd angstrom-gdm-autologin-hack \
	connman-gnome \
	gnome-panel \
	gnome-icon-theme angstrom-gnome-icon-theme-enable \
	gnome-themes \
	upower udisks \
	nautilus \
	matchbox-terminal \
	gtk-engine-clearlooks gtk-theme-clearlooks \
	${XSERVER} \
"
export IMAGE_BASENAME = "systemd-GNOME-image"

