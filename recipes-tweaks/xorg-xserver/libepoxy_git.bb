SUMMARY = "Epoxy is a library for handling OpenGL function pointer management for you."

LICENSE =  "MIT"
LIC_FILES_CHKSUM = "file://COPYING;md5=58ef4c80d401e07bd9ee8b6b58cf464b"

DEPENDS = "virtual/mesa virtual/egl util-macros virtual/libx11"

PV = "1.2"

SRC_URI = "git://github.com/anholt/libepoxy.git"
SRCREV = "20062c25e7612cab023cdef44d3277ba1bd0b2de"

S = "${WORKDIR}/git"

inherit autotools pythonnative pkgconfig
