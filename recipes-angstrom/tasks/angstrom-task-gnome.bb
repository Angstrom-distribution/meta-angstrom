DESCRIPTION = "Task for a GNOME based image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"

PR = "r12"

inherit task

# Most of these tasks are arch independant
PACKAGE_ARCH = "all"

PACKAGES += "task-gnome-apps task-gnome-fonts task-gnome task-gnome-gstreamer task-gnome-perl task-gnome-cups task-gnome-pulseaudio task-gnome-themes task-gnome-totem task-gnome-xserver-base "


RDEPENDS_task-gnome-apps = " \
  x11vnc \
  matchbox-terminal \
  epiphany \
  gedit \
"

RDEPENDS_task-gnome-fonts = " \
  fontconfig fontconfig-utils font-util \
  ttf-liberation-sans \
  ttf-liberation-serif \
  ttf-liberation-mono \
 "  

RDEPENDS_task-gnome = " \
  gnome-settings-daemon gnome-control-center \
  gnome-keyring \
  gdm-systemd angstrom-gdm-autologin-hack \
  gnome-power-manager \
  gnome-bluetooth \
  gnome-panel \
  gtk-engine-clearlooks gtk-theme-clearlooks angstrom-clearlooks-theme-enable \
  upower udisks \
  gnome-disk-utility \
  gnome-system-monitor \
  nautilus \
  gpe-scap \
  bash \ 
  tzdata \
"

RRECOMMENDS_task-gnome = "ofono"

DEPENDS_task-gnome-gstreamer = " \
  gst-plugins-base \
  gst-plugins-good \
  gst-plugins-bad \
  gst-plugins-ugly \
"

RDEPENDS_task-gnome-gstreamer = " \
  gst-ffmpeg \
  gst-plugins-base-meta \
"

RDEPENDS_task-gnome-perl = " \
  perl \
  libxml-parser-perl \
"

RDEPENDS_task-gnome-themes = " \
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

RDEPENDS_task-gnome-xserver-base = " \
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
  task-gnome-fonts \
  task-gnome \
  task-gnome-gstreamer \
  task-gnome-perl \
  task-gnome-themes \
  task-gnome-xserver-base \
"

RRECOMMENDS_${PN} = " \
   task-xserver \
"

