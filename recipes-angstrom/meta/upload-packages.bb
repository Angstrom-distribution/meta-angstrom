SUMMARY = "Upload built ipk packages to the Angstrom feed server"
DESCRIPTION = "Meta recipe that runs scripts/upload-packages.py against \
DEPLOY_DIR_IPK. Modeled on oe-core's package-index.bb: invoke it as its own \
bitbake run after the build you want published, e.g. \
'bitbake console-base-image && bitbake upload-packages'. Like package-index, \
the task has no dependency edge to every do_package_write_ipk in an \
arbitrary target set, so mixing it into the same invocation as image targets \
gives non-deterministic ordering - exactly the failure mode of the 2013 \
version of this recipe. Run it afterwards instead."
LICENSE = "MIT"

INHIBIT_DEFAULT_DEPS = "1"
PACKAGES = ""

inherit nopackages

deltask do_fetch
deltask do_unpack
deltask do_patch
deltask do_configure
deltask do_compile
deltask do_install
deltask do_populate_lic
deltask do_populate_sysroot

# Where to upload; override in local.conf / site.conf as needed.
# ssh must be able to authenticate non-interactively (BatchMode): have an
# agent or key available to the user running bitbake.
ANGSTROM_UPLOAD_REMOTE ??= "angstrom@eu.feeds.angstrom-distribution.org"
ANGSTROM_UPLOAD_REMOTE_DIR ??= "website/${FEED_BASEPATH}"
ANGSTROM_UPLOAD_JOBS ??= "4"
# Extra arguments for scripts/upload-packages.py, e.g. "--dry-run" or
# "--exclude 'ti*-sdk*'"
ANGSTROM_UPLOAD_ARGS ??= ""

UPLOAD_PACKAGES_SCRIPT = "${@os.path.normpath(os.path.join(d.getVar('THISDIR'), '..', '..', 'scripts', 'upload-packages.py'))}"

# Rerun on every invocation; needs real network (bitbake-worker unshares the
# network namespace for any task without [network] = "1"); serialize against
# a second bitbake invocation uploading from the same TOPDIR.
do_upload_packages[nostamp] = "1"
do_upload_packages[network] = "1"
do_upload_packages[lockfiles] = "${DEPLOY_DIR}/upload-packages.lock"

# rsync/ssh are not in oe-core's default HOSTTOOLS; angstrom.conf adds them
# via HOSTTOOLS_NONFATAL so they are available here when present on the host.
do_upload_packages() {
	python3 ${UPLOAD_PACKAGES_SCRIPT} \
		--deploy-dir-ipk "${DEPLOY_DIR_IPK}" \
		--remote "${ANGSTROM_UPLOAD_REMOTE}" \
		--remote-dir "${ANGSTROM_UPLOAD_REMOTE_DIR}" \
		--jobs "${ANGSTROM_UPLOAD_JOBS}" \
		--cache-file "${TOPDIR}/cache/upload-packages-state.json" \
		${ANGSTROM_UPLOAD_ARGS}
}
addtask do_upload_packages before do_build

EXCLUDE_FROM_WORLD = "1"
