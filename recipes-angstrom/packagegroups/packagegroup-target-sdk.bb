# Copyright (C) 2013 Khem Raj <raj.khem@gmail.com>
# Released under the MIT license (see COPYING.MIT for the terms)

DESCRIPTION = "On-device C/C++ SDK"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

inherit packagegroup

RDEPENDS_${PN} = "\
                  packagegroup-core-sdk \
                  packagegroup-core-standalone-sdk-target \
                 "
