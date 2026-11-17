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
# Let WorkManager and the Android framework settle after the last cache
# becomes available; avoid launching the VPN UI while filter I/O is peaking.
echo "Filter caches are ready. Allowing Android framework to settle before VPN UI automation."
sleep 12
adb shell dumpsys activity activities | grep -F "$PACKAGE" | tail -5 || true

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
  # The API 35 Pixel launcher can wedge while the emulator is loading the
  # large filter caches. Close the launcher ANR (not Guardian), disable only
  # that launcher in this disposable AVD, and keep the already foregrounded
  # Guardian activity available for the VPN consent flow.
  for recovery in 1 2 3; do
    adb shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1 || true
    xml="$(adb exec-out cat /sdcard/guardian-window.xml 2>/dev/null | tr -d '\r' || true)"
    if grep -q "Quickstep isn't responding" <<<"$xml"; then
      echo "Android Quickstep ANR detected; closing the emulator launcher (recovery $recovery/3)."
      close_bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if node.attrib.get("resource-id")=="android:id/aerr_close" and node.attrib.get("clickable")=="true":
        p=node.attrib.get("bounds","").strip("[]").split("][")
        if len(p)==2:
            x1,y1=map(int,p[0].split(",")); x2,y2=map(int,p[1].split(","))
            print((x1+x2)//2,(y1+y2)//2)
            break' <<<"$xml")"
      if [[ -z "$close_bounds" ]]; then
        echo "Quickstep ANR dialog did not expose its Close app control."
        return 1
      fi
      read -r cx cy <<<"$close_bounds"
      adb shell input tap "$cx" "$cy"
      sleep 2
      adb shell pm disable-user --user 0 com.android.launcher3 >/dev/null 2>&1 || true
      sleep 1
      continue
    fi
    break
  done
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
  # Quickstep can enter ANR after the large filter-cache warm-up. The
  # dialog may appear between the initial recovery check and this hierarchy
  # read, so handle it here as well instead of misreporting a missing app
  # button. Use the dialog's accessibility resource id, not a fixed tap.
  for recovery in 1 2 3; do
    [[ -z "$bounds" ]] || break
    if ! grep -q "Quickstep isn't responding" <<<"$xml"; then
      break
    fi
    echo "Quickstep ANR appeared during UI lookup; closing the emulator launcher (recovery $recovery/3)."
    close_bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if node.attrib.get("resource-id")=="android:id/aerr_close" and node.attrib.get("clickable")=="true":
        p=node.attrib.get("bounds","").strip("[]").split("][")
        if len(p)==2:
            x1,y1=map(int,p[0].split(",")); x2,y2=map(int,p[1].split(","))
            print((x1+x2)//2,(y1+y2)//2)
            break' <<<"$xml")"
    if [[ -z "$close_bounds" ]]; then
      echo "Quickstep ANR dialog did not expose its Close app control."
      break
    fi
    read -r cx cy <<<"$close_bounds"
    adb shell input tap "$cx" "$cy" || true
    sleep 2
    adb shell pm disable-user --user 0 com.android.launcher3 >/dev/null 2>&1 || true
    sleep 1
    adb shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1 || true
    xml="$(adb exec-out cat /sdcard/guardian-window.xml 2>/dev/null | tr -d '\r' || true)"
    bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if sys.argv[1] in node.attrib.get("text","") and node.attrib.get("clickable")=="true":
        p=node.attrib.get("bounds","").strip("[]").split("][")
        if len(p)==2:
            x1,y1=map(int,p[0].split(",")); x2,y2=map(int,p[1].split(","))
            print((x1+x2)//2,(y1+y2)//2,node.attrib.get("class",""),node.attrib.get("text",""))
            break' "$wanted" <<<"$xml")"
  done
  if [[ -z "$bounds" ]]; then
    echo "Could not locate clickable UI control: $wanted"
    echo "Final UI hierarchy:"
    echo "$xml"
    echo "Guardian process memory:"
    adb shell dumpsys meminfo "$PACKAGE" | tail -80 || true
    echo "Launcher process memory:"
    adb shell dumpsys meminfo com.android.launcher3 | tail -60 || true
    echo "Live app/system ANR and memory diagnostics:"
    grep -Ei 'ANR in|not responding|Quickstep|Guardian|FATAL EXCEPTION|OutOfMemory|lmkd|lowmemory' "$LIVE_LOG" | tail -240 || true
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

# Android's VPN confirmation dialog contains both "Allow" and
# "Don't allow". Match only an explicit positive action; a substring search
# for "allow" can select the denial button and silently reject VPN access.
consent_accepted=0
for attempt in $(seq 1 20); do
  adb shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1 || true
  xml="$(adb exec-out cat /sdcard/guardian-window.xml 2>/dev/null | tr -d '\r' || true)"
  consent_bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
positive={"allow","허용","앱 사용 중에만 허용","이번만 허용","항상 허용","확인","ok"}
for node in root.iter("node"):
    label=" ".join(node.attrib.get("text","").lower().split())
    if label in positive and node.attrib.get("clickable")=="true":
        parts=node.attrib.get("bounds","").strip("[]").split("][")
        if len(parts)==2:
            x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
            print((x1+x2)//2,(y1+y2)//2,label)
            break' <<<"$xml")"
  if [[ -n "$consent_bounds" ]]; then
    read -r x y consent_label <<<"$consent_bounds"
    echo "Accepting Android VPN consent using positive button '$consent_label' at $x,$y"
    adb shell input tap "$x" "$y"
    consent_accepted=1
    sleep 2
    break
  fi
  sleep 1
done
if [[ "$consent_accepted" -ne 1 ]]; then
  echo "Android VPN consent dialog did not expose a recognized positive action."
  echo "Current UI hierarchy:"
  echo "$xml"
  echo "Recent VPN consent log:"
  grep -E 'GuardianProtection|VpnDialogs|ConfirmDialog|VpnService' "$LIVE_LOG" | tail -100 || true
  exit 1
fi

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
before_forwarded="$(sed -n 's/.*name="dns_forwarded"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_failed="$(sed -n 's/.*name="dns_failed"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_queries="${before_queries:-0}"
before_blocked="${before_blocked:-0}"
before_forwarded="${before_forwarded:-0}"
before_failed="${before_failed:-0}"

echo "VPN active. Triggering a permitted DNS lookup."
allowed_output="$(adb shell ping -c 1 -W 5 example.com 2>&1 || true)"
printf '%s\n' "$allowed_output" | tee /tmp/guardian-allowed-dns.txt
sleep 2
prefs="$(read_prefs)"
after_queries="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_forwarded="$(sed -n 's/.*name="dns_forwarded"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_queries="${after_queries:-0}"
after_forwarded="${after_forwarded:-0}"
echo "Allowed DNS counters: queries ${before_queries} -> ${after_queries}; forwarded ${before_forwarded} -> ${after_forwarded}."
if (( after_queries <= before_queries || after_forwarded <= before_forwarded )); then
  echo "Allowed DNS lookup was not observed as a forwarded Guardian query."
  read_prefs
  exit 1
fi
if ! grep -Fq 'PING example.com (' <<<"$allowed_output"; then
  echo "Allowed hostname did not resolve to an IPv4 or IPv6 address through the active VPN."
  exit 1
fi

echo "Triggering a blocked-domain DNS lookup."
blocked_host="guardian-ci-${GITHUB_RUN_ID:-test}.doubleclick.net"
blocked_output="$(adb shell ping -c 1 -W 5 "$blocked_host" 2>&1 || true)"
printf '%s\n' "$blocked_output" | tee /tmp/guardian-blocked-dns.txt
sleep 2
prefs="$(read_prefs)"
after_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_failed="$(sed -n 's/.*name="dns_failed"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_blocked="${after_blocked:-0}"
after_failed="${after_failed:-0}"
if (( after_blocked <= before_blocked )); then
  echo "Blocked-domain lookup did not increment Guardian's blocked DNS counter."
  read_prefs
  exit 1
fi
if grep -Fq "PING $blocked_host (" <<<"$blocked_output"; then
  echo "Blocked hostname unexpectedly resolved to an address."
  exit 1
fi
if ! grep -Eiq 'unknown host|bad address|name or service not known|temporary failure in name resolution' <<<"$blocked_output"; then
  echo "Blocked lookup did not produce an explicit DNS resolution failure."
  exit 1
fi
if (( after_failed > before_failed )); then
  echo "Guardian recorded a DNS forwarding failure during the allow/block acceptance test."
  read_prefs
  exit 1
fi

echo "VPN DNS E2E passed: tunnel active, allowed hostname resolved and forwarded, blocked hostname failed resolution and incremented the block counter without an upstream failure."
echo "Allowed lookup:"
cat /tmp/guardian-allowed-dns.txt || true
echo "Blocked lookup:"
cat /tmp/guardian-blocked-dns.txt || true
read_prefs
