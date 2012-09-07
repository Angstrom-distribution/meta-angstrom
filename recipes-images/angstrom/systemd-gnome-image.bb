require systemd-image.bb

IMAGE_INSTALL += " \
	packagegroup-gnome \
	packagegroup-gnome-apps \
	packagegroup-gnome-themes \
	packagegroup-gnome-xserver-base \
	packagegroup-core-x11-xserver \
	packagegroup-gnome-fonts \
"

export IMAGE_BASENAME = "systemd-GNOME-image"

