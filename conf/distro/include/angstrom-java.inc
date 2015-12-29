# Set PREFFERED and VERSION stuff for working java runtime

# jamvm
PREFERRED_VERSION_jamvm = "1.5.5+1.6.0-devel+git%"
PREFERRED_VERSION_jamvm-native = "1.5.5+1.6.0-devel+git%"
PREFERRED_PROVIDER_java2-vm = "${DEFAULTJRE}"
PREFERRED_PROVIDER_java2-runtime = "${DEFAULTJRE}"
PREFERRED_PROVIDER_openjdk-7-jre = "${DEFAULTJRE}"

DEFAULTJRE ?= "openjdk-7-jre"
