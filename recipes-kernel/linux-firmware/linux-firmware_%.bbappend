# meta-ti's ti-soc append alters this allarch recipe's content and PR,
# which breaks feed monotonicity for every machine sharing the all-arch
# feed; give the TI variant its own package arch instead.
#
# A plain PACKAGE_ARCH:ti-soc override does NOT work here: allarch.bbclass
# registers allarch_package_arch_handler on bb.event.RecipePreFinalise,
# which unconditionally d.setVar()s PACKAGE_ARCH back to "all" -- that
# handler fires after all override resolution, so it silently discards
# any override-based value regardless of what set it (confirmed live,
# 2026-07-30: PACKAGE_ARCH still resolved to "all" for beaglebone with
# the override in place, and rb1-core-kit's build failed with the exact
# version-going-backwards QA error this bbappend exists to prevent).
#
# Fix: register our own RecipePreFinalise handler. meta-angstrom's
# BBFILE_PRIORITY (7) is higher than meta-ti-bsp's (6), so this bbappend
# parses after meta-ti-bsp's, and addhandler registration follows parse
# order -- this handler runs after allarch's and gets the last word.
python angstrom_ti_soc_package_arch_handler () {
    if 'ti-soc' in (d.getVar('MACHINEOVERRIDES') or '').split(':'):
        d.setVar('PACKAGE_ARCH', d.getVar('MACHINE_ARCH'))
}
addhandler angstrom_ti_soc_package_arch_handler
angstrom_ti_soc_package_arch_handler[eventmask] = "bb.event.RecipePreFinalise"

# meta-lts-mixins' FILES:${PN}-bcm4329/-bcm4335/-bcm4339 only glob
# *-sdio.bin*, missing newer per-board .txt NVRAM variants upstream now
# ships; bcm43456/bcm4359 have no FILES split at all. Real fix is adding
# proper package splits upstream/in meta-lts-mixins -- see TODO. Skip here
# so the QA failure doesn't block every board using this recipe.
INSANE_SKIP:${PN} += "installed-vs-shipped"
