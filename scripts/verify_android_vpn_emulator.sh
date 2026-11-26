#!/usr/bin/env bash

# Verify the installed-app inventory and live per-app VPN exclusion routing.
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
launch_result="$(adb_cmd shell am start -W -n "$PACKAGE/.MainActivity" 2>&1 || true)"
printf '%s\\n' "$launch_result"
if ! grep -q 'Status: ok' <<<"$launch_result"; then
  echo "am start -W did not return its normal status line; verifying the resumed Activity directly."
fi
sleep 3
foreground="$(adb_cmd shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' | tail -1 || true)"
if ! grep -q "$PACKAGE" <<<"$foreground"; then
  echo "Guardian Activity was not confirmed in the foreground; retrying the launch."
  for attempt in 1 2 3; do
    adb_cmd shell am start -n "$PACKAGE/.MainActivity" >/dev/null 2>&1 || true
    sleep 2
    foreground="$(adb_cmd shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' | tail -1 || true)"
    grep -q "$PACKAGE" <<<"$foreground" && break
  done
fi
echo "Guardian foreground activity: ${foreground:-not reported}"
grep -q "$PACKAGE" <<<"$foreground"

# UIAutomator occasionally returns an empty/non-XML dump while the emulator is
# still settling after a tab transition. Retry and validate before parsing/tapping.
dump_ui_hierarchy() {
  local remote_path="${1:-/sdcard/guardian-window.xml}"
  local dump_result xml attempt
  for attempt in $(seq 1 5); do
    adb_cmd shell rm -f "$remote_path" >/dev/null 2>&1 || true
    dump_result="$(adb_cmd shell uiautomator dump "$remote_path" 2>&1 || true)"
    if grep -Eq 'UI hier(archy|chary) dumped to:' <<<"$dump_result"; then
      xml="$(adb_cmd shell cat "$remote_path" 2>/dev/null | tr -d '\r' || true)"
      if [[ "$xml" == *'<hierarchy'* && "$xml" == *'</hierarchy>'* ]]; then
        printf '%s' "$xml"
        return 0
      fi
    fi
    echo "Retrying UIAutomator hierarchy capture ($attempt/5): ${dump_result:-no dump response}" >&2
    sleep 1
  done
  echo "UIAutomator did not return a complete hierarchy after 5 attempts: $remote_path" >&2
  return 1
}

tap_checkbox() {
  local wanted="$1"
  local xml bounds
  xml="$(dump_ui_hierarchy /sdcard/guardian-window.xml)"
  bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if (node.attrib.get("class") == "android.widget.CheckBox"
        and node.attrib.get("text","").rstrip().rsplit("\n",1)[-1].strip() == sys.argv[1]
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


tap_clickable_parent_of_text() {
  local wanted="$1" xml bounds
  xml="$(dump_ui_hierarchy /sdcard/guardian-window.xml)"
  bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
parents={child:parent for parent in root.iter() for child in parent}
for node in root.iter("node"):
    if node.attrib.get("text","").strip()==sys.argv[1] and node.attrib.get("visible-to-user","true")=="true":
        parent=parents.get(node)
        while parent is not None:
            if parent.attrib.get("clickable")=="true" and parent.attrib.get("enabled")=="true" and parent.attrib.get("visible-to-user","true")=="true":
                p=parent.attrib.get("bounds","").strip("[]").split("][")
                if len(p)==2:
                    x1,y1=map(int,p[0].split(",")); x2,y2=map(int,p[1].split(","))
                    print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
            parent=parents.get(parent)
raise SystemExit("No clickable parent found for exact visible text: "+sys.argv[1])' "$wanted" <<<"$xml")"
  local x y
  read -r x y <<<"$bounds"
  adb_cmd shell input tap "$x" "$y"
}

tap_content_description() {
  local wanted="$1"
  local xml bounds
  xml="$(dump_ui_hierarchy /sdcard/guardian-window.xml)"
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
  local xml bounds
  xml="$(dump_ui_hierarchy /sdcard/guardian-window.xml)"
  bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
parents={child: parent for parent in root.iter() for child in parent}
for node in root.iter("node"):
    if (sys.argv[1] not in node.attrib.get("text","")
        and sys.argv[1] not in node.attrib.get("content-desc","")):
        continue
    if node.attrib.get("visible-to-user","true") != "true":
        continue
    target=node
    while target is not None:
        if (target.attrib.get("visible-to-user","true")=="true"
            and target.attrib.get("clickable")=="true"
            and target.attrib.get("enabled")=="true"):
            parts=target.attrib.get("bounds","").strip("[]").split("][")
            if len(parts)==2:
                x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
                print((x1+x2)//2,(y1+y2)//2)
                raise SystemExit(0)
        target=parents.get(target)
raise SystemExit(1)' "$wanted" <<<"$xml")" || {
    echo "Could not locate visible clickable UI control or clickable text parent: $wanted"
    echo "$xml"
    return 1
  }
  read -r x y <<<"$bounds"
  adb_cmd shell input tap "$x" "$y"
}

# The current Android navigation has five primary destinations. Older
# published APKs still expose Filter and App Exclusions as direct bottom tabs.
# This script first exercises the previously published APK during upgrade E2E,
# so select the matching navigation model from the live accessibility tree.
# Resolve the Premium entry from the actual navigation tree. The published
# predecessor exposes a direct Premium tab, while current builds place it
# under Settings. Do not assume the Korean label or the legacy tab exists.
open_license_premium_menu() {
  local xml entry category
  xml="$(dump_ui_hierarchy /sdcard/guardian-license-navigation.xml)"
  entry="$(python3 scripts/android_navigation_compat.py entry <<<"$xml")" || {
    echo "Could not resolve the Guardian Premium navigation entry."
    echo "$xml"
    return 1
  }
  case "$entry" in
    direct:Premium) tap_content_description "Premium" ;;
    direct:프리미엄) tap_content_description "프리미엄" ;;
    settings) tap_content_description "설정" ;;
    all-menu) tap_content_description "전체 메뉴" ;;
    *)
      echo "Unsupported Guardian Premium navigation route: $entry"
      return 1
      ;;
  esac
  if [[ "$entry" == "settings" || "$entry" == "all-menu" ]]; then
    sleep 1
    xml="$(dump_ui_hierarchy /sdcard/guardian-settings-menu.xml)"
    category="$(python3 scripts/android_navigation_compat.py category <<<"$xml")" || {
      echo "Settings does not expose a supported License/Premium category."
      echo "$xml"
      return 1
    }
    scroll_to_text "$category"
  fi
  sleep 1
}

open_settings_menu_item() {
  local title="$1"
  local xml target
  xml="$(dump_ui_hierarchy /sdcard/guardian-settings-menu.xml)"
  # The predecessor APK used a dedicated "전체 메뉴" destination alongside
  # a separate Premium tab. Prefer that exact legacy route when it is present;
  # the detail page can also contain a nested control named "설정", which must
  # not be mistaken for the bottom-navigation destination.
  if grep -Fq 'content-desc="전체 메뉴"' <<<"$xml"; then
    # The published 0.7.45 predecessor can retain a nested Settings detail
    # page even when its generic All Menu tab is already selected. Re-select
    # the route from Home so the legacy category index is actually restored.
    if grep -Fq 'content-desc="홈"' <<<"$xml"; then
      tap_content_description "홈"
      sleep 0.5
    fi
    xml="$(dump_ui_hierarchy /sdcard/guardian-settings-menu.xml)"
    if grep -Fq 'content-desc="전체 메뉴"' <<<"$xml"; then
      tap_content_description "전체 메뉴"
      sleep 1
    fi
  elif grep -Fq 'content-desc="설정"' <<<"$xml"; then
    tap_content_description "설정"
    # On current phones, tapping Settings while a detail page is open must
    # return to the six-category index. Wait for that semantic state, not an
    # arbitrary sleep; otherwise a stale detail hierarchy can be mistaken for
    # the menu.
    local index_ready=0
    for attempt in $(seq 1 8); do
      sleep 0.4
      xml="$(dump_ui_hierarchy /sdcard/guardian-settings-menu.xml)"
      if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
parents={child: parent for parent in root.iter() for child in parent}
expected={"일반","필터링","라이선스 및 프리미엄","고객 지원","정보"}
found=set()
for node in root.iter("node"):
    if node.attrib.get("visible-to-user","true")!="true":
        continue
    if node.attrib.get("text","").strip() not in expected:
        continue
    parent=parents.get(node)
    while parent is not None:
        if (parent.attrib.get("visible-to-user","true")=="true"
            and parent.attrib.get("clickable")=="true"
            and parent.attrib.get("enabled")=="true"):
            found.add(node.attrib.get("text","").strip())
            break
        parent=parents.get(parent)
raise SystemExit(0 if found else 1)' <<<"$xml"; then
        index_ready=1
        break
      fi
    done
    if [[ "$index_ready" -ne 1 ]]; then
      echo "Settings navigation did not return to the phone category index."
      echo "Expected the Settings category index; current hierarchy follows:"
      echo "$xml"
      return 1
    fi
  else
    case "$title" in
      "앱 제외 관리") tap_content_description "앱 제외"; sleep 1 ;;
      "필터 관리") tap_content_description "필터 규칙"; sleep 1 ;;
      *)
        echo "Legacy navigation fallback is not defined for menu item: $title"
        echo "$xml"
        return 1
        ;;
    esac
  fi

  # The previous published APK uses the shorter "앱 제외" label, while the
  # redesigned Settings screen uses "앱 제외 관리". Resolve from the live
  # accessibility hierarchy instead of assuming the newest label exists.
  # The Settings workspace is one vertically scrollable surface. After an
  # About/Support detail check, the left category rail can be scrolled so its
  # first entries (General and Filtering) are above the viewport. Reset the
  # shared scroll surface to its top before resolving a category; otherwise
  # UIAutomator correctly reports those rail entries as not visible.
  for reset_attempt in $(seq 1 5); do
    adb_cmd shell input swipe 540 420 540 1510 350
    sleep 0.2
  done
  xml="$(dump_ui_hierarchy /sdcard/guardian-settings-menu.xml)"
  target="$(python3 scripts/android_navigation_compat.py settings-item "$title" <<<"$xml")" || {
    echo "Could not resolve Settings menu item '$title' from the live UI."
    echo "$xml"
    return 1
  }
  echo "Resolved Settings item: $title -> $target"
  scroll_to_text "$target"
  sleep 1
}

