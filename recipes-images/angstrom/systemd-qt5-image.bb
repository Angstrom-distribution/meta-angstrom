require systemd-image.bb

inherit features_check

inherit populate_sdk_qt5

REQUIRED_DISTRO_FEATURES = "wayland"

EXTRA_IMAGE_FEATURES += "splash"

IMAGE_INSTALL += " \
	packagegroup-qt5-qtcreator-debug \
	packagegroup-qt5 \
	packagegroup-qt5-apps \
	${@bb.utils.contains('DISTRO_FEATURES', 'wayland', 'qtwayland-plugins', '', d)} \
	systemd-analyze \
	weston \
	weston-init \
"
export IMAGE_BASENAME = "systemd-QT5-image"

