
# Something is not quite right with both jamvm and cacao, so use the host java as a workaround

do_compile_append() {
  if [ -e /usr/bin/java ] ; then
    sed -i -e 's:RUNTIME=java-initial:RUNTIME=/usr/bin/java:g' ecj-initial
  fi
}
