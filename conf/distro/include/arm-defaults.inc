# This function changes the default tune for machines which
# are based on armv7a to use common tune value, note that we enforce hard-float
# which is default on Ångström for armv7+
# so if you have one of those machines which are armv7a but can't support
# hard-float, please change tune = 'armv7athf' to tune = 'armv7at'
# below but then this is for your own distro, Ångström will not support
# it
# - Khem

def arm_tune_handler(d):
    tune = d.getVar('DEFAULTTUNE', True)
    features = d.getVar('TUNE_FEATURES', True).split()
    # TI am62xx (pocketbeagle2 et al) is Cortex-A53 but exposes no
    # cortex-specific TUNE_FEATURES token to match on below. SOC_FAMILY is
    # unset on every non-meta-ti machine, hence the `or ''`.
    if 'am62xx' in (d.getVar('SOC_FAMILY', True) or '').split(':'):
        return 'armv8a-crc'
    # Crypto extensions are optional ARMv8 IP: plenty of silicon here
    # (BCM2837, Amlogic GXL, several Snapdragon/QCM parts) ships without
    # them and SIGILLs on -crypto codegen. Deliberately no early return for
    # tunes carrying a literal 'nocrypto' token (meta-meson's
    # libretech-ac.conf): those still need normalizing, Angstrom has no
    # cortexFOO-named package arches.
    if 'armv7a' in features or 'armv7ve' in features or 'cortexa5' in features or 'cortexa7' in features or 'cortexa8' in features or 'cortexa9' in features or 'cortexa15' in features or 'cortexa17' in features:
        tune = 'armv7athf'
        if 'bigendian' in features:
            tune += 'b'
        if 'neon' in features:
            tune += '-neon'
    if 'armv8a' in features or 'cortexa32' in features or 'cortexa34' in features or 'cortexa35' in features or 'cortexa53' in features or 'cortexa57' in features or 'cortexa72' in features or 'cortexa73' in features:
        tune = 'armv8a-crc'
    if 'armv8-2a' in features or 'cortexa55' in features or 'cortexa65' in features or 'cortexa65ae' in features or 'cortexa75' in features or 'cortexa76' in features or 'cortexa76ae' in features or 'cortexa77' in features or 'cortexa78' in features or 'cortexa78ae' in features or 'cortexa78c' in features or 'cortexx1' in features or 'cortexx1c' in features or 'neoversen1' in features or 'neoversee1' in features:
        tune = 'armv8-2a'
    if 'armv8-4a' in features or 'neoversev1' in features or 'neoverse512tvb' in features:
        tune = 'armv8-4a'
    # armv9a: Cortex-A510/A710/A715, Cortex-X2/X3, Neoverse N2/V2
    if 'armv9a' in features or 'cortexa510' in features or 'cortexa710' in features or 'cortexa715' in features or 'cortexx2' in features or 'cortexx3' in features or 'neoversen2' in features or 'neoversev2' in features:
        tune = 'armv9a'
    return tune

DEFAULTTUNE:angstrom := "${@arm_tune_handler(d)}"