# Scroll the currently visible Android ScrollView until a named control is
# actually on-screen. Use the emulator's live dimensions so this also works
# after landscape/portrait Activity recreation.
scroll_to_text() {
  local wanted="$1" attempt size width height center_x start_y end_y xml
  size="$(adb_cmd shell wm size | awk -F': ' '/Physical size/ {print $2; exit}')"
  width="${size%x*}"
  height="${size#*x}"
  if [[ -z "$width" || -z "$height" || "$width" == "$height" ]]; then
    echo "Could not determine a usable emulator display size: $size"
    return 1
  fi
  center_x=$((width / 2))
  start_y=$((height * 4 / 5))
  end_y=$((height / 5))
  for attempt in $(seq 1 10); do
    xml="$(dump_ui_hierarchy /sdcard/guardian-scroll-target.xml)"
    if grep -Fq "text=\"$wanted\"" <<<"$xml"; then
      if tap_text "$wanted"; then return 0; fi
    elif grep -Fq "content-desc=\"$wanted\"" <<<"$xml"; then
      # Collapsed Android settings rails expose category labels as accessibility
      # descriptions instead of visible text nodes. Resolve the semantic target
      # directly; swiping the content pane cannot scroll the sidebar item.
      if tap_content_description "$wanted"; then return 0; fi
    fi
    echo "Scrolling to visible control '$wanted' (attempt $attempt/10; display ${width}x${height})."
    adb_cmd shell input swipe "$center_x" "$start_y" "$center_x" "$end_y" 450
    sleep 0.4
  done
  echo "Could not bring control into the visible viewport: $wanted"
  dump_ui_hierarchy /sdcard/guardian-scroll-target.xml
  return 1
}

# Android AlertDialog ListView row TextViews are intentionally non-clickable
# in UIAutomator's accessibility tree; ListView itself dispatches row taps.
# Resolve only an exact child of android:id/select_dialog_listview, leaving
# the generic clickable-control helper strict for buttons and navigation.
tap_text_row() {
  local wanted="$1"
  local xml bounds
  xml="$(dump_ui_hierarchy /sdcard/guardian-window.xml)"
  if ! bounds="$(python3 scripts/resolve_android_dialog_row.py "$wanted" <<<"$xml")"; then
    echo "Could not resolve one exact visible AlertDialog ListView row: $wanted"
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
    xml="$(adb_cmd shell cat /sdcard/guardian-window.xml 2>/dev/null | tr -d '\r' || true)"
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
open_license_premium_menu
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
premium_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
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
premium_license_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
# A prior UI flow can leave the first-run installed-app privacy disclosure on
# top of the settings screen. Do not accept it implicitly during the Premium
# focus check; dismiss it with the explicit cancel action, then inspect the
# underlying license field in a fresh hierarchy dump.
if grep -Fq 'text="설치 앱 목록 접근 안내"' <<<"$premium_license_xml"; then
  tap_text "취소"
  sleep 1
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  premium_license_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
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
open_license_premium_menu
sleep 1
premium_features_visible=0
for attempt in $(seq 1 8); do
  adb_cmd shell input swipe 540 1500 540 650 450
  sleep 1
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  premium_features_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
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
    xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
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
# The upgrade scenario starts on the previously published APK, whose
# seven-item navigation still labels this destination "보호 설정". The
# candidate APK uses the redesigned five-item navigation label "보호".
# Detect the installed version instead of applying the candidate label to the
# legacy APK before the in-place upgrade step.
adb_cmd shell uiautomator dump /sdcard/guardian-navigation.xml >/dev/null 2>&1
navigation_xml="$(adb_cmd shell cat /sdcard/guardian-navigation.xml | tr -d '\r')"
if grep -Fq 'content-desc="보호 설정"' <<<"$navigation_xml"; then
  tap_content_description "보호 설정"
elif grep -Fq 'content-desc="보호"' <<<"$navigation_xml"; then
  tap_content_description "보호"
else
  echo "Neither legacy nor redesigned Guardian protection navigation item is visible."
  echo "$navigation_xml"
  exit 1
fi
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-protection-controls.xml >/dev/null 2>&1
protection_controls_xml="$(adb_cmd shell cat /sdcard/guardian-protection-controls.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
duplicate=[node.attrib.get("text","") for node in root.iter("node")
    if node.attrib.get("class")=="android.widget.Button"
    and node.attrib.get("text","").strip() in {"보호 시작","보호 중지","보호 완전 중지","필터 및 사용자 규칙 관리","앱별 보호 제외 관리"}
    and node.attrib.get("visible-to-user","true")=="true"
    and node.attrib.get("clickable")=="true"]
if duplicate:
    raise SystemExit("Protection screen still exposes duplicate navigation or start/stop controls: "+repr(duplicate))' <<<"$protection_controls_xml"
open_settings_menu_item "앱 제외 관리"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
app_inventory_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
if grep -q '동의하고 계속' <<<"$app_inventory_xml"; then
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
  echo "Accepting the installed-app inventory disclosure at ($consent_x,$consent_y)."
  adb_cmd shell input tap "$consent_x" "$consent_y"
  # The installed-app inventory is loaded asynchronously; wait for the search
  # control instead of assuming the entire transition completes in two seconds.
  consent_transitioned=0
  for attempt in $(seq 1 20); do
    adb_cmd shell rm -f /sdcard/guardian-window.xml
    dump_result="$(adb_cmd shell uiautomator dump /sdcard/guardian-window.xml 2>&1 || true)"
    app_inventory_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml 2>/dev/null | tr -d '\r' || true)"
    if grep -q 'content-desc="앱별 보호 제외 검색 입력"' <<<"$app_inventory_xml"; then
      consent_transitioned=1
      break
    fi
    if (( attempt % 5 == 0 )); then
      echo "Waiting for installed-app inventory after consent ($attempt/20)."
    fi
    sleep 1
  done
  if [[ "$consent_transitioned" -ne 1 ]]; then
    echo "Installed-app inventory did not render after accepting consent."
    echo "Latest UIAutomator result: $dump_result"
    echo "$app_inventory_xml"
    exit 1
  fi
fi

# App exclusions now live on their own tab. Keep the test on that screen;
# do not navigate to Settings or search for the moved control there.
app_search_visible=0
for attempt in $(seq 1 20); do
  adb_cmd shell rm -f /sdcard/guardian-window.xml
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  app_inventory_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
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
  if grep -q '동의하고 계속' <<<"$app_inventory_xml"; then
    echo "Inventory disclosure reappeared after acceptance; refusing to continue behind the modal."
    echo "$app_inventory_xml"
    exit 1
  fi
  sleep 1
done
if [[ "$app_search_visible" -ne 1 ]]; then
  echo "App-exclusion search field did not appear on the dedicated app-exclusions screen."
  echo "$app_inventory_xml"
  exit 1
fi

inventory_ready=0
for attempt in $(seq 1 30); do
  adb_cmd shell rm -f /sdcard/guardian-window.xml
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  app_inventory_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
  if python3 -c 'import re,sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if node.attrib.get("class") != "android.widget.TextView" or node.attrib.get("visible-to-user","true") != "true":
        continue
    match=re.search(r"(?:전체 )?설치 앱 ([0-9]+)개(?: 조회 완료)?", node.attrib.get("text",""))
    if match and int(match.group(1)) > 0:
        raise SystemExit(0)
