require systemd-image.bb

inherit populate_sdk_qt5

EXTRA_IMAGE_FEATURES += "splash"

IMAGE_INSTALL += " \
	packagegroup-qt5-qtcreator-debug \
	packagegroup-qt5 \
	packagegroup-qt5-apps \
	xinput-calibrator \
	systemd-analyze \
"
export IMAGE_BASENAME = "systemd-QT5-image"

