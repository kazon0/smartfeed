#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$ROOT_DIR/android/SmartFeedAndroid"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB="$SDK_ROOT/platform-tools/adb"

if [[ ! -x "$ADB" ]]; then
  echo "Android adb not found at: $ADB" >&2
  echo "Install Android SDK Platform-Tools or set ANDROID_SDK_ROOT." >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  ANDROID_STUDIO_JAVA="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  if [[ -d "$ANDROID_STUDIO_JAVA" ]]; then
    export JAVA_HOME="$ANDROID_STUDIO_JAVA"
  fi
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java not found. Install Android Studio or set JAVA_HOME." >&2
  exit 1
fi

SERIAL="${1:-}"
if [[ -z "$SERIAL" ]]; then
  SERIAL="$($ADB devices | awk '$2 == "device" && $1 !~ /^emulator-/ { print $1; exit }')"
fi
if [[ -z "$SERIAL" ]]; then
  SERIAL="$($ADB devices | awk '$2 == "device" { print $1; exit }')"
fi
if [[ -z "$SERIAL" ]]; then
  echo "No Android device found. Connect a phone with USB debugging or start an emulator." >&2
  exit 1
fi

GRADLE_ARGS=(assembleDebug)
if [[ -n "${SMARTFEED_BASE_URL:-}" ]]; then
  GRADLE_ARGS+=("-PsmartfeedBaseUrl=$SMARTFEED_BASE_URL")
fi

echo "Building SmartFeed for $SERIAL..."
(
  cd "$ANDROID_DIR"
  ./gradlew "${GRADLE_ARGS[@]}"
)

APK="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
echo "Installing $APK..."
"$ADB" -s "$SERIAL" install -r "$APK"
"$ADB" -s "$SERIAL" shell am force-stop com.example.smartfeedandroid
"$ADB" -s "$SERIAL" shell am start -n com.example.smartfeedandroid/.MainActivity

echo "SmartFeed started on $SERIAL."
