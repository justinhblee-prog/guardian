#!/usr/bin/env bash
set -euo pipefail

# Bound every ADB call so a wedged emulator cannot stall CI indefinitely.
ADB_DEFAULT_TIMEOUT="${ADB_DEFAULT_TIMEOUT:-15}"
ADB_BOOT_TIMEOUT="${ADB_BOOT_TIMEOUT:-240}"
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
ADB_BIN="$(command -v adb)"

adb() {
  local seconds="$ADB_DEFAULT_TIMEOUT"
  case "${1:-} ${2:-}" in
    "wait-for-device") seconds=120 ;;
    "install"*|"install") seconds=120 ;;
    "start-server") seconds=30 ;;
  esac
  echo "+ adb $* (timeout=${seconds}s)" >&2
  timeout --signal=TERM --kill-after=3s "${seconds}s" "$ADB_BIN" "$@"
}

adb start-server
adb wait-for-device

wait_for_boot() {
  local deadline=$((SECONDS + ADB_BOOT_TIMEOUT))
  local state boot
  echo "Waiting up to ${ADB_BOOT_TIMEOUT}s for Android boot completion."
  while (( SECONDS < deadline )); do
    state="$(adb get-state 2>/dev/null || true)"
    boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    echo "Emulator readiness: adb_state=${state:-unavailable}, sys.boot_completed=${boot:-empty}"
    if [[ "$state" == "device" && "$boot" == "1" ]]; then
      echo "Android emulator boot completed."
      return 0
    fi
    sleep 3
  done
  echo "ERROR: Android emulator did not report sys.boot_completed=1 within ${ADB_BOOT_TIMEOUT}s." >&2
  echo "ADB devices:" >&2
  timeout --signal=TERM --kill-after=2s 10s "$ADB_BIN" devices -l >&2 || true
  echo "Bounded getprop diagnostic:" >&2
  timeout --signal=TERM --kill-after=2s 10s "$ADB_BIN" shell getprop sys.boot_completed >&2 || true
  return 1
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
  # The first launch requests Android 13+ notification permission. Grant it in the
  # test fixture so the permission controller cannot cover the app during startup.
  adb shell pm grant com.guardian.adblock android.permission.POST_NOTIFICATIONS
  adb shell am start -n com.guardian.adblock/.MainActivity
sleep 8
adb shell dumpsys activity activities | tee /tmp/guardian-activities.txt
grep -F 'com.guardian.adblock' /tmp/guardian-activities.txt
adb logcat -d -t 5000 | tee /tmp/guardian-logcat.txt
! grep -E 'FATAL EXCEPTION|Process: com\\.guardian\\.adblock.*has died' /tmp/guardian-logcat.txt
