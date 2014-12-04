SUMMARY = "NCurses Disk Usage"

DEPENDS = "ncurses"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://COPYING;md5=e5792c3f612026cfecfc6ff7c09738b5"

SRC_URI = "http://dev.yorhel.nl/download/ncdu-${PV}.tar.gz"
SRC_URI[md5sum] = "7535decc8d54eca811493e82d4bfab2d"
SRC_URI[sha256sum] = "f5994a4848dbbca480d39729b021f057700f14ef72c0d739bbd82d862f2f0c67"

inherit autotools pkgconfig
