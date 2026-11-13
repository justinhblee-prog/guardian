# Guardian AdBlock Android — Verification Record

기준 버전: 0.7.0-dev  
기준일: 2026-11-13

## Push CI policy

- Push CI runs Gradle clean + Android JUnit tests only.
- APK assembly and artifact upload are skipped on push.
- Final APK packaging is available only through manual workflow_dispatch after development is complete.

## Current regression test inventory

| Test suite | Test methods |
|---|---:|
| DomainFilterTest | 17 |
| Ipv4UdpTest | 6 |
| Ipv6UdpTest | 4 |
| ProtectionStateTest | 2 |
| FilterRuleParserTest | 8 |
| DnsProbeTest | 2 |
| FilterRuleComposerTest | 2 |
| DnsAddressValidatorTest | 2 |
| FilterListProfilesTest | 6 |
| VpnStartCancellationGateTest | 4 |
| Total | 53 |

## Covered areas

- DNS question validation, NXDOMAIN/SERVFAIL, upstream transaction/question matching
- DNS QNAME case-insensitive response matching with exact QTYPE/QCLASS
- IPv4 UDP DNS parse, exact packet length, fragments, options
- IPv6 UDP DNS mandatory checksum and response checksum
- stale persisted VPN state and live tunnel requirement
- Adblock domain anchor, allow exception, hosts entries, badfilter, unsupported modifier rejection
- DNS self-test query construction and hostname validation
- Filter-source badfilter isolation and custom DNS IPv4/IPv6 literal validation
- Official filter profile catalog, unique profile IDs, registry URLs, and minimum validation thresholds

## Functional boundaries not covered by CI

- Downloading current remote filter sources on a real Android device
- VPN permission / TUN establishment on physical devices
- Actual public DNS direct interception on physical Wi-Fi/mobile networks
- App exclusion behavior on OEM Android builds
- DoH/DoT, client-side TCP DNS, IPv6 extension header / fragment handling
- Actual ad-blocking effectiveness in real apps and browsers
- Network handover, sleep/wake, battery/lifecycle stability

A successful JUnit run does not prove real-world blocking. The in-app DNS self-test is intended to verify the packet-processing path when run on a device.

## Latest source version

- App version: 0.7.0-dev / versionCode 13
- Package: com.guardian.adblock
- APK: intentionally not generated during ongoing development
- Source repository: https://github.com/justinhblee-prog/guardian


## Latest successful push CI — 2026-11-13

- Workflow: https://github.com/justinhblee-prog/guardian/actions/runs/43428069445
- Source commit: e681062ee2cc3a3573aa4597e2eb545420fa81e1
- Result: **success**
- Android Gradle clean + testDebugUnitTest: **BUILD SUCCESSFUL**
- Source inventory: 40 JUnit test methods across 8 suites.
- APK assemble / upload: **skipped by policy**; this was a push test run, not a release build.


## Latest internal CI after DNS route and UI hardening

- Workflow: https://github.com/justinhblee-prog/guardian/actions/runs/43429078993
- Source commit: da40d5a3d8abecbfbc7855f484e547ee097adbd6
- Android unit-test step: **success**
- APK build/upload: skipped because this is a push CI run.
- Changes covered by compilation: custom resolver route inclusion, searchable installed-app exclusion list, configurable refresh interval, startup gate until official filter cache is available, and live-tunnel requirement for DNS self-test.


## Latest successful internal CI — 2026-11-13

- Workflow: https://github.com/justinhblee-prog/guardian/actions/runs/43429911684
- Source commit: 5ef6f94e1c46d760de445d9326bda9711f265083
- Android JUnit test step: **success**
- Gradle log: **BUILD SUCCESSFUL** (43 seconds for unit tests; follow-up build step successful)
- Test inventory at current source: 9 suites / 45 test methods.
- APK build and upload: intentionally skipped by push-CI policy.

This validates source compilation and unit tests only. It does not verify the Android VPN on a physical device or real-world ad blocking.


## Latest successful CI after filter profile and DNS engine hardening

- Workflow: https://github.com/justinhblee-prog/guardian/actions/runs/43430515269
- Source commit: 33bb38ed0abae177b8b24f74890a816a273c5351
- Result: **success**
- Android JUnit: **BUILD SUCCESSFUL**
- Test source inventory: 9 suites / 44 test methods.
- APK assemble/upload: skipped by push-CI policy.
- Includes large-list suffix matching regression, selectable official filter profiles, searchable app exclusions, refresh schedule controls, and advanced direct-DNS capture option.



## Current main source — pending CI

- Latest known source changes include UTF-8 BOM handling for downloaded lists and a startup guard that requires the selected filter profile to match the cached profile.
- Regression inventory: 10 suites / 49 JUnit test methods.
- CI for these latest changes has not yet been verified in this record.
- No APK was generated; physical-device VPN and real-world blocking tests remain outstanding.


## VPN lifecycle race fix — CI pending

- Fixed the startup/stop ordering race: a stop request arriving after ACTION_START but before the startup worker begins is no longer overwritten by a worker-side reset.
- Added a dedicated cancellation gate and two deterministic regression tests for stop-before-worker and explicit restart behavior.
- Latest source inventory: 10 suites / 49 test methods. CI result not yet confirmed.


## Enabled security-list readiness guard — CI pending

- Protection startup now also requires a valid cached HaGeZi Threat Intelligence list whenever the malware/phishing option is enabled; a failed first download can no longer silently start without the selected security layer.
- Filter profile metadata is synchronously committed after atomic cache replacement so a successful update is not reported before its selected-profile marker is persisted.
- Added regression coverage for security enabled/disabled and cache present/missing.
- Latest known source test inventory: 10 suites / 50 test methods (subject to repository inventory reconciliation); latest CI result pending.


## Atomic VPN activation and security-list gate — CI pending

- Final TUN descriptor activation is now serialized with stop/revoke cancellation; a stop cannot land between the last cancellation check and publishing the active tunnel state.
- Cancellation gate tests now cover early stop, restart, cancelled commit rejection, and stop-after-commit ordering.
- Protection start also requires the selected security filter cache when the security option is enabled.
- Filter profile metadata is synchronously committed after successful atomic cache replacement.
- Current source inventory: 10 suites / 52 test methods. Latest CI pending.


## Cache integrity validation and service-side startup guard — CI pending

- Cached primary and security filter files are considered ready only when both file existence and the persisted validated block-count threshold pass.
- The VPN service independently validates selected profile, cached profile, and enabled security-list readiness before loading rules; this also protects startup paths outside the dashboard button.
- Added pure regression tests for missing files, insufficient rule counts, and invalid thresholds.
- Current source inventory: 10 suites / 53 JUnit test methods. CI pending.