raise SystemExit(1)' <<<"$app_inventory_xml"; then
    inventory_ready=1
    break
  fi
  sleep 1
done
if [[ "$inventory_ready" -ne 1 ]]; then
  echo "App-exclusion inventory did not finish loading within 30 seconds."
  echo "$app_inventory_xml"
  exit 1
fi
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
app_inventory_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
package_listing="$(adb_cmd shell pm list packages --user 0 | tr -d '\r')"
installed_package_count="$(grep -c '^package:' <<<"$package_listing")"
guardian_package_count="$(grep -c '^package:com.guardian.adblock$' <<<"$package_listing" || true)"
if [[ "$guardian_package_count" -ne 1 ]]; then
  echo "PackageManager inventory did not contain exactly one Guardian package: $guardian_package_count"
  echo "$package_listing"
  exit 1
fi
# The app intentionally hides itself from its own VPN-exclusion inventory.
# Compare like-for-like: all user-0 packages except Guardian itself.
expected_exclusion_app_count="$((installed_package_count - guardian_package_count))"
echo "PackageManager installed apps: $installed_package_count; expected exclusion entries (excluding Guardian): $expected_exclusion_app_count"
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
# Wait for the IME and focused EditText to settle before typing; input events
# sent on the same frame as focus can drop the first characters on slow emulators.
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-keyboard.xml >/dev/null 2>&1
keyboard_search_xml="$(adb_cmd shell cat /sdcard/guardian-keyboard.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(node.attrib.get("class")=="android.widget.EditText"
       and node.attrib.get("content-desc")=="앱별 보호 제외 검색 입력"
       and node.attrib.get("visible-to-user","true")=="true"
       and node.attrib.get("enabled")=="true"
       for node in root.iter("node")):
    raise SystemExit("App-exclusion search field became hidden when the keyboard opened.")' <<<"$keyboard_search_xml"
search_query_ready=0
for attempt in $(seq 1 3); do
  adb_cmd shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
  adb_cmd shell input keyevent KEYCODE_DEL
  sleep 0.3
  adb_cmd shell input text 'com.android.settings'
  sleep 1
  adb_cmd shell uiautomator dump /sdcard/guardian-keyboard.xml >/dev/null 2>&1
  keyboard_search_xml="$(adb_cmd shell cat /sdcard/guardian-keyboard.xml | tr -d '\r')"
  if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if any(node.attrib.get("class")=="android.widget.EditText"
       and node.attrib.get("content-desc")=="앱별 보호 제외 검색 입력"
       and node.attrib.get("text","")=="com.android.settings"
       for node in root.iter("node")):
    raise SystemExit(0)
raise SystemExit(1)' <<<"$keyboard_search_xml"; then
    search_query_ready=1
    break
  fi
  echo "Retrying app-exclusion search text entry ($attempt/3); emulator input may have dropped key events."
done
if [[ "$search_query_ready" -ne 1 ]]; then
  echo "The emulator did not enter the complete package query into the app-exclusion search field."
  echo "$keyboard_search_xml"
  exit 1
fi
# Hide the IME only after the complete query is confirmed in the EditText.
adb_cmd shell input keyevent KEYCODE_BACK || true
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
app_filtered_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
nodes=list(root.iter("node"))
search=any(node.attrib.get("class")=="android.widget.EditText"
    and node.attrib.get("content-desc")=="앱별 보호 제외 검색 입력"
    and node.attrib.get("visible-to-user","true")=="true"
    for node in nodes)
result=any(node.attrib.get("text","").rstrip().rsplit("\n",1)[-1].strip()=="com.android.settings"
    and node.attrib.get("class")=="android.widget.CheckBox"
    and node.attrib.get("visible-to-user","true")=="true"
    and node.attrib.get("clickable")=="true"
    for node in nodes)
if not search:
    raise SystemExit("App-exclusion search field disappeared after entering text.")
if not result:
    raise SystemExit("Typing an installed package name did not filter the app list.")' <<<"$app_filtered_xml"
visible_exclusion_app_count="$(python3 -c 'import re,sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    match=re.search(r"전체 ([0-9]+)개", node.attrib.get("text",""))
    if match:
        print(match.group(1))
        break
else:
    raise SystemExit("Inventory count feedback is missing from the filtered exclusion UI.")' <<<"$app_filtered_xml")"
if [[ "$visible_exclusion_app_count" -ne "$expected_exclusion_app_count" ]]; then
  echo "App exclusion inventory is incomplete: UI=$visible_exclusion_app_count, PackageManager=$expected_exclusion_app_count (all installed packages minus Guardian)."
  echo "$app_filtered_xml"
  exit 1
fi
echo "Full installed-app inventory passed: $visible_exclusion_app_count apps are available for exclusion."
ANDROID_AUTO_PACKAGE="com.google.android.projection.gearhead"
android_auto_path="$(adb_cmd shell pm path "$ANDROID_AUTO_PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [[ "$android_auto_path" == package:* ]]; then
  echo "Verifying Android Auto is searchable by its installed package ID."
  tap_content_description "앱별 보호 제외 검색 입력"
  adb_cmd shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
  adb_cmd shell input keyevent KEYCODE_DEL
  adb_cmd shell input text "$ANDROID_AUTO_PACKAGE"
  sleep 1
  adb_cmd shell input keyevent KEYCODE_BACK || true
  sleep 1
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  android_auto_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
  python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(node.attrib.get("text","").rstrip().rsplit("\n",1)[-1].strip()=="com.google.android.projection.gearhead"
    and node.attrib.get("class")=="android.widget.CheckBox"
    and node.attrib.get("visible-to-user","true")=="true"
    and node.attrib.get("clickable")=="true"
    for node in root.iter("node")):
    raise SystemExit("Android Auto package was not searchable in the installed-app exclusion list.")' <<<"$android_auto_xml"
  echo "Verifying Android Auto is searchable by its displayed app name."
  tap_content_description "앱별 보호 제외 검색 입력"
  adb_cmd shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
  adb_cmd shell input keyevent KEYCODE_DEL
  adb_cmd shell input text 'Android Auto'
  sleep 1
  adb_cmd shell input keyevent KEYCODE_BACK || true
  sleep 1
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  android_auto_name_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
  python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(node.attrib.get("text","").split("\n",1)[0].strip()=="Android Auto"
    and node.attrib.get("text","").rstrip().rsplit("\n",1)[-1].strip()=="com.google.android.projection.gearhead"
    and node.attrib.get("class")=="android.widget.CheckBox"
    and node.attrib.get("visible-to-user","true")=="true"
    for node in root.iter("node")):
    raise SystemExit("Android Auto app-name search did not return the expected installed package.")' <<<"$android_auto_name_xml"
else
  echo "Android Auto is not installed in this emulator image; exact Android Auto lookup is skipped. Full inventory count and installed-package search were still verified."
fi
tap_content_description "홈"
home_controls_xml="$(dump_ui_hierarchy /sdcard/guardian-home-controls.xml)"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
controls=[node.attrib.get("text","").strip() for node in root.iter("node")
    if node.attrib.get("class")=="android.widget.Button"
    and node.attrib.get("text","").strip() in {"보호 시작","보호 중지","보호 완전 중지","다시 연결"}
    and node.attrib.get("visible-to-user","true")=="true"
    and node.attrib.get("clickable")=="true"]
if len(controls)!=1:
    raise SystemExit("Home must expose exactly one protection control; found "+repr(controls))' <<<"$home_controls_xml"
echo "App-exclusion search, package filtering, Android Auto lookup, and single Home protection control passed."

# Verify the simplified dashboard and all five persistent bottom-navigation destinations.
echo "Verifying the simplified home dashboard and all five bottom-navigation destinations."
home_navigation_xml="$(dump_ui_hierarchy /sdcard/guardian-home-navigation.xml)"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
nodes=list(root.iter("node"))
if any("화면 열기" in node.attrib.get("content-desc","") for node in nodes):
    raise SystemExit("Redundant home shortcut tile remains beside bottom navigation.")
controls=[node.attrib.get("text","").strip() for node in nodes
    if node.attrib.get("class")=="android.widget.Button"
    and node.attrib.get("text","").strip() in {"보호 시작","보호 중지","보호 완전 중지","다시 연결"}
    and node.attrib.get("visible-to-user","true")=="true"
    and node.attrib.get("clickable")=="true"]
if len(controls)!=1:
    raise SystemExit("Home must expose exactly one protection control; found "+repr(controls))
# This phase runs against the published predecessor APK 0.7.45-dev.
# That build already contains the five-destination navigation redesign.
expected={"홈","보호","활동","Premium","전체 메뉴"}
actual={node.attrib.get("content-desc") for node in nodes
        if node.attrib.get("class")=="android.widget.LinearLayout"
        and node.attrib.get("clickable")=="true"}
