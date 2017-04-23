# Do nothing, meta/lib/oe/path.py lacks the needed methods

#do_install[postfuncs] += "install_relative_symlinks"

#python install_relative_symlinks () {
#    oe.path.replace_absolute_symlinks(d.getVar('D'), d)
#}
