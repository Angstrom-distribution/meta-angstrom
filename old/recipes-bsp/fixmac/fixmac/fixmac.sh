#!/bin/sh

if [ -e /etc/ethernetmac.$1 ] ; then
	MACADDRESS="$(cat /etc/ethernetmac.$1)"
else
	MACADDRESS="$(ranpwd -m)"
	echo ${MACADDRESS} > /etc/ethernetmac.$1
fi

/sbin/ifconfig $1 hw ether ${MACADDRESS} && /sbin/ifconfig $1 up
