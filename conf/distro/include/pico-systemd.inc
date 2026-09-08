# systemd configuration for angstrom-pico

# pam, polkit, and efi drop out for free via DISTRO_FEATURES:remove in
# angstrom-pico.conf plus systemd's own filtering over DISTRO_FEATURES --
# no PACKAGECONFIG line needed for them here.
#
# resolved and nss-resolve must go together: leaving 'resolve' in
# nsswitch.conf with no daemon behind it fails every lookup.
#
# Deliberately kept: link-udev-shared (libsystemd-shared dedup is cheaper
# than the duplicated code), networkd + timesyncd (pico-resolv replaces
# resolved), logind + sysusers/gshadow/utmp (TTY/SSH login and loginctl
# still need these without PAM), myhostname + nss (musl workarounds pico
# must not inherit since it's glibc).
#
# kmod is not removed: still needed as long as kernel modules are built,
# separate from this trimming pass.
#
# hostnamed and timedated are deliberately kept -- systemd-conf's
# DHCP-supplied-timezone path and hostnamectl/timedatectl need them.
PACKAGECONFIG:remove:pn-systemd = " \
    machined nss-mymachines userdb homed importd portabled \
    localed \
    resolved nss-resolve \
    hibernate quotacheck ima idn backlight vconsole \
    osc-context set-time-epoch wheel-group \
"

# systemd's RRECOMMENDS:${PN} pulls pam-plugin-umask whenever PACKAGECONFIG
# has 'logind', regardless of the pam DISTRO_FEATURE. pico keeps logind, so
# without this, opkg installs pam-plugin-umask and its libpam dependency
# chain for a stack nothing on pico calls into (dropbear has no PACKAGECONFIG
# for it; shadow is built --without-libpam).
RRECOMMENDS:systemd:remove = "pam-plugin-umask"

# Presets are evaluated at each package's own install time during rootfs
# construction. A package installed before a preset file exists gets
# systemd's default (enable), and a later preset never retroactively
# re-applies. Force these explicitly instead, same as busybox_%.bbappend.
SYSTEMD_AUTO_ENABLE:systemd-firstboot = "disable"
SYSTEMD_AUTO_ENABLE:fstrim-timer = "disable"
SYSTEMD_AUTO_ENABLE:grow-rootfs = "disable"
