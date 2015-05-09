# Set PREFFERED and VERSION stuff for working java runtime

PREFERRED_VERSION_openjdk-7-jre = "75b13-2.5.4"
PREFERRED_VERSION_icedtea7-native = "2.1.3"

# jamvm
PREFERRED_VERSION_jamvm = "1.5.5+1.6.0-devel+git%"
PREFERRED_VERSION_jamvm-native = "1.5.5+1.6.0-devel+git%"

PREFERRED_PROVIDER_java2-vm = "${DEFAULTJRE}"
PREFERRED_PROVIDER_java2-runtime = "${DEFAULTJRE}"
PREFERRED_PROVIDER_openjdk-7-jre = "${DEFAULTJRE}"
PREFERRED_PROVIDER_virtual/java-initial-native = "jamvm-initial-native"

DEFAULTJRE ?= "openjdk-7-jre"
