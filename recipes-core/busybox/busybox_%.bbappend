# SYSTEMD_AUTO_ENABLE defaults to "enable" (systemd.bbclass), and each
# package's postinst runs `systemctl preset <service>` at that package's OWN
# install time during rootfs construction - evaluating whatever preset files
# exist on the rootfs at that moment, not the final image state. If these
# packages install before meta-angstrom's 10-angstrom.preset lands, they get
# enabled right then (no matching preset yet -> systemd's own default is
# enable) and a preset file installed afterward never retroactively re-runs
# that enablement. Confirmed live: booted under emulation, both units came up
# enabled+active despite the correct "disable" lines already being present in
# 10-angstrom.preset.
#
# Force it deterministically instead of depending on install order. There is
# no separate busybox-klogd package (klogd.service ships inside
# busybox-syslog and rides its enable/disable state via that unit's own
# Also=busybox-klogd.service in its [Install] section), so one override here
# governs both.
SYSTEMD_AUTO_ENABLE:${PN}-syslog = "disable"
