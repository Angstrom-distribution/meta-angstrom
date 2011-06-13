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
	connman-gnome connman-plugin-ntpd \
	gnome-panel \
	gnome-icon-theme angstrom-gnome-icon-theme-enable \
	gtk-engine-clearlooks gtk-theme-clearlooks \
	gnome-themes \
	upower udisks \
	gnome-disk-utility \
	nautilus \
	matchbox-terminal \
	gpe-scap \
	${XSERVER} \
	bash \
"

export IMAGE_BASENAME = "systemd-GNOME-image"

