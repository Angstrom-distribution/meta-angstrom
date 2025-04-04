#Angstrom bootstrap image
require console-base-image.bb

DEPENDS += "packagegroup-base-extended \
	   "

IMAGE_INSTALL += "packagegroup-base-extended \
	    "

export IMAGE_BASENAME = "console-image"
