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
    if 'armv7a' in features or 'armv7ve' in features or 'cortexa5' in features or 'cortexa7' in features or 'cortexa8' in features or 'cortexa9' in features or 'cortexa15' in features or 'cortexa17' in features:
        tune = 'armv7athf'
        if 'bigendian' in features:
            tune += 'b'
        if 'neon' in features:
            tune += '-neon'
    if 'armv8a' in features or 'cortexa32' in features or 'cortexa34' in features or 'cortexa35' in features or 'cortexa53' in features or 'cortexa57' in features or 'cortexa72' in features or 'cortexa73' in features:
        tune = 'armv8a-crc-crypto'
    if 'armv8-2a' in features or 'cortexa55' in features or 'cortexa65' in features or 'cortexa75' in features or 'cortexa76' in features or 'cortexa77' in features or 'cortexa78' in features:
        tune = 'armv8-2a-crypto'
    return tune

DEFAULTTUNE:angstrom := "${@arm_tune_handler(d)}"
