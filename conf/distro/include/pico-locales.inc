# oe-core defaults every glibc distro to IMAGE_LINGUAS = "c en-us en-gb",
# which builds a multi-megabyte locale-archive. glibc >= 2.35 has C.UTF-8
# built in and needs no locale files at all.
IMAGE_LINGUAS = ""
ENABLE_BINARY_LOCALE_GENERATION = "0"
GLIBC_GENERATE_LOCALES = "C.UTF-8"
IMAGE_LOCALES_ARCHIVE = "0"

# Nothing may RRECOMMEND glibc-gconvs, glibc-charmaps, or glibc-localedatas.
