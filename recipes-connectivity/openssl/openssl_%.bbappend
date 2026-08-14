# openssl's Makefile races under high -j: make schedules the same object
# twice and both invocations rename the same .d.tmp dependency file
# ("mv: cannot stat ... No such file or directory").
PARALLEL_MAKE = "-j 4"
