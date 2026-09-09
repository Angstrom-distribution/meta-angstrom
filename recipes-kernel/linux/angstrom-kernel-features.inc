FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# angstrom-base.cfg is the full distro-wide baseline (nftables, containers,
# IWD, four zram backends). Every angstrom-family distro gets it, pico
# included -- DISTRO_FEATURES:remove already keeps the matching userspace
# off pico's image, so a few unused kernel symbols aren't worth a
# per-distro carve-out here.
SRC_URI:append:angstrom = " file://angstrom-base.cfg"

# Sound is machine-scoped (qemuall), not distro-scoped. pico keeps 'alsa'
# in DISTRO_FEATURES, so this has real userspace to serve there too.
SRC_URI:append:angstrom:qemuall = " file://angstrom-qemu-sound.cfg"