if not expected.issubset(actual):
    raise SystemExit("Previous-version bottom navigation is missing destinations: "+repr(sorted(expected-actual)))
if not any(node.attrib.get("content-desc")=="홈" and node.attrib.get("selected")=="true"
           for node in nodes):
    raise SystemExit("Home is not the selected bottom-navigation destination.")' <<<"$home_navigation_xml"
for navigation_label in "보호" "활동" "Premium" "전체 메뉴"; do
  tap_content_description "$navigation_label"
  sleep 1
  navigation_xml="$(dump_ui_hierarchy /sdcard/guardian-navigation.xml)"
  if ! python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(node.attrib.get("content-desc")==sys.argv[1]
       and node.attrib.get("class")=="android.widget.LinearLayout"
       and node.attrib.get("selected")=="true"
       for node in root.iter("node")):
    raise SystemExit("Bottom navigation did not select destination: "+sys.argv[1])' "$navigation_label" <<<"$navigation_xml"; then
    echo "Bottom-navigation route failed: $navigation_label"
    echo "$navigation_xml"
    exit 1
  fi
done
tap_content_description "홈"
echo "Single home protection control and all five bottom-navigation destinations passed."



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
  xml="$(adb_poll shell cat /sdcard/guardian-window.xml 2>/dev/null | tr -d '\r' || true)"
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

echo "Verifying live per-app VPN exclusion changes through the dedicated app-exclusions tab."
open_settings_menu_item "앱 제외 관리"
sleep 1
tap_content_description "앱별 보호 제외 검색 입력"
sleep 1
adb_cmd shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_A
adb_cmd shell input keyevent KEYCODE_DEL
adb_cmd shell input text 'com.android.settings'
sleep 1
# Assert the EditText actually received the query before hiding the IME;
# a successful shell input command alone does not prove the UI accepted it.
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
app_inventory_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(node.attrib.get("class")=="android.widget.EditText"
       and node.attrib.get("text","").strip()=="com.android.settings"
       for node in root.iter("node")):
    raise SystemExit("App-exclusion search field did not retain the entered package query.")' <<<"$app_inventory_xml"
# The filtered app rows are rendered in batches; allow the requested package to
# appear instead of relying on a fixed one-second render delay.
settings_visible=0
for attempt in $(seq 1 10); do
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  app_inventory_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
  if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
raise SystemExit(0 if any(node.attrib.get("class")=="android.widget.CheckBox"
       and node.attrib.get("text","").rstrip().rsplit("\n",1)[-1].strip()=="com.android.settings"
       and node.attrib.get("clickable")=="true"
       and node.attrib.get("enabled")=="true"
       for node in root.iter("node")) else 1)' <<<"$app_inventory_xml"; then
    settings_visible=1
    break
  fi
  sleep 1
done
if [[ "$settings_visible" -ne 1 ]]; then
  echo "Settings package was not visible after filtering the dedicated app-exclusions list."
  echo "$app_inventory_xml"
  exit 1
fi
adb_cmd shell input keyevent KEYCODE_BACK || true
sleep 1
tap_checkbox "com.android.settings"
# A checkbox edits only the draft. The persisted VPN exclusion must not change
# until the user explicitly presses the save action.
prefs="$(read_prefs)"
if grep -A2 -B1 'name="excluded_apps"' <<<"$prefs" | grep -Fq '<string>com.android.settings</string>'; then
  echo "App exclusion draft was persisted before the explicit Save button was pressed."
  read_prefs
  exit 1
fi
tap_content_description "앱별 보호 제외 변경사항 저장"
# ADB input tap returns before Android has necessarily dispatched the click
# listener and committed SharedPreferences. Poll the durable state rather than
# racing the UI thread with an immediate run-as read.
excluded_saved=0
for attempt in $(seq 1 20); do
  prefs="$(read_prefs)"
  if grep -A2 -B1 'name="excluded_apps"' <<<"$prefs" | grep -Fq '<string>com.android.settings</string>'; then
    excluded_saved=1
    break
  fi
  sleep 0.5
done
if [[ "$excluded_saved" -ne 1 ]]; then
  echo "Explicit Save control was tapped but the selected package was not persisted after 10 seconds."
  read_prefs
  exit 1
fi
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
echo "Live exclusion enable passed: explicit Save persisted the draft and Android accepted the VPN exclusion."
tap_checkbox "com.android.settings"
prefs="$(read_prefs)"
if ! grep -A2 -B1 'name="excluded_apps"' <<<"$prefs" | grep -Fq '<string>com.android.settings</string>'; then
  echo "Removing an app changed persisted exclusions before the explicit Save button was pressed."
  read_prefs
  exit 1
fi
tap_content_description "앱별 보호 제외 변경사항 저장"
excluded_removed=0
for attempt in $(seq 1 20); do
  prefs="$(read_prefs)"
  if ! grep -A2 -B1 'name="excluded_apps"' <<<"$prefs" | grep -Fq '<string>com.android.settings</string>'; then
    excluded_removed=1
    break
  fi
  sleep 0.5
done
if [[ "$excluded_removed" -ne 1 ]]; then
  echo "Explicit Save control was tapped but the removed package remained persisted after 10 seconds."
  read_prefs
  exit 1
fi
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
legacy_prefs="$(read_prefs | sed 's|<boolean name="protection_restore_requested" value="true" />||g; s|<boolean name="protection_restore_requested" value="false" />||g; s|<boolean name="filter_auto_update" value="true" />|<boolean name="filter_auto_update" value="false" />|g')"
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

prefs="$(read_prefs)"
if ! grep -q 'name="filter_auto_update"[^>]*value="false"' <<<"$prefs"; then
  echo "The upgrade changed the user's explicit automatic filter-update OFF preference."
  read_prefs
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

# The pre-update fixture is intentionally 0.7.25-dev and cannot contain this feature.
# Run the compatibility UI/behavior check only after installing and verifying
# the current 0.7.26-dev APK above.
echo "Reopening the upgraded Guardian app for the foreground compatibility test."
adb_cmd shell monkey -p "$PACKAGE" 1 >/dev/null
sleep 2
current_version="$(adb_cmd shell dumpsys package "$PACKAGE" | sed -n 's/.*versionName=//p' | head -1 | tr -d '\r')"
expected_version="${GUARDIAN_CURRENT_VERSION_NAME:-0.7.26-dev}"
if [[ "$current_version" != "$expected_version" ]]; then
  echo "Foreground-app compatibility test requires $expected_version but installed Guardian is $current_version."
  adb_cmd shell dumpsys package "$PACKAGE" | grep -E 'versionCode|versionName' || true
  exit 1
fi

# The predecessor APK cannot exercise candidate-only Settings and rule-editor UI.
# Run Activity recreation and unsaved custom-rule persistence only after the
# in-place upgrade has confirmed the candidate version is installed.
# Exercise a real Activity recreation in landscape while unsaved user-rule text
# is present. The tab and draft must survive rotation without saving to prefs.
echo "Verifying filter-screen and unsaved rule state survive device rotation."
open_settings_menu_item "필터링"
tap_text "전체 필터 규칙 편집"
sleep 1
tap_first_edit_text
adb_cmd shell input text 'rotation-preserved.example'
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
before_rotation_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any("rotation-preserved.example" in node.attrib.get("text","") for node in root.iter("node")):
    raise SystemExit("Synthetic rule text was not entered before rotation.")' <<<"$before_rotation_xml"
adb_cmd shell settings put system accelerometer_rotation 0
adb_cmd shell settings put system user_rotation 1
sleep 3
adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
rotation_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
nodes=list(root.iter("node"))
selected=any(node.attrib.get("content-desc")=="설정"
             and node.attrib.get("selected")=="true" for node in nodes)
filter_editor=any(node.attrib.get("text","")=="허용 도메인" for node in nodes)
draft=any("rotation-preserved.example" in node.attrib.get("text","") for node in nodes)
if not (selected and filter_editor and draft):
    raise SystemExit("Activity rotation lost the selected filter destination, filter editor, or unsaved rule draft.")' <<<"$rotation_xml"
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
scroll_to_text "규칙 저장 및 적용"
sleep 1
tap_text "규칙 저장 및 적용"
sleep 2
adb_cmd shell input keyevent KEYCODE_BACK || true
tap_content_description "홈"
echo "Activity rotation state restoration and custom-rule save passed."

# Check a user-defined domain only after the candidate editor has actually
# saved and applied the rule; the predecessor APK cannot satisfy this test.
prefs="$(read_prefs)"
before_custom_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_custom_blocked="${before_custom_blocked:-0}"
echo "Triggering DNS lookup for the saved custom block rule."
adb_cmd shell ping -c 1 -W 5 "rotation-preserved.example" >/tmp/guardian-custom-blocked-dns.txt 2>&1 || true
sleep 2
prefs="$(read_prefs)"
after_custom_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_custom_blocked="${after_custom_blocked:-0}"
if (( after_custom_blocked <= before_custom_blocked )); then
  echo "Saved custom block rule did not increment Guardian's blocked DNS counter after save."
  cat /tmp/guardian-custom-blocked-dns.txt || true
  read_prefs
  exit 1
