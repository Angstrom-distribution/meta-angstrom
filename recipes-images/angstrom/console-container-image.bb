#Angstrom container image

require console-base-image.bb

CORE_IMAGE_EXTRA_INSTALL += " \
        podman \
        cdi \
        dtc \
        meson systemd-dev libiio-dev ninja gcc-symlinks binutils pkgconf \
"

export IMAGE_BASENAME = "container-image"


