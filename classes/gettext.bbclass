def gettext_dependencies(d):
    if d.getVar('INHIBIT_DEFAULT_DEPS', True) and not oe.utils.inherits(d, 'cross-canadian'):
        return ""
    if d.getVar('USE_NLS', True) == 'no':
        return "gettext-minimal-native"
    return "gettext-native"

def gettext_oeconf(d):
    if d.getVar('USE_NLS', True) == 'no':
        return '--disable-nls'
    # Remove the NLS bits if USE_NLS is no or INHIBIT_DEFAULT_DEPS is set
    if d.getVar('INHIBIT_DEFAULT_DEPS', True) and not oe.utils.inherits(d, 'cross-canadian'):
        return '--disable-nls'
    return "--enable-nls"

BASEDEPENDS_append = " ${@gettext_dependencies(d)}"
EXTRA_OECONF_append = " ${@gettext_oeconf(d)}"

# Without this, msgfmt from gettext-native will not find ITS files
# provided by target recipes (for example, polkit.its).
GETTEXTDATADIRS_append_class-target = ":${STAGING_DATADIR}/gettext"
export GETTEXTDATADIRS
