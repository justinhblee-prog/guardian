#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.guardian.adblock"
APP_DATA="/data/user/0/$PACKAGE"
PREFS="$APP_DATA/shared_prefs/guardian_settings.xml"
MAIN_CACHE="adguard_dns_filter.txt"
SECURITY_CACHE="hagezi_threat_intelligence.txt"
ANDROID_CACHE="awavenue_android_ads.txt"

# A stalled ADB command must not consume the entire emulator job timeout.
adb_cmd() {
  timeout --signal=TERM --kill-after=2s 30s adb "$@"
}

# Polling must be bounded tightly so a wedged ADB daemon cannot multiply
# the cache-wait loop into a job-long hang. Normal commands keep the longer cap.
adb_poll() {
  timeout --signal=TERM --kill-after=2s 8s adb "$@"
}

adb_cmd shell settings put global private_dns_mode off
adb_cmd shell settings delete global private_dns_specifier >/dev/null 2>&1 || true

# The preceding emulator verification already launched the app and its first-run
# filter refresh may still be running. Do not force-stop that process: doing so
# cancels the very downloads this readiness gate is waiting for. Bring the
# existing Activity back to the foreground and verify that Android accepted it.
launch_result="$(adb_cmd shell am start -W -n "$PACKAGE/.MainActivity" 2>&1)"
printf '%s\\n' "$launch_result"
grep -q 'Status: ok' <<<"$launch_result"
sleep 3
foreground="$(adb_cmd shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | tail -1 || true)"
echo "Guardian foreground activity: ${foreground:-not reported}"
grep -q "$PACKAGE" <<<"$foreground"

read_prefs() {
  adb_cmd shell run-as "$PACKAGE" cat "$PREFS" 2>/dev/null | tr -d '\r' || true
}

cache_ready() {
  local listing prefs
  listing="$(adb_poll shell run-as "$PACKAGE" ls -1 "$APP_DATA/files" 2>&1)" || {
    if (( ${attempt:-1} == 1 )); then echo "Cache listing unavailable: $listing" >&2; fi
    return 1
  }
  prefs="$(adb_poll shell run-as "$PACKAGE" cat "$PREFS" 2>/dev/null | tr -d '\r' || true)"
  grep -Fxq "$MAIN_CACHE" <<<"$listing" || return 1
  grep -q 'name="filter_list_block_count"[^>]*value="[1-9][0-9]*"' <<<"$prefs" || return 1

  # Optional filter sources are required only when enabled in app settings.
  if grep -q 'name="security_filter_enabled"[^>]*value="true"' <<<"$prefs"; then
    grep -Fxq "$SECURITY_CACHE" <<<"$listing" || return 1
    grep -q 'name="security_filter_block_count"[^>]*value="[1-9][0-9]*"' <<<"$prefs" || return 1
  fi
  if grep -q 'name="android_ads_filter_enabled"[^>]*value="true"' <<<"$prefs"; then
    grep -Fxq "$ANDROID_CACHE" <<<"$listing" || return 1
    grep -q 'name="android_ads_filter_block_count"[^>]*value="[1-9][0-9]*"' <<<"$prefs" || return 1
  fi
  return 0
}

echo "Waiting for all enabled filter lists to be downloaded and cached."
# This CI scenario explicitly enables the optional security filter before
# first launch; require its cache and parsed rule count rather than treating it
# as optional, otherwise the test could pass after silently disabling it.
prefs="$(read_prefs)"
grep -q 'name="security_filter_enabled"[^>]*value="true"' <<<"$prefs" || {
  echo "Security filter was not enabled in the emulator fixture."
  read_prefs
  exit 1
}

ready=0
for attempt in $(seq 1 120); do
  if (( attempt % 10 == 1 )); then
    echo "Filter cache wait: $(( (attempt - 1) * 2 ))s elapsed; checking enabled filter lists (bounded ADB poll)."
  fi
  if cache_ready; then
    ready=1
    break
  fi
  if (( attempt % 15 == 0 )); then
    echo "Current Guardian filter preferences:"
    read_prefs | grep -E 'filter_|security_|android_ads_' || true
    echo "Current filter cache files:"
    adb_poll shell run-as "$PACKAGE" ls -lh "$APP_DATA/files" 2>&1 || true
    echo "Recent Guardian app logs:"
    adb_poll logcat -d -t 1500 -s Guardian:V GuardianProtection:V GuardianVpn:V 2>&1 | tail -100 || true
  fi
  sleep 2
