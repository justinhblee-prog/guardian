#!/usr/bin/env bash
set -euo pipefail

adb start-server
adb wait-for-device

wait_for_boot() {
  local ready=0
  for attempt in $(seq 1 180); do
    local state boot
    state="$(adb get-state 2>/dev/null || true)"
    boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$state" == "device" && "$boot" == "1" ]]; then
      ready=1
      break
    fi
    sleep 2
  done
  test "$ready" -eq 1
}

wait_for_package_service() {
  local ready=0
  for attempt in $(seq 1 90); do
    if adb shell service check package 2>/dev/null | grep -q 'found' &&
       adb shell pm list packages 2>/dev/null | grep -q 'com.android.settings'; then
      ready=1
      break
    fi
    sleep 2
  done
  test "$ready" -eq 1
}

recover_emulator_framework() {
  echo "Android package service unavailable; rebooting emulator framework."
  adb reboot || true
  adb wait-for-device
  wait_for_boot
  wait_for_package_service
}

wait_for_boot
wait_for_package_service
adb logcat -c

python3 scripts/verify_apk_metadata.py /tmp/guardian-0.7.11-dev.apk 24 0.7.11-dev android/app/build/outputs/apk/debug/app-debug.apk 26 0.7.13-dev

previous_installed=0
for attempt in 1 2 3; do
  if adb shell service check package 2>/dev/null | grep -q 'found' &&
     adb install -r -t /tmp/guardian-0.7.11-dev.apk; then
    previous_installed=1
    break
  fi
  echo "Previous APK install attempt ${attempt} failed."
  if ! adb shell service check package 2>/dev/null | grep -q 'found'; then
    recover_emulator_framework
  else
    sleep 5
  fi
done
test "$previous_installed" -eq 1
adb shell pm path com.guardian.adblock | grep -F 'package:'

upgraded=0
for attempt in 1 2 3; do
  if adb shell service check package 2>/dev/null | grep -q 'found' &&
     adb install -r -t android/app/build/outputs/apk/debug/app-debug.apk; then
    upgraded=1
    break
  fi
  echo "APK upgrade attempt ${attempt} failed."
  if ! adb shell service check package 2>/dev/null | grep -q 'found'; then
    recover_emulator_framework
  else
    sleep 5
  fi
done
test "$upgraded" -eq 1
adb shell pm path com.guardian.adblock | grep -F 'package:'
  # The first launch requests Android 13+ notification permission. Grant it in the\n  # test fixture so the permission controller cannot cover the app during startup.\n  adb shell pm grant com.guardian.adblock android.permission.POST_NOTIFICATIONS\n  adb shell am start -n com.guardian.adblock/.MainActivity\nsleep 8
adb shell dumpsys activity activities | tee /tmp/guardian-activities.txt
grep -F 'com.guardian.adblock' /tmp/guardian-activities.txt
adb logcat -d -t 5000 | tee /tmp/guardian-logcat.txt
! grep -E 'FATAL EXCEPTION|Process: com\\.guardian\\.adblock.*has died' /tmp/guardian-logcat.txt
