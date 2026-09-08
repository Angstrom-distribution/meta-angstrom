# angstrom and angstrom-pico otherwise share the same feed path (same
# version/format/libc); without ${DISTRO} here their differently-built
# packages silently overwrite each other the moment a machine tune matches.
FEED_BASEPATH = "feeds/${DISTRO_VERSION}/${DISTRO}/${ANGSTROM_PKG_FORMAT}/${TCLIBC}/"
