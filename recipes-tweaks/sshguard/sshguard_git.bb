SUMMARY = "SSHguard protects hosts from brute-force attacks against SSH and other services."

LICENSE = "ISC"
LIC_FILES_CHKSUM = "file://COPYING;md5=47a33fc98cd20713882c4d822a57bf4d"

PV = "1.6.3+git${SRCPV}"

SRCREV = "601a4e6a1e91e0c65f1f36947c62d8f5ddbfb23a"
SRC_URI = "git://bitbucket.org/sshguard/sshguard.git;protocol=https \
           file://firewall \
           file://sshguard.service \
           file://sshguard-journalctl \
          "

S = "${WORKDIR}/git"

DEPENDS = "flex-native"

inherit autotools-brokensep systemd

EXTRA_OECONF += " --with-firewall=iptables \
                  --with-iptables=${sbindir}/iptables \
                "

do_install_append() {
    install -d ${D}${libdir}/sshguard
    install -m 0755 ${WORKDIR}/firewall ${D}${libdir}/sshguard
    install -m 0755 ${WORKDIR}/sshguard-journalctl ${D}${libdir}/sshguard

    sed -i -e s:/bin:${base_bindir}:g -e s:/usr/sbin:${sbindir}:g ${D}${libdir}/sshguard/sshguard-journalctl

    install -d ${D}${systemd_unitdir}/system
    install -m 0644 ${WORKDIR}/sshguard.service ${D}${systemd_unitdir}/system
    sed -i -e s:/usr/lib:${libdir}:g ${D}${systemd_unitdir}/system/sshguard.service 
}

FILES_${PN} += "${systemd_unitdir}"
RDEPENDS_${PN} += "iptables"
