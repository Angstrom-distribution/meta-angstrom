DESCRIPTION = "Configuration files for online package repositories aka feeds"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PV = "${DISTRO_VERSION}"
PR = "r20"
PACKAGE_ARCH = "${MACHINE_ARCH}"

FEED_BASEPATH ?= "unstable/feed/"

IWMMXT_FEED = "${@bb.utils.contains('MACHINE_FEATURES', 'iwmmxt', 'iwmmxt', '',d)}"

do_compile() {
	mkdir -p ${S}/${sysconfdir}/opkg

	# Weed out duplicates, e.g. arm1176* will show up twice
	FILTERED_FEED_ARCHS="$(echo ${FEED_ARCHS}| tr ' ' '\n' | sort | uniq | tr '\n' ' ')"

	for feed in base ; do
		rm -f ${S}/${sysconfdir}/opkg/${feed}-feed.conf
		for feed_arch in ${FILTERED_FEED_ARCHS} ; do
			echo "src/gz ${feed}-${feed_arch} ${ANGSTROM_URI}/${FEED_BASEPATH}${feed_arch}/${feed}" >> ${S}/${sysconfdir}/opkg/${feed}-feed.conf
		done
	done

	echo "src/gz ${MACHINE_ARCH} ${ANGSTROM_URI}/${FEED_BASEPATH}${FEED_ARCH}/machine/${MACHINE_ARCH}" >  ${S}/${sysconfdir}/opkg/${MACHINE_ARCH}-feed.conf
	echo "#src/gz sdk ${ANGSTROM_URI}/${FEED_BASEPATH}sdk" > ${S}/${sysconfdir}/opkg/sdk-feed.conf
	echo "src/gz no-arch ${ANGSTROM_URI}/${FEED_BASEPATH}all" > ${S}/${sysconfdir}/opkg/noarch-feed.conf
		
	# iwmmxt is a special case, add the iwmmxt feed for machine that have 'iwmmxt' in MACHINE_FEATURES
		if [ "${IWMMXT_FEED}" = "iwmmxt" ] ; then
	  echo "src/gz iwmmxt ${ANGSTROM_URI}/${FEED_BASEPATH}iwmmxt/base" > ${S}/${sysconfdir}/opkg/iwmmxt-feed.conf
	fi  

}


do_install () {
	install -d ${D}${sysconfdir}/opkg
	install -m 0644  ${S}/${sysconfdir}/opkg/* ${D}${sysconfdir}/opkg/
}

FILES:${PN} = "${sysconfdir}/opkg/base-feed.conf \
					${sysconfdir}/opkg/${MACHINE_ARCH}-feed.conf \
					${sysconfdir}/opkg/noarch-feed.conf \
					${sysconfdir}/opkg/sdk-feed.conf \
					"

CONFFILES:${PN} += "${sysconfdir}/opkg/base-feed.conf \
					${sysconfdir}/opkg/${MACHINE_ARCH}-feed.conf \
					${sysconfdir}/opkg/noarch-feed.conf \
					${sysconfdir}/opkg/sdk-feed.conf \
					"

RRECOMMENDS:${PN} += "opkg"

