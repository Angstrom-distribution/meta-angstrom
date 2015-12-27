# Fix host side failures with ssp linking
do_compile_prepend() {
	for i in $(grep noexecstack ${S} -rn | awk -F: '{print $1}' | sort | uniq) ; do sed -i -e 's:noexecstack:noexecstack -lssp:g' $i ; done
}
