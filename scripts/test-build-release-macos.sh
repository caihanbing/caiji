#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
temp_root="$(mktemp -d)"
trap 'rm -rf "$temp_root"' EXIT

fixture_root="$temp_root/repo"
mkdir -p "$fixture_root/scripts"
cp "$repo_root/scripts/build-release-macos.sh" "$fixture_root/scripts/build-release-macos.sh"
cp "$repo_root/scripts/package-versioned-apk.sh" "$fixture_root/scripts/package-versioned-apk.sh"
build_script="$fixture_root/scripts/build-release-macos.sh"
package_script="$repo_root/scripts/package-versioned-apk.sh"

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

fake_tools="$temp_root/fake-tools"
output_dir="$temp_root/output"
mkdir -p "$fake_tools" "$output_dir"
cat > "$fake_tools/aapt" <<'EOF'
#!/usr/bin/env bash
if [[ "$1" == "dump" && "$2" == "badging" ]]; then
    printf "package: name='app.caiji.android' versionCode='4' versionName='0.2.1'\n"
    printf "sdkVersion:'26'\ntargetSdkVersion:'34'\n"
fi
EOF
chmod +x "$fake_tools/aapt"
printf 'sample release bytes\n' > "$temp_root/input.apk"
"$package_script" "$fake_tools/aapt" "$temp_root/input.apk" "$output_dir"
[[ -f "$output_dir/caiji-0.2.1-release.apk" ]]
[[ -f "$output_dir/caiji-0.2.1-release.apk.sha256" ]]
(cd "$output_dir" && shasum -a 256 -c caiji-0.2.1-release.apk.sha256 >/dev/null)
printf 'PASS versioned APK name and checksum\n'

cat > "$fake_tools/wrong-aapt" <<'EOF'
#!/usr/bin/env bash
printf "package: name='app.caiji.other' versionCode='4' versionName='0.2.1'\n"
EOF
chmod +x "$fake_tools/wrong-aapt"
set +e
wrong_output="$("$package_script" "$fake_tools/wrong-aapt" "$temp_root/input.apk" "$temp_root/wrong-output" 2>&1)"
wrong_status=$?
set -e
if [[ "$wrong_status" -eq 0 || "$wrong_output" != *"Unexpected APK package"* ]]; then
    printf 'FAIL wrong package validation: status=%s output=%s\n' "$wrong_status" "$wrong_output" >&2
    exit 1
fi
printf 'PASS rejects APK with unexpected package\n'
