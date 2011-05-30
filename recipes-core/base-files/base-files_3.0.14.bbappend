FILESEXTRAPATHS := "${THISDIR}/${PN}"
 
PRINC = "5"

# Original: volatiles = "cache run log lock tmp"
# We don't any of those in volatiles, so:
volatiles = ""
dirs755 += "${localstatedir}/cache \
            ${localstatedir}/run \
            ${localstatedir}/log \
            ${localstatedir}/lock \
            ${localstatedir}/lock/subsys \
            ${localstatedir}/tmp \
            ${localstatedir}/volatile/tmp \
           "

