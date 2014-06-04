# Enable more linux-yocto features for various machines

FILESEXTRAPATHS_prepend := "${THISDIR}/${PN}:"

SRC_URI_append_fri2 = " file://bt.cfg "
KERNEL_FEATURES_append_fri2 = " \
                                  features/netfilter/netfilter.scc \
                                  features/usb-net/usb-net.scc \
                                  features/wifi/wifi-all.scc \
                                  features/media/media-all.scc \
                                  features/leds/leds \
                                  features/rfkill/rfkill \
                                "


