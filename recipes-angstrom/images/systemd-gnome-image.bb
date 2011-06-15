require systemd-image.bb

IMAGE_INSTALL += " \
	task-gnome \
	task-gnome-apps \
	task-gnome-themes \
	task-gnome-xserver-base \
	task-gnome-xserver \
	task-gnome-fonts \
"

export IMAGE_BASENAME = "systemd-GNOME-image"

