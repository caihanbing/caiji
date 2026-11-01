#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
sdk_root="$(printenv ANDROID_SDK_ROOT || true)"
if [[ -z "$sdk_root" ]]; then
    sdk_root="$(printenv ANDROID_HOME || true)"
fi

if [[ -z "$sdk_root" || ! -d "$sdk_root" ]]; then
    printf 'Android SDK directory does not exist. Set ANDROID_SDK_ROOT or ANDROID_HOME to the local SDK.\n' >&2
    exit 2
fi

signing_properties="$repo_root/private-signing/signing.properties"
if [[ ! -f "$signing_properties" ]]; then
    printf 'release signing properties are missing: %s\n' "$signing_properties" >&2
    exit 2
fi

build_tools="$sdk_root/build-tools/34.0.0"
if [[ ! -x "$build_tools/apksigner" || ! -x "$build_tools/aapt" ]]; then
    printf 'Android SDK Build Tools 34.0.0 (apksigner and aapt) are missing from %s\n' "$build_tools" >&2
    exit 2
fi

command -v java >/dev/null 2>&1 || {
    printf 'Java is not available on PATH.\n' >&2
    exit 2
}

export ANDROID_HOME="$sdk_root"
export ANDROID_SDK_ROOT="$sdk_root"
cd "$repo_root"

./gradlew --max-workers=2 :app:testDebugUnitTest :app:lintDebug :app:assembleRelease

apk="$repo_root/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk" ]]; then
    printf 'Release APK was not produced: %s\n' "$apk" >&2
    exit 1
fi

printf '\nAPK: %s\n' "$apk"
"$build_tools/aapt" dump badging "$apk" | /usr/bin/grep -E '^(package:|sdkVersion:|targetSdkVersion:)'
"$build_tools/apksigner" verify --verbose --print-certs "$apk" | /usr/bin/grep -E 'Verified|Signer #1 certificate SHA-256 digest'
shasum -a 256 "$apk"