fi
if grep -Eq 'PING rotation-preserved\\.example \\([^)]*\\)' /tmp/guardian-custom-blocked-dns.txt; then
  echo "Custom-blocked domain unexpectedly resolved to an address."
  cat /tmp/guardian-custom-blocked-dns.txt || true
  read_prefs
  exit 1
fi


# The installed APK may use either the legacy or redesigned five-destination navigation.
echo "Verifying the current APK's supported five-destination bottom navigation."
adb_cmd shell monkey -p "$PACKAGE" 1 >/dev/null
sleep 2
tap_content_description "홈"
current_home_xml="$(dump_ui_hierarchy /sdcard/guardian-current-home-navigation.xml)"
navigation_layout="$(python3 scripts/android_navigation_compat.py navigation <<<"$current_home_xml")"
case "$navigation_layout" in
  redesigned) navigation_labels=("보호" "앱 제외" "활동" "설정") ;;
  legacy:Premium) navigation_labels=("보호" "활동" "Premium" "전체 메뉴") ;;
  legacy:프리미엄) navigation_labels=("보호" "활동" "프리미엄" "전체 메뉴") ;;
  *)
    echo "Unsupported current APK navigation layout: $navigation_layout"
    exit 1
    ;;
esac
navigation_labels_csv="$(IFS=,; echo "${navigation_labels[*]}")"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
expected=set(sys.argv[1].split(","))
nodes=list(root.iter("node"))
actual={n.attrib.get("content-desc") for n in nodes
        if n.attrib.get("class")=="android.widget.LinearLayout"
        and n.attrib.get("clickable")=="true"}
if not expected.issubset(actual):
    raise SystemExit("Current APK navigation is missing destinations: "+repr(sorted(expected-actual)))
if not any(n.attrib.get("content-desc")=="홈" and n.attrib.get("selected")=="true" for n in nodes):
    raise SystemExit("Current APK Home destination is not selected.")' "$navigation_labels_csv" <<<"$current_home_xml"
for navigation_label in "${navigation_labels[@]}"; do
  tap_content_description "$navigation_label"
  sleep 1
  current_navigation_xml="$(dump_ui_hierarchy /sdcard/guardian-current-navigation.xml)"
  python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(n.attrib.get("content-desc")==sys.argv[1]
       and n.attrib.get("class")=="android.widget.LinearLayout"
       and n.attrib.get("selected")=="true" for n in root.iter("node")):
    raise SystemExit("Current APK did not select navigation destination: "+sys.argv[1])' "$navigation_label" <<<"$current_navigation_xml"
done
tap_content_description "홈"
echo "Current APK compatible five-destination navigation passed ($navigation_layout)."
echo "Verifying opt-in foreground-app VPN auto-pause and automatic resume."
# Grant the special app-op as the emulator equivalent of the user's Usage Access
# approval. The UI flow still selects the target app and explicitly enables the feature.
adb_cmd shell appops set "$PACKAGE" GET_USAGE_STATS allow
adb_cmd shell am start -a android.settings.SETTINGS >/dev/null
sleep 2
adb_cmd shell monkey -p "$PACKAGE" 1 >/dev/null
sleep 2
# The bottom-navigation label is a non-clickable child of the tab item.
# Target the clickable parent via its accessibility content description, then
# assert that the Settings page actually opened before scrolling its content.
tap_content_description "설정"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-settings-tab.xml >/dev/null 2>&1
settings_tab_xml="$(adb_cmd shell cat /sdcard/guardian-settings-tab.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
nodes=list(root.iter("node"))
texts={node.attrib.get("text","") for node in nodes}
selected_settings=any(node.attrib.get("content-desc")=="설정"
                      and node.attrib.get("selected")=="true"
                      for node in nodes)
required_settings = {"설정", "일반", "필터링", "PRO", "지원", "정보"}
missing_settings = required_settings - texts
if missing_settings or not selected_settings:
    raise SystemExit("Guardian Settings landing page is missing agreed categories: " + repr(sorted(missing_settings)))' <<<"$settings_tab_xml"
# Settings is now a persistent compact left rail with a right-side content
# workspace. All five categories remain visible without scrolling.
ensure_text_visible() {
  local wanted="$1" attempt size width height center_x start_y end_y xml
  size="$(adb_cmd shell wm size | awk -F': ' '/Physical size/ {print $2; exit}')"
  width="${size%x*}"
  height="${size#*x}"
  if [[ -z "$width" || -z "$height" || "$width" == "$height" ]]; then
    echo "Could not determine a usable emulator display size: $size"
    return 1
  fi
  center_x=$((width / 2))
  start_y=$((height * 4 / 5))
  end_y=$((height / 5))
  for attempt in $(seq 1 10); do
    xml="$(dump_ui_hierarchy /sdcard/guardian-scroll-target.xml)"
    if grep -Fq "text=\"$wanted\"" <<<"$xml"; then
      echo "Visible without activation: $wanted"
      return 0
    fi
    echo "Scrolling to reveal '$wanted' without opening it (attempt $attempt/10)."
    adb_cmd shell input swipe "$center_x" "$start_y" "$center_x" "$end_y" 450
    sleep 0.4
  done
  echo "Could not reveal the category without activating it: $wanted"
  dump_ui_hierarchy /sdcard/guardian-scroll-target.xml
  return 1
}
ensure_text_visible "PRO"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-license-menu.xml >/dev/null 2>&1
license_menu_xml="$(adb_cmd exec-out cat /sdcard/guardian-license-menu.xml | tr -d '\r')"
if ! grep -Fq 'PRO' <<<"$license_menu_xml"; then
  echo "The compact PRO Settings rail entry is not visible."
  echo "$license_menu_xml"
  exit 1
fi

# Verify the About submenu's version row, then navigate back through the
# actual Settings category index before opening General. A detail-page swipe
# cannot select a sibling category and previously left the test on About.
scroll_to_text "정보"
sleep 1
# The About page is taller than a phone viewport. Verify the version row
# and company disclosure in separate snapshots: scrolling to the company block
# necessarily moves the version row above the visible viewport. UIAutomator
# reports visible nodes only, so requiring both in one dump is a false failure.
expected_current_version="${GUARDIAN_CURRENT_VERSION_NAME:-}"
if [[ -z "$expected_current_version" ]]; then
  echo "The current APK version name was not passed to the About verification."
  exit 1
fi
ensure_text_visible "버전 코드"
adb_cmd shell uiautomator dump /sdcard/guardian-about-version.xml >/dev/null 2>&1
about_version_xml="$(adb_cmd exec-out cat /sdcard/guardian-about-version.xml | tr -d '\r')"
if ! grep -Fq '버전 코드' <<<"$about_version_xml" ||
   ! grep -Fq "$expected_current_version" <<<"$about_version_xml"; then
  echo "The About screen did not expose the expected installed app version code: $expected_current_version"
  echo "$about_version_xml"
  exit 1
fi
ensure_text_visible "회사 및 개발자 정보"
adb_cmd shell uiautomator dump /sdcard/guardian-about-company.xml >/dev/null 2>&1
about_company_xml="$(adb_cmd exec-out cat /sdcard/guardian-about-company.xml | tr -d '\r')"
if ! grep -Fq '회사 및 개발자 정보' <<<"$about_company_xml" ||
   ! grep -Fq '(주)유진컨설팅' <<<"$about_company_xml"; then
  echo "The About screen did not expose the expected company information."
  echo "$about_company_xml"
  exit 1
fi
open_settings_menu_item "필터링"
sleep 1
filtering_settings_xml="$(dump_ui_hierarchy /sdcard/guardian-filtering-settings.xml)"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
visible={node.attrib.get("text","").strip() for node in root.iter("node")
         if node.attrib.get("visible-to-user","true")=="true"}
required={"사용자 차단 규칙","사용자 허용 규칙","전체 필터 규칙 편집"}
missing=required-visible
obsolete={"보호 및 필터 소스","앱별 제외"} & visible
if missing or obsolete:
    raise SystemExit("Filtering Settings contents mismatch; missing="+repr(sorted(missing))+" obsolete="+repr(sorted(obsolete)))' <<<"$filtering_settings_xml"
echo "Settings > 필터링 shows all user block/allow rules and omits duplicate protection-source and app-exclusion entries."

open_settings_menu_item "일반"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-general-settings.xml >/dev/null 2>&1
general_settings_xml="$(adb_cmd exec-out cat /sdcard/guardian-general-settings.xml | tr -d '\r')"
if ! grep -Fq '일반' <<<"$general_settings_xml" || ! grep -Fq '프로그램 업데이트 확인' <<<"$general_settings_xml"; then
  echo "The General Settings screen does not expose the dedicated Program Updates entry."
  echo "$general_settings_xml"
  exit 1
