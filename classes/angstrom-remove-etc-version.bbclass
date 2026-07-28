# do_rootfs:append, not ROOTFS_POSTPROCESS_COMMAND: rootfs_reproducible (which
# (re)writes /etc/version) runs late in ROOTFS_POSTPROCESS_COMMAND, so a plain
# ROOTFS_POSTPROCESS_COMMAND += here can still run before it depending on class
# inherit order. IMAGE_POSTPROCESS_COMMAND is too late in the other direction:
# do_image_tar/do_image_wic/etc. run between do_image and do_image_complete, so
# the image is already packed by the time IMAGE_POSTPROCESS_COMMAND runs.
# do_rootfs:append runs after the whole do_rootfs task body (all of
# ROOTFS_POSTPROCESS_COMMAND included) finishes, and do_image only starts after
# do_rootfs completes -- strictly between the two.
python do_rootfs:append() {
    import os
    version_file = d.expand("${IMAGE_ROOTFS}/etc/version")
    if os.path.exists(version_file):
        os.remove(version_file)
}
