DESCRIPTION = "QT5 based demo apps group"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

inherit packagegroup

RDEPENDS_${PN} = " \
	cinematicexperience \
	qt5-demo-extrafiles \
	qt5everywheredemo \
	qt5nmapcarousedemo \
	qt5ledscreen \
	qt5nmapper \
	qtsmarthome \
	quitbattery \
	quitindicators \
"