fi
tap_text "프로그램 업데이트 확인"
sleep 1
updates_xml="$(dump_ui_hierarchy /sdcard/guardian-program-updates.xml)"
# The updater is embedded in the right-hand Settings workspace. On a phone,
# its install control can be below the initial viewport even though the panel
# expanded correctly. Verify the panel header/check action first, then scroll
# the workspace until the install action is actually visible.
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
visible={node.attrib.get("text","").strip() for node in root.iter("node")
         if node.attrib.get("visible-to-user","true")=="true"}
required={"일반","프로그램 업데이트","개발 업데이트 확인"}
missing=required-visible
if missing:
    raise SystemExit("Inline Program Updates panel did not expand; missing: "+repr(sorted(missing)))' <<<"$updates_xml" || {
  echo "The inline Program Updates panel did not expand inside Settings > General."
  echo "$updates_xml"
  exit 1
}
install_visible=0
for attempt in $(seq 1 8); do
  if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
raise SystemExit(0 if any(node.attrib.get("text","").strip()=="업데이트 설치"
  and node.attrib.get("visible-to-user","true")=="true" for node in root.iter("node")) else 1)' <<<"$updates_xml"; then
    install_visible=1
    break
  fi
  adb_cmd shell input swipe 800 1510 800 980 350
  sleep 0.35
  updates_xml="$(dump_ui_hierarchy /sdcard/guardian-program-updates.xml)"
done
if [[ "$install_visible" -ne 1 ]]; then
  echo "The expanded Program Updates panel does not expose its install action after scrolling."
  echo "$updates_xml"
  exit 1
fi

# Exercise the actual development update check, not only the presence of its
# controls. PR builds are intentionally not published as releases, so the
# currently published manifest must resolve to "already latest" for this
# higher versionCode. This validates network/API parsing and the no-update UI
# without downloading or installing an untrusted/unpublished build.
if [[ "${GUARDIAN_CURRENT_VERSION_NAME:-}" == *-dev ]]; then
  tap_text "개발 업데이트 확인"
  update_check_ready=0
  for attempt in $(seq 1 20); do
    sleep 1
    updates_xml="$(dump_ui_hierarchy /sdcard/guardian-program-updates-result.xml)"
    if grep -Fq '공개된 개발 릴리스 중 현재 설치 버전보다 새로운 버전이 없습니다.' <<<"$updates_xml"; then
      update_check_ready=1
      break
    fi
    if grep -Eq '업데이트 확인에 실패|업데이트를 확인할 수 없습니다|오류' <<<"$updates_xml"; then
      echo "Development update check returned an error instead of a valid latest-version response."
      echo "$updates_xml"
      exit 1
    fi
  done
  if [[ "$update_check_ready" -ne 1 ]]; then
    echo "Development update check did not reach the expected latest-version state."
    echo "$updates_xml"
    exit 1
  fi
  echo "Development update check reached the expected latest-version state."
fi
echo "Settings > 일반 > 프로그램 업데이트 확인 expands the inline updater panel and executes its version check."
tap_content_description "설정"
sleep 1
# The redesigned app removed the global header exit action. Exit is now
# a dedicated Settings rail category; tap its clickable parent description.
tap_content_description "종료 설정"
sleep 1
exit_confirmation_xml="$(dump_ui_hierarchy /sdcard/guardian-exit-confirmation.xml)"
if ! grep -Fq 'Guardian 종료' <<<"$exit_confirmation_xml" ||
   ! grep -Fq '앱을 종료하시겠습니까?' <<<"$exit_confirmation_xml" ||
   ! grep -Fq '취소' <<<"$exit_confirmation_xml"; then
  echo "The Settings app-exit control did not open its expected confirmation dialog."
  echo "$exit_confirmation_xml"
  exit 1
