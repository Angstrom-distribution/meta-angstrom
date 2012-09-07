DESCRIPTION = "Meta package to enable zeroconf audio with pulseaudio"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r7"

inherit packagegroup allarch

RDEPENDS_${PN} = " \
  avahi-daemon \
  pulseaudio-server \
  pulseaudio-module-udev-detect \
  pulseaudio-module-stream-restore \
  pulseaudio-module-default-device-restore \
  pulseaudio-module-position-event-sounds \
  pulseaudio-module-null-sink \
  pulseaudio-module-alsa-sink \
  pulseaudio-module-alsa-source \
  pulseaudio-module-cli \
  pulseaudio-module-simple-protocol-tcp \
  pulseaudio-module-native-protocol-unix \
  pulseaudio-module-cli-protocol-unix \
  pulseaudio-module-zeroconf-publish \
  pulseaudio-module-suspend-on-idle \
"

pkg_postinst_${PN} () {
#!/bin/sh
if [ "x$D" != "x" ]; then
        exit 1
fi

mkdir -p /etc/pulse || true
# Remove existing entries
if [ -e /etc/pulse/session ] ; then
	sed -i -e /load-module\ module-native-protocol-tcp/d -e /load-module\ module-zeroconf-publish/d /etc/pulse/session
fi
# Enable network and zeroconf plugins, allow localhost and usbnet access (where available)
echo "load-module module-native-protocol-tcp auth-ip-acl=127.0.0.1;192.168.0.0/16" >> /etc/pulse/session
echo "load-module module-zeroconf-publish" >> /etc/pulse/session
}
