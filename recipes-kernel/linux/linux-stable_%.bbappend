# Distro-wide baseline kernel config (nftables, containers, IWD, zram)
require recipes-kernel/linux/angstrom-kernel-features.inc

# angstrom-pico's own minimal kernel config.
SRC_URI:append:angstrom-pico = " file://angstrom-pico.cfg"

# qemuarmv5 has no board-specific KBUILD_DEFCONFIG in meta-linux-mainline.
# versatile_defconfig matches arm/versatile-pb.dtb (ARMv5TE, the QEMU
# "versatile" board's core).
KBUILD_DEFCONFIG:qemuarmv5 = "versatile_defconfig"

# qemuarmv5.conf's QB_DTB wants "zImage-versatile-pb.dtb" in DEPLOY_DIR;
# without this nothing builds or deploys a devicetree and runqemu fails
# with "DTB not found". Mainline reorganised ARM dts files into per-vendor
# subdirs; versatile-pb.dts still lives under arch/arm/boot/dts/arm/.
KERNEL_DEVICETREE:qemuarmv5 = "arm/versatile-pb.dtb"

# XZ over gzip/LZO: smallest kernel-image by a wide margin, and the 32 MiB
# NAND budget has to share space with an initramfs. xz-native is what the
# in-tree zImage self-decompression stub shells out to at build time;
# oe-core's linux-stable recipe doesn't carry it by default.
DEPENDS:append:angstrom-pico = " xz-native"
