#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 3 ]]; then
    printf 'Usage: %s <aapt> <apk> <output-directory>\n' "$0" >&2
    exit 2
fi

aapt="$1"
source_apk="$2"
output_dir="$3"
if [[ ! -x "$aapt" || ! -f "$source_apk" ]]; then
    printf 'Android metadata tool or source APK is missing.\n' >&2
    exit 2
fi

badging="$("$aapt" dump badging "$source_apk")"
package_name="$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)"
version_code="$(printf '%s\n' "$badging" | sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
version_name="$(printf '%s\n' "$badging" | sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" | head -1)"
if [[ "$package_name" != "app.caiji.android" ]]; then
    printf 'Unexpected APK package: %s\n' "${package_name:-<empty>}" >&2
    exit 1
fi
if [[ ! "$version_code" =~ ^[1-9][0-9]*$ ]]; then
    printf 'Unable to read a valid versionCode from the APK: %s\n' "${version_code:-<empty>}" >&2
    exit 1
fi
if [[ ! "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9]+)?$ ]]; then
    printf 'Unable to read a valid versionName from the APK: %s\n' "${version_name:-<empty>}" >&2
    exit 1
fi

mkdir -p "$output_dir"
versioned_apk="$output_dir/caiji-${version_name}-release.apk"
checksum_file="$versioned_apk.sha256"
cp "$source_apk" "$versioned_apk"
(cd "$output_dir" && shasum -a 256 "$(basename "$versioned_apk")" > "$(basename "$checksum_file")")
printf '%s\n' "$versioned_apk"
