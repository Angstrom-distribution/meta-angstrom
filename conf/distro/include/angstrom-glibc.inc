# glibc:
PNBLACKLIST[libiconv] = "the glibc provided iconv library is used"

TARGET_OS = "linux"

# sane-toolchain.inc has this, and explains the GNU and ABI suffices:
#TARGET_OS = "${@compute_os_portion_of_target_triplet(d)}"

TARGET_OS .= "${@['','-gnueabi'][d.getVar('TARGET_ARCH',1) in ['arm', 'armeb']]}"
TARGET_OS .= "${@['','-gnuspe'][d.getVar('TUNE_PKGARCH',1) in ['ppce500', 'ppce500v2']]}"

# perl has some problems, see http://bugs.openembedded.net/show_bug.cgi?id=1616
# [23:00] oxo: glibc (any version) on sparc does not like (repeat me) "-Os"

TARGET_LINK_HASH_STYLE = "${@['-Wl,--hash-style=gnu',''][d.getVar('TARGET_ARCH',1) in ['mips', 'mipsel', 'mips64', 'mips64el']]}"

# We use gold as default linker for arm/x86 on eglibc based images
DISTRO_FEATURES_append_arm = " ld-is-gold"
DISTRO_FEATURES_append_x86 = " ld-is-gold"
DISTRO_FEATURES_append_x86-64 = " ld-is-gold"

