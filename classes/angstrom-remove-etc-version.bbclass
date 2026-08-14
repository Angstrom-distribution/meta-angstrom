# Not ROOTFS_POSTPROCESS_COMMAND: rootfs_reproducible (re)writes /etc/version
# late in that list and inherit order decides who runs first. Not
# IMAGE_POSTPROCESS_COMMAND either: do_image_* has already packed the rootfs
# by then. do_rootfs:append runs after the whole task body, before do_image.
python do_rootfs:append() {
    import os
    version_file = d.expand("${IMAGE_ROOTFS}/etc/version")
    if os.path.exists(version_file):
        os.remove(version_file)
}
