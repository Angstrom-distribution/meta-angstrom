# qcomflash is meta-qcom's own image type: a tarball bundling rootfs/boot/
# DTB/GPT partition bins/rawprogram+patch XML/Firehose loader/bootloader,
# the artifact LAVA's qcomflash (QDL) deploy expects. Nothing inherits
# image_types_qcom.bbclass by default, so IMAGE_FSTYPES alone leaves
# IMAGE_CMD:qcomflash undefined and breaks parsing of every image recipe
# in the build -- both lines are needed.
IMAGE_CLASSES += "image_types_qcom"
IMAGE_FSTYPES += "qcomflash"
