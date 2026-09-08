FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

# angstrom-base.cfg is the full distro-wide baseline (nftables, containers,
# IWD, four zram backends) -- too heavy for pico.
SRC_URI:append:angstrom = " file://angstrom-base.cfg"

# Sound is machine-scoped (qemuall), not distro-scoped. pico has already
# dropped 'alsa' from DISTRO_FEATURES and ships no ALSA userspace, so
# CONFIG_SND* is dead kernel config on pico -- dropped for pico below
# along with the base cfg.
SRC_URI:append:angstrom:qemuall = " file://angstrom-qemu-sound.cfg"

# angstrom-pico's DISTROOVERRIDES keeps "angstrom" active (so other
# :angstrom-scoped bbappends still fire for pico), which means the two
# SRC_URI:append lines above fire for pico too. Pull both back out
# explicitly, or switching qemuarmv5 to linux-stable pulls in the full
# distro-wide nftables/containers/BPF/four-zram-backend baseline pico
# exists to avoid.
SRC_URI:remove:angstrom-pico = " file://angstrom-base.cfg file://angstrom-qemu-sound.cfg"
