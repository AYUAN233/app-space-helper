#!/usr/bin/env bash
# Copyright (C) 2026 AYUAN
# SPDX-License-Identifier: GPL-3.0-only
# Build the APK without Gradle.
#
# Requirements:
#   - JDK 17+
#   - Android SDK Platform 34 (android.jar)
#   - Android SDK build-tools with d8.jar, apksigner.jar, aapt and zipalign
#   - zip
#
# On ARM64 Linux the bundled native aapt/zipalign are x86_64 binaries and
# cannot run. Install the distro packages (`aapt`, `zipalign`) and this script
# will prefer them automatically.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
VERSION_NAME="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$MANIFEST" | head -n 1)"
VERSION_NAME="${VERSION_NAME:-1.0.4}"

if [ -z "${ANDROID_SDK_ROOT:-}" ] && [ -z "${ANDROID_HOME:-}" ]; then
  for candidate in "$HOME/Android/Sdk" /root/android-sdk /opt/android-sdk /usr/lib/android-sdk; do
    if [ -d "$candidate" ]; then
      ANDROID_SDK_ROOT="$candidate"
      break
    fi
  done
fi
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  echo "Android SDK not found. Set ANDROID_SDK_ROOT or ANDROID_HOME." >&2
  exit 1
fi

if [ -n "${ANDROID_BUILD_TOOLS:-}" ]; then
  BUILD_TOOLS="$ANDROID_BUILD_TOOLS"
elif [ -d "$SDK/build-tools/35.0.0" ]; then
  BUILD_TOOLS="$SDK/build-tools/35.0.0"
else
  BUILD_TOOLS="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort | tail -n 1)"
fi
if [ -z "${BUILD_TOOLS:-}" ] || [ ! -d "$BUILD_TOOLS" ]; then
  echo "Android build-tools not found under $SDK/build-tools." >&2
  exit 1
fi

ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-34/android.jar}"
D8_JAR="$BUILD_TOOLS/lib/d8.jar"
APKSIGNER_JAR="$BUILD_TOOLS/lib/apksigner.jar"

if [ -n "${AAPT:-}" ]; then
  true
elif "$BUILD_TOOLS/aapt" version >/dev/null 2>&1; then
  AAPT="$BUILD_TOOLS/aapt"
elif command -v aapt >/dev/null 2>&1; then
  AAPT="$(command -v aapt)"
else
  echo "aapt not found. Install Android build-tools or the aapt package." >&2
  exit 1
fi

if [ -n "${ZIPALIGN:-}" ]; then
  true
elif "$BUILD_TOOLS/zipalign" -h >/dev/null 2>&1; then
  ZIPALIGN="$BUILD_TOOLS/zipalign"
elif command -v zipalign >/dev/null 2>&1; then
  ZIPALIGN="$(command -v zipalign)"
else
  echo "zipalign not found. Install Android build-tools or the zipalign package." >&2
  exit 1
fi

for tool in "$AAPT" "$ZIPALIGN" "$ANDROID_JAR" "$D8_JAR" "$APKSIGNER_JAR"; do
  if [ ! -e "$tool" ]; then
    echo "Missing Android build tool: $tool" >&2
    exit 1
  fi
done
for cmd in java javac keytool zip; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing command: $cmd" >&2
    exit 1
  fi
done

BUILD="$ROOT/build"
DIST="$ROOT/dist"
GEN="$BUILD/gen"
CLASSES="$BUILD/classes"
DEX="$BUILD/dex"
KEYSTORE="${APP_KEYSTORE:-$ROOT/signing/appspace.jks}"
STORE_PASS="${APP_KEYSTORE_PASS:-appspace2026}"
KEY_PASS="${APP_KEY_PASS:-appspace2026}"

rm -rf "$BUILD"
mkdir -p "$GEN" "$CLASSES" "$DEX" "$DIST"

echo "[1/6] Compiling resources"
"$AAPT" package -f -m \
  -J "$GEN" \
  -M "$MANIFEST" \
  -S "$ROOT/app/src/main/res" \
  -I "$ANDROID_JAR" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  -F "$BUILD/unsigned.apk"

echo "[2/6] Compiling Java"
find "$ROOT/app/src/main/java" -name '*.java' -print > "$BUILD/sources.txt"
echo "$GEN/com/dsh/appspace/R.java" >> "$BUILD/sources.txt"
javac -encoding UTF-8 -source 17 -target 17 -Xlint:-options \
  -classpath "$ANDROID_JAR" -d "$CLASSES" @"$BUILD/sources.txt"

echo "[3/6] Dexing"
find "$CLASSES" -name '*.class' -print > "$BUILD/classes.txt"
java -cp "$D8_JAR" com.android.tools.r8.D8 \
  --min-api 26 --lib "$ANDROID_JAR" --output "$DEX" @"$BUILD/classes.txt"

echo "[4/6] Packaging"
cp "$BUILD/unsigned.apk" "$BUILD/unsigned-with-dex.apk"
( cd "$DEX" && zip -q -j "$BUILD/unsigned-with-dex.apk" classes.dex )
"$ZIPALIGN" -f -p 4 "$BUILD/unsigned-with-dex.apk" "$BUILD/aligned.apk"

echo "[5/6] Signing"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -alias appspace \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "CN=AYUAN, OU=Local, O=AYUAN, L=Local, ST=Local, C=CN" >/dev/null 2>&1
  echo "Generated local signing key: $KEYSTORE"
fi
OUT="$DIST/AppSpaceHelper-v$VERSION_NAME.apk"
java -jar "$APKSIGNER_JAR" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias appspace \
  --ks-pass "pass:$STORE_PASS" \
  --key-pass "pass:$KEY_PASS" \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --out "$OUT" \
  "$BUILD/aligned.apk"
java -jar "$APKSIGNER_JAR" verify --verbose --print-certs "$OUT"

echo "[6/6] Verifying manifest"
"$AAPT" dump badging "$OUT" | head -n 10
ls -lh "$OUT"
echo "APK: $OUT"
