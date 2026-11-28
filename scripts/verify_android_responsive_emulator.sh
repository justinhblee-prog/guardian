#!/usr/bin/env bash
# Exercise Guardian's adaptive navigation on compact, tablet, and landscape
# emulator windows. This runs after VPN E2E and never toggles VPN state.
set -euo pipefail
PACKAGE="com.guardian.adblock"
adb_cmd() { timeout --signal=TERM --kill-after=2s 30s adb "$@"; }
reset_display() {
  adb_cmd shell wm size reset >/dev/null 2>&1 || true
  adb_cmd shell wm density reset >/dev/null 2>&1 || true
}
trap reset_display EXIT
dump_ui() {
  local remote="/sdcard/guardian-responsive.xml" output xml
  for attempt in $(seq 1 5); do
    adb_cmd shell rm -f "$remote" >/dev/null 2>&1 || true
    output="$(adb_cmd shell uiautomator dump "$remote" 2>&1 || true)"
    xml="$(adb_cmd shell cat "$remote" 2>/dev/null | tr -d '\r' || true)"
    if [[ "$xml" == *'<hierarchy'* && "$xml" == *'</hierarchy>'* ]]; then printf '%s' "$xml"; return 0; fi
    echo "Retrying responsive UI hierarchy capture ($attempt/5): $output" >&2
    sleep 1
  done
  echo "Responsive UI hierarchy could not be captured: $output" >&2
  return 1
}
verify_layout() {
  local size="$1" density="$2" expected="$3" label="$4"
  echo "Checking $label: size=$size density=$density navigation=$expected"
  adb_cmd shell wm size "$size"
  adb_cmd shell wm density "$density"
  sleep 2
  local launch xml
  launch="$(adb_cmd shell am start -W -n "$PACKAGE/.MainActivity" 2>&1)"
  grep -q 'Status: ok' <<<"$launch"
  sleep 1
  xml="$(dump_ui)"
  python3 -c '
import sys,xml.etree.ElementTree as ET
mode=sys.argv[1]; root=ET.fromstring(sys.stdin.read()); nodes=list(root.iter("node"))
labels=("홈","보호","앱 제외","활동","설정"); found={}
for label in labels:
    matches=[n for n in nodes if n.attrib.get("content-desc")==label and n.attrib.get("visible-to-user","true")=="true" and n.attrib.get("clickable")=="true"]
    if not matches: raise SystemExit("Missing visible navigation destination: "+label)
    parts=matches[0].attrib.get("bounds","").strip("[]").split("][")
    if len(parts)!=2: raise SystemExit("Invalid navigation bounds: "+label)
    x1,y1=map(int,parts[0].split(",")); x2,y2=map(int,parts[1].split(","))
    found[label]=((x1+x2)//2,(y1+y2)//2)
points=[found[x] for x in labels]
if mode=="rail":
    if not all(abs(points[i][0]-points[0][0])<=36 for i in range(1,len(points))) or not all(points[i][1]<points[i+1][1] for i in range(len(points)-1)):
        raise SystemExit("Expected vertical navigation rail, got "+repr(found))
else:
    if not all(abs(points[i][1]-points[0][1])<=36 for i in range(1,len(points))) or not all(points[i][0]<points[i+1][0] for i in range(len(points)-1)):
        raise SystemExit("Expected horizontal bottom navigation, got "+repr(found))
print("Adaptive navigation placement passed: "+repr(found))
' "$expected" <<<"$xml"
}
tap_navigation() {
  local label="$1" xml bounds
  xml="$(dump_ui)"
  bounds="$(python3 -c '
import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
for n in root.iter("node"):
    if n.attrib.get("content-desc")==sys.argv[1] and n.attrib.get("clickable")=="true" and n.attrib.get("visible-to-user","true")=="true":
        p=n.attrib.get("bounds","").strip("[]").split("][")
        if len(p)==2:
            x1,y1=map(int,p[0].split(",")); x2,y2=map(int,p[1].split(",")); print((x1+x2)//2,(y1+y2)//2); break
' "$label" <<<"$xml")"
  [[ -n "$bounds" ]] || { echo "Could not locate navigation destination: $label"; echo "$xml"; return 1; }
  read -r x y <<<"$bounds"
  adb_cmd shell input tap "$x" "$y"
  sleep 1
  xml="$(dump_ui)"
  python3 -c '
import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read()); label=sys.argv[1]; nodes=list(root.iter("node"))
if not any(n.attrib.get("content-desc")==label and n.attrib.get("selected")=="true" and n.attrib.get("visible-to-user","true")=="true" for n in nodes):
    raise SystemExit("Selected navigation state was not retained: "+label)
if not any(label in n.attrib.get("text","") for n in nodes):
    raise SystemExit("Destination content did not render: "+label)
print("Navigation selection and destination content passed: "+label)
' "$label" <<<"$xml"
}
echo "Verify expanded tablet rail."
verify_layout "1600x2560" 160 "rail" "expanded tablet"
tap_navigation "보호"
echo "Resize to compact window and verify bottom navigation."
verify_layout "700x1200" 160 "bottom" "compact window"
tap_navigation "활동"
echo "Resize to landscape tablet and verify state restoration."
verify_layout "1200x700" 160 "rail" "landscape tablet"
xml="$(dump_ui)"
python3 -c '
import sys,xml.etree.ElementTree as ET
root=ET.fromstring(sys.stdin.read())
if not any(n.attrib.get("content-desc")=="활동" and n.attrib.get("selected")=="true" for n in root.iter("node")):
    raise SystemExit("Selected Activity destination was not preserved after resize/rotation.")
print("Selected tab survived resize/landscape recreation.")
' <<<"$xml"
echo "Guardian adaptive navigation emulator verification passed."
