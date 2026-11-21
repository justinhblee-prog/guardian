#!/usr/bin/env bash
set -euo pipefail

# Exercise the actual Settings screen at a tablet logical width. Restore the
# emulator overrides even when an assertion fails so later diagnostics remain
# representative of the default phone profile.
adb_cmd() { timeout --signal=TERM --kill-after=2s 30s adb "$@"; }
restore_display() {
  adb_cmd shell wm size reset >/dev/null 2>&1 || true
  adb_cmd shell wm density reset >/dev/null 2>&1 || true
}
trap restore_display EXIT

adb_cmd wait-for-device
adb_cmd shell wm size 1600x2560
adb_cmd shell wm density 240
sleep 2

display="$(adb_cmd shell wm size | tr -d '\r')"
density="$(adb_cmd shell wm density | tr -d '\r')"
echo "$display"
echo "$density"
grep -Fq 'Override size: 1600x2560' <<<"$display"
grep -Fq 'Override density: 240' <<<"$density"

# 1600px / (240dpi / 160) = 1066dp, above the 1000dp two-column breakpoint.
width_dp="$(adb_cmd shell dumpsys window displays | sed -n 's/.*cur=\([0-9]*\)x\([0-9]*\).*/\1/p' | head -1)"
if [[ -z "$width_dp" ]]; then
  width_dp="$(adb_cmd shell wm size | sed -n 's/.*Override size: \([0-9]*\)x.*/\1/p')"
fi
test -n "$width_dp"
python3 - "$width_dp" <<'PY'
import sys
width_px = int(sys.argv[1])
width_dp = width_px / 1.5
print(f"Tablet test logical width: {width_dp:.1f}dp")
if width_dp < 1000:
    raise SystemExit("Tablet test did not cross the 1000dp settings breakpoint.")
PY

