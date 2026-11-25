#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

echo "== Android build environment =="
java -version
gradle --version
sdkmanager --list_installed

echo "== Validate Android project =="
test -f android/settings.gradle.kts -o -f android/settings.gradle
test -d android/app/src/main
test -s shared/filter_rules.txt

echo "== Android JUnit =="
gradle -p android --no-daemon clean testDebugUnitTest

echo "== Android Lint =="
gradle -p android --no-daemon lintDebug

echo "== Debug APK =="
gradle -p android --no-daemon assembleDebug

APK="android/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
sha256sum "$APK"
stat -c 'APK size: %s bytes' "$APK"
echo "PASS: Android unit tests, lint, and Debug APK build completed."
