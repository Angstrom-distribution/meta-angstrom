FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://journald.conf"

PACKAGECONFIG_append   = " \
                   machined \
                   hostnamed \
                   timedated \
                   timesyncd \
                   localed \
                   logind \
                   polkit \
                   \
                   networkd \
                   resolved \
                   iptc \
                   libidn \
                   lz4 \
"

do_install_append() {
        cp ${WORKDIR}/journald.conf ${D}${sysconfdir}/systemd
}

# fix pager corruption with busybox less/more
RRECOMMENDS_${PN} += "less"
