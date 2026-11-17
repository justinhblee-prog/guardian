#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.guardian.adblock"
APP_DATA="/data/user/0/$PACKAGE"
PREFS="$APP_DATA/shared_prefs/guardian_settings.xml"
MAIN_CACHE="adguard_dns_filter.txt"
SECURITY_CACHE="hagezi_threat_intelligence.txt"
ANDROID_CACHE="awavenue_android_ads.txt"

# Keep a rolling app/system log from before the VPN tap. If ADB later drops
# offline, post-failure adb logcat cannot recover the evidence.
LIVE_LOG="/tmp/guardian-vpn-live-logcat.txt"
adb logcat -c
adb logcat -v threadtime >"$LIVE_LOG" 2>&1 &
LOGCAT_PID=$!
cleanup_logcat() { kill "$LOGCAT_PID" >/dev/null 2>&1 || true; }
trap cleanup_logcat EXIT

adb shell settings put global private_dns_mode off
adb shell settings delete global private_dns_specifier >/dev/null 2>&1 || true
adb shell am force-stop "$PACKAGE" || true
adb shell monkey -p "$PACKAGE" 1 >/dev/null
sleep 3

read_prefs() {
  adb shell run-as "$PACKAGE" cat "$PREFS" 2>/dev/null | tr -d '\r' || true
}

cache_ready() {
  local listing prefs
  listing="$(timeout --signal=TERM --kill-after=2s 12s adb shell run-as "$PACKAGE" ls -1 "$APP_DATA/files" 2>&1)" || {
    if (( ${attempt:-1} == 1 )); then echo "Cache listing unavailable: $listing" >&2; fi
    return 1
  }
  prefs="$(read_prefs)"
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
ready=0
for attempt in $(seq 1 240); do
  if (( attempt % 10 == 1 )); then
    echo "Filter cache wait: $(( (attempt - 1) * 2 ))s elapsed; checking all three enabled lists."
  fi
  if cache_ready; then
    ready=1
    break
  fi
  sleep 2
done
if [[ "$ready" -ne 1 ]]; then
  echo "Filter cache readiness timed out."
  echo "Live logcat captured while ADB was connected (last 300 matching lines):"
  grep -E 'Guardian|FATAL EXCEPTION|VpnService|ActivityManager|lmkd|OutOfMemory|lowmemory' "$LIVE_LOG" | tail -300 || true
  echo "Persisted filter update errors:"
  read_prefs || true
  echo "Filter cache files:"
  adb shell run-as "$PACKAGE" ls -la "$APP_DATA" || true
  adb shell run-as "$PACKAGE" ls -la "$APP_DATA/files" || true
  adb shell run-as "$PACKAGE" cat "$PREFS" || true
  echo "Guardian filter/network log:"
  adb logcat -d -t 6000 | grep -E "Guardian|Filter|WorkManager|OkHttp|UnknownHost|SSL|Exception|timeout" | tail -300 || true
  echo "Emulator network state:"
  adb shell dumpsys connectivity | tail -160 || true
  exit 1
fi

# Locate the visible home-screen control by its accessibility text rather than
# relying on a fixed screen coordinate.
tap_text() {
  local wanted="$1"
  local xml bounds
  adb shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  xml="$(adb exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
  printf '%s\n' "$xml" > /tmp/guardian-window-before-tap.xml
  bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
matches=[]
for node in root.iter("node"):
    if sys.argv[1] in node.attrib.get("text","") and node.attrib.get("clickable")=="true":
        parts=node.attrib.get("bounds","").strip("[]").split("][")
        if len(parts)==2:
            x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
            matches.append(((x1+x2)//2,(y1+y2)//2,node.attrib.get("class",""),node.attrib.get("text","")))
if matches:
    x,y,klass,label=matches[0]
    print(x,y,klass,label)' "$wanted" <<<"$xml")"
  if [[ -z "$bounds" ]]; then
    echo "Could not locate clickable UI control: $wanted"
    echo "$xml"
    return 1
  fi
  read -r x y klass label <<<"$bounds"
  echo "Tapping clickable UI node: text='$label' class='$klass' at $x,$y"
  adb shell input tap "$x" "$y"
  sleep 2
  if [[ "$wanted" == "보호 시작" ]] && ! grep -q "GuardianProtection.*Protection button tapped" "$LIVE_LOG"; then
    echo "UI tap did not produce the expected protection-button click log."
    echo "Captured UI hierarchy:"
    cat /tmp/guardian-window-before-tap.xml
    echo "Recent app log:"
    grep -E 'Guardian|FATAL EXCEPTION|VpnService|ActivityManager|lmkd|OutOfMemory|lowmemory' "$LIVE_LOG" | tail -120 || true
    return 1
  fi
}

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
  adb shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1 || true
  xml="$(adb exec-out cat /sdcard/guardian-window.xml 2>/dev/null | tr -d '\r' || true)"
  consent_bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    label=node.attrib.get("text","").lower()
    if any(k in label for k in ("allow","허용","확인","ok")):
        parts=node.attrib.get("bounds","").strip("[]").split("][")
        if len(parts)==2:
            x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
            print((x1+x2)//2,(y1+y2)//2)
            break' <<<"$xml")"
  if [[ -n "$consent_bounds" ]]; then
    read -r x y <<<"$consent_bounds"
    adb shell input tap "$x" "$y"
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
  echo "Live logcat captured while ADB was connected (last 500 matching lines):"
  grep -E 'Guardian|FATAL EXCEPTION|VpnService|ActivityManager|lmkd|OutOfMemory|lowmemory' "$LIVE_LOG" | tail -500 || true
  echo "ADB state at failure:"
  adb get-state 2>&1 || true
  exit 1
fi

before_queries="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_queries="${before_queries:-0}"
before_blocked="${before_blocked:-0}"

echo "VPN active. Triggering a permitted DNS lookup."
adb shell ping -c 1 -W 5 example.com >/tmp/guardian-allowed-dns.txt 2>&1 || true
sleep 2
prefs="$(read_prefs)"
after_allowed="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_allowed="${after_allowed:-0}"
if (( after_allowed <= before_queries )); then
  echo "Allowed DNS lookup did not increment Guardian's observed DNS query counter."
  read_prefs
  exit 1
fi

echo "Triggering a blocked-domain DNS lookup."
adb shell ping -c 1 -W 5 "guardian-ci-${GITHUB_RUN_ID:-test}.doubleclick.net" >/tmp/guardian-blocked-dns.txt 2>&1 || true
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

echo "VPN E2E passed: tunnel active, allowed DNS observed, and blocked DNS counter incremented."
echo "Allowed lookup:"
cat /tmp/guardian-allowed-dns.txt || true
echo "Blocked lookup:"
cat /tmp/guardian-blocked-dns.txt || true
read_prefs
