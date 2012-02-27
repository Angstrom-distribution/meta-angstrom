DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r5"

inherit task

# Most of these tasks are arch independant
PACKAGE_ARCH = "all"

PACKAGES += "task-gnome3-apps task-gnome3-fonts task-gnome3 task-gnome3-gstreamer task-gnome3-perl task-gnome3-cups task-gnome3-pulseaudio task-gnome3-themes task-gnome3-totem task-gnome3-xserver-base "


RDEPENDS_task-gnome3-apps = " \
  x11vnc \
  matchbox-terminal \
  epiphany \
 "

RDEPENDS_task-gnome3-fonts = " \
  fontconfig fontconfig-utils font-util \
  ttf-liberation-sans \
  ttf-liberation-serif \
  ttf-liberation-mono \
 "  

RDEPENDS_task-gnome3 = " \
  gnome-settings-daemon gnome-control-center \
  gnome-keyring \
  gdm-systemd angstrom-gdm-autologin-hack \
  gnome-power-manager3 \
  gnome-bluetooth \
  gnome-panel3 \
  gtk-engine-clearlooks gtk-theme-clearlooks \
  upower udisks \
  gnome-disk-utility \
  nautilus3 \
  gpe-scap \
  bash \ 
  tzdata \
"

DEPENDS_task-gnome3-gstreamer = " \
  gst-plugins-base \
  gst-plugins-good \
  gst-plugins-bad \
  gst-plugins-ugly \
"

RDEPENDS_task-gnome3-gstreamer = " \
  gst-ffmpeg \
  gst-plugins-base-meta \
"

RDEPENDS_task-gnome3-perl = " \
  perl \
  libxml-parser-perl \
"

RDEPENDS_task-gnome3-themes = " \
  angstrom-gnome-icon-theme-enable \
  gnome-icon-theme \
  gnome-themes \
  gnome-theme-crux \
  gnome-theme-highcontrast \
  gnome-theme-highcontrastinverse \
  gnome-theme-highcontrastlargeprint \
  gnome-theme-highcontrastlargeprintinverse \
  gnome-theme-largeprint \
  gnome-theme-mist \
  gtk-engine-clearlooks \
  gtk-engine-crux-engine \
  gtk-engine-glide \
  gtk-engine-hcengine \
  gtk-engine-thinice \
  gtk-engine-redmond95 \
  gtk-theme-clearlooks \
  gtk-theme-crux \
  gtk-theme-mist \
  gtk-theme-thinice \
  gtk-theme-redmond \
  hicolor-icon-theme \
 "

RDEPENDS_task-gnome3-xserver-base = " \
  dbus-x11 \
  iso-codes \
  mime-support \
  xauth \
  xdg-utils \
  xhost \
  xinetd \
  xinit \
  xrandr \
  xrdb \
  xset \
  xvinfo \
 "

RDEPENDS_${PN} = " \
  task-gnome3-fonts \
  task-gnome3 \
  task-gnome3-gstreamer \
  task-gnome3-perl \
  task-gnome3-themes \
  task-gnome3-xserver-base \
"

RRECOMMENDS_${PN} = " \
   task-xserver \
"

