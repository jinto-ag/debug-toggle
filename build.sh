#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"

if [ -d "$ANDROID_SDK/build-tools/35.0.0" ]; then
    BUILD_TOOLS="$ANDROID_SDK/build-tools/35.0.0"
else
    BUILD_TOOLS=$(find "$ANDROID_SDK/build-tools" -maxdepth 1 -mindepth 1 2>/dev/null | sort -V | tail -n 1)
fi

KEYTOOL_BIN="keytool"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    KEYTOOL_BIN="$JAVA_HOME/bin/keytool"
elif command -v keytool >/dev/null 2>&1; then
    KEYTOOL_BIN=$(command -v keytool)
fi

echo "1. Gradle assembleRelease..."
"$PROJECT_DIR/gradlew" assembleRelease --no-daemon

echo "2. Signing APK..."
UNSIGNED_APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
KEYSTORE="$PROJECT_DIR/release.keystore"
if [ ! -f "$KEYSTORE" ]; then
    "$KEYTOOL_BIN" -genkey -v -keystore "$KEYSTORE" \
        -alias debugtoggle -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass debugtoggle -keypass debugtoggle -dname "CN=DebugToggle,O=jinto-ag,C=IN"
fi

"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" \
    --ks-pass pass:debugtoggle \
    --key-pass pass:debugtoggle \
    --out "$PROJECT_DIR/DebugToggle-release.apk" \
    "$UNSIGNED_APK"

echo "SUCCESS! Output: $PROJECT_DIR/DebugToggle-release.apk"
ls -lh "$PROJECT_DIR/DebugToggle-release.apk"
