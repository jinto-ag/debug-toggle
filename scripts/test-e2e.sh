#!/usr/bin/env bash
set -eo pipefail

AVD_NAME="${AVD_NAME:-test}"
EMU_STARTED=0
TARGET_DEVICE=""

cleanup() {
    if [ "$EMU_STARTED" -eq 1 ] && [ -n "$TARGET_DEVICE" ]; then
        echo "Stopping emulator ($TARGET_DEVICE)..."
        adb -s "$TARGET_DEVICE" emu kill 2>/dev/null || true
    fi
}
trap cleanup EXIT

# Determine target device or launch minimal emulator
if [ "$1" == "--emulator" ] || [ -n "$USE_EMULATOR" ]; then
    TARGET_DEVICE=$(adb devices | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -n 1)
    if [ -z "$TARGET_DEVICE" ]; then
        echo "Starting headless emulator ($AVD_NAME)..."
        EMULATOR_BIN="${ANDROID_HOME:-$HOME/Android/Sdk}/emulator/emulator"
        if [ ! -x "$EMULATOR_BIN" ]; then
            EMULATOR_BIN="emulator"
        fi
        "$EMULATOR_BIN" -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect >/dev/null 2>&1 &
        EMU_PID=$!
        EMU_STARTED=1
        
        echo "Waiting for emulator to boot..."
        adb wait-for-device
        TARGET_DEVICE=$(adb devices | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -n 1)
        until [ "$(adb -s "$TARGET_DEVICE" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]; do
            sleep 2
        done
        echo "Emulator booted ($TARGET_DEVICE)."
    fi
else
    TARGET_DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
    if [ -z "$TARGET_DEVICE" ]; then
        if [ "$1" == "--ci" ] || [ "$CI" == "true" ]; then
            echo "CI environment without connected device: skipping device E2E pass."
            exit 0
        fi
        echo "No device online. Launching headless emulator ($AVD_NAME)..."
        exec "$0" --emulator
    fi
fi

ADB_CMD="adb -s $TARGET_DEVICE"
DEVICE_MODEL=$($ADB_CMD shell getprop ro.product.model | tr -d '\r')
ANDROID_VER=$($ADB_CMD shell getprop ro.build.version.release | tr -d '\r')
echo "Target: $DEVICE_MODEL (Android $ANDROID_VER, serial: $TARGET_DEVICE)"

PASSED=0
FAILED=0

assert_eq() {
    local name="$1"
    local expected="$2"
    local actual="$3"
    if [ "$expected" == "$actual" ]; then
        echo "[PASS] $name"
        PASSED=$((PASSED + 1))
    else
        echo "[FAIL] $name (expected: '$expected', got: '$actual')"
        FAILED=$((FAILED + 1))
    fi
}

# Install latest release APK
if [ -f "DebugToggle-release.apk" ]; then
    $ADB_CMD install -r -g DebugToggle-release.apk >/dev/null 2>&1 || true
fi

# Grant secure settings permission
$ADB_CMD shell pm grant com.jintoag.debugtoggle android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true

# Test 1: Auto-restoration of Developer Options
$ADB_CMD shell settings put global development_settings_enabled 0
assert_eq "Developer options disabled manually" "0" "$($ADB_CMD shell settings get global development_settings_enabled | tr -d '\r')"

$ADB_CMD shell am start -n com.jintoag.debugtoggle/.MainActivity >/dev/null 2>&1
sleep 1.5
assert_eq "Developer options restored automatically by app" "1" "$($ADB_CMD shell settings get global development_settings_enabled | tr -d '\r')"

# Test 2: USB Debugging setting toggle
$ADB_CMD shell settings put global adb_enabled 1
assert_eq "USB debugging state set to 1" "1" "$($ADB_CMD shell settings get global adb_enabled | tr -d '\r')"

# Test 3: Wireless Debugging setting toggle
$ADB_CMD shell settings put global adb_wifi_enabled 1
assert_eq "Wireless debugging state set to 1" "1" "$($ADB_CMD shell settings get global adb_wifi_enabled | tr -d '\r')"

# Test 4: ADB Auth Connection Timeout toggle
$ADB_CMD shell settings put global adb_allowed_connection_time 0
assert_eq "Auth timeout disabled (0)" "0" "$($ADB_CMD shell settings get global adb_allowed_connection_time | tr -d '\r')"

$ADB_CMD shell settings put global adb_allowed_connection_time 604800000
assert_eq "Auth timeout restored to default (604800000)" "604800000" "$($ADB_CMD shell settings get global adb_allowed_connection_time | tr -d '\r')"

$ADB_CMD shell settings put global adb_allowed_connection_time 0

# Test 5: Permission verification
PERM_STATE=$($ADB_CMD shell dumpsys package com.jintoag.debugtoggle | grep "WRITE_SECURE_SETTINGS" | grep -o "granted=true" | head -n 1)
assert_eq "WRITE_SECURE_SETTINGS granted" "granted=true" "$PERM_STATE"

# Test 6: Tile service declaration
TILES_COUNT=$($ADB_CMD shell dumpsys package com.jintoag.debugtoggle | grep -c "TileService" || true)
if [ "$TILES_COUNT" -ge 2 ]; then
    assert_eq "Tile services registered (Usb + Wifi)" "true" "true"
else
    assert_eq "Tile services registered (Usb + Wifi)" "true" "false"
fi

echo "Results: $PASSED passed, $FAILED failed."
if [ "$FAILED" -gt 0 ]; then
    exit 1
fi
exit 0
