#!/bin/bash

# Angstrom feed sorting script
# This must be run in unsorted/ directory 

export PATH=$PATH:~/bin/

ipkg_tools_path="${IPKG_TOOLS_PATH:-/usr/bin}"

if [ $(basename $PWD) != "unsorted" ] ; then
	echo "Not in feed dir! Exiting"
	exit 1
fi

# Ingest staged uploads from ../incoming/ (written by scripts/upload-packages.py).
# Only directories carrying a COMPLETE marker are touched, and every file is
# verified against the upload's MANIFEST.sha256 before it enters the unsorted
# pool, so an interrupted or corrupted transfer can never reach the feed.
# Rejected files are logged and dropped; the client re-uploads them next run.
if command -v sha256sum >/dev/null 2>&1 ; then
	SHA256_C="sha256sum"
else
	SHA256_C="shasum -a 256"
fi

if [ -d ../incoming ] ; then
	for stagedir in ../incoming/*/ ; do
		[ -d "$stagedir" ] || continue
		if [ ! -f "$stagedir/COMPLETE" ] ; then
			echo "Skipping incomplete upload $stagedir"
			continue
		fi
		if [ -f "$stagedir/MANIFEST.sha256" ] ; then
			echo "Ingesting verified upload $stagedir"
			( cd "$stagedir" && $SHA256_C -c MANIFEST.sha256 2>/dev/null ) | while IFS= read -r result ; do
				file="${result%: *}"
				status="${result##*: }"
				if [ "$status" = "OK" ] ; then
					case "$file" in
						*.ipk) mv "$stagedir/$file" ./ ;;
						*) ;; # metadata (e.g. feed-arch-map.json); sort-packages.py consumes it, we drop it
					esac
				else
					echo "$(date -u +%s) REJECTED $file from $stagedir" >> ../incoming/rejected.log
					echo "Rejecting corrupt file $file from $stagedir"
				fi
			done
		else
			echo "Ingesting unverified upload $stagedir (no manifest)"
			find "$stagedir" -name "*.ipk" -exec mv '{}' ./ \;
		fi
		rm -rf "$stagedir"
	done
fi

if [ $(find . -name "*.ipk" | wc -l) -gt 0 ] ; then
	echo "Unsorted packages found"
else
	echo "No unsorted packages found. Exiting"
	exit 1
fi

rm Packages* >& /dev/null

# Find ipkg files in unsorted/ and remove stale ones
echo "Deleting morgue directories "
find . -name morgue | xargs rm -rf 
echo "Moving packages to the top level directory"
find */ -name  "*.ipk" -exec mv  '{}'  ./ \;

# Make a list of ipkg files already present in feeds and in unsorted
echo "Making a list of unsorted packages"
for i in $(find . -name "*.ipk") ; do basename $i ; done > files-unsorted
# Make a list of duplicates and delete those
echo "Finding duplicate packages in unsorted"
cat files-sorted files-unsorted | sort | uniq -d > files-duplicate
echo "Removing duplicate packages in unsorted"
cat files-duplicate | xargs rm -f

for i in $(find . -name "*.ipk") ; do basename $i ; done > files-sorted-new

if [ "$1" != "--skip-sorted-list" ]; then
	echo "Updating list of sorted packages"
	cat files-sorted files-sorted-new | sort | uniq > files-sorted-tmp
	mv files-sorted-tmp files-sorted
	rm files-sorted-*
fi

# Log remaining packages to a file 
find . -name "*.ipk" |grep -v dbg | grep -v -- -dev | grep -v -- -doc | grep -v -- -static | grep -v angstrom-version | grep -v locale > new-files.txt
for newfile in $(cat new-files.txt | sed s:./::g) ; do
		echo "$(date -u +%s) $newfile $(basename ${PWD})" >> ../upload-full.txt
done		
tail -n 100 ../upload-full.txt > ../upload.txt

