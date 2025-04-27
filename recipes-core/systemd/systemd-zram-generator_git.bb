SUMMARY = "Systemd unit generator for zram devices"
HOMEPAGE = "https://github.com/systemd/zram-generator"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=3fced11d6df719b47505837a51c16ae5"

DEPENDS = "systemd"

inherit cargo pkgconfig systemd

PV = "1.2.1"
SRCREV = "1ad16bee60979cca3945276ce85c278df035c650"
# Generated using 'cargo bitbake' inside the git checkout
SRC_URI = "git://github.com/systemd/zram-generator.git;protocol=https;branch=main \
    crate://crates.io/ahash/0.7.8 \
    crate://crates.io/anstyle/1.0.10 \
    crate://crates.io/anyhow/1.0.98 \
    crate://crates.io/autocfg/1.4.0 \
    crate://crates.io/bitflags/1.3.2 \
    crate://crates.io/bitflags/2.9.0 \
    crate://crates.io/cc/1.2.20 \
    crate://crates.io/cfg-if/1.0.0 \
    crate://crates.io/clap/4.5.37 \
    crate://crates.io/clap_builder/4.5.37 \
    crate://crates.io/clap_lex/0.7.4 \
    crate://crates.io/ctor/0.2.9 \
    crate://crates.io/dlv-list/0.3.0 \
    crate://crates.io/errno/0.3.11 \
    crate://crates.io/fasteval/0.2.4 \
    crate://crates.io/fastrand/2.3.0 \
    crate://crates.io/fs_extra/1.3.0 \
    crate://crates.io/getrandom/0.2.16 \
    crate://crates.io/getrandom/0.3.2 \
    crate://crates.io/hashbrown/0.12.3 \
    crate://crates.io/libc/0.2.172 \
    crate://crates.io/liboverdrop/0.1.0 \
    crate://crates.io/linux-raw-sys/0.9.4 \
    crate://crates.io/log/0.4.27 \
    crate://crates.io/memoffset/0.6.5 \
    crate://crates.io/nix/0.23.2 \
    crate://crates.io/once_cell/1.21.3 \
    crate://crates.io/ordered-multimap/0.4.3 \
    crate://crates.io/proc-macro2/1.0.95 \
    crate://crates.io/quote/1.0.40 \
    crate://crates.io/r-efi/5.2.0 \
    crate://crates.io/rust-ini/0.18.0 \
    crate://crates.io/rustix/1.0.5 \
    crate://crates.io/shlex/1.3.0 \
    crate://crates.io/syn/2.0.101 \
    crate://crates.io/tempfile/3.19.1 \
    crate://crates.io/unicode-ident/1.0.18 \
    crate://crates.io/version_check/0.9.5 \
    crate://crates.io/wasi/0.11.0+wasi-snapshot-preview1 \
    crate://crates.io/wasi/0.14.2+wasi-0.2.4 \
    crate://crates.io/windows-sys/0.59.0 \
    crate://crates.io/windows-targets/0.52.6 \
    crate://crates.io/windows_aarch64_gnullvm/0.52.6 \
    crate://crates.io/windows_aarch64_msvc/0.52.6 \
    crate://crates.io/windows_i686_gnu/0.52.6 \
    crate://crates.io/windows_i686_gnullvm/0.52.6 \
    crate://crates.io/windows_i686_msvc/0.52.6 \
    crate://crates.io/windows_x86_64_gnu/0.52.6 \
    crate://crates.io/windows_x86_64_gnullvm/0.52.6 \
    crate://crates.io/windows_x86_64_msvc/0.52.6 \
    crate://crates.io/wit-bindgen-rt/0.39.0 \
"
SRC_URI[ahash-0.7.8.sha256sum] = "891477e0c6a8957309ee5c45a6368af3ae14bb510732d2684ffa19af310920f9"
SRC_URI[anstyle-1.0.10.sha256sum] = "55cc3b69f167a1ef2e161439aa98aed94e6028e5f9a59be9a6ffb47aef1651f9"
SRC_URI[anyhow-1.0.98.sha256sum] = "e16d2d3311acee920a9eb8d33b8cbc1787ce4a264e85f964c2404b969bdcd487"
SRC_URI[autocfg-1.4.0.sha256sum] = "ace50bade8e6234aa140d9a2f552bbee1db4d353f69b8217bc503490fc1a9f26"
SRC_URI[bitflags-1.3.2.sha256sum] = "bef38d45163c2f1dde094a7dfd33ccf595c92905c8f8f4fdc18d06fb1037718a"
SRC_URI[bitflags-2.9.0.sha256sum] = "5c8214115b7bf84099f1309324e63141d4c5d7cc26862f97a0a857dbefe165bd"
SRC_URI[cc-1.2.20.sha256sum] = "04da6a0d40b948dfc4fa8f5bbf402b0fc1a64a28dbf7d12ffd683550f2c1b63a"
SRC_URI[cfg-if-1.0.0.sha256sum] = "baf1de4339761588bc0619e3cbc0120ee582ebb74b53b4efbf79117bd2da40fd"
SRC_URI[clap-4.5.37.sha256sum] = "eccb054f56cbd38340b380d4a8e69ef1f02f1af43db2f0cc817a4774d80ae071"
SRC_URI[clap_builder-4.5.37.sha256sum] = "efd9466fac8543255d3b1fcad4762c5e116ffe808c8a3043d4263cd4fd4862a2"
SRC_URI[clap_lex-0.7.4.sha256sum] = "f46ad14479a25103f283c0f10005961cf086d8dc42205bb44c46ac563475dca6"
SRC_URI[ctor-0.2.9.sha256sum] = "32a2785755761f3ddc1492979ce1e48d2c00d09311c39e4466429188f3dd6501"
SRC_URI[dlv-list-0.3.0.sha256sum] = "0688c2a7f92e427f44895cd63841bff7b29f8d7a1648b9e7e07a4a365b2e1257"
SRC_URI[errno-0.3.11.sha256sum] = "976dd42dc7e85965fe702eb8164f21f450704bdde31faefd6471dba214cb594e"
SRC_URI[fasteval-0.2.4.sha256sum] = "4f4cdac9e4065d7c48e30770f8665b8cef9a3a73a63a4056a33a5f395bc7cf75"
SRC_URI[fastrand-2.3.0.sha256sum] = "37909eebbb50d72f9059c3b6d82c0463f2ff062c9e95845c43a6c9c0355411be"
SRC_URI[fs_extra-1.3.0.sha256sum] = "42703706b716c37f96a77aea830392ad231f44c9e9a67872fa5548707e11b11c"
SRC_URI[getrandom-0.2.16.sha256sum] = "335ff9f135e4384c8150d6f27c6daed433577f86b4750418338c01a1a2528592"
SRC_URI[getrandom-0.3.2.sha256sum] = "73fea8450eea4bac3940448fb7ae50d91f034f941199fcd9d909a5a07aa455f0"
SRC_URI[hashbrown-0.12.3.sha256sum] = "8a9ee70c43aaf417c914396645a0fa852624801b24ebb7ae78fe8272889ac888"
SRC_URI[libc-0.2.172.sha256sum] = "d750af042f7ef4f724306de029d18836c26c1765a54a6a3f094cbd23a7267ffa"
SRC_URI[liboverdrop-0.1.0.sha256sum] = "08e5373d7512834e2fbbe4100111483a99c28ca3818639f67ab2337672301f8e"
SRC_URI[linux-raw-sys-0.9.4.sha256sum] = "cd945864f07fe9f5371a27ad7b52a172b4b499999f1d97574c9fa68373937e12"
SRC_URI[log-0.4.27.sha256sum] = "13dc2df351e3202783a1fe0d44375f7295ffb4049267b0f3018346dc122a1d94"
SRC_URI[memoffset-0.6.5.sha256sum] = "5aa361d4faea93603064a027415f07bd8e1d5c88c9fbf68bf56a285428fd79ce"
SRC_URI[nix-0.23.2.sha256sum] = "8f3790c00a0150112de0f4cd161e3d7fc4b2d8a5542ffc35f099a2562aecb35c"
SRC_URI[once_cell-1.21.3.sha256sum] = "42f5e15c9953c5e4ccceeb2e7382a716482c34515315f7b03532b8b4e8393d2d"
SRC_URI[ordered-multimap-0.4.3.sha256sum] = "ccd746e37177e1711c20dd619a1620f34f5c8b569c53590a72dedd5344d8924a"
SRC_URI[proc-macro2-1.0.95.sha256sum] = "02b3e5e68a3a1a02aad3ec490a98007cbc13c37cbe84a3cd7b8e406d76e7f778"
SRC_URI[quote-1.0.40.sha256sum] = "1885c039570dc00dcb4ff087a89e185fd56bae234ddc7f056a945bf36467248d"
SRC_URI[r-efi-5.2.0.sha256sum] = "74765f6d916ee2faa39bc8e68e4f3ed8949b48cccdac59983d287a7cb71ce9c5"
SRC_URI[rust-ini-0.18.0.sha256sum] = "f6d5f2436026b4f6e79dc829837d467cc7e9a55ee40e750d716713540715a2df"
SRC_URI[rustix-1.0.5.sha256sum] = "d97817398dd4bb2e6da002002db259209759911da105da92bec29ccb12cf58bf"
SRC_URI[shlex-1.3.0.sha256sum] = "0fda2ff0d084019ba4d7c6f371c95d8fd75ce3524c3cb8fb653a3023f6323e64"
SRC_URI[syn-2.0.101.sha256sum] = "8ce2b7fc941b3a24138a0a7cf8e858bfc6a992e7978a068a5c760deb0ed43caf"
SRC_URI[tempfile-3.19.1.sha256sum] = "7437ac7763b9b123ccf33c338a5cc1bac6f69b45a136c19bdd8a65e3916435bf"
SRC_URI[unicode-ident-1.0.18.sha256sum] = "5a5f39404a5da50712a4c1eecf25e90dd62b613502b7e925fd4e4d19b5c96512"
SRC_URI[version_check-0.9.5.sha256sum] = "0b928f33d975fc6ad9f86c8f283853ad26bdd5b10b7f1542aa2fa15e2289105a"
SRC_URI[wasi-0.11.0+wasi-snapshot-preview1.sha256sum] = "9c8d87e72b64a3b4db28d11ce29237c246188f4f51057d65a7eab63b7987e423"
SRC_URI[wasi-0.14.2+wasi-0.2.4.sha256sum] = "9683f9a5a998d873c0d21fcbe3c083009670149a8fab228644b8bd36b2c48cb3"
SRC_URI[windows-sys-0.59.0.sha256sum] = "1e38bc4d79ed67fd075bcc251a1c39b32a1776bbe92e5bef1f0bf1f8c531853b"
SRC_URI[windows-targets-0.52.6.sha256sum] = "9b724f72796e036ab90c1021d4780d4d3d648aca59e491e6b98e725b84e99973"
SRC_URI[windows_aarch64_gnullvm-0.52.6.sha256sum] = "32a4622180e7a0ec044bb555404c800bc9fd9ec262ec147edd5989ccd0c02cd3"
SRC_URI[windows_aarch64_msvc-0.52.6.sha256sum] = "09ec2a7bb152e2252b53fa7803150007879548bc709c039df7627cabbd05d469"
SRC_URI[windows_i686_gnu-0.52.6.sha256sum] = "8e9b5ad5ab802e97eb8e295ac6720e509ee4c243f69d781394014ebfe8bbfa0b"
SRC_URI[windows_i686_gnullvm-0.52.6.sha256sum] = "0eee52d38c090b3caa76c563b86c3a4bd71ef1a819287c19d586d7334ae8ed66"
SRC_URI[windows_i686_msvc-0.52.6.sha256sum] = "240948bc05c5e7c6dabba28bf89d89ffce3e303022809e73deaefe4f6ec56c66"
SRC_URI[windows_x86_64_gnu-0.52.6.sha256sum] = "147a5c80aabfbf0c7d901cb5895d1de30ef2907eb21fbbab29ca94c5b08b1a78"
SRC_URI[windows_x86_64_gnullvm-0.52.6.sha256sum] = "24d5b23dc417412679681396f2b49f3de8c1473deb516bd34410872eff51ed0d"
SRC_URI[windows_x86_64_msvc-0.52.6.sha256sum] = "589f6da84c646204747d1270a2a5661ea66ed1cced2631d546fdfb155959f9ec"
SRC_URI[wit-bindgen-rt-0.39.0.sha256sum] = "6f42320e61fe2cfd34354ecb597f86f413484a798ba44a8ca1165c58d42da6c1"

S = "${WORKDIR}/git"
CARGO_SRC_DIR = ""
B = "${S}"

# Substitute 'frozen' with 'offline'
CARGO_BUILD_FLAGS = "-v --offline --target ${RUST_HOST_SYS} ${BUILD_MODE} --manifest-path=${CARGO_MANIFEST_PATH}"

do_compile() {
	# Work around panic = "abort" errors, see https://github.com/meta-rust/meta-rust/issues/343
	sed -i /panic/d Cargo.toml
	export RUSTFLAGS="${RUSTFLAGS}"
	oe_runmake build NOMAN=true CARGO="${CARGO}" CARGOFLAGS="${CARGO_BUILD_FLAGS}" BUILDTYPE="OpenEmbedded"
}

do_install() {
	oe_runmake install NOBUILD=true NOMAN=true BUILDTYPE="${RUST_HOST_SYS}/release" DESTDIR="${D}" PREFIX="${prefix}"
}

FILES:${PN} += "${systemd_unitdir}"
