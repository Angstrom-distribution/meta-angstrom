# Copyright (C) 2014 Khem Raj <raj.khem@gmail.com>
# Released under the MIT license (see COPYING.MIT for the terms)

DESCRIPTION = "Python 3 runtime and modules"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58 \
                    file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

inherit packagegroup

RDEPENDS_${PN} = "\
        python3-2to3 \
        python3-audio \
        python3-codecs \
        python3-compile \
        python3-compression\
        python3-core \
        python3-crypt \
        python3-ctypes \
        python3-curses \
        python3-datetime \
        python3-debugger \
        python3-difflib \
        python3-distutils \
        python3-doctest \
        python3-email \
        python3-html \
        python3-idle \
        python3-image \
        python3-io  \
        python3-json \
        python3-lang \
        python3-logging \
        python3-mailbox \
        python3-man \
        python3-math \
        python3-mime \
        python3-misc \
        python3-modules \
        python3-multiprocessing \
        python3-netclient \
        python3-netserver \
        python3-numbers \
        python3-pickle \
        python3-pkgutil \
        python3-pprint \
        python3-profile \
        python3-pydoc \
        python3-pyvenv \
        python3-re \
        python3-readline \
        python3-shell \
        python3-smtpd \
        python3-sqlite3  \
        python3-sqlite3-tests \
        python3-stringold \
        python3-subprocess \
        python3-terminal \
        python3-tests \
        python3-textutils \
        python3-threading \
        python3-unittest \
        python3-unixadmin \
        python3-xml \
        python3-distribute \
"
