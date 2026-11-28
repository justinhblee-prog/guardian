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

# Android 14/15 emulator images may inherit opportunistic Private DNS before
# the first app launch. The emulator NAT DNS forwarder (10.0.2.3) does not
# provide DNS-over-TLS, so disable Private DNS before probing or downloading
# the first filter list. The VPN E2E script repeats this setting after launch.
adb_cmd shell settings put global private_dns_mode off
adb_cmd shell settings delete global private_dns_specifier >/dev/null 2>&1 || true

wait_for_network() {
  local last_result=""
  for attempt in $(seq 1 30); do
    local result
    result="$(adb_cmd shell ping -c 1 -W 3 adguardteam.github.io 2>&1 || true)"
    last_result="$result"
    if grep -Eq 'PING adguardteam\.github\.io \([^)]*\)' <<<"$result"; then
      echo "Emulator DNS/network ready: $result"
      return 0
    fi
    sleep 2
  done
  echo "Emulator has no working DNS/network connection to the filter host. Last DNS probe: $last_result"
  adb_cmd shell dumpsys connectivity || true
  adb_cmd shell ip addr show || true
  adb_cmd shell getprop | grep -E 'net\.dns|dhcp' || true
  return 1
}
wait_for_network
adb_cmd logcat -c

PREVIOUS_APK="${GUARDIAN_PREVIOUS_APK:-/tmp/guardian-0.7.25-dev.apk}"
PREVIOUS_VERSION_CODE="${GUARDIAN_PREVIOUS_VERSION_CODE:-38}"
PREVIOUS_VERSION_NAME="${GUARDIAN_PREVIOUS_VERSION_NAME:-0.7.25-dev}"
CURRENT_VERSION_CODE="${GUARDIAN_CURRENT_VERSION_CODE:-39}"
CURRENT_VERSION_NAME="${GUARDIAN_CURRENT_VERSION_NAME:-0.7.26-dev}"
python3 scripts/verify_apk_metadata.py "$PREVIOUS_APK" "$PREVIOUS_VERSION_CODE" "$PREVIOUS_VERSION_NAME" android/app/build/outputs/apk/debug/app-debug.apk "$CURRENT_VERSION_CODE" "$CURRENT_VERSION_NAME"

previous_installed=0
for attempt in 1 2 3; do
  if adb_cmd shell service check package 2>/dev/null | grep -q 'found' &&
     adb_cmd install -r -t "$PREVIOUS_APK"; then
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

# Keep the previous APK installed for the full VPN E2E scenario. The following
# script starts protection on this version, then replaces it with the new APK
# while the tunnel is active to verify MY_PACKAGE_REPLACED auto-recovery.
CURRENT_APK='android/app/build/outputs/apk/debug/app-debug.apk'
test -s "$CURRENT_APK"
echo "Previous development version is installed; the new APK is ready for the live-VPN update test."
adb_cmd shell pm path com.guardian.adblock | grep -F 'package:'

# The first launch requests Android 13+ notification permission. Grant it in the
# test fixture so the permission controller cannot cover the app during startup.
adb_cmd shell pm grant com.guardian.adblock android.permission.POST_NOTIFICATIONS

# Enable the optional threat-intelligence feed before first launch. Keep remote
# shell quoting explicit: adb shell joins arguments into one command string.
adb_cmd shell run-as com.guardian.adblock mkdir -p shared_prefs
# Keep recurring updates disabled in the fixture. Initial filter population
# must still run because a missing first-run cache is setup, not a scheduled
# refresh. Mark the migration as complete so the explicit OFF choice survives
# the in-place update verification.
printf '%s' '<map><boolean name="security_filter_enabled" value="true" /><boolean name="filter_auto_update" value="false" /><boolean name="filter_auto_update_default_off_v1" value="true" /></map>' |
  adb_cmd shell run-as com.guardian.adblock tee shared_prefs/guardian_settings.xml >/dev/null

adb_cmd shell am start -n com.guardian.adblock/.MainActivity
sleep 8
adb_cmd shell dumpsys activity activities | tee /tmp/guardian-activities.txt
grep -F 'com.guardian.adblock' /tmp/guardian-activities.txt
adb_cmd logcat -d -t 5000 | tee /tmp/guardian-logcat.txt
python3 scripts/verify_guardian_crash_log.py /tmp/guardian-logcat.txt
