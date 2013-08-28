# Image with a GNOME2 desktop and various tools installed

CONMANPKGS = "connman connman-angstrom-settings connman-plugin-loopback connman-plugin-ethernet connman-plugin-wifi connman-gnome"

require systemd-image.bb

ARCHTOOLS = ""
ARCHTOOLS_x86 = "dmidecode"

IMAGE_INSTALL += " \
	xinput-calibrator \
	systemd-analyze \
	packagegroup-gnome \
	packagegroup-gnome-apps \
	packagegroup-gnome-themes \
	packagegroup-gnome-xserver-base \
	packagegroup-core-x11-xserver \
	packagegroup-gnome-fonts \
	\
	packagegroup-sdk-target \
	\
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

export IMAGE_BASENAME = "development-GNOME-image"

