#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
KOTLINC="${KOTLINC:-kotlinc}"
JAVA="${JAVA:-java}"
command -v "$KOTLINC" >/dev/null 2>&1 || { echo "kotlinc not found; install Kotlin compiler." >&2; exit 2; }
command -v "$JAVA" >/dev/null 2>&1 || { echo "java not found; install a JDK." >&2; exit 2; }
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
"$KOTLINC" \
  "$ROOT/android/app/src/main/java/com/guardian/adblock/FilterRuleParser.kt" \
  "$ROOT/android/app/src/main/java/com/guardian/adblock/DomainFilter.kt" \
  "$ROOT/android/app/src/main/java/com/guardian/adblock/Ipv4Udp.kt" \
  # Ipv6Udp shares its extension-header walker with the DNS-over-TCP packet codec.
  "$ROOT/android/app/src/main/java/com/guardian/adblock/DnsTcpPacket.kt" \
  "$ROOT/android/app/src/main/java/com/guardian/adblock/Ipv6Udp.kt" \
  "$ROOT/android/core-checks/CoreSmoke.kt" \
  -include-runtime -d "$TMP/guardian-core-check.jar"
"$JAVA" -jar "$TMP/guardian-core-check.jar"