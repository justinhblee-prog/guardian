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
nodes = list(root.iter("node"))
for i, node in enumerate(nodes):
    if node.attrib.get("text") == "설정" or node.attrib.get("content-desc") == "설정":
        current = node
        # Prefer the clickable navigation parent, not its label child.
        if node.attrib.get("clickable") == "true":
            bounds = node.attrib.get("bounds", "")
        else:
            bounds = ""
        if not bounds:
            # UIAutomator XML preserves nesting, so find the nearest clickable
            # ancestor by walking the tree recursively.
            def walk(parent):
                for child in list(parent):
                    if child is node:
                        return parent
                    found = walk(child)
                    if found is not None:
                        return found
                return None
            parent = walk(root)
            while parent is not None:
                if parent.attrib.get("clickable") == "true":
                    bounds = parent.attrib.get("bounds", "")
                    break
                parent = next((p for p in root.iter() if parent in list(p)), None)
        if bounds:
            import re
            m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
            if m:
                x1,y1,x2,y2 = map(int,m.groups())
                print(f"{(x1+x2)//2} {(y1+y2)//2}")
                raise SystemExit(0)
raise SystemExit("Could not locate the clickable Settings navigation item.")
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