done
if [[ "$ready" -ne 1 ]]; then
  echo "Filter cache readiness timed out."
  echo "Persisted filter update errors:"
  read_prefs || true
  echo "Filter cache files:"
  adb_cmd shell run-as "$PACKAGE" ls -la "$APP_DATA" || true
  adb_cmd shell run-as "$PACKAGE" ls -la "$APP_DATA/files" || true
  adb_cmd shell run-as "$PACKAGE" cat "$PREFS" || true
  echo "Guardian filter/network log:"
  adb_cmd logcat -d -t 12000 | grep -E "Guardian|Filter|WorkManager|OkHttp|UnknownHost|SSL|Exception|timeout" | tail -500 || true
  echo "Guardian crash buffer:"
  adb_cmd logcat -b crash -d -t 2000 || true
  echo "Guardian process exit history:"
  adb_cmd shell dumpsys activity exit-info "$PACKAGE" || true
  echo "Guardian memory state:"
  adb_cmd shell dumpsys meminfo "$PACKAGE" || true
  echo "Process death/ANR events:"
  adb_cmd logcat -b events -d -t 4000 | grep -E "am_proc_died|am_kill|am_crash|am_anr" | tail -120 || true
  echo "Emulator network state:"
  adb_cmd shell dumpsys connectivity | tail -160 || true
  exit 1
fi

