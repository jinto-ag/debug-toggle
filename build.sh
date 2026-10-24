#!/bin/bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"

if [ -d "$ANDROID_SDK/build-tools/35.0.0" ]; then
    BUILD_TOOLS="$ANDROID_SDK/build-tools/35.0.0"
else
    BUILD_TOOLS=$(find "$ANDROID_SDK/build-tools" -maxdepth 1 -mindepth 1 2>/dev/null | sort -V | tail -n 1)
fi

if [ -f "$ANDROID_SDK/platforms/android-34/android.jar" ]; then
    PLATFORM="$ANDROID_SDK/platforms/android-34/android.jar"
else
    PLATFORM=$(find "$ANDROID_SDK/platforms" -name "android.jar" 2>/dev/null | sort -V | tail -n 1)
fi

JAVAC_BIN="javac"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC_BIN="$JAVA_HOME/bin/javac"
elif command -v javac >/dev/null 2>&1; then
    JAVAC_BIN=$(command -v javac)
fi

KEYTOOL_BIN="keytool"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    KEYTOOL_BIN="$JAVA_HOME/bin/keytool"
elif command -v keytool >/dev/null 2>&1; then
    KEYTOOL_BIN=$(command -v keytool)
fi

APP_DIR="$PROJECT_DIR/app/src/main"
BUILD_DIR="$PROJECT_DIR/build"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes"

echo "1. aapt2 compile resources..."
"$BUILD_TOOLS/aapt2" compile --dir "$APP_DIR/res" -o "$BUILD_DIR/compiled_res.zip"

echo "2. aapt2 link..."
sed 's|<manifest |<manifest package="com.jintoag.debugtoggle" |' "$APP_DIR/AndroidManifest.xml" > "$BUILD_DIR/AndroidManifest.xml"
"$BUILD_TOOLS/aapt2" link --manifest "$BUILD_DIR/AndroidManifest.xml" \
    -I "$PLATFORM" \
    --min-sdk-version 26 \
    --target-sdk-version 35 \
    "$BUILD_DIR/compiled_res.zip" \
    -o "$BUILD_DIR/base.apk" \
    --java "$BUILD_DIR/gen"

echo "3. javac compile..."
"$JAVAC_BIN" -d "$BUILD_DIR/classes" \
    -cp "$PLATFORM" \
    "$APP_DIR/java/com/jintoag/debugtoggle/"*.java \
    "$BUILD_DIR/gen/com/jintoag/debugtoggle/"*.java

echo "4. d8 dexing..."
"$BUILD_TOOLS/d8" --lib "$PLATFORM" \
    --output "$BUILD_DIR/" \
    "$BUILD_DIR/classes/com/jintoag/debugtoggle/"*.class

echo "5. Assemble APK..."
cd "$BUILD_DIR"
zip -uj base.apk classes.dex

echo "6. zipalign..."
"$BUILD_TOOLS/zipalign" -p -f 4 base.apk aligned.apk

echo "7. Sign APK..."
KEYSTORE="$PROJECT_DIR/release.keystore"
if [ ! -f "$KEYSTORE" ]; then
    "$KEYTOOL_BIN" -genkey -v -keystore "$KEYSTORE" \
        -alias debugtoggle -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass debugtoggle -keypass debugtoggle -dname "CN=DebugToggle,O=jinto-ag,C=US"
fi

"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" \
    --ks-pass pass:debugtoggle \
    --key-pass pass:debugtoggle \
    --out "$PROJECT_DIR/DebugToggle-release.apk" \
    aligned.apk

echo "SUCCESS! Output: $PROJECT_DIR/DebugToggle-release.apk"
ls -lh "$PROJECT_DIR/DebugToggle-release.apk"
