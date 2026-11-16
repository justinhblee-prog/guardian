#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.guardian.adblock"
PREFS="shared_prefs/guardian_settings.xml"
MAIN_CACHE="adguard_dns_filter.txt"
SECURITY_CACHE="hagezi_threat_intelligence.txt"
ANDROID_CACHE="awavenue_android_ads.txt"

adb shell am force-stop "$PACKAGE" || true
adb shell monkey -p "$PACKAGE" 1 >/dev/null
sleep 3

read_prefs() {
  adb shell run-as "$PACKAGE" cat "$PREFS" 2>/dev/null | tr -d '\r' || true
}

cache_ready() {
  adb shell run-as "$PACKAGE" ls -l files 2>/dev/null | grep -q "$MAIN_CACHE" &&
  adb shell run-as "$PACKAGE" ls -l files 2>/dev/null | grep -q "$SECURITY_CACHE" &&
  adb shell run-as "$PACKAGE" ls -l files 2>/dev/null | grep -q "$ANDROID_CACHE"
}

echo "Waiting for all enabled filter lists to be downloaded and cached."
ready=0
for attempt in $(seq 1 180); do
  if cache_ready; then
    prefs="$(read_prefs)"
    if grep -q 'name="filter_list_block_count"' <<<"$prefs" &&
       grep -q 'name="security_filter_block_count"' <<<"$prefs" &&
       grep -q 'name="android_ads_filter_block_count"' <<<"$prefs"; then
      ready=1
      break
    fi
  fi
  sleep 2
done
if [[ "$ready" -ne 1 ]]; then
  echo "Filter cache readiness timed out."
  adb shell run-as "$PACKAGE" ls -la files || true
  read_prefs || true
  exit 1
fi

# Locate the visible home-screen control by its accessibility text rather than
# relying on a fixed screen coordinate.
tap_text() {
  local wanted="$1"
  local xml
  adb shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  xml="$(adb exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
  local bounds
  bounds="$(python3 -c 'import re,sys
xml=sys.stdin.read()
for node in re.findall(r"<node\\b[^>]*>",xml):
    t=re.search(r"\\btext=\"([^\"]*)\"",node)
    b=re.search(r"\\bbounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"",node)
    if t and b and sys.argv[1] in t.group(1):
        x1,y1,x2,y2=map(int,b.groups())
        print((x1+x2)//2,(y1+y2)//2)
        break' "$wanted" <<<"$xml")"
  if [[ -z "$bounds" ]]; then
    echo "Could not locate UI control: $wanted"
    echo "$xml"
    return 1
  fi
  read -r x y <<<"$bounds"
  adb shell input tap "$x" "$y"
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
  consent_bounds="$(python3 -c 'import re,sys
xml=sys.stdin.read()
for node in re.findall(r"<node\\b[^>]*>",xml):
    t=re.search(r"\\btext=\"([^\"]*)\"",node)
    b=re.search(r"\\bbounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"",node)
    if t and b and any(k in t.group(1).lower() for k in ("allow","허용","확인","ok")):
        x1,y1,x2,y2=map(int,b.groups())
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
  adb logcat -d -t 3000 | grep -E 'Guardian|FATAL EXCEPTION|VpnService' | tail -200 || true
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
