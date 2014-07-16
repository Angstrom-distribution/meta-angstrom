SUMMARY = "Open Lighting Architecture - OLA"
DESCRIPTION = "The Open Lighting Architecture (OLA) is part of the Open Lighting Project and provides applications with a mechanism to send and receive DMX512 & RDM commands using hardware devices and DMX over IP protocols. This enables software lighting controllers to communicate with hardware either via Ethernet or traditional DMX512 networks. \
OLA can also convert DMX512 data sent using DMX over IP protocols from one format to another, allowing devices from different manufacturers to interact with one another. For example a Strand Lighting Console using ShowNet can send DMX512 to an Enttec EtherGate. When combined with a physical DMX interface such as the DMX USB Pro, OLA can send and receive data from wired DMX512 networks."

LICENSE = "GPLv2 & LGPLv2.1"
LIC_FILES_CHKSUM = "file://LICENCE;md5=f1be0425937acddca9ed750ce30f821d"

DEPENDS = "libmicrohttpd avahi libusb1 libftdi cppunit protobuf protobuf-native ola-native"
DEPENDS_class-native = "protobuf cppunit"

PV = "0.9.1"
SRCREV = "8c7067514793baaeaa3033d77de163d981d7c73a"
SRC_URI = "git://github.com/OpenLightingProject/ola.git;protocol=https"

S = "${WORKDIR}/git"

inherit autotools pythonnative

export BUILD_SYS
export HOST_SYS
export STAGING_LIBDIR

EXTRA_OECONF = " --disable-unittests \
               "

EXTRA_OECONF_append_class-target = " --with-ola-protoc=${STAGING_BINDIR_NATIVE}/ola_protoc"

# -fvisibility-inlines-hidden breaks stuff
CXXFLAGS = "${CFLAGS}"

# The code is not Werror safe
do_configure_prepend() {
	sed -i -e 's:-Werror::g' ${S}/configure.ac
	sed -i -e 's:-Werror::g' ${S}/Makefile.am
}

do_install_append_class-native() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/protoc/ola_protoc ${D}${bindir}
}

FILES_${PN}-dbg += "${libdir}/*/.debug"
FILES_${PN} += "${datadir}/olad ${libdir}/olad/*.so.*"
FILES_${PN}-staticdev += "${libdir}/olad/*.a"
FILES_${PN}-dev += "${libdir}/olad/*.la ${libdir}/olad/*.so"

BBCLASSEXTEND = "native nativesdk"