fi
# Resolve the actual Android dialog Cancel button by its stable resource ID;
# the generic text helper can race a modal transition and inspect stale text.
cancel_bounds="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if (node.attrib.get("resource-id")=="android:id/button2"
        and node.attrib.get("text","").strip()=="취소"
        and node.attrib.get("visible-to-user","true")=="true"
        and node.attrib.get("clickable")=="true"
        and node.attrib.get("enabled")=="true"):
        p=node.attrib.get("bounds","").strip("[]").split("][")
        if len(p)==2:
            x1,y1=map(int,p[0].split(",")); x2,y2=map(int,p[1].split(","))
            print((x1+x2)//2,(y1+y2)//2); raise SystemExit(0)
raise SystemExit("Guardian exit dialog Cancel button was not actionable.")' <<<"$exit_confirmation_xml")"
read -r cancel_x cancel_y <<<"$cancel_bounds"
adb_cmd shell input tap "$cancel_x" "$cancel_y"
sleep 1
tap_content_description "설정"
sleep 1
# Return to the top so the persistent left category rail remains in view.
for attempt in $(seq 1 5); do
  adb_cmd shell input swipe 540 420 540 1510 350
  sleep 0.25
done
sleep 1

# Verify the customer-facing support center in the redesigned Settings rail.
# The support card is rendered in the right workspace after selecting the
# persistent left-rail destination; it is no longer a card in a vertical index.
# The redesigned Settings rail exposes both a clickable parent description
# ("지원 설정") and a visible child label ("지원"). Some API 35 UIAutomator
# snapshots omit the parent content description while retaining the child.
# Prefer the stable parent, then fall back to the visible label and its clickable
# ancestor so this check follows the actual UI rather than failing on a11y tree shape.
if ! tap_content_description "지원 설정"; then
  tap_text "지원"
fi
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-support-settings.xml >/dev/null 2>&1
support_settings_xml="$(adb_cmd exec-out cat /sdcard/guardian-support-settings.xml | tr -d '\r')"
if ! python3 -c 'import sys, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read())
texts = [node.attrib.get("text", "") for node in root.iter("node")]
required = (
    "고객지원 센터",
    "자주 묻는 질문 (Q&A)",
    "문의 접수 시 입력한 내용과 선택 이메일은 답변 처리를 위해 서버로 전송됩니다",
)
missing = [item for item in required if not any(item in text for text in texts)]
if missing:
    raise SystemExit("Customer support Settings text missing: " + ", ".join(missing))' <<<"$support_settings_xml"; then
  echo "Customer support center card is missing from the dedicated support submenu."
  echo "$support_settings_xml"
  exit 1
fi
echo "Customer support center found in the dedicated Support submenu."

tap_text "자주 묻는 질문 (Q&A)"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-support-faq.xml >/dev/null 2>&1
support_faq_xml="$(adb_cmd exec-out cat /sdcard/guardian-support-faq.xml | tr -d '\r')"
grep -Fq '광고 차단이 되지 않아요' <<<"$support_faq_xml" || {
  echo "Customer support FAQ list did not open."
  echo "$support_faq_xml"
  exit 1
}
tap_text "광고 차단이 되지 않아요"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-support-faq-answer.xml >/dev/null 2>&1
support_faq_answer_xml="$(adb_cmd exec-out cat /sdcard/guardian-support-faq-answer.xml | tr -d '\r')"
grep -Fq '앱 내부에 포함된 광고' <<<"$support_faq_answer_xml" || {
  echo "Customer support FAQ answer did not render."
  echo "$support_faq_answer_xml"
  exit 1
}
faq_dismiss_label="$(python3 scripts/android_navigation_compat.py dismiss <<<"$support_faq_answer_xml")"
tap_text "$faq_dismiss_label"
# The Settings support card is taller than the phone viewport; scroll its
# remaining actions into view before tapping the inquiry form entry.
scroll_to_text "문의 및 의견 보내기"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-support-form.xml >/dev/null 2>&1
support_form_xml="$(adb_cmd exec-out cat /sdcard/guardian-support-form.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
nodes=list(root.iter("node"))
fields=[n for n in nodes if n.attrib.get("class")=="android.widget.EditText"]
if len(fields)<3:
    raise SystemExit("Support submission form must expose subject, optional email, and message fields.")
if not any("문의 및 의견 보내기" in n.attrib.get("text","") for n in nodes):
    raise SystemExit("Support submission dialog title is missing.")' <<<"$support_form_xml"
tap_text "취소"
tap_text "내 문의 답변 확인"
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-support-lookup.xml >/dev/null 2>&1
support_lookup_xml="$(adb_cmd exec-out cat /sdcard/guardian-support-lookup.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
fields=[n for n in root.iter("node") if n.attrib.get("class")=="android.widget.EditText"]
if len(fields)<2:
    raise SystemExit("Support ticket lookup must expose ticket reference and private access-code fields.")' <<<"$support_lookup_xml"
tap_text "취소"
echo "Customer support center FAQ, inquiry submission form, and private lookup UI passed."


auto_pause_picker_visible=0
# Support dialogs can return to the previously active destination on some
# Android builds. Reset through Home before entering Settings so this assertion
# validates the actual Settings route rather than relying on a stale tab state.
tap_content_description "홈"
sleep 1
# Compatibility and auto-pause controls now live in Full Menu > Filtering.
# Navigate to that submenu explicitly before searching its scrollable content.
settings_tab_selected=0
settings_navigation_label="설정"
settings_navigation_xml="$(dump_ui_hierarchy /sdcard/guardian-settings-navigation.xml)"
# Published predecessor versions use a "전체 메뉴" bottom destination;
# current builds use "설정". Resolve the actual clickable navigation control
# from the live hierarchy so the test never taps a nested settings label.
if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
raise SystemExit(0 if any(n.attrib.get("content-desc")=="전체 메뉴"
    and n.attrib.get("clickable")=="true"
    and n.attrib.get("visible-to-user","true")=="true"
    for n in root.iter("node")) else 1)' <<<"$settings_navigation_xml"; then
  settings_navigation_label="전체 메뉴"
fi
for attempt in $(seq 1 3); do
  tap_content_description "$settings_navigation_label"
  sleep 1
  settings_navigation_xml="$(dump_ui_hierarchy /sdcard/guardian-settings-navigation.xml)"
  if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
wanted=sys.argv[1]
if any(node.attrib.get("content-desc")==wanted
       and node.attrib.get("selected")=="true"
       and node.attrib.get("clickable")=="true"
       for node in root.iter("node")):
    raise SystemExit(0)
raise SystemExit(1)' "$settings_navigation_label" <<<"$settings_navigation_xml"; then
    settings_tab_selected=1
    break
  fi
  echo "$settings_navigation_label navigation did not become selected (attempt $attempt/3); retrying."
done
if [[ "$settings_tab_selected" -ne 1 ]]; then
  echo "Could not select the $settings_navigation_label navigation destination before checking VPN auto-pause."
  echo "$settings_navigation_xml"
  exit 1
fi
# Foreground-app VPN auto-pause belongs to General Settings, alongside
# DNS transport and compatibility controls. It is not part of Filtering.
# Select the General category from the legacy or redesigned Settings index.
if [[ "$settings_navigation_label" == "전체 메뉴" ]]; then
  legacy_general_target="$(python3 scripts/android_navigation_compat.py settings-item "일반" <<<"$settings_navigation_xml" 2>/dev/null || true)"
  if [[ -z "$legacy_general_target" ]]; then
    echo "Legacy Full Menu does not expose the General Settings destination."
    echo "$settings_navigation_xml"
    exit 1
  fi
  scroll_to_text "$legacy_general_target"
else
  ensure_text_visible "일반"
  tap_clickable_parent_of_text "일반"
fi
sleep 1
for attempt in $(seq 1 24); do
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  auto_pause_settings_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
  if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if any(node.attrib.get("content-desc")=="VPN 자동 일시중지 앱 선택"
       and node.attrib.get("class")=="android.widget.Button"
       and node.attrib.get("visible-to-user","true")=="true"
       and node.attrib.get("clickable")=="true"
       and node.attrib.get("enabled")=="true"
       for node in root.iter("node")):
    raise SystemExit(0)
raise SystemExit(1)' <<<"$auto_pause_settings_xml"; then
    auto_pause_picker_visible=1
    echo "Foreground-app compatibility controls found after $attempt Settings scroll checks."
    break
  fi
  # Swipe up to reveal settings content below the current viewport.
  adb_cmd shell input swipe 540 1500 540 600 450
  sleep 0.5
done
if [[ "$auto_pause_picker_visible" -ne 1 ]]; then
  echo "Foreground-app auto-pause controls were not visible in Settings."
  echo "$auto_pause_settings_xml"
  exit 1
fi
# Verify the Usage Access control is a real enabled action when access is
# missing; cancel its confirmation dialog so the test does not leave Guardian.
adb_cmd shell uiautomator dump /sdcard/guardian-usage-access.xml >/dev/null 2>&1
usage_access_xml="$(adb_cmd shell cat /sdcard/guardian-usage-access.xml | tr -d '\r')"
usage_access_enabled="$(python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if node.attrib.get("content-desc")=="사용 정보 접근 권한 설정":
        print(node.attrib.get("enabled","false")); break' <<<"$usage_access_xml")"
if [[ "$usage_access_enabled" == "true" ]]; then
  tap_content_description "사용 정보 접근 권한 설정"
  sleep 1
  adb_cmd shell uiautomator dump /sdcard/guardian-usage-dialog.xml >/dev/null 2>&1
  usage_dialog_xml="$(adb_cmd shell cat /sdcard/guardian-usage-dialog.xml | tr -d '\r')"
  if ! grep -q '설정 열기' <<<"$usage_dialog_xml"; then
    echo "Usage Access button did not open its confirmation dialog."
    echo "$usage_dialog_xml"
    exit 1
  fi
  tap_text "취소"
else
  if ! grep -q '사용 정보 접근 권한 허용됨' <<<"$usage_access_xml"; then
    echo "Usage Access control is disabled without showing that permission is already granted."
    echo "$usage_access_xml"
    exit 1
  fi
fi

tap_content_description "VPN 자동 일시중지 앱 선택"
sleep 1
# The app inventory is shared with App Exclusions and is protected by an
# explicit first-use disclosure. Accept that disclosure in the test flow when
# it has not already been accepted; otherwise the next search tap silently
# lands on the Settings page and the picker remains unopened.
adb_cmd shell uiautomator dump /sdcard/guardian-compatibility-disclosure.xml >/dev/null 2>&1
compatibility_disclosure_xml="$(adb_cmd exec-out cat /sdcard/guardian-compatibility-disclosure.xml | tr -d '\\r')"
if grep -Fq '설치 앱 목록 접근 안내' <<<"$compatibility_disclosure_xml"; then
  if ! grep -Fq '앱별 보호 제외 및 선택 앱 자동 VPN 일시중지 기능' <<<"$compatibility_disclosure_xml"; then
    echo "Unexpected app-inventory disclosure content in compatibility picker."
    echo "$compatibility_disclosure_xml"
    exit 1
  fi
  tap_text "동의하고 계속"
  sleep 2
fi
# Wait for the actual picker search control rather than assuming the dialog
# opened synchronously after the disclosure transition.
compatibility_search_ready=0
for attempt in $(seq 1 20); do
  adb_cmd shell rm -f /sdcard/guardian-compatibility-picker.xml
  adb_cmd shell uiautomator dump /sdcard/guardian-compatibility-picker.xml >/dev/null 2>&1
  compatibility_picker_xml="$(adb_cmd exec-out cat /sdcard/guardian-compatibility-picker.xml | tr -d '\\r')"
  if grep -Fq 'VPN 자동 일시중지 앱 검색 입력' <<<"$compatibility_picker_xml"; then
    compatibility_search_ready=1
    break
  fi
  sleep 1
done
if [[ "$compatibility_search_ready" -ne 1 ]]; then
  echo "Compatibility app picker did not open after its first-use disclosure."
  echo "$compatibility_picker_xml"
  exit 1
fi
tap_content_description "VPN 자동 일시중지 앱 검색 입력"
sleep 1
adb_cmd shell input text 'com.android.settings'
sleep 1
adb_cmd shell uiautomator dump /sdcard/guardian-compatibility-keyboard.xml >/dev/null 2>&1
compatibility_keyboard_xml="$(adb_cmd shell cat /sdcard/guardian-compatibility-keyboard.xml | tr -d '\r')"
python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(node.attrib.get("class")=="android.widget.EditText"
       and node.attrib.get("content-desc")=="VPN 자동 일시중지 앱 검색 입력"
       and node.attrib.get("text","")=="com.android.settings"
       for node in root.iter("node")):
    raise SystemExit("Compatibility app search query was not entered completely.")' <<<"$compatibility_keyboard_xml"
adb_cmd shell input keyevent KEYCODE_BACK || true
compatibility_row_ready=0
for attempt in $(seq 1 20); do
  adb_cmd shell rm -f /sdcard/guardian-window.xml
  adb_cmd shell uiautomator dump /sdcard/guardian-window.xml >/dev/null 2>&1
  auto_pause_picker_xml="$(adb_cmd shell cat /sdcard/guardian-window.xml | tr -d '\r')"
  if python3 -c 'import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if any(node.attrib.get("class")=="android.widget.CheckBox"
       and node.attrib.get("text","").rstrip().rsplit("\n",1)[-1].strip()=="com.android.settings"
       and node.attrib.get("visible-to-user","true")=="true"
       and node.attrib.get("clickable")=="true"
       for node in root.iter("node")):
    raise SystemExit(0)
raise SystemExit(1)' <<<"$auto_pause_picker_xml"; then
    compatibility_row_ready=1
    break
  fi
  sleep 1
done
if [[ "$compatibility_row_ready" -ne 1 ]]; then
  echo "Compatibility app picker did not finish loading the searched package."
  echo "$auto_pause_picker_xml"
  exit 1
