FILESEXTRAPATHS := "${THISDIR}/${PN}"
 
PRINC = "14"

SRC_URI_append = " file://var-run.conf"

# Original: volatiles = "cache run log lock tmp"
# We don't any of those in volatiles, so:
volatiles = ""
dirs755 += "${localstatedir}/cache \
            ${localstatedir}/run \
            ${localstatedir}/log \
            ${localstatedir}/lock \
            ${localstatedir}/lock/subsys \
            ${localstatedir}/tmp \
            ${localstatedir}/volatile/tmp \
            /run \
           "

BASEFILESISSUEINSTALL = "do_install_angstromissue"

do_install_angstromissue () {
    echo ${MACHINE} > ${D}${sysconfdir}/hostname

    install -m 644 ${WORKDIR}/issue*  ${D}${sysconfdir}
        if [ -n "${DISTRO_NAME}" ]; then
        echo -n "${DISTRO_NAME} " >> ${D}${sysconfdir}/issue
        echo -n "${DISTRO_NAME} " >> ${D}${sysconfdir}/issue.net
        if [ -n "${DISTRO_VERSION}" ]; then
            echo -n "${DISTRO_VERSION} " >> ${D}${sysconfdir}/issue
            echo -e "${DISTRO_VERSION} \n" >> ${D}${sysconfdir}/issue.net
        fi
        echo "- Kernel \r" >> ${D}${sysconfdir}/issue
        echo >> ${D}${sysconfdir}/issue
    fi
}

do_install_append() {
	install -d ${D}${sysconfdir}/tmpfiles.d
	install -m 0644 ${WORKDIR}/var-run.conf ${D}${sysconfdir}/tmpfiles.d/
}
