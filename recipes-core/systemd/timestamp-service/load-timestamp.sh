#!/bin/sh

if [ "$1" != "--save" ] ; then

# Set the system clock from timestamp file
# if the timestamp is 1 minute or more recent
# than the current systemtime.

SYSTEMDATE=$(/bin/date -u "+%4Y%2m%2d%2H%2M")

TIMESTAMP=$(/bin/cat /etc/timestamp 2>/dev/null)

if [ $SYSTEMDATE -lt $TIMESTAMP ] 2>/dev/null ; then
	echo "Update systemtime from /etc/timestamp"
	/bin/date -u ${TIMESTAMP#????}${TIMESTAMP%????????}
fi

else
# Store the current systemtime in /etc/timestamp
	/bin/date -u +%4Y%2m%2d%2H%2M > /etc/timestamp
fi
