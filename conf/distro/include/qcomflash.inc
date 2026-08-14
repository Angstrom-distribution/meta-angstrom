# qcomflash is meta-qcom's own IMAGE_TYPES entry: a tarball bundling
# rootfs/boot/DTB/GPT partition bins/rawprogram+patch XML/Firehose loader/
# bootloader, the artifact LAVA's qcomflash deploy strategy (QDL) expects.
# Nothing inherits image_types_qcom.bbclass by default, so IMAGE_FSTYPES
# alone leaves IMAGE_CMD:qcomflash undefined and breaks parsing of every
# image recipe in the build. Both lines together, as meta-qcom's own
# ci/base.yml does. Required from angstrom.conf under the qcom override.
IMAGE_CLASSES += "image_types_qcom"
IMAGE_FSTYPES += "qcomflash"
