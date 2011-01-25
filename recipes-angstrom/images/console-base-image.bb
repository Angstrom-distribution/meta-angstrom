#Angstrom bootstrap image

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${POKYBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

IMAGE_PREPROCESS_COMMAND = "rootfs_update_timestamp"

ANGSTROM_EXTRA_INSTALL ?= ""

ZZAPSPLASH = ' ${@base_contains("MACHINE_FEATURES", "screen", "psplash-zap", "",d)}'

DEPENDS = "task-base \
           ${SPLASH} \
           ${ZZAPSPLASH} \
	   "

IMAGE_INSTALL += "task-base \
	    ${ANGSTROM_EXTRA_INSTALL} \
	    ${SPLASH} \
	    ${ZZAPSPLASH} \
	    task-boot \
        update-alternatives-cworth \
"

IMAGE_LINGUAS = ""

inherit image
