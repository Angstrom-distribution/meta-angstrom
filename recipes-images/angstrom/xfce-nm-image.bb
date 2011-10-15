require systemd-image.bb

CONMANPKGS = "networkmanager network-manager-applet"

IMAGE_INSTALL += " \
	task-xfce-base \
	task-gnome-xserver-base \
	task-xserver \
	task-gnome-fonts \
	angstrom-gdm-autologin-hack angstrom-gdm-xfce-hack gdm gdm-systemd \
	angstrom-gnome-icon-theme-enable gtk-engine-clearlooks gtk-theme-clearlooks angstrom-clearlooks-theme-enable \
"

export IMAGE_BASENAME = "XFCE-NetworkManager-image"

