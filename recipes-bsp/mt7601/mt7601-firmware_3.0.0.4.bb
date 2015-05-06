DESCRIPTION = "Firmware for the MT7601 wifi chipset"

LICENSE = "All-Rights-Reserved"
LIC_FILES_CHKSUM = "file://chips/mt7601.c;beginline=1;endline=16;md5=6b5b873c36ab8e794b4bc8d9212bb8bd"

SRC_URI = "http://groenholdt.net/Computers/RaspberryPi/MediaTek-MT7601-USB-WIFI-on-the-Raspberry-Pi/DPO_MT7601U_LinuxSTA_3.0.0.4_20130913.tar.bz2"

S = "${WORKDIR}/DPO_MT7601U_LinuxSTA_3.0.0.4_20130913"
SRC_URI[md5sum] = "5f440dccc8bc952745a191994fc34699"
SRC_URI[sha256sum] = "d7089c9b40f8623e36e9558050b750e9b5db563f5c4746add26f7956e4f19bcb"

do_compile() {
    :
}

do_install() {
    install -d ${D}/lib/firmware

    # The new driver expects all lowercase names
    for binfile in mcu/bin/*.bin ; do
        install -m 0644 $binfile ${D}/lib/firmware/$(basename $binfile | tr '[:upper:]' '[:lower:]')
    done

    install -d ${D}${sysconfdir}/Wireless/RT2870STA
    install -m 0644 *.dat ${D}${sysconfdir}/Wireless/RT2870STA/
}

FILES_${PN} += "${sysconfdir} /lib/firmware"
