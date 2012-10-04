CONMANPKGS ?= "connman connman-plugin-loopback connman-plugin-ethernet connman-plugin-wifi connman-systemd connman-gnome"

require systemd-image.bb

IMAGE_INSTALL += " \
	task-gnome \
	task-gnome-apps \
	task-gnome-themes \
	task-gnome-xserver-base \
	task-xserver \
	task-gnome-fonts \
	cronie-systemd \
	ntpdate \
	florence \
	xinput-calibrator \
	linux-firmware linux-firmware-wl12xx \
	kernel-modules \
"

export IMAGE_BASENAME = "systemd-GNOME-image"

