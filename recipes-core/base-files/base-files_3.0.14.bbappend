FILESEXTRAPATHS := "${THISDIR}/${PN}"
 
PRINC = "9"

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

# Something during do_rootfs makes /var/run a symlink, we do not want that, so we remove it on 1st boot again
pkg_postinst_${PN} () {
#!/bin/sh
if [ "x$D" != "x" ]; then
        exit 1
fi
	
if [ -L ${localstatedir}/run ] ; then
	echo "fixing ${localstatedir}/run"
	rm ${localstatedir}/run
	mkdir ${localstatedir}/run
fi
}
