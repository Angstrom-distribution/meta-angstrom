require git.inc

SRC_URI = "http://git-core.googlecode.com/files/git-${PV}.tar.gz"
SRC_URI[md5sum] = "2e2ee53243ab8e7cf10f15c5229c3fce"
SRC_URI[sha256sum] = "335e978814659f328e377715b13a33336859275ae6f215bf28bbbb2ae711bb43"

EXTRA_OECONF += "ac_cv_snprintf_returns_bogus=no ac_cv_c_c99_format=yes \
                 ac_cv_fread_reads_directories=${ac_cv_fread_reads_directories=yes} \
                 "