# Locate the visible home-screen control by its accessibility text rather than
# relying on a fixed screen coordinate.
tap_text() {
  local wanted="$1"
  local xml
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
  local bounds
  bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if ((sys.argv[1] in node.attrib.get("text","")
         or sys.argv[1] in node.attrib.get("content-desc",""))
        and node.attrib.get("visible-to-user", "true") == "true"
        and node.attrib.get("clickable") == "true"
        and node.attrib.get("enabled") == "true"):
        parts=node.attrib.get("bounds","").strip("[]").split("][")
        if len(parts)==2:
            x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
            print((x1+x2)//2,(y1+y2)//2)
            break' "$wanted" <<<"$xml")"
  if [[ -z "$bounds" ]]; then
    echo "Could not locate visible clickable UI control: $wanted"
    echo "$xml"
    return 1
  fi
  read -r x y <<<"$bounds"
  adb_cmd shell input tap "$x" "$y"
}

tap_first_edit_text() {
  local xml bounds
  for attempt in $(seq 1 5); do
    adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
    xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
    bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if (node.attrib.get("class") == "android.widget.EditText"
        and node.attrib.get("visible-to-user", "true") == "true"
        and node.attrib.get("enabled") == "true"):
        parts=node.attrib.get("bounds","").strip("[]").split("][")
        if len(parts)==2:
            x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
            print((x1+x2)//2,(y1+y2)//2)
            break' <<<"$xml")"
    if [[ -n "$bounds" ]]; then break; fi
    adb_cmd shell input swipe 540 1500 540 600 450
    sleep 1
  done
  if [[ -z "$bounds" ]]; then
    echo "Could not locate a visible enabled rule editor after scrolling."
    echo "$xml"
    return 1
  fi
  read -r x y <<<"$bounds"
  adb_cmd shell input tap "$x" "$y"
}

# Exercise a real Activity recreation in landscape while unsaved user-rule text
# is present. The tab and draft must survive rotation without saving to prefs.
echo "Verifying filter-screen and unsaved rule state survive device rotation."
tap_text "필터"
tap_first_edit_text
adb_cmd shell input text 'rotation-preserved.example'
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
before_rotation_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any("rotation-preserved.example" in node.attrib.get("text","") for node in root.iter("node")):
    raise SystemExit("Synthetic rule text was not entered before rotation.")' <<<"$before_rotation_xml"
adb_cmd shell settings put system accelerometer_rotation 0
adb_cmd shell settings put system user_rotation 1
sleep 3
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
rotation_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
nodes=list(root.iter("node"))
selected=any(node.attrib.get("content-desc")=="필터" and node.attrib.get("selected")=="true" for node in nodes)
draft=any("rotation-preserved.example" in node.attrib.get("text","") for node in nodes)
if not (selected and draft):
    raise SystemExit("Activity rotation lost the selected filter tab, restored scroll position, or unsaved rule draft.")' <<<"$rotation_xml"
if ! adb_cmd logcat -d -s GuardianUI:I | grep -E "Activity restored after configuration change; tab=rules scrollY=[1-9][0-9]* hasRuleDraft=true"; then
  echo "Activity did not retain the unsaved rule editor draft across rotation."
  adb_cmd logcat -d -s GuardianUI:I | tail -80
  exit 1
fi
if ! adb_cmd logcat -d -s GuardianUI:I | grep -E "Saving Activity state; tab=rules scrollY=[1-9][0-9]*"; then
  echo "Activity did not save the scrolled filter-editor position before rotation."
  exit 1
fi
adb_cmd shell settings put system user_rotation 0
sleep 2
# Commit the rule entered before rotation. This verifies that the retained draft
# can be saved after Activity recreation and reaches the VPN rule engine.
tap_text "규칙 저장 및 적용"
sleep 1
adb_cmd shell input keyevent KEYCODE_BACK || true
tap_text "홈"
echo "Activity rotation state restoration and custom-rule save passed."

prefs="$(read_prefs)"
if grep -q 'name="protection_active".*value="true"' <<<"$prefs"; then
  echo "VPN already active before test; stopping it first."
  tap_text "보호 중지"
  sleep 3
fi

echo "Requesting VPN start from the actual app UI."
tap_text "보호 시작"

# Accept Android's VPN consent prompt if it appears.
for attempt in $(seq 1 15); do
  echo "Checking Android VPN consent dialog (attempt $attempt/15)."
  adb_poll shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1 || true
  xml="$(adb_poll exec-out cat /sdcard/guardian-window.xml 2>/dev/null | tr -d '\r' || true)"
  consent_bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
try:
    root=ET.fromstring(sys.stdin.read())
except ET.ParseError:
    raise SystemExit(0)
for node in root.iter("node"):
    label=node.attrib.get("text","").strip().lower()
    resource_id=node.attrib.get("resource-id","")
    is_ok_button=(resource_id == "android:id/button1" or label in ("ok","allow","허용","확인"))
    if (is_ok_button
        and node.attrib.get("package") == "com.android.vpndialogs"
        and node.attrib.get("class") == "android.widget.Button"
        and node.attrib.get("visible-to-user", "true") == "true"
        and node.attrib.get("clickable") == "true"
        and node.attrib.get("enabled") == "true"):
        parts=node.attrib.get("bounds","").strip("[]").split("][")
        if len(parts)==2:
            x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
            print((x1+x2)//2,(y1+y2)//2)
            break' <<<"$xml")"
  if [[ -n "$consent_bounds" ]]; then
    read -r x y <<<"$consent_bounds"
    echo "Approving Android VPN consent dialog at ($x,$y)."
    adb_poll shell input tap "$x" "$y"
    sleep 2
    break
  fi
  sleep 1
done

active=0
for attempt in $(seq 1 60); do
  prefs="$(read_prefs)"
  if grep -q 'name="protection_active".*value="true"' <<<"$prefs"; then
    active=1
    break
  fi
  sleep 2
done
if [[ "$active" -ne 1 ]]; then
  echo "VPN did not become active. Current preferences and diagnostics:"
  read_prefs
  adb_cmd logcat -d -t 3000 | grep -E 'Guardian|FATAL EXCEPTION|VpnService' | tail -200 || true
  exit 1
fi

before_queries="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_queries="${before_queries:-0}"
before_blocked="${before_blocked:-0}"

echo "VPN active. Triggering a permitted DNS lookup."
adb_cmd shell ping -c 1 -W 5 example.com >/tmp/guardian-allowed-dns.txt 2>&1 || true
sleep 2
prefs="$(read_prefs)"
after_allowed="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_allowed="${after_allowed:-0}"
if (( after_allowed <= before_queries )); then
  echo "Allowed DNS lookup did not increment Guardian's observed DNS query counter."
  read_prefs
  exit 1
fi
if ! grep -Eq 'PING example\.com \([^)]*\)' /tmp/guardian-allowed-dns.txt; then
  echo "Allowed DNS query was observed, but example.com did not resolve to an address through the active VPN."
  cat /tmp/guardian-allowed-dns.txt || true
  read_prefs
  exit 1
fi

echo "Triggering a blocked-domain DNS lookup."
adb_cmd shell ping -c 1 -W 5 "guardian-ci-${GITHUB_RUN_ID:-test}.doubleclick.net" >/tmp/guardian-blocked-dns.txt 2>&1 || true
sleep 2
prefs="$(read_prefs)"
after_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_blocked="${after_blocked:-0}"
if (( after_blocked <= before_blocked )); then
  echo "Blocked-domain lookup did not increment Guardian's blocked DNS counter."
  cat /tmp/guardian-blocked-dns.txt || true
  read_prefs
  exit 1
fi
if grep -Eq 'PING guardian-ci-[^ ]*\.doubleclick\.net \([^)]*\)' /tmp/guardian-blocked-dns.txt; then
  echo "Blocked test domain unexpectedly resolved to an address."
  cat /tmp/guardian-blocked-dns.txt || true
  read_prefs
  exit 1
fi

echo "Triggering a domain blocked only by the saved user rule."
before_custom_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_custom_blocked="${before_custom_blocked:-0}"
adb_cmd shell ping -c 1 -W 5 "rotation-preserved.example" >/tmp/guardian-custom-blocked-dns.txt 2>&1 || true
sleep 2
prefs="$(read_prefs)"
after_custom_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_custom_blocked="${after_custom_blocked:-0}"
if (( after_custom_blocked <= before_custom_blocked )); then
  echo "Saved custom block rule did not increment Guardian's blocked DNS counter."
  cat /tmp/guardian-custom-blocked-dns.txt || true
  read_prefs
  exit 1
fi
if grep -Eq 'PING rotation-preserved\.example \([^)]*\)' /tmp/guardian-custom-blocked-dns.txt; then
  echo "Custom-blocked domain unexpectedly resolved to an address."
  cat /tmp/guardian-custom-blocked-dns.txt || true
  read_prefs
  exit 1
fi

echo "VPN E2E passed: tunnel active, allowed DNS resolved, built-in and saved custom block rules returned no address."
echo "Custom blocked lookup:"
cat /tmp/guardian-custom-blocked-dns.txt || true
echo "Allowed lookup:"
cat /tmp/guardian-allowed-dns.txt || true
echo "Blocked lookup:"
cat /tmp/guardian-blocked-dns.txt || true
read_prefs
