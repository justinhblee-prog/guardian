# Guardian AdBlock Android — Verification Record

기준 버전: 0.6.4-dev  
기준일: 2026-11-13

## Automated verification gates

Latest release CI is expected to verify all of the following:

1. Shared DNS rule asset exists.
2. VPN service manifest declares `BIND_VPN_SERVICE`, foreground service type `specialUse`, and the special-use subtype property.
3. Guardian launcher icon resources exist for adaptive and legacy launchers.
4. JDK 17 / Android SDK 35 / Build Tools 35.0.0 / Gradle 8.9 setup succeeds.
5. `clean testDebugUnitTest assembleDebug` succeeds.
6. Built APK reports package ID `com.guardian.adblock`, versionCode 11, versionName `0.6.4-dev`.
7. APK exists, SHA-256 is calculated, and the installable artifact is uploaded.

## Test coverage

- DNS shared rules and allow-overrides-block behavior
- DNS question parsing and malformed query rejection
- NXDOMAIN and SERVFAIL response construction
- Upstream response transaction/question validation
- Case-insensitive DNS QNAME matching with exact QTYPE/QCLASS comparison
- Ambiguous dot bytes inside DNS wire labels rejected
- IPv4 UDP DNS parsing, exact packet-length validation, fragment rejection, IPv4 options
- IPv6 UDP DNS parsing, mandatory checksum verification, invalid checksum rejection, response checksum generation

## Functional boundaries not covered by CI

- Physical Android installation and VPN consent
- Actual DNS queries over a physical Wi-Fi/mobile network
- Device-specific Android foreground-service behavior
- Network handover and long-duration battery/lifecycle behavior
- Real-world ad-blocking effectiveness
- DoH/DoT and client-side TCP DNS (not implemented)
- IPv6 extension header and fragment reassembly (not implemented)

CI success is not a substitute for physical-device acceptance. The final APK must be described as a development build until the device checks above have been performed.


## Actual latest successful CI result — 2026-11-13

- Workflow: [43423112883](https://github.com/justinhblee-prog/guardian/actions/runs/43423112883)
- Built source commit: `05dd104a97ea4f9f2bcf908514a6c82bf0e3b5f1`
- Workflow conclusion: **success**
- Android Gradle `clean testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL**
- Manifest/launcher checks: **PASS**
- APK package identity: `com.guardian.adblock`, versionCode `11`, versionName `0.6.4-dev` — **PASS**
- Artifact ID: `14034534107`
- Artifact ZIP size: 841,764 bytes
- Artifact ZIP SHA-256: `7b24f3abe7bd676389c32efc0afb1d26f6aa5fdc0038bfedc65c076cf9ab9942`
- Extracted APK size: 849,811 bytes
- APK SHA-256: `daded536157e3a71fe205dd0a47513228e8ecacb88be784cda87925c4ee48130`
- Downloaded ZIP integrity and APK internal checksum comparison: **PASS**
- JUnit source contains 25 test methods across DNS, IPv4 and IPv6 suites; the Gradle test task passed.

The artifact is an installable Android Debug APK. Physical-device acceptance remains outstanding.
