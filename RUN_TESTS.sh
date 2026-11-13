#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT"
python -m pytest -q dns_engine/test_dns_engine.py windows_engine/test_windows_protection.py
python -m compileall -q dns_engine windows_engine
if command -v node >/dev/null 2>&1; then
  node --test tests/browser_rules.test.cjs
else
  echo "SKIP: Node.js unavailable; browser extension tests not run."
fi
if command -v kotlinc >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
  ./VERIFY_ANDROID_CORE.sh
else
  echo "SKIP: Kotlin compiler/JDK unavailable; Android core smoke test not run."
fi