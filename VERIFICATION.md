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
| DomainFilterTest | 15 |
| Ipv4UdpTest | 6 |
| Ipv6UdpTest | 4 |
| ProtectionStateTest | 2 |
| FilterRuleParserTest | 7 |
| DnsProbeTest | 2 |
| FilterRuleComposerTest | 2 |
| DnsAddressValidatorTest | 2 |
| Total | 40 |

## Covered areas

- DNS question validation, NXDOMAIN/SERVFAIL, upstream transaction/question matching
- DNS QNAME case-insensitive response matching with exact QTYPE/QCLASS
- IPv4 UDP DNS parse, exact packet length, fragments, options
- IPv6 UDP DNS mandatory checksum and response checksum
- stale persisted VPN state and live tunnel requirement
- Adblock domain anchor, allow exception, hosts entries, badfilter, unsupported modifier rejection
- DNS self-test query construction and hostname validation
- Filter-source badfilter isolation and custom DNS IPv4/IPv6 literal validation

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