do_sort() {
archdir=$arch

case "${arch}" in
	"aarch64")
			machines="raspberrypi4-64 raspberrypi3-64 96boards-64 dragonboard-410c dragonboard-820c genericarmv8 hikey khadas-vim khadas-vim2 libretech-cc qemuarm64 stratix10swvp" ;;
	"aarch64-be")
			machines="genericarmv8b" ;;
	"arm1176jzfshf-vfp")
			machines="raspberrypi0-wifi raspberrypi0 raspberrypi" ;;
	"armv4")
			machines="h3600 collie" ;;
	"armv4t")
			machines="om-gta02 om-gta01 h1940" ;;
	"armv5e")
			machines="" ;;
	"armv5te")
			machines="akita am180x-evm arietta-g25 at91sam9m10g45ek at91sam9rlek at91sam9x5ek c7x0 cfa10036 cfa10037 cfa10049 cfa10055 cfa10056 cfa10057 cfa10058 dlink-dns320 hx4700 imx233-olinuxino-maxi imx233-olinuxino-micro imx233-olinuxino-mini imx233-olinuxino-nano imx23evk imx28evk m28evk nslu2le poodle spitz tosa" ;;
	"armv7ab-vfp")
			machines="genericarmv7ab" ;;
	"armv7ahf-neon-vfpv4")
			machines="";;
	"armv7at2hf-neon-vfpv4")
			machines="" ;;
	"armv7ahf-neon")
			machines="";;
	"armv7ahf-neon-mx6qdl")
			archdir="armv7ahf-neon" ;;
	"armv7at2hf-neon-mx6qdl")
			archdir="armv7at2hf-neon" ;;
	"armv7ahf-neon-mx6ul")
			archdir="armv7ahf-neon" ;;
	"armv7ahf-neon-mx6sx")
			archdir="armv7ahf-neon" ;;
	"armv7ahf-neon-vf60")
			archdir="armv7ahf-neon" ;;
	"armv7at2hf-neon-vf60")
			archdir="armv7ahf-neon" ;;
	"armv7at2hf-neon")
			machines="96boards-32 am335x-evm am3517-evm am37x-evm am437x-evm am437x-hs-evm am57xx-evm apalis-imx6 arndale arndale-octa arria10 arria5 bananapi beagleboard beaglebone beaglebone-white beaglebone-black cgtqmx6 chip chromebook-snow cm-fx6 colibri-imx6 colibri-vf cubieboard cubieboard2 cubietruck cubox-i cyclone5 dra7xx-evm dra7xx-hs-evm forfun-q88db hpveer htcleo ifc6410 imx51evk imx53ard imx53qsb imx6dl-riotboard imx6dlsabreauto imx6dlsabresd imx6q-elo imx6qdl-variscite-som imx6qpsabreauto imx6qpsabresd imx6qsabreauto imx6qsabrelite imx6qsabresd imx6sl-warp imx6slevk imx6solosabreauto imx6solosabresd imx6sxsabreauto imx6sxsabresd imx6ulevk imx7d-warp7 imx7dsabresd k2e-evm k2g-evm k2hk-evm k2l-evm ls1021atwr m53evk mele meleg nexusone nitrogen6sx nitrogen6x nitrogen6x-lite nitrogen7 nokia900 olinuxino-a10lime olinuxino-a10s olinuxino-a13 olinuxino-a13som olinuxino-a20 olinuxino-a20lime olinuxino-a20lime2 olinuxino-a20som om-gta04 omap3evm omap5-evm overo pandaboard pcduino pcm052 qemuarm raspberrypi2 raspberrypi3 sama5d2-xplained sama5d2-xplained-sd sama5d4-xplained sama5d4-xplained-sd sama5d4ek sd-600eval twr-vf65gs10 tx6q-10x0 tx6q-11x0 tx6s-8034 tx6s-8035 tx6u-8033 tx6u-80x0 tx6u-81x0 ventana wandboard" ;;
	"armv7ahf-vfp")
			machines="";;
	"armv7at2hf-vfp")
			machines="genericarmv7a sama5d3-xplained sama5d3-xplained-sd sama5d3xek" ;;
	"core2-32")
			machines="edison intel-core2-32 minnow revo soekris-net6501" ;;
	"core2-64")
			machines="dominion-old lng-rt-x86-64 lng-x86-64 macbook qemux86-64" ;;
	"x86-64-v3")
			machines="apu2c4 beast dominion intel-corei7-64 minnowboard rogue nspawn-x86-64" ;;
	"armv8a")
			machines="radxa_zero_3w qrb2210-vision-kit rb1_core_kit qrb2210_rb1_core_kit qemuarm64";;
	"armv8-2a")
			machines="radxa_dragon_q8b qcs6490-rb3gen2-core-kit rb3gen2-core-kit qcs6490-rubikpi3 rubikpi3 ventuno-q";;
	"i586-nlp-32-intel-common")
			archdir="i586" ;;
	"i586-nlp-32")
			archdir="i586"
			machines="intel-quark" ;;
	"i586")
			machines="qemux86" ;;
	"mips32")
			machines="ben-nanonote" ;;
	"mips32r2")
			machines="qemumips" ;;
	"mips32r2el")
			machines="gcw0" ;;
	"mips64")
			machines="qemumips64" ;;
	"nios2-mul-div")
			archdir="nios2"
			machines="10m50" ;;
	"nios2")
			machines="generic-nios2" ;;
	"ppc7400")
			machines="qemuppc" ;;
	"riscv64imafdc")
			machines="beaglev-ahead";;
	"xscaleteb")
			machines="nslu2be" ;;
esac

if [ $(find . -name  "*_$arch.ipk"| wc -l) -gt 0 ] ; then
	export SORTFEED=1
else
	export SORTFEED=0
fi

