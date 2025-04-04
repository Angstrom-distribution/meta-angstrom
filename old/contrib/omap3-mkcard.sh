#! /bin/sh
# mkcard.sh v0.5
# (c) Copyright 2009 Graeme Gregory <dp@xora.org.uk>
# Licensed under terms of GPLv2
#
# Parts of the procudure base on the work of Denys Dmytriyenko
# http://wiki.omap.com/index.php/MMC_Boot_Format

export LC_ALL=C

if [ $# -ne 1 ]; then
	echo "Usage: $0 <drive>"
	exit 1;
fi

DRIVE=$1

dd if=/dev/zero of=$DRIVE bs=1024 count=1024

SIZE=`fdisk -l $DRIVE | grep Disk | grep bytes | awk '{print $5}'`

echo DISK SIZE - $SIZE bytes

CYLINDERS=`echo $SIZE/255/63/512 | bc`

echo CYLINDERS - $CYLINDERS

{
echo ,9,0x0C,*
echo ,,,-
} | sfdisk -D -H 255 -S 63 -C $CYLINDERS $DRIVE

sleep 1

if [ -b ${DRIVE}1 ]; then
	umount ${DRIVE}1
	mkfs.vfat -F 32 -n "boot" ${DRIVE}1
else
	if [ -b ${DRIVE}p1 ]; then
		umount ${DRIVE}p1
		mkfs.vfat -F 32 -n "boot" ${DRIVE}p1
	else
		echo "Cant find boot partition in /dev"
	fi
fi

if [ -b ${DRIVE}2 ]; then
	umount ${DRIVE}2
	mke2fs -j -L "Angstrom" ${DRIVE}2
else
	if [ -b ${DRIVE}p2 ]; then
		umount ${DRIVE}p2
		mke2fs -j -L "Angstrom" ${DRIVE}p2
	else
		echo "Cant find rootfs partition in /dev"
	fi
fi

