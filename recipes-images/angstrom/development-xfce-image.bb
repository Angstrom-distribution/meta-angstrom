# Image with a GNOME2 desktop and various tools installed

CONMANPKGS = "connman connman-angstrom-settings connman-plugin-loopback connman-plugin-ethernet connman-plugin-wifi connman-gnome"

require systemd-image.bb

ARCHTOOLS = ""
ARCHTOOLS_x86 = "dmidecode"

IMAGE_INSTALL += " \
	xinput-calibrator \
	systemd-analyze \
	\
	packagegroup-xfce-base \
	packagegroup-gnome-xserver-base \
	packagegroup-core-x11-xserver \
	packagegroup-gnome-fonts \
	angstrom-gnome-icon-theme-enable gtk-engine-clearlooks gtk-theme-clearlooks angstrom-clearlooks-theme-enable \
	\
	angstrom-gdm-autologin-hack angstrom-gdm-xfce-hack gdm \
	\
	packagegroup-sdk-target \
	\
	bash \
        pciutils \
	usbutils \
	i2c-tools \
	parse-edid \
	memtester \
	alsa-utils \
	devmem2 \
	iw \
	bonnie++ \
	hdparm \
	iozone3 \
	iperf \
	lmbench \
	rt-tests \
	evtest \
	bc \
	fb-test \
	tcpdump \
	procps \
	util-linux \
	coreutils \
	ethtool \
	bridge-utils \
	wget \
	screen \
	minicom \
	rsync \
	vim vim-vimrc \
	${ARCHTOOLS} \
	\
	git \
	\
	e2fsprogs-mke2fs \
	dosfstools \
	parted \
	xfsprogs \
	btrfs-tools \
	\
	python-core python-modules \
"

export IMAGE_BASENAME = "development-XFCE-image"

IMAGE_PREPROCESS_COMMAND += "do_delete_gnome_session ; "

do_delete_gnome_session () {
	rm -f ${IMAGE_ROOTFS}${datadir}/xsessions/gnome.desktop
}

