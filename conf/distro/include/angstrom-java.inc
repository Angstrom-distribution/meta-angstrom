# Set PREFFERED and VERSION stuff for working java runtime

# jamvm
PREFERRED_VERSION_jamvm = "1.5.5+1.6.0-devel+git%"
PREFERRED_VERSION_jamvm-native = "1.5.5+1.6.0-devel+git%"
PREFERRED_PROVIDER_java2-vm = "${DEFAULTJRE}"
PREFERRED_PROVIDER_java2-runtime = "${DEFAULTJRE}"
PREFERRED_PROVIDER_openjdk-7-jre = "${DEFAULTJRE}"

# Possible provider: cacao-initial-native and jamvm-initial-native
PREFERRED_PROVIDER_virtual/java-initial-native = "jamvm-initial-native"

# Possible provider: cacao-native and jamvm-native
PREFERRED_PROVIDER_virtual/java-native = "jamvm-native"

# Optional since there is only one provider for now
PREFERRED_PROVIDER_virtual/javac-native = "ecj-bootstrap-native"

DEFAULTJRE ?= "openjdk-7-jre"
