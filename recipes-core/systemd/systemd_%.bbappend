FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://journald.conf"

PACKAGECONFIG_append   = " \
                   ldconfig \
                   timedated \
                   timesyncd \
                   logind \
                   \
                   networkd \
                   resolved \
                   iptc \
                   libidn \
                   lz4 \
                   importd \
                   journal-upload \
                   xz \
                   zlib \
                   bzip2 \
                   gcrypt \
"

do_install_append() {
        cp ${WORKDIR}/journald.conf ${D}${sysconfdir}/systemd
}

# fix pager corruption with busybox less/more
RRECOMMENDS_${PN} += "less"
