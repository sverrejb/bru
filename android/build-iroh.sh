#!/usr/bin/env bash
# Builds libiroh_ffi.so from the iroh-ffi submodule. Based on iroh-ffi's own Android build.
set -euo pipefail

# Pin toolchain versions.
rust=1.99.0
cargo_ndk=3.5.4
ndk=27.2.12479018
targets=(aarch64-linux-android armv7-linux-androideabi)

here=$(cd "$(dirname "$0")" && pwd)
src=$here/iroh-ffi
out=$here/iroh-jniLibs
cargo_home=${CARGO_HOME:-$HOME/.cargo}
ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/$ndk}

# Check the NDK and submodule are in place
grep -q "Pkg.Revision = $ndk" "$ANDROID_NDK_HOME/source.properties" || { echo "ANDROID_NDK_HOME is not NDK $ndk" >&2; exit 1; }
[[ -f $src/Cargo.toml ]] || { echo "$src is empty, run git submodule update --init" >&2; exit 1; }

# Install Rust
rustup toolchain install "$rust" --profile minimal --no-self-update $(printf -- '--target %s ' "${targets[@]}")

ndk_root=$cargo_home/cargo-ndk-$cargo_ndk
[[ -x $ndk_root/bin/cargo-ndk ]] || cargo "+$rust" install cargo-ndk --version "$cargo_ndk" --locked --root "$ndk_root"
export PATH=$ndk_root/bin:$PATH

# Strip machine-specific paths from the binary
remap="[\"--remap-path-prefix=$cargo_home=/cargo\", \"--remap-path-prefix=$src=/iroh-ffi\"]"

# Build
rm -rf "$out"
(cd "$src" && cargo "+$rust" ndk -t armeabi-v7a -t arm64-v8a -o "$out" build --release --lib -p iroh-ffi --locked --config "target.'cfg(target_os = \"android\")'.rustflags=$remap")

# Check for 16 KB alignment
PATH=$(echo "$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/*/bin):$PATH bash "$src/scripts/verify_android_page_size.sh" "$out"

# Check no build paths leaked through the remapping
leaks=$(strings -a "$out"/*/libiroh_ffi.so | grep -E '(^|[^[:alnum:]_.-])/(home|Users|root|src|builds?|vagrant|tmp|private|opt)/' || true)
[[ -z $leaks ]] || { echo "build paths leaked into the .so:" >&2; echo "$leaks" | sort -u | head -20 >&2; exit 1; }