adb_cmd shell am force-stop com.guardian.adblock
adb_cmd shell am start -n com.guardian.adblock/.MainActivity >/dev/null
sleep 4
adb_cmd shell uiautomator dump /sdcard/guardian-tablet-nav.xml >/dev/null 2>&1
nav_xml="$(adb_cmd exec-out cat /sdcard/guardian-tablet-nav.xml | tr -d '\r')"
tap_point="$(python3 - "$nav_xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.fromstring(sys.argv[1])
for node in root.iter("node"):
    if (node.attrib.get("content-desc") == "설정"
        and node.attrib.get("visible-to-user", "true") == "true"
        and node.attrib.get("clickable") == "true"
        and node.attrib.get("enabled") == "true"):
        parts = node.attrib.get("bounds", "").strip("[]").split("][")
        if len(parts) == 2:
            x1, y1 = map(int, parts[0].split(","))
            x2, y2 = map(int, parts[1].split(","))
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit("Could not locate visible clickable Settings navigation item.")
PY
)"
read -r nav_x nav_y <<<"$tap_point"
adb_cmd shell input tap "$nav_x" "$nav_y"
sleep 2
adb_cmd shell uiautomator dump /sdcard/guardian-tablet-settings.xml >/dev/null 2>&1
settings_xml="$(adb_cmd exec-out cat /sdcard/guardian-tablet-settings.xml | tr -d '\r')"
python3 - "$settings_xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.fromstring(sys.argv[1])
expected = ["일반", "필터링", "라이선스 및 프리미엄", "고객 지원", "정보"]
centers = {}
for node in root.iter("node"):
    label = node.attrib.get("text", "").strip()
    if label in expected:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            centers[label] = ((x1 + x2) // 2, (y1 + y2) // 2)
missing = [label for label in expected if label not in centers]
if missing:
    raise SystemExit("Settings category labels missing from tablet UI: " + ", ".join(missing))
left = [centers[label][0] for label in expected[:3]]
right = [centers[label][0] for label in expected[3:]]
print("Tablet Settings category centers:", centers)
if max(left) >= min(right):
    raise SystemExit("Settings cards did not render as two distinct columns.")
if min(right) - max(left) < 200:
    raise SystemExit("Settings columns are too close; expected a clear horizontal separation.")
print("PASS: all five Settings categories are visible in two distinct columns at tablet width.")
PY

# Change the same emulator to landscape without restarting the Activity. This
# verifies that the Settings destination survives a configuration/size change
# and remains a two-column tablet layout after the rotation.
adb_cmd shell wm size 2560x1600
sleep 3
landscape_display="$(adb_cmd shell wm size | tr -d '\r')"
grep -Fq 'Override size: 2560x1600' <<<"$landscape_display"
adb_cmd shell uiautomator dump /sdcard/guardian-tablet-landscape.xml >/dev/null 2>&1
landscape_xml="$(adb_cmd exec-out cat /sdcard/guardian-tablet-landscape.xml | tr -d '\r')"
python3 - "$landscape_xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.fromstring(sys.argv[1])
expected = ["일반", "필터링", "라이선스 및 프리미엄", "고객 지원", "정보"]
centers = {}
for node in root.iter("node"):
    label = node.attrib.get("text", "").strip()
    if label in expected:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            centers[label] = ((x1 + x2) // 2, (y1 + y2) // 2)
missing = [label for label in expected if label not in centers]
if missing:
    raise SystemExit("Settings destination/content was not preserved in landscape: " + ", ".join(missing))
left = [centers[label][0] for label in expected[:3]]
right = [centers[label][0] for label in expected[3:]]
if max(left) >= min(right) or min(right) - max(left) < 200:
    raise SystemExit("Settings did not retain two distinct columns in landscape.")
print("PASS: Settings remains visible and two-column after tablet landscape resize.")
print("Landscape category centers:", centers)
PY

# Exercise the responsive breakpoint on the same live Settings Activity. A
# 1498px viewport at 240dpi is 998.7dp and must use one column. The nominal
# 1500px/1000dp display reports only 999dp to this Activity because Android's
# app window excludes a 1dp system inset. Give the Activity a clear margin above
# the breakpoint (1512px at 240dpi) and assert its own reported width; the exact
# 999/1000dp policy boundary remains covered by the responsive-width unit test.
adb_cmd shell wm size 1498x1600
sleep 2
narrow_display="$(adb_cmd shell wm size | tr -d '\r')"
grep -Fq 'Override size: 1498x1600' <<<"$narrow_display"
adb_cmd shell uiautomator dump /sdcard/guardian-settings-999dp.xml >/dev/null 2>&1
narrow_xml="$(adb_cmd exec-out cat /sdcard/guardian-settings-999dp.xml | tr -d '\r')"
python3 - "$narrow_xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.fromstring(sys.argv[1])
expected = ["일반", "필터링", "라이선스 및 프리미엄", "고객 지원", "정보"]
centers = {}
for node in root.iter("node"):
    label = node.attrib.get("text", "").strip()
    if label in expected:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            centers[label] = ((x1 + x2) // 2, (y1 + y2) // 2)
missing = [label for label in expected if label not in centers]
if missing:
    raise SystemExit("Settings categories missing at 999dp: " + ", ".join(missing))
xs = [point[0] for point in centers.values()]
if max(xs) - min(xs) >= 100:
    raise SystemExit("Settings should use one column below the 1000dp breakpoint.")
print("PASS: all five Settings categories use one column at 998.7dp.")
print("999dp category centers:", centers)
PY

adb_cmd logcat -c
adb_cmd shell wm size 1512x1600
sleep 3
boundary_display="$(adb_cmd shell wm size | tr -d '\r')"
echo "$boundary_display"
grep -Fq 'Override size: 1512x1600' <<<"$boundary_display"
echo "Android Activity/window configuration at 1008dp display / 1000dp minimum app width:"
adb_cmd shell dumpsys activity activities | grep -E 'mLastReportedConfiguration|screenWidthDp|mOverrideConfig|mCurrentConfig' | tail -20 || true
echo "Guardian responsive width diagnostics:"
responsive_log="$(adb_cmd logcat -d -s GuardianUI:I)"
echo "$responsive_log"
grep -Eq 'Settings responsive widthDp=10[0-9][0-9] twoColumns=true' <<<"$responsive_log" || {
  echo "Guardian Activity did not receive at least 1000dp at the 1512px test size." >&2
  exit 1
}
adb_cmd shell uiautomator dump /sdcard/guardian-settings-1000dp.xml >/dev/null 2>&1
boundary_xml="$(adb_cmd exec-out cat /sdcard/guardian-settings-1000dp.xml | tr -d '\r')"
python3 - "$boundary_xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.fromstring(sys.argv[1])
expected = ["일반", "필터링", "라이선스 및 프리미엄", "고객 지원", "정보"]
centers = {}
for node in root.iter("node"):
    label = node.attrib.get("text", "").strip()
    if label in expected:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            centers[label] = ((x1 + x2) // 2, (y1 + y2) // 2)
missing = [label for label in expected if label not in centers]
if missing:
    raise SystemExit("Settings categories missing at 1000dp: " + ", ".join(missing))
left = [centers[label][0] for label in expected[:3]]
right = [centers[label][0] for label in expected[3:]]
print("Nominal 1000dp Settings category centers:", centers)
print("Observed column center spread:", min(right) - max(left))
if max(left) >= min(right) or min(right) - max(left) < 200:
    raise SystemExit("Settings did not switch to two columns when the Activity received at least 1000dp.")
print("PASS: Settings switches to two columns when the Activity receives at least 1000dp.")
PY
