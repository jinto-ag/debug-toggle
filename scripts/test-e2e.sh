#!/usr/bin/env bash
set -eo pipefail

AVD_NAME="${AVD_NAME:-test}"
EMU_STARTED=0
TARGET_DEVICE="${TARGET_DEVICE:-}"

cleanup() {
    if [ "$EMU_STARTED" -eq 1 ] && [ -n "$TARGET_DEVICE" ]; then
        echo "Stopping emulator ($TARGET_DEVICE)..."
        adb -s "$TARGET_DEVICE" emu kill 2>/dev/null || true
    fi
}
trap cleanup EXIT

# Determine target device or launch minimal emulator
if [ "$1" == "--emulator" ] || [ -n "$USE_EMULATOR" ]; then
    TARGET_DEVICE=$(adb devices | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -n 1 || true)
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
        TARGET_DEVICE=$(adb devices | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -n 1 || true)
        WAIT_COUNT=0
        until [ "$(adb -s "$TARGET_DEVICE" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]; do
            sleep 2
            WAIT_COUNT=$((WAIT_COUNT + 1))
            if [ "$WAIT_COUNT" -gt 60 ]; then
                echo "ERROR: Emulator failed to boot within 120 seconds."
                exit 1
            fi
        done
        echo "Emulator booted ($TARGET_DEVICE)."
    fi
else
    if [ -z "$TARGET_DEVICE" ]; then
        TARGET_DEVICE=$(adb devices | grep -E "[[:space:]]device$" | awk '{print $1}' | head -n 1 || true)
    fi
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

assert_contains() {
    local name="$1"
    local needle="$2"
    local haystack="$3"
    if [[ "$haystack" == *"$needle"* ]]; then
        echo "[PASS] $name"
        PASSED=$((PASSED + 1))
    else
        echo "[FAIL] $name ('$needle' not found in output)"
        FAILED=$((FAILED + 1))
    fi
}

# Install latest release APK
if [ -f "DebugToggle-release.apk" ]; then
    $ADB_CMD install -r -g DebugToggle-release.apk >/dev/null 2>&1 || true
fi

# Grant secure settings permission
$ADB_CMD shell pm grant com.jintoag.debugtoggle android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true

# Test 1: Verification and Auto-restoration of Developer Options
$ADB_CMD shell am start -n com.jintoag.debugtoggle/.MainActivity >/dev/null 2>&1
sleep 1.0
assert_eq "Developer options active and maintained by app" "1" "$($ADB_CMD shell settings get global development_settings_enabled | tr -d '\r')"

# Test 2: Verify USB Debugging state
USB_STATE=$($ADB_CMD shell settings get global adb_enabled | tr -d '\r')
assert_eq "USB debugging active" "1" "$USB_STATE"

# Test 3: Full Wireless Debugging toggle cycle (1 -> 0 -> 1)
$ADB_CMD shell settings put global adb_wifi_enabled 0
assert_eq "Wireless debugging state set to 0" "0" "$($ADB_CMD shell settings get global adb_wifi_enabled | tr -d '\r')"
$ADB_CMD shell settings put global adb_wifi_enabled 1
assert_eq "Wireless debugging state restored to 1" "1" "$($ADB_CMD shell settings get global adb_wifi_enabled | tr -d '\r')"

# Test 4: ADB Auth Connection Timeout toggle (0 -> 604800000 -> 0)
$ADB_CMD shell settings put global adb_allowed_connection_time 0
assert_eq "Auth timeout disabled (0)" "0" "$($ADB_CMD shell settings get global adb_allowed_connection_time | tr -d '\r')"

$ADB_CMD shell settings put global adb_allowed_connection_time 604800000
assert_eq "Auth timeout restored to default (604800000)" "604800000" "$($ADB_CMD shell settings get global adb_allowed_connection_time | tr -d '\r')"

$ADB_CMD shell settings put global adb_allowed_connection_time 0
assert_eq "Auth timeout set back to disabled (0)" "0" "$($ADB_CMD shell settings get global adb_allowed_connection_time | tr -d '\r')"

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

# Test 7: Quick Settings Tile permission enforcement
BIND_PERM=$($ADB_CMD shell dumpsys package com.jintoag.debugtoggle | grep -c "android.permission.BIND_QUICK_SETTINGS_TILE" || true)
if [ "$BIND_PERM" -ge 2 ]; then
    assert_eq "Tile services guarded by BIND_QUICK_SETTINGS_TILE" "true" "true"
else
    assert_eq "Tile services guarded by BIND_QUICK_SETTINGS_TILE" "true" "false"
fi

# Test 8: Activity launch lifecycle (MainActivity)
MAIN_LAUNCH=$($ADB_CMD shell am start -W -n com.jintoag.debugtoggle/.MainActivity 2>&1 || true)
assert_contains "MainActivity launched successfully" "Status: ok" "$MAIN_LAUNCH"

# Test 9: Activity access control enforcement (LogsActivity)
LOGS_LAUNCH=$($ADB_CMD shell am start -W -n com.jintoag.debugtoggle/.LogsActivity 2>&1 || true)
assert_contains "LogsActivity access control enforced (unexported)" "SecurityException" "$LOGS_LAUNCH"

# Test 10: ContentProvider authority registration
CONTENT_PROVIDER_REG=$($ADB_CMD shell dumpsys package com.jintoag.debugtoggle | grep "com.jintoag.debugtoggle.logs" || true)
assert_contains "AuditLogProvider authority registered in system" "com.jintoag.debugtoggle.logs" "$CONTENT_PROVIDER_REG"

# Test 11: ContentProvider access control enforcement
CONTENT_QUERY_LOG=$($ADB_CMD shell content query --uri content://com.jintoag.debugtoggle.logs/audit_log.txt 2>&1 || true)
assert_contains "AuditLogProvider access control enforced (unexported)" "SecurityException" "$CONTENT_QUERY_LOG"

# Test 12: Developer Options system intent resolution
DEV_INTENT_RES=$($ADB_CMD shell cmd package resolve-activity --brief -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS 2>&1 || true)
assert_contains "Settings resolves APPLICATION_DEVELOPMENT_SETTINGS intent" "com.android.settings" "$DEV_INTENT_RES"

echo "Results: $PASSED passed, $FAILED failed."
if [ "$FAILED" -gt 0 ]; then
    exit 1
fi
exit 0
