# mesa-pvr's own 'glvnd' PACKAGECONFIG DEPENDS on libglvnd, but libglvnd also
# PROVIDES virtual/libgl, and bitbake skips a whole recipe (all its outputs,
# not just the contested virtual/*) once a competing PREFERRED_PROVIDER wins
# that slot. With PREFERRED_PROVIDER_virtual/libgl set to mesa-pvr (meta-ti's
# ti33x.inc/mesa-pvr.inc), libglvnd is skipped outright, so mesa-pvr's own
# glvnd-mode DEPENDS resolves to nothing: "ERROR: Nothing PROVIDES 'libglvnd'".
#
# glvnd-vendor mode and being the direct virtual/libgl provider are mutually
# exclusive by design (that's the entire point of GLVND dispatch), so drop it
# here rather than removing 'glvnd' from DISTRO_FEATURES distro-wide.
PACKAGECONFIG:remove = "glvnd"

# mesa-pvr-25.inc carries INSANE_SKIP:${PN}-megadriver += "dev-so" (copied
# from upstream oe-core's mesa.inc), but PN here is 'mesa-pvr', not 'mesa' --
# so it resolves to the non-existent package 'mesa-pvr-megadriver'. The
# actual DRI-plugin package is hardcoded to the literal name
# 'mesa-megadriver' (FILES:mesa-megadriver / RPROVIDES:mesa-megadriver, kept
# for drop-in compatibility with vanilla mesa), so the skip silently misses
# it and do_package_qa fails on the unversioned *_dri.so symlinks that are
# normal/expected for dlopen'd DRI driver modules.
INSANE_SKIP:mesa-megadriver += "dev-so"