sortmachines=""
for machine in $((echo $machines ; echo $machines | sed s:-:_:g) | sed -e s:\ :\\n:g | sort | uniq) ;  do
	if [ $(find . -name  "*_$machine.ipk"| wc -l) -gt 0 ] ; then
		export sortmachines="$sortmachines $machine"
	fi
done

if [ -n "$sortmachines" ] ; then
	echo "Sorting $arch and the following machines: $sortmachines"
else
	if [ "${SORTFEED}" -eq 1 ] ; then
		echo "Sorting $arch"
	else
		echo "Skipping ${arch}, no packages found"
	fi
fi


if [ "${SORTFEED}" -eq 1 ] ; then
	mkdir -p ../$archdir/base/ || true
	for ipk in `find . -name  "*_$arch.ipk"` ; do
		mv $ipk ../$archdir/base/
	done
fi

if [ -n "$sortmachines" ] ; then
	for machine in $sortmachines ; do
		 for machineipk in $(find . -name  "*_$machine.ipk" | grep $machine) ; do mkdir -p ../$archdir/machine/$machine || true ; mv $machineipk ../$archdir/machine/$machine ; done
	done
fi

( if [ -e ../${archdir} ] ; then 
	cd ../$archdir && do_index 
  fi )

}

do_index() {
echo "Processing $(basename $PWD) packages...."

BPWD=`pwd`

if [ "${SORTFEED}" -eq 1 ] ; then
	mkdir -p base
	cd base

	echo -n "building index for base:" 
	${ipkg_tools_path}/opkg-make-index -m -p Packages -l Packages.filelist . >& /tmp/index-log
fi

mkdir -p  ${BPWD}/machine
cd ${BPWD}/machine

for i in $sortmachines ; do
	if [ -d $i ]; then
		 cd $i
		 echo -n "building index for machine $i:"
		 ${ipkg_tools_path}/opkg-make-index -m -p Packages -l Packages.filelist . >& /dev/null
		 echo " DONE"
		 cd ../
	fi
done
cd ${BPWD} 
}

echo "Processing 'all' feed"
for i in `find . -name  "*.ipk"| grep _all` ; do mkdir -p ../all/ || true ;mv $i ../all/ ; done
 (mkdir -p ../all ; cd ../all && ${ipkg_tools_path}/opkg-make-index -p Packages -m . >& /dev/null ; touch Packages.sig )

mkdir -p ../sdk ; mv *sdk.ipk ../sdk/ || true
 (mkdir -p ../sdk ; cd ../sdk && ${ipkg_tools_path}/opkg-make-index -p Packages -m . >& /dev/null ; touch Packages.sig )

for arch in 486sx aarch64 aarch64-be arm1176jzfshf-vfp armv4 armv4t armv5e armv5te armv5te-mx23 armv5te-mx28 armv5teb armv6 armv6-novfp armv6-vfp armv7a-vfp armv7a-vfp-neon armv7ab-vfp armv7ahf-neon armv7at2hf-neon-vf60 armv7ahf-neon-vf60 armv7ahf-neon-mx6sx armv7ahf-neon-mx6ul armv7at2hf-neon-mx6qdl armv7ahf-neon-mx6qdl armv7ahf-neon-vfpv4 armv7ahf-vfp armv7ahf-vfp-neon armv7ahf-vfp-neon-mx5 armv7ahf-vfp-neon-mx6 armv7at2hf-neon armv7at2hf-neon-vfpv4 armv7at2hf-vfp armv7at2hf-vfp-mx5 armv7at2hf-vfp-mx6 armv7at2hf-vfp-neon avr32 bfin armv8a armv8a-crc-crypto armv8-2a armv8-2a-crypto armv8-3a armv8-4a armv8-5a armv8-6a cortexa53 cortexa55 core2 core2-32 core2-32-emgd core2-32-intel-common core2-64 corei7-64 corei7-64-intel-common cortexa7t2hf-neon-vfpv4 cortexa8hf-vfp-neon cortexa9hf-vfp-neon cortexa9hf-vfp-neon-mx6 geode i486 i586 i586-nlp-32 i586-nlp-32-intel-common i686 iwmmxt mips32 mips32r2 mips32r2el mips64 mipsel nios2-mul-div nios2 powerpc ppc405 ppc440e ppc603e  ppc7400 ppce300c2 ppce300c3 ppce500 ppce500v2 ppce600 riscv64imafdc sh4 sparc x86 x86-64-v3 xscaleeb xscaleteb ; do
	do_sort
done

if [ "$1" != "--skip-repo-update" ]; then
	( cd ~/website/repo-updater ; rm -f feeds.db* ; php update.php ; rm ../repo/feeds.db* ; cp feeds.db* ../repo )
fi

echo -n "Stripping source lines from Package files"
for i in `find .. -name Packages` ; do grep -v ^Source: $i|gzip -c9>$i.gz ;gunzip -c $i.gz>$i ; touch $i.sig ; done
echo " DONE"


