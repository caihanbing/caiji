#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
temp_root="$(mktemp -d)"
trap 'rm -rf "$temp_root"' EXIT

fixture_root="$temp_root/repo"
mkdir -p "$fixture_root/scripts"
cp "$repo_root/scripts/build-release-macos.sh" "$fixture_root/scripts/build-release-macos.sh"
build_script="$fixture_root/scripts/build-release-macos.sh"

run_failure_case() {
    local case_name="$1"
    local sdk_root="$2"
    local expected="$3"
    local output status

    set +e
    output="$(ANDROID_SDK_ROOT="$sdk_root" ANDROID_HOME="$sdk_root" bash "$build_script" 2>&1)"
    status=$?
    set -e

    if [[ "$status" -ne 2 ]]; then
        printf 'FAIL %s: expected exit 2, got %s\n%s\n' "$case_name" "$status" "$output" >&2
        return 1
    fi
    if [[ "$output" != *"$expected"* ]]; then
        printf 'FAIL %s: expected diagnostic %s\n%s\n' "$case_name" "$expected" "$output" >&2
        return 1
    fi
    printf 'PASS %s\n' "$case_name"
}

run_failure_case "missing SDK path" "$temp_root/missing-sdk" "Android SDK directory does not exist"
mkdir -p "$temp_root/present-sdk"
run_failure_case "missing release signing properties" "$temp_root/present-sdk" "release signing properties are missing"
