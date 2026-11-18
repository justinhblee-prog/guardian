#!/usr/bin/env bash
set -euo pipefail

# Bound every ADB call so an offline emulator cannot stall the whole CI job.
adb_cmd() {
  timeout --signal=TERM --kill-after=2s 30s adb "$@"
}

adb_cmd start-server
adb_cmd wait-for-device

wait_for_boot() {
  local ready=0
  for attempt in $(seq 1 180); do
    local state boot
    state="$(adb_cmd get-state 2>/dev/null || true)"
    boot="$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
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
    if adb_cmd shell service check package 2>/dev/null | grep -q 'found' &&
       adb_cmd shell pm list packages 2>/dev/null | grep -q 'com.android.settings'; then
      ready=1
      break
    fi
    sleep 2
  done
  test "$ready" -eq 1
}

recover_emulator_framework() {
  echo "Android package service unavailable; rebooting emulator framework."
  adb_cmd reboot || true
  adb_cmd wait-for-device
  wait_for_boot
  wait_for_package_service
}

wait_for_boot
wait_for_package_service

wait_for_network() {
  for attempt in $(seq 1 30); do
    local result
    result="$(adb_cmd shell ping -c 1 -W 3 adguardteam.github.io 2>&1 || true)"
    if grep -Eq 'PING adguardteam\.github\.io \([^)]*\)' <<<"$result"; then
      echo "Emulator DNS/network ready: $result"
      return 0
    fi
    sleep 2
  done
  echo "Emulator has no working DNS/network connection to the filter host."
  adb_cmd shell dumpsys connectivity || true
  adb_cmd shell ip addr show || true
  adb_cmd shell getprop | grep -E 'net\.dns|dhcp' || true
  return 1
}
wait_for_network
adb_cmd logcat -c

python3 scripts/verify_apk_metadata.py /tmp/guardian-0.7.14-dev.apk 27 0.7.14-dev android/app/build/outputs/apk/debug/app-debug.apk 28 0.7.15-dev

previous_installed=0
for attempt in 1 2 3; do
  if adb_cmd shell service check package 2>/dev/null | grep -q 'found' &&
     adb_cmd install -r -t /tmp/guardian-0.7.14-dev.apk; then
    previous_installed=1
    break
  fi
  echo "Previous APK install attempt ${attempt} failed."
  if ! adb_cmd shell service check package 2>/dev/null | grep -q 'found'; then
    recover_emulator_framework
  else
    sleep 5
  fi
done
test "$previous_installed" -eq 1
adb_cmd shell pm path com.guardian.adblock | grep -F 'package:'

upgraded=0
for attempt in 1 2 3; do
  if adb_cmd shell service check package 2>/dev/null | grep -q 'found' &&
     adb_cmd install -r -t android/app/build/outputs/apk/debug/app-debug.apk; then
    upgraded=1
    break
  fi
  echo "APK upgrade attempt ${attempt} failed."
  if ! adb_cmd shell service check package 2>/dev/null | grep -q 'found'; then
    recover_emulator_framework
  else
    sleep 5
  fi
done
test "$upgraded" -eq 1
adb_cmd shell pm path com.guardian.adblock | grep -F 'package:'

# The first launch requests Android 13+ notification permission. Grant it in the
# test fixture so the permission controller cannot cover the app during startup.
adb_cmd shell pm grant com.guardian.adblock android.permission.POST_NOTIFICATIONS

# Enable the optional threat-intelligence feed before first launch. Keep remote
# shell quoting explicit: adb shell joins arguments into one command string.
adb_cmd shell run-as com.guardian.adblock mkdir -p shared_prefs
printf '%s' '<map><boolean name="security_filter_enabled" value="true" /></map>' |
  adb_cmd shell run-as com.guardian.adblock tee shared_prefs/guardian_settings.xml >/dev/null

adb_cmd shell am start -n com.guardian.adblock/.MainActivity
sleep 8
adb_cmd shell dumpsys activity activities | tee /tmp/guardian-activities.txt
grep -F 'com.guardian.adblock' /tmp/guardian-activities.txt
adb_cmd logcat -d -t 5000 | tee /tmp/guardian-logcat.txt
python3 scripts/verify_guardian_crash_log.py /tmp/guardian-logcat.txt
