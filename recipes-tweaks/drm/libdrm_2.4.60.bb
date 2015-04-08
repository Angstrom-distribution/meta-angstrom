require libdrm.inc

SRC_URI += "file://installtests.patch \
            file://0001-fix-for-meta-atmel.patch \
           "
SRC_URI[md5sum] = "13e35a7a1cf38b4c9c0fa0f8c9be6b93"
SRC_URI[sha256sum] = "99575fc6c8e31f59193f5320fd4db7a5478e2641b5266147caab9aa875b59889"