fi
tap_checkbox "com.android.settings"
tap_text "저장"
sleep 1
adb_cmd shell input swipe 540 1450 540 1000 350
sleep 1
tap_content_description "선택 앱 실행 중 VPN 자동 일시중지"
sleep 1
tap_text "활성화"
sleep 1
prefs="$(read_prefs)"
if ! grep -q 'name="app_foreground_auto_pause_enabled"[^>]*value="true"' <<<"$prefs" ||
   ! grep -A2 -B1 'name="app_foreground_auto_pause_packages"' <<<"$prefs" | grep -Fq '<string>com.android.settings</string>'; then
  echo "Foreground-app auto-pause settings were not persisted."
  read_prefs
  exit 1
fi

echo "Launching the selected app. Guardian must close the VPN tunnel."
adb_cmd shell am start -a android.settings.SETTINGS >/dev/null
auto_pause_observed=0
for attempt in $(seq 1 40); do
  prefs="$(read_prefs)"
  if grep -q 'name="app_foreground_auto_pause_active"[^>]*value="true"' <<<"$prefs" &&
     grep -q 'name="protection_active"[^>]*value="false"' <<<"$prefs"; then
    auto_pause_observed=1
    break
  fi
  sleep 1
done
if [[ "$auto_pause_observed" -ne 1 ]]; then
  echo "Selected foreground app did not pause the whole VPN tunnel."
  read_prefs
  adb_cmd logcat -d -t 5000 | grep -E 'GuardianProtection|foreground|UsageStats|FATAL EXCEPTION' | tail -220 || true
  adb_cmd shell dumpsys vpn || true
  exit 1
fi
echo "Foreground-app auto-pause engaged: Settings is active and Guardian protection is paused."

echo "Returning to Guardian. VPN protection must automatically reconnect."
adb_cmd shell monkey -p "$PACKAGE" 1 >/dev/null
auto_resume_observed=0
for attempt in $(seq 1 60); do
  prefs="$(read_prefs)"
  if grep -q 'name="protection_active"[^>]*value="true"' <<<"$prefs" &&
     grep -q 'name="app_foreground_auto_pause_active"[^>]*value="false"' <<<"$prefs"; then
    auto_resume_observed=1
    break
  fi
  sleep 1
done
if [[ "$auto_resume_observed" -ne 1 ]]; then
  echo "Guardian did not automatically restore the VPN after the selected app closed."
  read_prefs
  adb_cmd logcat -d -t 7000 | grep -E 'GuardianProtection|foreground|UsageStats|FATAL EXCEPTION' | tail -260 || true
  adb_cmd shell dumpsys vpn || true
  adb_cmd shell dumpsys connectivity | tail -180 || true
  exit 1
fi
before_auto_resume_dns="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_auto_resume_dns="${before_auto_resume_dns:-0}"
adb_cmd shell ping -c 1 -W 5 example.com >/tmp/guardian-after-compatibility-resume-dns.txt 2>&1 || true
sleep 2
prefs="$(read_prefs)"
after_auto_resume_dns="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_auto_resume_dns="${after_auto_resume_dns:-0}"
if (( after_auto_resume_dns <= before_auto_resume_dns )); then
  echo "DNS processing did not resume after foreground-app compatibility pause."
  read_prefs
  cat /tmp/guardian-after-compatibility-resume-dns.txt || true
  exit 1
fi
echo "Foreground-app auto-pause/resume passed: VPN tunnel and DNS processing recovered automatically."

echo "Verifying the home protection control can stop an active VPN tunnel."
adb_cmd shell monkey -p "$PACKAGE" 1 >/dev/null
sleep 1
# The previous compatibility test opens Settings and may leave the custom DNS
# field focused. Dismiss its IME before tapping the bottom navigation; otherwise
# the keyboard can cover the Home tab and the stop-control assertion targets
# the still-visible Settings page instead of the dashboard.
adb_cmd shell input keyevent KEYCODE_BACK || true
sleep 1
tap_content_description "홈"
sleep 1
tap_text "보호 중지"
protection_stopped=0
for attempt in $(seq 1 30); do
  prefs="$(read_prefs)"
  if grep -q 'name="protection_active"[^>]*value="false"' <<<"$prefs"; then
    protection_stopped=1
    break
  fi
  sleep 1
done
if [[ "$protection_stopped" -ne 1 ]]; then
  echo "The home protection toggle did not stop the active VPN service."
  read_prefs
  adb_cmd logcat -d -t 3000 | grep -E 'GuardianProtection|GuardianVpn|FATAL EXCEPTION' | tail -160 || true
  exit 1
fi
echo "Home protection start/stop toggle passed."

echo "Verifying automatic VPN protection recovery after a full Android device reboot."
tap_text "보호 시작"
protection_started=0
for attempt in $(seq 1 30); do
  prefs="$(read_prefs)"
  if grep -q 'name="protection_active"[^>]*value="true"' <<<"$prefs" &&
     ! grep -q 'name="protection_recovery_pending"[^>]*value="true"' <<<"$prefs"; then
    protection_started=1
    break
  fi
  sleep 1
done
if [[ "$protection_started" -ne 1 ]]; then
  echo "Could not establish VPN protection before the reboot recovery test."
  read_prefs
  exit 1
fi
before_reboot_dns="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_reboot_dns="${before_reboot_dns:-0}"
echo "Rebooting Android emulator while Guardian VPN protection is active."
adb_cmd reboot
timeout --signal=TERM --kill-after=2s 30s adb wait-for-device
boot_completed=0
for attempt in $(seq 1 60); do
  boot_state="$(adb_poll shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$boot_state" == "1" ]]; then
    boot_completed=1
    break
  fi
  sleep 2
done
if [[ "$boot_completed" -ne 1 ]]; then
  echo "Android emulator did not finish rebooting within the bounded recovery window."
  adb_cmd logcat -d -t 2500 | grep -E 'GuardianProtection|GuardianVpn|FATAL EXCEPTION' | tail -180 || true
  exit 1
fi
echo "Android emulator reboot completed; waiting for the boot receiver to restore Guardian."
reboot_recovery=0
for attempt in $(seq 1 60); do
  prefs="$(read_prefs)"
  if grep -q 'name="protection_active"[^>]*value="true"' <<<"$prefs" &&
     ! grep -q 'name="protection_recovery_pending"[^>]*value="true"' <<<"$prefs"; then
    reboot_recovery=1
    break
  fi
  sleep 2
done
if [[ "$reboot_recovery" -ne 1 ]]; then
  echo "Guardian did not automatically restore VPN protection after Android reboot."
  read_prefs
  adb_cmd logcat -d -t 5000 | grep -E 'GuardianProtection|GuardianVpn|FATAL EXCEPTION|startForegroundService' | tail -260 || true
  adb_cmd shell dumpsys vpn || true
  adb_cmd shell dumpsys connectivity | tail -180 || true
  exit 1
fi
before_reboot_dns="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_reboot_dns="${before_reboot_dns:-0}"
adb_cmd shell ping -c 1 -W 5 example.com >/tmp/guardian-after-reboot-allowed-dns.txt 2>&1 || true
sleep 2
prefs="$(read_prefs)"
after_reboot_dns="$(sed -n 's/.*name="dns_queries"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_reboot_dns="${after_reboot_dns:-0}"
if (( after_reboot_dns <= before_reboot_dns )); then
  echo "Guardian VPN restored after reboot, but DNS query processing did not resume."
  read_prefs
  cat /tmp/guardian-after-reboot-allowed-dns.txt || true
  exit 1
fi
before_reboot_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
before_reboot_blocked="${before_reboot_blocked:-0}"
adb_cmd shell ping -c 1 -W 5 "guardian-reboot-${GITHUB_RUN_ID:-test}.doubleclick.net" >/tmp/guardian-after-reboot-blocked-dns.txt 2>&1 || true
sleep 2
prefs="$(read_prefs)"
after_reboot_blocked="$(sed -n 's/.*name="dns_blocked"[^>]*value="\([0-9]*\)".*/\1/p' <<<"$prefs" | tail -1)"
after_reboot_blocked="${after_reboot_blocked:-0}"
if (( after_reboot_blocked <= before_reboot_blocked )) ||
   grep -Eq 'PING guardian-reboot-[^ ]*\.doubleclick\.net \([^)]*\)' /tmp/guardian-after-reboot-blocked-dns.txt; then
  echo "Guardian reboot recovery did not resume DNS ad blocking."
  read_prefs
  cat /tmp/guardian-after-reboot-blocked-dns.txt || true
  adb_cmd logcat -d -t 4000 | grep -E 'GuardianProtection|GuardianVpn|FATAL EXCEPTION' | tail -220 || true
  exit 1
fi
echo "Device reboot recovery passed: boot receiver restored VPN protection, allowed DNS resumed, and the ad test domain was blocked."
