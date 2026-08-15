# Each package's postinst runs `systemctl preset <service>` at its own
# install time during rootfs construction, against whatever preset files
# exist on the rootfs right then. Installing before 10-angstrom.preset lands
# means these units get enabled (systemd's default without a matching preset)
# and no later preset file re-runs that decision -- confirmed live, both came
# up enabled+active. Force it instead of depending on install order. There is
# no busybox-klogd package: klogd.service ships inside busybox-syslog and
# follows it via Also=, so one override governs both.
SYSTEMD_AUTO_ENABLE:${PN}-syslog = "disable"
