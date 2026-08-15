# Enable the stock hourly/daily/weekly/monthly run-parts jobs by default;
# cronie's own do_install already creates the /etc/cron.<period> dirs, they
# just aren't wired into /etc/crontab out of the box.
do_install:append() {
	sed -i '/run-parts \/etc\/cron\./ s/^#//' ${D}${sysconfdir}/crontab
}
