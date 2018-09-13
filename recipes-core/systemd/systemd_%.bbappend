FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://journald.conf"

PACKAGECONFIG_append   = " \
                   machined \
                   hostnamed \
                   timedated \
                   timesyncd \
                   localed \
                   logind \
                   firstboot \
                   utmp \
                   polkit \
                   \
                   networkd \
                   resolved \
                   iptc \
                   libidn \
                   \
                   lz4 \
                   importd \
                   journal-upload \
                   zlib \
                   bzip2 \
                   xz \
                   gcrypt \
"

do_install_append() {
        cp ${WORKDIR}/journald.conf ${D}${sysconfdir}/systemd
}

# fix pager corruption with busybox less/more
RRECOMMENDS_${PN} += "less"


pkg_postinst_${PN}_append () {
        sed -e '/^hosts:/s/\s*\<myhostname\>//' \
                -e '/^hosts:/s/\s*myhostname//' \
                -i $D${sysconfdir}/nsswitch.conf
}
