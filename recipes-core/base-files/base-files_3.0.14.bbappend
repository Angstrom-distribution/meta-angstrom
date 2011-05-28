FILESEXTRAPATHS := "${THISDIR}/${PN}"
 
PRINC = "3"

# Original: volatiles = "cache run log lock tmp"
# We don't want all those in volatiles, so:
volatiles = "tmp"
dirs755 += "${localstatedir}/cache \
            ${localstatedir}/run \
            ${localstatedir}/log \
            ${localstatedir}/lock \
            ${localstatedir}/lock/subsys \
           "

