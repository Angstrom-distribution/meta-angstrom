# Fixes for meta-ti-bsp's "k3r5" multiconfig (BBMULTICONFIG += "k3r5",
# conf/multiconfig/k3r5.conf) -- the R5/baremetal firmware build (TF-A,
# OP-TEE, R5 SPL) that runs alongside the main build on every K3-family
# board (AM62x, AM64x, J721e, ...). k3r5 already isolates its own TMPDIR
# (TI_TMPDIR_APPEND, "-k3r5") so byte-identical *-native/cross recipes
# shared with the main build don't normally collide -- these three fixes
# close the gaps this distro's own policy opened in that isolation.

# Give k3r5 its own private deploy dir, following TMPDIR. Angstrom's own
# TOPDIR-based DEPLOY_DIR (see the "Put deploy front and center" block
# above) collapses both multiconfigs onto one shared deploy/licenses+spdx
# tree regardless of TMPDIR separation, since do_populate_lic/
# do_create_spdx's "shared area" check is task-signature-keyed, not
# content-keyed. Check BB_CURRENT_MC directly (bitbake's own ground
# truth for the active multiconfig) rather than a ":k3r5" override --
# that only reaches recipes carrying the k3r5 token in MACHINEOVERRIDES,
# and *-native recipes don't inherit that chain.
DEPLOY_DIR = "${@ '${TMPDIR}/deploy' if d.getVar('BB_CURRENT_MC') == 'k3r5' else '${TOPDIR}/deploy' }"

# TI_COMMON_DEPLOY stays pointed at the real SHARED deploy dir (not
# ${DEPLOY_DIR}, private for k3r5 above) so k3r5's own boot-firmware
# deploy (DEPLOY_DIR_IMAGE:k3r5 in k3r5.conf) still lands where the main
# build's do_image[mcdepends] expects it.
TI_COMMON_DEPLOY = "${TOPDIR}/deploy"
DEPLOY_DIR_IMAGE:ti-soc = "${TOPDIR}/deploy/images/${MACHINE}"

# Weak default: k3r5.conf's own TCLIBC = "baremetal" (set earlier in its
# own parse) must win, or every mc:k3r5:*-native recipe collapses onto
# the same glibc signature as the main build and collides on the shared
# license/SPDX deploy path.
TCLIBC ?= "glibc"

# Drop create-spdx for the k3r5 multiconfig only. Even with DEPLOY_DIR
# correctly separated (above), byte-identical *-native/cross recipes
# shared between k3r5 and the main build hit a genuinely open, unmerged
# oe-core bug: do_create_recipe_spdx's "static document" lookup
# (oe/sbom30.py find_jsonld, keyed on PN + arch list, not multiconfig)
# can miss a dependency's SPDX doc when hash-equivalence satisfies it
# from the other multiconfig's tree instead of actually re-executing --
# confirmed still present on oe-core master (no SSTATE_PKGARCH/
# SSTATE_ARCHS_TUNEPKG disambiguation landed as of 2026-08). Full list
# is oe-core's own INHERIT_DISTRO default (defaultsetup.conf) minus
# create-spdx; SBOM/license tracking for the R5 firmware blob is a
# reasonable scope reduction until upstream lands a real fix.
INHERIT_DISTRO = "${@ 'debian devshell sstate license remove-libtool buildstats uninative' if d.getVar('BB_CURRENT_MC') == 'k3r5' else 'debian devshell sstate license remove-libtool create-spdx buildstats uninative' }"
