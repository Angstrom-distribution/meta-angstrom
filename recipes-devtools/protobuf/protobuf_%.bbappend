FILESEXTRAPATHS:prepend := "${THISDIR}/${BPN}:"

# protobuf_configure_target(target) hardcodes target_link_libraries(libprotobuf
# ...) in its protobuf_LINK_LIBATOMIC branch instead of using its own
# ${target} parameter, and even fixing that surfaces a second cascading
# upstream inconsistency (some protobuf_configure_target callers already
# use the plain target_link_libraries signature elsewhere, which CMake
# refuses to mix with the keyword PRIVATE signature) -- see the patch's
# own commit message for the full story. Only reached on platforms
# lacking hardware 64-bit atomics (protobuf_HAVE_BUILTIN_ATOMICS fails),
# e.g. armv5e. Confirmed live building protobuf for qemuarmv5 (2026-08-17).
SRC_URI:append = " file://0001-protobuf-configure-target-use-set_property-not-targe.patch"
