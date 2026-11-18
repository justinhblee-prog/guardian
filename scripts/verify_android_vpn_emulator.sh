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

tap_checkbox() {
  local wanted="$1"
  local xml bounds
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
  bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if (node.attrib.get("class") == "android.widget.CheckBox"
        and sys.argv[1] in node.attrib.get("text","")
        and node.attrib.get("visible-to-user", "true") == "true"
        and node.attrib.get("clickable") == "true"
        and node.attrib.get("enabled") == "true"):
        parts=node.attrib.get("bounds","").strip("[]").split("][")
        if len(parts)==2:
            x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
            print((x1+x2)//2,(y1+y2)//2)
            break' "$wanted" <<<"$xml")"
  if [[ -z "$bounds" ]]; then
    echo "Could not locate visible app-exclusion checkbox: $wanted"
    echo "$xml"
    return 1
  fi
  read -r x y <<<"$bounds"
  adb_cmd shell input tap "$x" "$y"
}

tap_content_description() {
  local wanted="$1"
  local xml bounds
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
  bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if (node.attrib.get("content-desc","") == sys.argv[1]
        and node.attrib.get("visible-to-user", "true") == "true"
        and node.attrib.get("clickable") == "true"
        and node.attrib.get("enabled") == "true"):
        parts=node.attrib.get("bounds","").strip("[]").split("][")
        if len(parts)==2:
            x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
            print((x1+x2)//2,(y1+y2)//2)
            break' "$wanted" <<<"$xml")"
  if [[ -z "$bounds" ]]; then
    echo "Could not locate visible control by content description: $wanted"
    echo "$xml"
    return 1
  fi
  read -r x y <<<"$bounds"
  adb_cmd shell input tap "$x" "$y"
}

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

dismiss_dev_update_prompt() {
  local xml
  for attempt in $(seq 1 8); do
    adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1 || true
    xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml 2>/dev/null | tr -d '\r' || true)"
    if ! grep -Fq 'text="Guardian 개발 업데이트"' <<<"$xml"; then
      return 0
    fi
    if grep -Fq 'text="나중에"' <<<"$xml"; then
      tap_text "나중에"
      sleep 1
      echo "Dismissed the optional development update prompt for emulator navigation."
      return 0
    fi
    sleep 1
  done
  echo "Development update prompt remained visible and could not be dismissed safely."
  echo "$xml"
  return 1
}


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

echo "Verifying the dedicated Guardian Premium menu and license activation focus."
dismiss_dev_update_prompt
tap_content_description "프리미엄"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
premium_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
text=" ".join(node.attrib.get("text","") for node in root.iter("node"))
for expected in ("Guardian Premium", "GUARDIAN  /  PRO MEMBERSHIP"):
    if expected not in text:
        raise SystemExit("Premium screen is missing expected content: "+expected)' <<<"$premium_xml"
if grep -Fq 'text="PRO 라이선스 활성화"' <<<"$premium_xml"; then
  tap_text "PRO 라이선스 활성화"
else
  tap_text "PRO 라이선스 관리"
fi
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
premium_license_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
# A prior UI flow can leave the first-run installed-app privacy disclosure on
# top of the settings screen. Do not accept it implicitly during the Premium
# focus check; dismiss it with the explicit cancel action, then inspect the
# underlying license field in a fresh hierarchy dump.
if grep -Fq 'text="설치 앱 목록 접근 안내"' <<<"$premium_license_xml"; then
  tap_text "취소"
  sleep 1
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  premium_license_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
fi
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(node.attrib.get("class")=="android.widget.EditText"
    and node.attrib.get("content-desc")=="PRO 라이선스 활성화 코드 입력"
    and node.attrib.get("visible-to-user","true")=="true"
    and node.attrib.get("focused")=="true"
    for node in root.iter("node")):
    raise SystemExit("Premium CTA did not focus the PRO license activation input.")' <<<"$premium_license_xml"
adb_cmd shell input keyevent KEYCODE_BACK || true

# The PRO feature cards are below the membership details on a phone-sized
# viewport. Return to Premium, scroll the actual page, and verify the feature
# card only after it enters the visible UI hierarchy.
tap_content_description "프리미엄"
sleep 1
premium_features_visible=0
for attempt in $(seq 1 8); do
  adb_cmd shell input swipe 540 1500 540 650 450
  sleep 1
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  premium_features_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
  if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
visible={node.attrib.get("text","") for node in root.iter("node")
         if node.attrib.get("visible-to-user","true")=="true"}
if "앱 코드 피싱 보호" in visible and "검사 엔진 연동 필요" in visible:
    raise SystemExit(0)
raise SystemExit(1)' <<<"$premium_features_xml"; then
    premium_features_visible=1
    break
  fi
done
if [[ "$premium_features_visible" -ne 1 ]]; then
  echo "Premium phishing-protection status card did not become visible after scrolling."
  echo "$premium_features_xml"
  exit 1
fi
echo "Premium menu, entitlement status, activation-field focus, and feature status passed."

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

echo "Verifying the app-exclusion search field is automatically revealed and filters installed apps."
# The app-exclusion picker is part of Settings. Open that tab directly rather
# than looking for a Protection-tab shortcut that is not present in this UI.
tap_content_description "설정"
sleep 2
# Settings is a long page; scroll until the app inventory search input is visible.
app_search_visible=0
for attempt in $(seq 1 16); do
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  app_inventory_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
  if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if any(node.attrib.get("class")=="android.widget.EditText"
       and node.attrib.get("content-desc")=="앱별 보호 제외 검색 입력"
       and node.attrib.get("visible-to-user","true")=="true"
       for node in root.iter("node")):
    raise SystemExit(0)
raise SystemExit(1)' <<<"$app_inventory_xml"; then
    app_search_visible=1
    break
  fi
  adb_cmd shell input swipe 540 1500 540 600 450
  sleep 1
done
if [[ "$app_search_visible" -ne 1 ]]; then
  echo "App-exclusion search field did not appear while scrolling the Settings tab."
  echo "$app_inventory_xml"
  exit 1
fi
sleep 2
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
app_inventory_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
if grep -q '동의하고 계속' <<<"$app_inventory_xml"; then
  # Resolve the AlertDialog positive button by stable Android resource ID.
  consent_bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if (node.attrib.get("resource-id")=="android:id/button1"
        and node.attrib.get("text")=="동의하고 계속"
        and node.attrib.get("class")=="android.widget.Button"
        and node.attrib.get("clickable")=="true"
        and node.attrib.get("enabled")=="true"):
        parts=node.attrib.get("bounds","").strip("[]").split("][")
        if len(parts)==2:
            x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
            print((x1+x2)//2,(y1+y2)//2)
            break' <<<"$app_inventory_xml")"
  if [[ -z "$consent_bounds" ]]; then
    echo "Installed-app inventory disclosure is visible but its positive button could not be located."
    echo "$app_inventory_xml"
    exit 1
  fi
  read -r consent_x consent_y <<<"$consent_bounds"
  adb_cmd shell input tap "$consent_x" "$consent_y"
  sleep 2
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  app_inventory_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
  if grep -q '동의하고 계속' <<<"$app_inventory_xml"; then
    echo "Installed-app inventory disclosure did not close after accepting consent."
    echo "$app_inventory_xml"
    exit 1
  fi
fi
installed_package_count="$(adb_cmd shell pm list packages --user 0 | tr -d '\r' | grep -c '^package:')"
expected_exclusion_app_count="$installed_package_count"
visible_exclusion_app_count="$(python3 -c 'import re,sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    match=re.search(r"전체 설치 앱 ([0-9]+)개", node.attrib.get("text",""))
    if match:
        print(match.group(1))
        break
else:
    raise SystemExit("Full installed-app count is missing from the exclusion UI.")' <<<"$app_inventory_xml")"
if [[ "$visible_exclusion_app_count" -ne "$expected_exclusion_app_count" ]]; then
  echo "App exclusion inventory is incomplete: UI=$visible_exclusion_app_count, PackageManager=$expected_exclusion_app_count (all installed packages minus Guardian)."
  echo "$app_inventory_xml"
  exit 1
fi
echo "Full installed-app inventory passed: $visible_exclusion_app_count apps are available for exclusion."
app_search_xml="$app_inventory_xml"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
search=any(node.attrib.get("class")=="android.widget.EditText"
    and node.attrib.get("content-desc")=="앱별 보호 제외 검색 입력"
    and node.attrib.get("visible-to-user","true")=="true"
    for node in root.iter("node"))
if not search:
    raise SystemExit("App-exclusion search input is not visible after opening its management screen.")' <<<"$app_search_xml"
tap_content_description "앱별 보호 제외 검색 입력"
adb_cmd shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
adb_cmd shell input keyevent KEYCODE_DEL
adb_cmd shell input text 'com.android.settings'
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
app_filtered_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
nodes=list(root.iter("node"))
search=any(node.attrib.get("class")=="android.widget.EditText"
    and node.attrib.get("content-desc")=="앱별 보호 제외 검색 입력"
    and node.attrib.get("visible-to-user","true")=="true"
    for node in nodes)
result=any("com.android.settings" in node.attrib.get("text","")
    and node.attrib.get("class")=="android.widget.CheckBox"
    and node.attrib.get("visible-to-user","true")=="true"
    and node.attrib.get("clickable")=="true"
    for node in nodes)
if not search:
    raise SystemExit("App-exclusion search field disappeared after entering text.")
if not result:
    raise SystemExit("Typing an installed package name did not filter the app list.")' <<<"$app_filtered_xml"
adb_cmd shell input keyevent KEYCODE_BACK || true
tap_content_description "홈"
echo "App-exclusion search visibility, focus, and filtering passed."

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
adb_cmd shell input swipe 540 1500 540 600 450
sleep 1
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

echo "Verifying live per-app VPN exclusion changes through the app UI."
tap_content_description "보호"
sleep 1
app_management_visible=0
for attempt in $(seq 1 12); do
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  app_management_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
  if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if any("앱별 보호 제외 관리" in node.attrib.get("text","")
       and node.attrib.get("class")=="android.widget.Button"
       and node.attrib.get("visible-to-user","true")=="true"
       and node.attrib.get("clickable")=="true"
       and node.attrib.get("enabled")=="true"
       for node in root.iter("node")):
    raise SystemExit(0)
raise SystemExit(1)' <<<"$app_management_xml"; then
    app_management_visible=1
    break
  fi
  adb_cmd shell input swipe 540 1500 540 600 450
  sleep 1
done
if [[ "$app_management_visible" -ne 1 ]]; then
  echo "App exclusion management button was not visible while VPN was active."
  echo "$app_management_xml"
  exit 1
fi
tap_text "앱별 보호 제외 관리"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
post_vpn_search_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(node.attrib.get("class")=="android.widget.EditText"
    and node.attrib.get("content-desc")=="앱별 보호 제외 검색 입력"
    and node.attrib.get("visible-to-user","true")=="true"
    for node in root.iter("node")):
    raise SystemExit("App-exclusion search input is not visible while VPN is active.")' <<<"$post_vpn_search_xml"
tap_content_description "앱별 보호 제외 검색 입력"
adb_cmd shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
adb_cmd shell input keyevent KEYCODE_DEL
adb_cmd shell input text 'com.android.settings'
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
exclusion_xml="$(adb_cmd exec-out cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
matches=[n for n in root.iter("node")
         if "com.android.settings" in n.attrib.get("text","")
         and n.attrib.get("class")=="android.widget.CheckBox"
         and n.attrib.get("visible-to-user","true")=="true"
         and n.attrib.get("clickable")=="true"]
if len(matches)!=1:
    raise SystemExit("Expected exactly one visible Settings app exclusion checkbox, got "+str(len(matches)))' <<<"$exclusion_xml"
# Hide the IME before tapping the filtered row. UIAutomator can report a row
# behind the keyboard as visible-to-user even though the tap lands on the IME.
adb_cmd shell input keyevent KEYCODE_BACK || true
sleep 1
tap_checkbox "com.android.settings"
excluded_applied=0
for attempt in $(seq 1 30); do
  prefs="$(read_prefs)"
  if grep -q 'name="protection_active"[^>]*value="true"' <<<"$prefs" &&
     grep -A2 -B1 'name="excluded_apps_applied"' <<<"$prefs" | grep -Fq '<string>com.android.settings</string>'; then
    excluded_applied=1
    break
  fi
  sleep 1
done
if [[ "$excluded_applied" -ne 1 ]]; then
  echo "Selecting Settings did not result in an active VPN with the package recorded as applied."
  read_prefs
  adb_cmd logcat -d -t 3000 | grep -E 'GuardianProtection|GuardianVpn|exclusion|FATAL EXCEPTION' | tail -160 || true
  exit 1
fi
echo "Live exclusion enable passed: Android accepted com.android.settings in the rebuilt VPN configuration."
tap_checkbox "com.android.settings"
restored_included=0
for attempt in $(seq 1 30); do
  prefs="$(read_prefs)"
  if grep -q 'name="protection_active"[^>]*value="true"' <<<"$prefs" &&
     ! grep -A2 -B1 'name="excluded_apps_applied"' <<<"$prefs" | grep -Fq '<string>com.android.settings</string>'; then
    restored_included=1
    break
  fi
  sleep 1
done
if [[ "$restored_included" -ne 1 ]]; then
  echo "Removing Settings from exclusions did not restore an active VPN without that package in the applied set."
  read_prefs
  adb_cmd logcat -d -t 3000 | grep -E 'GuardianProtection|GuardianVpn|exclusion|FATAL EXCEPTION' | tail -160 || true
  exit 1
fi
adb_cmd shell input keyevent KEYCODE_BACK || true
tap_content_description "홈"
echo "Live per-app exclusion enable/disable and VPN reconfiguration passed."

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

echo "Verifying automatic VPN protection recovery after an in-place app update."
before_update_queries="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_update_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_update_queries="${before_update_queries:-0}"
before_update_blocked="${before_update_blocked:-0}"
if ! grep -q 'name="protection_active"[^>]*value="true"' <<<"$prefs"; then
  echo "Refusing to test app replacement because the previous APK does not have active VPN protection."
  read_prefs
  exit 1
fi
# Simulate an installation from a pre-restore-intent build. The new app must
# migrate using the legacy active flag if the durable preference did not exist.
legacy_prefs="$(read_prefs | sed 's|<boolean name="protection_restore_requested" value="true" />||g; s|<boolean name="protection_restore_requested" value="false" />||g')"
printf '%s' "$legacy_prefs" | adb_cmd shell run-as "$PACKAGE" tee shared_prefs/guardian_settings.xml >/dev/null
if grep -q 'name="protection_restore_requested"' <<<"$(read_prefs)"; then
  echo "Legacy upgrade fixture unexpectedly retained the new restore-intent preference."
  read_prefs
  exit 1
fi
adb_cmd logcat -c
adb_cmd install -r -t android/app/build/outputs/apk/debug/app-debug.apk
adb_cmd shell pm path "$PACKAGE" | grep -F 'package:'
restore_requested=0
restore_receiver_seen=0
for attempt in $(seq 1 60); do
  prefs="$(read_prefs)"
  if grep -q 'name="protection_restore_requested"[^>]*value="true"' <<<"$prefs"; then
    restore_requested=1
  fi
  if adb_poll logcat -d -t 6000 -s GuardianProtection:I 2>/dev/null |
      grep -Fq 'Requested VPN restore after system event: android.intent.action.MY_PACKAGE_REPLACED'; then
    restore_receiver_seen=1
  fi
  if [[ "$restore_requested" -eq 1 && "$restore_receiver_seen" -eq 1 ]]; then
    break
  fi
  sleep 2
done
if [[ "$restore_requested" -ne 1 || "$restore_receiver_seen" -ne 1 ]]; then
  echo "Package replacement did not request automatic VPN restoration."
  echo "restore_requested=$restore_requested receiver_seen=$restore_receiver_seen"
  read_prefs
  adb_cmd logcat -d -t 6000 | grep -E 'GuardianProtection|GuardianVpn|MY_PACKAGE_REPLACED|FATAL EXCEPTION' | tail -250 || true
  adb_cmd shell dumpsys vpn || true
  exit 1
fi

echo "New version requested VPN restoration automatically. Verifying the restored tunnel blocks DNS."
after_update_dns=0
for attempt in $(seq 1 45); do
  prefs="$(read_prefs)"
  if grep -q 'name="protection_active"[^>]*value="true"' <<<"$prefs"; then
    adb_cmd shell ping -c 1 -W 5 "guardian-after-update-${GITHUB_RUN_ID:-test}.doubleclick.net" >/tmp/guardian-after-update-blocked-dns.txt 2>&1 || true
    sleep 2
    prefs="$(read_prefs)"
    current_queries="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
    current_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
    current_queries="${current_queries:-0}"
    current_blocked="${current_blocked:-0}"
    if (( current_queries > before_update_queries && current_blocked > before_update_blocked )); then
      after_update_dns=1
      break
    fi
  fi
  sleep 2
done
if [[ "$after_update_dns" -ne 1 ]]; then
  echo "VPN did not automatically restore DNS protection after package replacement."
  read_prefs
  cat /tmp/guardian-after-update-blocked-dns.txt 2>/dev/null || true
  adb_cmd logcat -d -t 8000 | grep -E 'GuardianProtection|GuardianVpn|MY_PACKAGE_REPLACED|FATAL EXCEPTION' | tail -300 || true
  adb_cmd shell dumpsys vpn || true
  adb_cmd shell dumpsys connectivity | tail -160 || true
  exit 1
fi
if grep -Eq 'PING guardian-after-update-[^ ]*\.doubleclick\.net \([^)]*\)' /tmp/guardian-after-update-blocked-dns.txt; then
  echo "Post-update blocked domain unexpectedly resolved to an address."
  cat /tmp/guardian-after-update-blocked-dns.txt || true
  exit 1
fi

echo "Automatic package-update recovery passed: receiver relaunched VPN, DNS queries resumed, and the test ad domain was blocked."
