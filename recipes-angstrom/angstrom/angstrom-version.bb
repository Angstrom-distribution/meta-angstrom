LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PV = "${DISTRO_VERSION}"
PR = "r11"
PE = "2"

SRC_URI = "file://lsb_release"

PACKAGES = "${PN}"
PACKAGE_ARCH = "${MACHINE_ARCH}"

def get_layers(bb, d):
	layers = (bb.data.getVar("BBLAYERS", d, 1) or "").split()
	layers_branch_rev = ["%-17s = \"%s:%s\"" % (os.path.basename(i), \
		base_get_metadata_git_branch(i, None).strip().strip('()'), \
		base_get_metadata_git_revision(i, None)) \
			for i in layers]
	i = len(layers_branch_rev)-1
	p1 = layers_branch_rev[i].find("=")
	s1= layers_branch_rev[i][p1:]
	while i > 0:
		p2 = layers_branch_rev[i-1].find("=")
		s2= layers_branch_rev[i-1][p2:]
		if s1 == s2:
			layers_branch_rev[i-1] = layers_branch_rev[i-1][0:p2]
			i -= 1
		else:
			i -= 1
			p1 = layers_branch_rev[i].find("=")
			s1= layers_branch_rev[i][p1:]

	layertext = "Configured Openembedded layers:\n%s\n" % '\n'.join(layers_branch_rev)
	layertext = layertext.replace('<','')
	layertext = layertext.replace('>','')
	return layertext

do_install() {
	install -d ${D}${sysconfdir}
	echo "Angstrom ${DISTRO_VERSION} (Core edition)" > ${D}${sysconfdir}/angstrom-version
	echo "Built from branch: ${METADATA_BRANCH}" >> ${D}${sysconfdir}/angstrom-version
	echo "Revision: ${METADATA_REVISION}" >> ${D}${sysconfdir}/angstrom-version
	echo "Target system: ${TARGET_SYS}" >> ${D}${sysconfdir}/angstrom-version

	echo "${@get_layers(bb, d)}" > ${D}${sysconfdir}/angstrom-build-info

	echo "NAME=Angstrom" > ${D}${sysconfdir}/os-release
	echo "ID=angstrom" >> ${D}${sysconfdir}/os-release
	echo "PRETTY_NAME=The Ångström Distribution" >> ${D}${sysconfdir}/os-release
	echo "ANSI_COLOR=1;35" >> ${D}${sysconfdir}/os-release
	
	install -d ${D}${bindir}
	install -m 0755 ${WORKDIR}/lsb_release ${D}${bindir}/
}
