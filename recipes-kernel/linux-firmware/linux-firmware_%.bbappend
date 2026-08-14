# meta-ti's ti-soc append alters this allarch recipe's content and PR, which
# breaks feed monotonicity for every machine sharing the all-arch feed; give
# the TI variant its own package arch instead.
#
# A PACKAGE_ARCH:ti-soc override does NOT work: allarch.bbclass's own
# RecipePreFinalise handler setVar()s PACKAGE_ARCH back to "all" after every
# override is resolved (confirmed live -- beaglebone still got "all" and
# rb1-core-kit still hit the version-going-backwards QA error). Our own
# handler does: meta-angstrom's BBFILE_PRIORITY (7) beats meta-ti-bsp's (6),
# so this bbappend parses later and addhandler order follows parse order.
python angstrom_ti_soc_package_arch_handler () {
    if 'ti-soc' in (d.getVar('MACHINEOVERRIDES') or '').split(':'):
        d.setVar('PACKAGE_ARCH', d.getVar('MACHINE_ARCH'))
}
addhandler angstrom_ti_soc_package_arch_handler
angstrom_ti_soc_package_arch_handler[eventmask] = "bb.event.RecipePreFinalise"

# meta-lts-mixins' FILES:${PN}-bcm4329/-bcm4335/-bcm4339 glob only
# *-sdio.bin*, missing the per-board .txt NVRAM variants upstream now ships;
# bcm43456/bcm4359 have no FILES split at all. Real fix is proper package
# splits upstream -- see TODO.
INSANE_SKIP:${PN} += "installed-vs-shipped"
