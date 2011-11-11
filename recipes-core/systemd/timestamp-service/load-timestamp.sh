#!/bin/sh

# Set the system clock from hardware clock
# If the timestamp is 1 day or more recent than the current time,
# use the timestamp instead.
SYSTEMDATE=$(/bin/date -u "+%4Y%2m%2d%2H%2M")

YEAR=$(cat /etc/timestamp | cut -b 9-12)
MONTH=$(cat /etc/timestamp | cut -b 1-2)
DAY=$(cat /etc/timestamp | cut -b 3-4)
TIME=$(cat /etc/timestamp | cut -b 5-8)

TIMESTAMP="$YEAR$MONTH$DAY$TIME"

if [ $SYSTEMDATE -lt $TIMESTAMP ]; then 
	echo "Update systemtime from /etc/timestamp"
	date -u $(cat /etc/timestamp)
fi

if [ "$1" = "--save" ] ; then
	/bin/date -u +%2m%2d%2H%2M%4Y > /etc/timestamp
fi
