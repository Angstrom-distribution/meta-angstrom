# Real-hardware bring-up image for angstrom-pico: console-base-image's
# fuller dev toolset, rebuilt under pico's trimming. pico-image.bb is the
# separate, deliberately minimal production target.

require recipes-images/angstrom/console-base-image.bb

export IMAGE_BASENAME = "console-pico-image"

# USB composite gadget (ACM serial console + CDC-Subset networking), see
# recipes-connectivity/usb-multi-gadget/. Bring-up tooling, not shipped in
# the minimal production image.
CORE_IMAGE_EXTRA_INSTALL += " usb-multi-gadget"

# iiotool DEPENDS on systemd, so only install under a systemd distro --
# same guard console-base-image.bb already uses above for systemd-networkd.
CORE_IMAGE_EXTRA_INSTALL += " ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'iiotool', '', d)}"

CORE_IMAGE_EXTRA_INSTALL += " python3 python3-modules"

# PANU client for Bluetooth PAN bring-up. Ships with its service disabled
# by default (needs a paired MAC in /etc/bt-pan-client.conf), so it's safe
# even on machines with no Bluetooth adapter.
CORE_IMAGE_EXTRA_INSTALL += " bluetooth-pan-client"

# alsa-utils-speaker-test only carries the WAV files; speaker-test itself
# is in the main alsa-utils package. Both needed for audio bring-up.
CORE_IMAGE_EXTRA_INSTALL += " alsa-utils alsa-utils-speaker-test"

CORE_IMAGE_EXTRA_INSTALL += " python3-dbus"

# resolved is dropped from this distro's systemd build (pico-systemd.inc);
# pico-resolv is the replacement and must be installed explicitly here too.
CORE_IMAGE_EXTRA_INSTALL += " pico-resolv"

# qemuarmv5 has no wireless hardware, so iwd has nothing to manage and
# restart-loops on boot. Excluded there only; real boards with wifi keep it.
CORE_IMAGE_EXTRA_INSTALL:remove:qemuarmv5 = "iwd"
