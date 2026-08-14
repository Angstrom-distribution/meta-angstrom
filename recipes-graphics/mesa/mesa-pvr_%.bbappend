# mesa-pvr's 'glvnd' PACKAGECONFIG DEPENDS on libglvnd, but libglvnd also
# PROVIDES virtual/libgl, and bitbake skips a recipe entirely once a competing
# PREFERRED_PROVIDER wins that slot. With meta-ti pinning virtual/libgl to
# mesa-pvr, libglvnd is skipped and mesa-pvr's own glvnd-mode DEPENDS resolves
# to nothing: "ERROR: Nothing PROVIDES 'libglvnd'". Drop the PACKAGECONFIG
# here rather than dropping 'glvnd' from DISTRO_FEATURES distro-wide.
PACKAGECONFIG:remove = "glvnd"

# mesa-pvr-25.inc's INSANE_SKIP:${PN}-megadriver expands to the non-existent
# 'mesa-pvr-megadriver': the DRI-plugin package keeps the literal name
# 'mesa-megadriver' for drop-in compatibility with vanilla mesa. So the skip
# misses it and do_package_qa trips over the unversioned *_dri.so symlinks
# that dlopen'd DRI modules are supposed to have.
INSANE_SKIP:mesa-megadriver += "dev-so"
