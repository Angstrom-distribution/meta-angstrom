FILESEXTRAPATHS:prepend:angstrom := "${THISDIR}/${PN}:"
SRC_URI:append:angstrom = "\
                           file://journald.conf \
                           file://zram-generator.conf \
                           file://rc-local.service \
			   file://10-angstrom.preset \
"

# Enable hardware watchdog, set it to 30 seconds
WATCHDOG_RUNTIME_SEC = "30"

do_install:append:angstrom() {
	# This disables the 'mac' policy for pni-names
	# We do not want MAC address based naming, for example the wifi on RB1
	# gets a new MAC address every boot *and* doesn't support any of the
	# naming features. This leads to a new, unpredictable interface name on
	# every boot
	sed -i -e 's: mac::g' ${D}${nonarch_libdir}/systemd/network/99-default.link

	# Journald config snipping to limit IO and storage
	install -d ${D}${sysconfdir}/systemd/journald.conf.d/
	install -m 0644 ${UNPACKDIR}/journald.conf ${D}${sysconfdir}/systemd/journald.conf.d/angstrom.conf

	# enable ZRAM
	install -m 0644 ${UNPACKDIR}/zram-generator.conf ${D}${sysconfdir}/systemd/

	# install DISTRO presets
	install -d ${D}${systemd_unitdir}/system-preset
	install -m 0644 ${UNPACKDIR}/10-angstrom.preset ${D}${systemd_unitdir}/system-preset
}

SRC_URI:append:angstrom-pico = " \
	file://journald-pico.conf \
	file://zram-generator-pico.conf \
	file://20-angstrom-pico.preset \
"

do_install:append:angstrom-pico() {
	# Journald: volatile storage for pico (no persistent writes to NAND/SD).
	# The 50- prefix sorts after angstrom.conf so its keys win.
	install -d ${D}${sysconfdir}/systemd/journald.conf.d/
	install -m 0644 ${UNPACKDIR}/journald-pico.conf ${D}${sysconfdir}/systemd/journald.conf.d/50-angstrom-pico.conf

	# zram: lzo-rle at 100% of RAM, pico's compressed swap choice.
	# The pico-specific filename overrides parent distro's zstd config.
	install -d ${D}${sysconfdir}/systemd/
	install -m 0644 ${UNPACKDIR}/zram-generator-pico.conf ${D}${sysconfdir}/systemd/zram-generator.conf

	# Presets: disable first-boot services not needed on pico.
	install -d ${D}${systemd_unitdir}/system-preset
	install -m 0644 ${UNPACKDIR}/20-angstrom-pico.preset ${D}${systemd_unitdir}/system-preset
}

# Compile systemd in Thumb mode on pico (qemuarmv5's tune carries both "arm"
# and "thumb" in TUNE_FEATURES, feature-arm-thumb.inc respects this
# per-recipe). Scoped to angstrom-pico only -- angstrom itself stays ARM mode.
ARM_INSTRUCTION_SET:pn-systemd:angstrom-pico = "thumb"

