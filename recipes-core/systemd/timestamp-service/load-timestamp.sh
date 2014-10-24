#!/bin/sh

# Set the system clock from hardware clock
# If the timestamp is 1 day or more recent than the current time,
# use the timestamp instead.
SYSTEMDATE=$(/bin/date -u "+%4Y%2m%2d%2H%2M")

TIMESTAMP=$(/bin/cat /etc/timestamp 2>/dev/null)

if [ $SYSTEMDATE -lt $TIMESTAMP ] 2>/dev/null ; then
	echo "Update systemtime from /etc/timestamp"
	/bin/date -u ${TIMESTAMP#????}${TIMESTAMP%????????}
fi

if [ "$1" = "--save" ] ; then
	/bin/date -u +%4Y%2m%2d%2H%2M > /etc/timestamp
fi

