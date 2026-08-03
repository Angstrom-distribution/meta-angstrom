# openssl's Makefile has a known parallel-build race: under high -j, make can
# schedule the same object twice, and both invocations race to rename the
# same .d.tmp dependency file ("mv: cannot stat ... No such file or
# directory"). Cap parallelism for this recipe only.
PARALLEL_MAKE = "-j 4"
