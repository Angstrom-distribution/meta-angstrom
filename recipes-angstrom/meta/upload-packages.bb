DESCRIPTION  = "Meta recipe to upload the built packages to the angstrom feed server"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=3f40d7994397109285ec7b81fdeb3b58"
AUTHOR = " Graeme 'XorA' Gregory, Koen Kooi"

# Angstrom webserver
REMOTEM = "angstrom@eu.feeds.angstrom-distribution.org"

# Feed dir we want to upload to
REMOTED = "website/${FEED_BASEPATH}"

# set some vars to get rid of spurious deps
INHIBIT_DEFAULT_DEPS = "1"

do_fetch[noexec] = "1"
do_unpack[noexec] = "1"
do_patch[noexec] = "1"
do_configure[noexec] = "1"
do_compile[noexec] = "1"
do_install[noexec] = "1"
do_package[noexec] = "1"
do_package_write[noexec] = "1"
do_package_write_ipk[noexec] = "1"
do_package_write_rpm[noexec] = "1"
do_package_write_deb[noexec] = "1"
do_populate_sysroot[noexec] = "1"

do_upload_packages[nostamp] = "1"
do_upload_packages[dirs] = "${DEPLOY_DIR_IPK}"

do_upload_packages() {

	cd ${DEPLOY_DIR}

	# create upload dir
	mkdir -p upload-queue || true

	# Find and delete morgue dirs, we don't need them
	echo "Deleting morgue directories"
	find ipk/ -name "morgue" -exec rm -rf \{\} \; || true

	# Copy symlink packages to an upload queue
	echo "Symlink packages to upload queue"
	find ipk/ -name "*.ipk" -exec ln -sf $PWD/\{\} upload-queue/ \;

	# Find file already present on webserver
	echo "Getting file list from server"
	ssh -C ${REMOTEM} 'mkdir -p ${REMOTED}/unsorted ; touch ${REMOTED}/unsorted/files-sorted'
	scp -C ${REMOTEM}:${REMOTED}/unsorted/files-sorted files-remote
	ls upload-queue/ | grep -v morgue > files-local

	# Check for files already present on webserver
	echo "Checking for duplicates"
	cat files-remote files-local | sort | uniq -u >files-uniq
	cat files-uniq files-local | sort | uniq -d > files-trans

	# Clean up some unwanted files
	rm -f upload-queue/bigbuck* 
	rm -f upload-queue/ti*-sdk*
	rm -f upload-queue/ti*tree*

	# Copy over non-duplicate files
	echo "Starting rsync..."
	rsync -vz --partial --copy-links --progress --files-from=files-trans upload-queue/ ${REMOTEM}:${REMOTED}/unsorted/

	# Clean up temporary files
	echo "Removing upload queue"
	rm -rf files-remote files-local files-uniq files-trans upload-queue	
}

addtask do_upload_packages before do_build
EXCLUDE_FROM_WORLD = "1"

