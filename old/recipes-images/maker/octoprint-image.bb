#Angstrom image for 3d printing

require recipes-images/angstrom/systemd-image.bb

IMAGE_INSTALL += " \
        octoprint-nginx \
        screen \
        bash \
        rsync \
        sshfs-fuse \
        v4l-utils uvc-ctrl \
	python-pillow \
	avrdude \
"

export IMAGE_BASENAME = "Octoprint-image"

