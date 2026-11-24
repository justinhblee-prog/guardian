

## 2026-11-24 — DEX 041 논리 헤더 JVM smoke
- DEX header/container 순회 helper를 kotlinc 1.9.0/JRE 21로 분리 컴파일·실행했습니다.
- PASS 8/8: DEX 035 little-endian/reverse-endian, DEX 041 단일/복수 논리 DEX 개수, container_size 불일치, 두 번째 logical header_offset 불일치, truncated logical file, 미지원 버전 거부.
- 이는 헤더 판정 로직 smoke입니다. APK ZIP parser·전체 DEX scanner·Android Gradle/JUnit·실제 APK/SAF 검증은 미실행입니다.
## 2026-11-24 — DEX 041 논리 헤더 전체 순회 및 개수 정합성

- 이전 041 검사는 첫 컨테이너 헤더만 확인하고 ZIP 엔트리 수를 DEX 파일 수로 표시했습니다. 여러 논리 DEX가 한 컨테이너에 들어가는 경우 실제 논리 파일 수가 잘못 표시되고 후속 헤더 오프셋/크기 손상도 확인하지 못했습니다.
- 이제 container_size를 기준으로 모든 논리 헤더를 순회하며 041 magic/version, endian tag, 0x78 header_size, 각 logical file_size 범위, 공통 container_size 및 현재 위치와 일치하는 header_offset을 검증합니다. `Result.dexCount`는 ZIP DEX 엔트리 수가 아니라 검증된 논리 DEX 헤더 수를 반영합니다.
- 두 논리 DEX의 정상 container 결과 count=2, 두 번째 헤더의 잘못된 header_offset 거부 회귀 테스트를 추가했습니다. 여전히 DEX section/class/code item 검증이나 전체 bytecode verifier는 아닙니다.
- Android Gradle/JUnit·실제 APK 검증은 미실행입니다.

## 2026-11-24 — DEX 041 논리 파일 크기 경계 테스트

- DEX 041 컨테이너에서 첫 논리 DEX의 `file_size`가 물리 컨테이너보다 작은 합법적인 형식을 검증하는 fixture를 추가했습니다. 두 논리 DEX header의 container_size와 header_offset을 컨테이너 전체/각 시작 위치에 맞춰 생성합니다.
- 단일 DEX fixture 통과뿐 아니라 logical file_size < physical container_size 상황에서 현재 검사기가 유효한 컨테이너를 거부하지 않는지 확인하도록 테스트 소스를 보강했습니다. 전체 논리 헤더 순회는 여전히 정식 DEX 컨테이너 검증 범위 밖입니다.

## 2026-11-24 — DEX 041 컨테이너 헤더 지원

- 기존 DEX 검사기가 모든 버전에 header_size 0x70과 file_size=물리 파일 길이를 요구해 Android 16 실험 형식인 DEX 041을 거부할 수 있었습니다.
- 지원 버전을 035·037~041로 명시하고, 035~040은 0x70 헤더/파일 크기 규칙을, 041은 0x78 헤더와 container_size 및 최초 header_offset=0을 검증하도록 분기했습니다. 041의 file_size는 논리 DEX 범위이므로 물리 컨테이너보다 작을 수 있습니다.
- DEX 041 정상 컨테이너 헤더, 잘못된 container_size, 잘못된 header_offset 및 미지원 버전 거부 테스트를 추가했습니다. 이는 전체 DEX 컨테이너의 모든 논리 헤더/섹션을 검증하는 정식 parser가 아니며 문자열 triage 범위입니다.
- Android Gradle/JUnit 및 실 APK 검증은 미실행입니다.

## 2026-11-24 — 피싱 URL 호스트 끝점 정규화 검증

- URL 호스트를 `trimEnd('.')`로 정규화해 `example.com..`의 연속된 마지막 점이 제거되고 정상 주소로 오인될 수 있던 문제를 수정했습니다.
- DNS absolute name 표기에 해당하는 마지막 점 하나만 제거하고, 두 개 이상 연속된 끝점은 INVALID로 거부합니다.
- `example.com.` 정상 입력과 `example.com..` 거부 회귀 테스트를 추가했습니다. 분석기는 여전히 오프라인 구조 신호 검사이며 안전성 보증이나 악성 판정기가 아닙니다.

## 2026-11-24 — 퓨니코드가 아닌 percent-encoded 실행 파일 경로 탐지

- 피싱 URL 분석기가 `URI.rawPath`만 검사해 `%2Eapk`, `.%61%70%6b`처럼 percent-encoded 확장자를 놓칠 수 있던 우회 경계를 수정했습니다.
- raw path에서 최대 3회까지 제한적으로 percent decoding을 수행해 대소문자·인코딩된 실행 파일 확장자를 검사합니다. query string은 경로로 오인하지 않도록 계속 제외합니다.
- 단일/다중 인코딩된 `.apk`, 대소문자 `.exe` 탐지와 query parameter에만 `.apk`가 있는 정상 경로 비탐지 테스트를 추가했습니다. Android Gradle/JUnit 실행은 미실행입니다.

## 2026-11-24 — APK ZIP 항목 수 상한 회귀 테스트

- APK 정적 검사기의 ZIP 항목 수 10,000개 제한을 회귀 테스트로 고정했습니다. 10,001개 항목을 가진 작은 ZIP이 `LIMIT_EXCEEDED`로 종료되는지 확인합니다.
- APK 입력은 항목 수 외에도 전체 archive byte, 전체 uncompressed byte, DEX byte 상한을 각각 적용합니다. 128MiB/64MiB 대용량 한도 테스트는 기기 테스트 비용을 고려해 현재 JVM 회귀에는 추가하지 않았습니다.

## 2026-11-24 — 최대 ZIP 주석 길이 tail ring 경계 테스트

- EOCD 검증 tail ring이 최대 ZIP comment 길이 65,535바이트와 22바이트 EOCD를 담아 정확히 한 바퀴 도는 경계에서 정상 APK를 허용하는지 테스트를 추가했습니다.
- 기존 EOCD 누락/변조/trailing data 거부 테스트와 함께 최대 comment 허용 동작을 확인합니다.

## 2026-11-24 — APK ZIP 중앙 디렉터리·EOCD 종료 검증

- `ZipInputStream`은 local file header만으로 항목을 읽고 ZIP 중앙 디렉터리/EOCD가 누락된 잘린 아카이브도 끝까지 읽은 것처럼 반환할 수 있었습니다.
- 입력 스트림 끝의 최대 ZIP comment 포함 65,557바이트를 제한적으로 추적하고, EOF까지 남은 바이트를 읽은 뒤 EOCD signature/comment length·단일 디스크·중앙 디렉터리 항목 수·central directory offset/size의 EOF 직전 연속성을 확인하도록 강화했습니다. ZIP64 sentinel은 현재 APK 검사 상한과 맞지 않아 거부합니다.
- 정상 APK 픽스처 외에 EOCD 제거, comment length 변조, EOCD 뒤 trailing bytes가 있는 파일을 거부하는 테스트를 추가했습니다. Android SAF/실제 APK 통합 검증은 미실행입니다.

## 2026-11-24 — 비표준 숫자 IPv4 호스트 거부

- URL 호스트를 일반 DNS 이름으로 받아들일 수 있던 정수형·16진수형·축약형 IPv4 표현을 식별하도록 보강했습니다.
- `2130706433`, `0x7f000001`, `0x7f.1`, `127.1` 및 잘못된 dotted-quad 숫자 주소는 분석 입력 단계에서 INVALID로 거부합니다. 표준 IPv4 주소는 계속 IP_LITERAL_HOST 주의 신호로 분류합니다.
- 비표준 주소 5개 거부와 정상 IPv4 신호 테스트를 추가했습니다. 이는 브라우저·기기별 URL parser 전체를 재현한 것은 아니며 보수적인 오인 방지 규칙입니다.
- Android Gradle/JUnit·실제 링크 열기 동작은 미검증입니다.

## 2026-11-24 — 역엔디안 DEX 테스트 픽스처 정정

- 역엔디안 DEX 테스트의 endian_tag 숫자 값과 기록 바이트 순서를 혼동한 픽스처를 수정했습니다. 역엔디안 파일은 32비트 헤더 필드를 big-endian으로 기록하되, marker 바이트를 little-endian으로 읽었을 때 reverse constant가 되도록 생성합니다.
- 검사기 로직은 변경하지 않았으며 테스트 데이터의 엔디안 표현만 바로잡았습니다.

## 2026-11-24 — DEX 헤더 엔디안 판독 일치

- DEX 헤더에서 reverse-endian tag를 허용하면서 file_size/header_size는 항상 little-endian으로 읽던 불일치를 수정했습니다.
- endian tag를 먼저 판별하고 32비트 헤더 필드는 해당 바이트 순서로 읽습니다. 알 수 없는 tag는 APK 유효성 실패로 처리합니다.
- little-endian 및 reverse-endian 헤더 픽스처가 모두 검사기에서 SCANNED 상태가 되는 회귀 테스트를 추가했습니다. 이는 DEX 전체 형식/바이트코드 검증이나 악성코드 무해 판정이 아닙니다.
- Android Gradle/JUnit 및 실제 APK 파일 검증은 미실행입니다.

## 2026-11-24 — 피싱 URL 대괄호 IPv6 검증

- 대괄호 호스트를 IPv6 주소로 검증하지 않아 `[example.com]` 등 잘못된 authority가 통과할 수 있던 문제를 수정했습니다.
- 대괄호 호스트는 콜론을 포함하고 실제 IPv6 리터럴 파싱에 성공해야 하며, URL에서 허용하지 않는 zone-id 표기는 보수적으로 거부합니다.
- 정상 IPv6 + 기본 포트, 도메인 이름 대괄호, 잘못된 IPv6 압축 표기 회귀 테스트를 추가했습니다.
- Android Gradle/JUnit·실기기 URL 동작 검증은 미실행입니다.

## 2026-11-24 — APK DEX 헤더 검증 강화

- APK 내 classes*.dex를 첫 네 바이트의 매직 문자열만으로 인정하던 검증을 강화했습니다.
- DEX 고정 헤더 최소 크기, 버전 숫자/널 종료, 선언된 file_size 일치, header_size(0x70), endian tag를 확인한 뒤에만 문자열 분석을 수행합니다. 전체 DEX 검증기나 악성코드 판정으로 과장하지 않도록 범위를 명시했습니다.
- 테스트용 APK fixture를 최소 DEX 헤더 구조로 바꾸고, 짧은 가짜 DEX·잘못된 file_size·잘못된 header_size 거부 테스트를 추가했습니다.
- Android Gradle/JUnit 및 실기기 APK 선택 검증은 미실행입니다.

## 2026-11-24 — 피싱 URL 스킴별 기본 포트 판별

- 기존 분석기는 HTTP/HTTPS 모두 80·443 포트를 기본 포트로 취급해 HTTPS:80 및 HTTP:443을 일반 포트로 오인했습니다.
- URL scheme에 맞는 기본 포트만 통상 포트로 처리합니다(HTTP 80, HTTPS 443). 반대 조합과 다른 사용자 지정 포트는 주의 신호로 분류합니다.
- 두 정상 기본 포트와 두 스킴-포트 불일치 회귀 테스트를 추가했습니다. 이는 구조적 주의 신호이며 피싱 여부를 확정하지 않습니다.
- 독립 Kotlin/JVM 분석기 테스트만 반영했고 Android 통합 검증은 미실행입니다.

# Changelog

## 2026-11-24 — 피싱 URL 파서 경계 검증 보강
- 비정상 포트(1~65535 범위 밖), 과도하게 긴 IDN ASCII 변환 도메인, 숫자·점만으로 구성됐지만 유효한 IPv4가 아닌 호스트를 거부합니다.
- 낮은 점수 설명을 "주의 신호 없음"으로 오해하지 않도록 높은 위험 신호가 확인되지 않았다는 표현으로 조정했습니다.
- Kotlin/JVM URL smoke에서 비정상 포트와 잘못된 숫자형 IP 경계를 추가 확인했습니다.

## 2026-11-24 — PRO APK DEX 정적 검사 및 미리보기 UI 연결
- 사용자가 선택한 APK에서 classes*.dex를 제한된 메모리/압축 해제 크기/파일 항목 수 내에서 검사하고, 동적 코드 로딩·프로세스 실행·SMS·접근성·오버레이·기기 관리자·클립보드·앱 설치·WebView JS 브리지 API 문자열을 찾습니다.
- DEX 클래스와 메서드 문자열을 별도 확인하도록 보강했습니다. API 참조는 정상 앱에도 존재할 수 있으므로 위험 신호이며 악성 판정이 아닙니다.
- Premium 개발 미리보기에 URL 분석 입력과 APK 파일 선택/검사 UI를 연결했습니다. 개발 미리보기는 실제 PRO entitlement를 부여하지 않으며 production-ready gate는 닫혀 있습니다.
- 독립 Kotlin/JVM smoke 5개 APK 시나리오와 URL 분석 9개 시나리오 통과. Android Gradle/JUnit, 실제 SAF 파일 선택 및 실기기 검증은 미완료입니다.

## 2026-11-24 — PRO 피싱 URL 구조 분석 엔진 1차 구현
- 오프라인 URL 분석기를 추가해 HTTP 비암호화, IP literal, punycode, 혼합 문자 체계, 주소 내 사용자 정보, 로그인/인증 유도형 도메인 문구, 과도한 하위 도메인, 단축 URL, 비표준 포트, 실행 파일 확장자를 주의 신호로 분류합니다.
- 위험도는 구조적 신호 합산이며, 낮은 점수도 안전 보증이 아닙니다. 외부 평판 조회나 실시간 악성 URL DB 조회를 수행하지 않습니다.
- Kotlin/JVM smoke 9개 시나리오 통과. Android UI/PRO gate 연결, APK 코드 정적 검사, Android Gradle/JUnit은 미완료입니다.

## 2026-11-23 — Windows 조용한 DNS 실행 및 반응형 설정 탐색
- Windows DNS 어댑터 조회·변경에 사용하는 PowerShell 실행에 CREATE_NO_WINDOW 및 STARTF_USESHOWWINDOW를 적용해 보호 시작/중지 중 콘솔 창이 표시되지 않도록 수정했습니다.
- Windows 설정 카테고리는 항목 선택만으로 자동 축소되지 않으며, 사용자가 접기 버튼을 누를 때만 아이콘 레일로 전환됩니다.
- Android 휴대폰 설정은 좌우 분할/아이콘 레일을 제거하고, 전체 폭 카테고리 목록과 전체 폭 상세 화면을 순서대로 표시하도록 변경했습니다. 태블릿은 분할 레이아웃을 유지하되 카테고리 선택만으로 자동 축소하지 않습니다.
- Windows 기본 창 크기를 최대 1200×760으로 조정하고 화면 크기에 맞춰 축소되도록 변경했습니다.
- Windows PowerShell 숨김 실행과 설정 메뉴 유지 동작에 회귀 테스트를 추가했습니다. CI/에뮬레이터 검증 전에는 실행 확인으로 간주하지 않습니다.

## 2026-11-23 — 개발 버전 기준값 단일화 및 회귀 방지
- Android VPN E2E가 이전 개발 APK의 `앱 제외` 메뉴를 현재 `앱 제외 관리`와 구분하도록 접근성 트리 기반 메뉴 별칭 해석과 회귀 테스트를 추가했습니다.
- 이번 수정 전 런타임 검증은 VPN 시작 전 메뉴 탐색에서 중단됐으며, VPN 차단 동작의 통과 근거로 사용하지 않습니다.
- Android 개발 버전 계산기의 기본 기준을 마지막 개발 릴리스 `0.7.48-dev / versionCode 61`로 맞췄습니다.
- CI workflow에서 별도 버전 기준값을 중복 전달하지 않고, 계산기의 단일 기준값을 사용하도록 변경했습니다.
- 회귀 테스트가 계산기 기준에서 산출되는 다음 버전(`0.7.49 / versionCode 62`)과 Android Gradle fallback의 일치를 검사하도록 추가했습니다.
- 이 변경은 버전 기준값의 일관성을 검사하는 자동화이며, APK 빌드·실기기 설치·업데이트 검증 완료를 의미하지 않습니다.

## v0.7.10-dev — DNS fallback and upgrade verification (2026-11-16)
- Retry the next configured DNS resolver when an upstream returns a matching SERVFAIL or REFUSED response instead of treating that error packet as a usable answer.
- Require a successful A-record resolution before installing DNS-only VPN routes; the connectivity diagnostic now uses the same success criteria.
- Add regression coverage for retryable SERVFAIL/REFUSED versus a valid NXDOMAIN answer.
- Bump Android versionCode to 23 and versionName to 0.7.10-dev; CI verifies an in-place signed upgrade from v0.7.9-dev.
- CI run #651: unit tests and lint passed; emulator install/upgrade/launch verification is in progress at this changelog update.

## 2026-11-15 — Android integration
- Merge verified Android license device-release and wide-tablet dashboard changes (PR #54/#55).
- Follow-up license mutation serialization and server-revocation handling is under CI in PR #56.


## v0.7.4-dev — Filter cache rollback safety (2026-11-14)
- Replace downloaded filter caches with a backup-preserving swap; if metadata persistence fails, restore the previous known-good cache.
- Recover a cache interrupted between the backup and replacement moves before cache reads and cache-validity checks.
- Add JVM regression tests for successful replacement, metadata failure rollback, and interrupted-swap recovery.
- CI verification is running for this change; physical-device behavior remains unverified.

## v0.7.3-dev — Android protection reliability candidate (2026-11-14)
- Prevent optional security and Android-ad filter switches from changing while their cache snapshot is being downloaded and validated.
- Reapply the locked state when the Protection screen is recreated during an active filter update, and clear stale view references when switching tabs.
- Keep filter update feedback synchronized across the Protection and Filters screens.
- Automatically disable optional lists only when their first download fails and no last-known-good cache exists; preserve existing valid caches on update failure.
- CI release candidate increments Android versionCode to 16 and versionName to 0.7.3-dev.
- Limitations remain: no autonomous DNS-over-TCP retransmission timer/partial-ACK recovery, no IPv6 extension-header/IP-fragment reassembly, and no real-device acceptance evidence yet.


## v0.4.0
- Local UDP/TCP DNS development proxy with block/allow rules and bounded TTL cache.

## v0.4.1-dev — Windows protection foundation
- Added Windows DNS adapter inspection, DPAPI-protected backup, restore and explicit IPv4 loopback configuration commands.
- Added a pywin32 Windows Service host that supervises the DNS engine child process and checks the localhost TCP listener before reporting startup.
- Added a hard safety guard that refuses IPv4 activation when IPv6 DNS servers are configured.
- Added unit tests for safe import behavior, IPv6 bypass refusal, missing IPv4 adapter refusal and port-53 config generation.
- Not a complete Windows system protection release: service hosting, IPv6 listener, lifecycle recovery and real Windows acceptance testing remain outstanding.

## v0.5.0-dev — Android VPN implementation scaffold
- Added native Android Kotlin project using VpnService.
- Added IPv4 UDP DNS packet parsing and synthetic NXDOMAIN responses for embedded blocked domains.
- Added protected upstream DNS forwarding to 1.1.1.1 for non-blocked domains.
- Added foreground notification and start/stop controls.
- Added JVM unit tests for domain matching, DNS parsing, and IPv4 UDP parsing.
- Not production-ready: Android device validation, full common-filter integration, IPv6/TCP DNS, and dynamic lists remain open.

## v0.5.1-dev — Android DNS hardening
- DNS 질의 헤더를 검증해 응답 패킷, 비표준 opcode, 복수 질문을 거부합니다.
- NXDOMAIN 응답은 검증된 단일 질문만 포함하고 RD 비트를 보존합니다.
- IPv4 조각화 패킷과 잘못된 UDP 길이를 거부합니다.
- Android 단위 회귀 테스트를 추가했습니다.

## v0.6.0-dev — Common filter rules integration
- Added canonical cross-platform `shared/filter_rules.txt` rule format.
- Android loads shared block/allow rules from packaged assets.
- Windows system DNS configuration enables the same shared rule source.
- Python DNS engine validates and loads shared rules; allow rules take precedence.
- Added regression tests for shared rule loading and allowlist precedence.

## v0.6.0-dev — verification hardening (2026-11-13)
- Windows activation now requires a decryptable DPAPI backup and a live localhost DNS NXDOMAIN probe before adapter changes.
- Partial Windows adapter activation failures trigger rollback from the protected backup.
- Android shared-rule loading fails closed when no valid block rule exists; VPN startup fails closed if rules or VPN establishment fail.
- Added Windows regression coverage and platform build/verification helper scripts.
- Android/Windows physical integration and installer packaging remain release gates, not claimed as completed.


## 2026-11-13 — Android upstream DNS response validation
- Validate upstream DNS transaction ID, QR/opcode, question count, and exact question bytes before returning a response to the VPN client.
- Reject truncated replies because TCP DNS fallback is not implemented yet.
- Verify the UDP reply originates from the configured upstream resolver endpoint.
- Added Android JUnit regression coverage for mismatched/spoofed response rejection.

### Additional verification hardening
- Added regression coverage for truncated upstream DNS replies, mismatched question fields, and inconsistent IPv4/UDP lengths.
- Added a standalone Kotlin DNS-core compile/smoke-check script (8 assertions); this is supplemental and does not replace Android Gradle/JUnit or device testing.
- Added repository ignore rules and removed transient pytest cache files from the release archive.

### Windows rollback safety hardening
- Before changing adapter DNS, verify that the DPAPI backup includes a matching snapshot for every current IPv4 adapter (interface index and alias).
- Refuse activation before any `netsh` changes when a rollback snapshot is absent or incomplete.
- Added two regression tests for missing IPv4 snapshots and newly present adapters not covered by the backup.

## 2026-11-13 — Browser extension filter hardening
- Reduced custom dynamic filter rules to 4,000 and reserved 1,000 dynamic-rule slots for allowlist entries, keeping the combined total within the standard 5,000-rule quota.
- Added strict hostname-label validation and normalized/deduplicated allowlist entries.
- Added 15-second filter download timeout, 5 MB response-size limit, HTTPS redirect validation, and credential-free HTTPS URL validation.
- Reject an empty/unsupported downloaded filter before replacing active rules; the previous working rules remain active.
- Turning off the custom filter now removes only custom filter rules while preserving user allowlist rules.
- Added five Node.js regression tests for parsing, validation, rule quotas, disabling filters, and safe replacement.
- Updated the browser extension manifest version to 0.6.0.


## 2026-11-13 — Browser filter download memory-safety hardening
- Enforce the 5 MB filter-list cap during streamed reads and cancel the response immediately when exceeded, rather than buffering an oversized body before checking its size.
- Keep options-page allowlist validation consistent with background DNS-label validation.
- Add regression tests for oversized streamed responses and HTTPS-to-HTTP redirects.


## 2026-11-13 — Least-privilege browser filter permissions
- Removed broad install-time `<all_urls>` host access from the Chrome extension.
- Added optional HTTPS host permissions; the options page requests access only to the exact origin selected for the custom filter after the user clicks Update.
- The background worker verifies that permission before downloading; missing permission leaves active rules untouched.
- Reject cross-origin HTTPS redirects so a filter source cannot silently expand the extension's host access.
- Added three Node.js regression tests for optional permission scope, missing permission, and cross-origin redirect rejection.

### Security hardening — 2026-11-13
- Centralized filter URL validation in the background worker.
- Normalize filter URLs and remove fragments before saving.
- Check optional host permission before saving an enabled filter configuration, preventing a denied permission from leaving a broken enabled state.
- Added regression tests for insecure schemes, embedded credentials, and URL normalization.

## v0.6.1-dev — Android user-managed rules and status
- Added on-device custom block and allow lists to the Android app, with hostname validation, duplicate normalization, and a 1,000-entry cap per list.
- Persisted user rules locally and reload them into the active VPN service without restarting the tunnel.
- Added visible protection state and service failure messages, with an app-scoped status broadcast.
- Made rule replacement atomic by swapping one immutable block/allow snapshot; invalid rule replacements keep the previously active rules.
- Added Kotlin core smoke and JUnit regression coverage for custom rule normalization, allow precedence, and failed reload retention.
- Bumped Android app version to 0.6.1-dev (versionCode 8).
- Full Android Gradle/JUnit build, APK packaging, and physical-device validation remain unverified because this workspace has no Android SDK/Gradle installation and cannot resolve the Gradle distribution host.


## v0.6.2-dev — Android upstream failure behavior
- When an unblocked DNS query cannot obtain a valid upstream UDP response, the VPN now returns a validated DNS SERVFAIL response instead of silently dropping the query and leaving apps waiting for a timeout.
- Added a reusable validated DNS error-response builder, preserving opcode, RD and CD while setting QR/RA and clearing answer sections.
- Added JUnit and standalone Kotlin smoke regression coverage for SERVFAIL flags, section counts, and malformed-query rejection.
- Bumped Android app version to 0.6.2-dev (versionCode 9). Android Gradle/JUnit build, APK creation and physical-device verification remain pending.


## v0.6.3-dev — Android IPv6 DNS and upstream TCP fallback (2026-11-13)
- Added a dedicated IPv6 virtual DNS endpoint and /128 route alongside the existing IPv4 DNS endpoint.
- Added strict IPv6 UDP/53 parsing, mandatory UDP checksum validation, extension-header/fragment rejection, and response checksum generation.
- Added upstream DNS-over-TCP retry when a UDP response is truncated (RFC 7766 length framing, bounded connect/read timeout, transaction/question validation).
- Added standalone Kotlin regression checks for IPv6 query parsing, generated responses, and invalid checksum rejection.
- Client-side DNS-over-TCP interception, IPv6 extension-header/fragment reassembly, DoH/DoT, Android APK build, and device validation remain outstanding.

## 2026-11-13 — Android packet-loop resilience and IPv4 length validation
- Coalesced status broadcasts to at most one UI refresh per 500 ms while persisting every DNS event.
- Published a clear inactive state if the VPN packet-processing thread exits unexpectedly.
- Added explicit IPv4 TUN buffer-bound checks and required the IPv4 total-length field to match the complete packet length.
- Added a regression test for oversized caller lengths and trailing packet bytes.
- The IPv4 length hardening is awaiting a CI run on the latest main commit; do not treat it as part of the previously verified APK until that run succeeds.


## 2026-11-13 — Rules tab feedback isolation
- Separated custom-rule validation/save feedback from the dashboard VPN status view.
- Prevented status refresh broadcasts from replacing rule-editor success and validation messages.
- Verified with Android unit-test task and debug APK CI build (run 43421570913).


## v0.6.5-dev — Android lifecycle, DNS validation and release polish (2026-11-13)
- Confirm active protection only when the current app process owns a live VPN tunnel; persisted stale flags no longer make the dashboard claim protection is running after process restart.
- Show a dedicated live protection badge and disable the start action while the tunnel is active.
- Reject DNS wire QNAME labels containing literal dot bytes to prevent ambiguous conversion into dotted hostnames used for domain matching.
- Add regression tests for ambiguous DNS labels and case-insensitive QNAME response matching while requiring exact QTYPE/QCLASS.
- Add Guardian adaptive and legacy launcher icons.
- Bump Android version to 0.6.5-dev (versionCode 12).
- Strengthen CI with VPN manifest/foreground-service declaration checks, launcher asset checks, APK package identity/version validation, SHA-256 and artifact upload.
- Real-device VPN lifecycle and network-transition acceptance remain required before production release.


## v0.6.5-dev — stale protection-state recovery (2026-11-13)
- Extracted a testable protection-state guard: persisted state alone never represents a live VPN session.
- When the dashboard detects a stale active flag after process restart, it now clears the persisted active flag and stores the recovery message.
- User rule saves now request live rule reload only when the current process actually owns an active VPN tunnel; stale sessions save rules for the next start instead.
- Added two regression tests for stale persisted state and live tunnel requirements.


## v0.7.1-dev — AdGuard-style filtering and diagnostics (2026-11-13)
- Identified the previous effective-blocking gap: the built-in baseline contained only ten domains, and no downloaded AdGuard DNS filter or Adblock rule parser existed.
- Added a DNS-compatible Adblock/hosts parser for anchored domain block rules, exception rules, hosts entries, and badfilter handling. Unsupported contextual/rewriting modifiers are ignored rather than guessed.
- Added download, validation, app-private caching, safe cache replacement, and configurable 6h/12h/24h/7-day app-launch updates for selectable official DNS filter profiles.
- Added optional HaGeZi Threat Intelligence malware/phishing list.
- Added upstream DNS choices (Cloudflare, Google, Quad9, AdGuard DNS, AdGuard Family DNS, custom IPv4/IPv6 literals), installed-app VPN exclusions, live DNS activity/diagnostics, and log reset.
- Added source-isolated badfilter composition and regression tests so one filter list cannot cancel another list's matching domain rule.
- Added optional direct DNS capture for common public resolvers. Because Android VPN routes are IP-based rather than port-based, the UI exposes this as an advanced option and warns that non-DNS traffic to those resolver IPs may also be captured.
- Optimized block/allow matching to use bounded DNS-label suffix lookups instead of scanning the full downloaded list for every query.
- Added an in-process DNS rule-engine self-test for active block matching, NXDOMAIN response generation, and normal-domain allow behavior; it does not claim to validate the VPN network path.
- Moved large filter parsing and reload work off the VPN service main thread.
- Changed push CI to run tests only; final APK packaging is gated behind a manual release workflow.
- Physical-device verification and final APK are intentionally pending.


### 0.7.1-dev 추가 변경
- 앱 제외 검색, 업데이트 주기 선택, 커스텀 DNS 라우트, 필터 준비 확인을 추가했습니다.


### 0.7.1-dev — VPN activation and filter readiness hardening
- Serialized the final TUN activation commit against stop/revoke cancellation to close a check-then-activate race.
- Added deterministic regression tests for cancelled activation rejection and activation/stop ordering.
- Require the optional malware/phishing list cache before protection starts when that layer is enabled.
- Commit selected filter-profile metadata synchronously after atomic cache replacement.
- Latest source: 10 test suites / 52 JUnit methods; CI pending; no APK generated.


### 0.7.1-dev — Adblock important modifier priority
- Preserve `$important` domain blocks separately and evaluate them before allow exceptions.
- Keep important-rule behavior isolated by source and allow `$badfilter` to disable matching important blocks within that source.
- Ignore unsupported important exceptions rather than applying incorrect allow semantics.
- Include important rules in downloaded-cache validation and visible block counts.
- Add parser and composed-engine regression tests; latest source CI pending.


### CI fix — `$important,badfilter` modifier parsing
- Fixed detection of `$badfilter` when it appears after another comma-separated modifier.
- Latest Android unit-test CI passed: 59 tests completed, 0 failures (run 43433588312).


### VPN routing safety
- Make direct interception of public DNS resolver IPs opt-in because IP-prefix VPN routes can capture non-DNS traffic to those same addresses.
- Keep the self-test aligned with the safe default and display the advanced-routing side-effect warning.
- Add a regression test for the default configuration.


### Reliability and UI polish — 2026-11-13
- Restore a previously active VPN service after Android reclaims its process (`START_STICKY`), while respecting explicit stop state.
- Keep the dashboard honest during a service restart and offer a reconnect action rather than clearing the persisted restore intent.
- Replace the app-owned virtual-DNS UDP self-test (which bypasses the app's own VPN route on Android) with an accurately labelled in-process rule-engine test.
- Apply consistent dark styling to DNS/profile/update-interval selectors and avoid refreshing detached home views.
- Serialize CI per branch and cancel superseded push runs.
- Latest CI: run 43436954231 passed; 12 suites / 68 JUnit test methods.


## Latest verification update

- Explicit user stop now clears the persisted sticky-service restart flag even if VPN startup has not yet committed a TUN descriptor. Routine service cleanup without a live tunnel does not overwrite the original startup failure reason.
- Added two unit tests for this stop/start race policy.
- [GitHub Actions run 43436954231](https://github.com/justinhblee-prog/guardian/actions/runs/43436954231): **success** (Android unit tests passed; APK assembly intentionally skipped on push).
- Test inventory: 12 suites / 68 test methods.


## 0.7.1-dev — background filter refresh and lifecycle reliability

- Add WorkManager 2.12.0 periodic background filter refresh with network constraint and selectable cadence.
- Cancel scheduled refresh when automatic updates are disabled; retry transient failures with exponential backoff.
- Refresh active filter sources and reload an active VPN service after successful updates.
- Serialize cache downloads to avoid manual/background write races.
- Add interval validation tests; inventory grows to 12 suites / 68 test methods.


## Latest WorkManager verification

- [GitHub Actions run 43437792450](https://github.com/justinhblee-prog/guardian/actions/runs/43437792450): **success**
- Source commit: `5d7624fd445edadb1a1ed24a482b9e2d25f07d41`
- Android unit tests: **passed**; APK assembly was skipped because this was a push-triggered validation run.
- Test inventory: **12 suites / 68 test methods**.


## IPv6 DNS upstream presets
- Add IPv6 presets for Cloudflare, Google, Quad9 and AdGuard DNS (default and family protection).
- Include AdGuard default IPv6 resolver addresses in optional direct-DNS capture routes.
- Add regression coverage for the official IPv6 resolver literals.


### IPv6 DNS presets — CI verified
- Added IPv6 upstream presets for Cloudflare, Google, Quad9 and AdGuard DNS default/family protection.
- Added AdGuard default IPv6 addresses to optional direct-DNS capture routes and IPv6 validation regression coverage.
- [CI run 43443209122](https://github.com/justinhblee-prog/guardian/actions/runs/43443209122) passed on `f518711e559bfb8873128f82c07a1a157a5f2068`; 12 suites / 68 methods.
- APK build intentionally skipped on push.


## 2026-11-13 — Continuous Android build verification
- Every push now runs Android unit tests and assembles the debug APK, then verifies package ID, versionCode/versionName, file presence, and SHA-256.
- Push builds remain internal CI checks; only a manual release dispatch uploads the installable APK artifact.


## 2026-11-13 — Verified Android APK artifact
- Every push now runs Android unit tests, assembles the debug APK, and checks package/version identity and SHA-256.
- Published the installable `0.7.1-dev` debug APK artifact from successful run 43445545156.
- 12 Kotlin test suites / 70 JUnit methods passed.
- Physical-device acceptance remains outstanding; this artifact is a debug build, not a Play Store production-signed release.


## 0.7.2-dev — Safe UDP DNS response sizing
- Cap DNS UDP replies to 1232 bytes to respect the IPv6 minimum-MTU safe DNS payload.
- When upstream replies exceed the cap, build a standards-shaped TC response retaining the validated DNS question instead of writing an oversized IP packet to TUN.
- Add regression tests for TC/QR/RA/RD/CD flags, question preservation, and malformed query rejection.
- Client-side TCP/53 retry remains unsupported and is documented as a limitation.


## 2026-11-13 — 0.7.2-dev verified APK
- VersionCode 15 / versionName 0.7.2-dev.
- 12 Kotlin test suites / 72 JUnit tests passed.
- Debug APK assembled, package identity and SHA-256 verified, and installable artifact uploaded.
- Final CI run: https://github.com/justinhblee-prog/guardian/actions/runs/43447121533
- APK SHA-256: `a1d41cf8937b2c09f7c4ca8d0b02ddf252e95f935b1cee3ed1d485c5470f55d8`


## Unreleased — Android DNS-over-TCP/53

- Add a defensive IPv4/IPv6 TCP packet codec for DNS traffic to the virtual resolver.
- Integrate a bounded TCP session manager into the VPN packet loop.
- Add DNS-over-TCP length framing, ordered partial-stream reassembly, multi-query parsing, and segmented DNS replies.
- Apply the existing domain block/allow engine and configured upstream resolver to TCP DNS requests.
- Correct server sequence advancement after SYN and handle payload+FIN segments.
- Add regression tests for IPv4/IPv6 TCP checksums, handshake sequence numbers, DNS framing, partial-frame reassembly, and FIN cleanup.
- CI run 43452443314 passed: 16 suites / 99 JUnit tests; debug APK assembly, package identity and SHA-256 checks passed.
- TCP retransmission/loss recovery, IPv6 extension headers, IP fragmentation, DoH/DoT and physical-device validation remain outstanding.

- Improve the Protection tab with live start/stop control, quick filter switches, and navigation to filter/app exclusion settings.

- Add Android `lintDebug` to CI and fix the app-private broadcast receiver registration flag.

- Clarify that the DNS self-test validates local rules/NXDOMAIN generation only, not live resolver routing or in-app ads.

- Add selectable UDP/TCP/DoT upstream transport, provider TLS host defaults, custom TLS hostname validation, SNI and certificate hostname verification.

- Add DNS-over-HTTPS upstream transport using TLS/443 and HTTP POST binary DNS messages; parse bounded Content-Length and chunked responses, reject redirects and invalid media types.

- Fix DoH TLS-host settings visibility when reopening the settings screen with DoH already selected.

- Add an end-to-end IPv6 TCP DNS session regression test.


## 0.7.2-dev — CI verification refresh (2026-11-13)
- Re-ran Android unit tests and lint on source commit `827198814d0b4441afe72c2ddb81b6d1a41a8f19`.
- Debug APK assembly, package identity/version check, and SHA-256 verification passed in GitHub Actions run 43525073201.
- CI-built APK SHA-256: `c40229d5c3a25173092233b2e23ace19d9c3d6f831837d6d6684c20f156495ee`.
- Push workflow intentionally skipped manual-release artifact upload; this is not a new installable APK release.


## v0.7.2-dev — TCP DNS 세션 안정성 및 진단 보완 (2026-11-14)
- 이미 연결된 DNS TCP 세션에 지연 재전송된 동일 SYN이 도착해도 기존 sequence state를 보존하고 동일 SYNACK으로 응답합니다.
- 지연 SYN 이후 새로운 DNS TCP 질의가 계속 처리되는 회귀 테스트를 추가했습니다.
- 모순된 TCP 제어 플래그(SYN+FIN/RST, RST+FIN/PSH)를 거부합니다.
- Android 비공개 DNS 자동/호스트명 모드를 홈·보호 화면에서 진단해, 암호화 DNS 우회 가능성을 사용자에게 안내합니다.
- GitHub Actions run 43530335902: Android unit tests, lint, debug APK assembly, package/version and SHA-256 checks all succeeded.
- Test inventory: 17 suites / 104 JUnit test methods.
- CI artifact upload was skipped by push policy; this is not a final APK release. Device-level VPN and ad-blocking acceptance remains outstanding.


## 2026-11-14 — Filter download redirect security
- Replaced automatic HTTPS connection redirects with a bounded manual redirect policy.
- Reject HTTP downgrade redirects, embedded URL credentials, missing or malformed locations, unsupported 3xx responses, and redirect chains exceeding five hops.
- Added six unit tests for relative/absolute HTTPS redirects and rejection cases.
- GitHub Actions run 43531095663: 18 test suites / 110 test methods passed; lint, debug APK assembly, package identity and checksum checks succeeded.
- APK artifact upload remains disabled for push-triggered CI; this is build verification, not a final APK release.


## 2026-11-14 — Android version display and build configuration
- Settings now displays the app's actual Gradle `versionName` through `BuildConfig.VERSION_NAME` instead of a hard-coded version string.
- Explicitly enabled Android `BuildConfig` generation in the app module.
- Verified by GitHub Actions run 43531690119: Android unit tests, lintDebug, assembleDebug, package/version identity, and APK SHA-256 checks all passed for commit `1616c8fdd11c92c1c982c3b24c4661242433d366`.


## 2026-11-14 — DNS-over-TCP FIN retransmission reliability
- Keep a half-closed DNS-over-TCP session until the client acknowledges the server FIN, allowing a lost final response/FIN to be replayed on an exact retransmission.
- Replay cached server packets for retransmitted final client data/FIN segments, avoiding duplicate DNS resolution.
- Added regression tests for data+FIN and pure FIN retransmission paths.
- Verified by GitHub Actions run 43533452981: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, package/version checks, and APK SHA-256 generation passed. APK SHA-256: `b7a35a98f76ce20aa272358c9b0d2f5584a8ec7dd6f88308427840fbe7613b89`.
- Push workflow skipped artifact upload; no device-level ad-blocking claim is made.


## 2026-11-14 — Truthful protection status diagnostics
- Protection screen detail now reflects observed DNS request, block, forward, and failure counters instead of claiming DNS is being filtered solely because the VPN tunnel is active.
- Zero observed DNS requests are clearly reported; opportunistic/hostname Android Private DNS modes warn about possible encrypted DNS bypass.
- Added five unit tests for diagnostic state combinations. CI validation pending.


## 2026-11-14 — Protection detail refresh consistency
- Fixed the Protection tab refresh path so an active VPN session cannot replace observed DNS counters with a generic persisted service message.
- Inactive sessions continue to show specific service failure messages.
- Added two regression tests for both paths.


## 0.7.4-dev — Filter refresh retry efficiency (2026-11-14)
- Refresh each enabled filter source only when its own cache is missing/invalid or its configured update interval has elapsed.
- Avoid downloading already-successful, still-fresh lists again when WorkManager retries after an optional source failure.
- Add regression coverage for retry freshness, stale boundaries, missing metadata, future timestamps and minimum interval behavior.
- Advance Android package to versionCode 17 / versionName `0.7.4-dev`.
- CI run 43570828960 passed unit tests, lint, debug APK assembly, package identity and checksum validation. APK SHA-256: `d60df611f004d5114d9e1aaeb832a5837ce4287c5dd1b7c51c5909c8a4cfb577`. Push CI skipped artifact upload.


## 0.7.5-dev — DNS address safety and protection scope copy (2026-11-14)
- Safely handle a nullable IPv6 `hostAddress` during numeric resolver normalization.
- Clarify in the home protection-scope card that app-originated DoH/DoT bypass requests are not intercepted; this is distinct from Guardian's own encrypted upstream transports.
- CI run 43571389815 passed unit tests, lint, debug APK assembly, package identity and checksum validation.
- Package: versionCode 18 / `0.7.5-dev`; CI APK SHA-256: `f756ab65ce8d74cc7a7da45457b0cc2987b83acc83e9c993eab0a3f624492e1a`. Push CI skipped artifact upload.


## 0.7.6-dev — DNS-over-TCP resolver exception isolation (2026-11-14)
- Catch unexpected resolver callback exceptions inside the DNS TCP session manager and convert them to a DNS SERVFAIL response instead of allowing the packet loop to terminate the entire VPN service.
- Add regression coverage proving a thrown resolver exception returns SERVFAIL and the established TCP session continues to process the next DNS request.
- Package versionCode 19 / `0.7.6-dev`. CI verification pending.


## 0.7.6-dev — DNS-over-TCP resolver exception isolation (2026-11-14)
- DNS TCP session manager now catches unexpected exceptions from the resolver callback and returns a standards-shaped SERVFAIL response; a resolver exception no longer escapes to the VPN packet loop and terminates the tunnel.
- Regression test confirms SERVFAIL and successful processing of a subsequent query in the same TCP session.
- CI [43572032012](https://github.com/justinhblee-prog/guardian/actions/runs/43572032012) completed successfully: shared DNS rules and manifest checks, Android unit tests, Android lint, Debug APK assembly, package identity/version and APK checksum checks passed.
- Package: `com.guardian.adblock`, versionCode `19`, versionName `0.7.6-dev`.
- CI debug APK SHA-256: `b5ed0b9ee635eadc1ba5c337f94eed9c47767215198e2b60df5748fbf53e1e91` (about 2.9 MB). Ordinary push workflow skipped artifact upload.
- This is automated CI evidence, not physical-device validation; APK is debug-signed and not a production-signed release.


## 0.7.7-dev — Filter screen option race prevention (2026-11-14)
- The Rules/Filters screen uses CheckBox controls for optional security and Android-ad filters. Previously, an update started from that screen only disabled the corresponding Switch controls on the Protection screen, leaving the CheckBoxes interactive while the worker snapshot was downloading. This could change an option after its cache snapshot and leave an enabled filter without a validated cache.
- Track and disable both screen variants during the complete update, restore interaction afterward, and synchronize the control off when a first-time optional-list download fails.
- Package versionCode 20 / `0.7.7-dev`. CI verification pending.


## 0.7.7-dev — Filter option update-race fix (2026-11-14)
- Prevent changes to optional security and Android-app advertising filter toggles while a filter update is in progress. Both the Protection screen Switches and Filters screen CheckBoxes are disabled, including when navigating between screens during an update; controls are restored on success or failure.
- When a first-time optional list download fails and no valid cache exists, the matching control is switched off so the UI and persisted configuration remain aligned.
- CI [43572504150](https://github.com/justinhblee-prog/guardian/actions/runs/43572504150) completed successfully: unit tests, lint, debug APK assembly, package identity/version and checksum checks passed.
- Package: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`.
- CI debug APK SHA-256: `1dbfd5b33e134ad25c949e24c0750793c9d43c83ec9888230f107e93c49754c4` (about 2.9 MB). This ordinary push run skipped artifact upload.
- Final package workflow now creates a source ZIP containing source, tests, workflow and project documentation, plus a Korean verification summary alongside the installable debug APK and SHA-256 file.


## 최종 산출물 패키지 생성 완료 — 2026-11-14

- 최종 CI 실행: [43572937308](https://github.com/justinhblee-prog/guardian/actions/runs/43572937308) — 성공. Android 단위 테스트, lintDebug, assembleDebug, 패키지명·버전 확인, APK checksum, 소스 패키징 및 artifact 업로드가 모두 통과했습니다.
- 커밋: `94bab42e1a9dd7e10b61fad52f2546fc075dbdcf`
- 앱: `com.guardian.adblock`, versionCode `20`, versionName `0.7.7-dev`.
- 설치 APK: `app-debug.apk`, 3,014,104 bytes. SHA-256: `4797b47bad0c92574e0b7f9b8bf347960c46e688c95be43ee6158b2d1a4a93e7`.
- 전체 소스 ZIP: `Guardian-AdBlock-Source-v0.7.7-dev.zip` (161,340 bytes, 98 entries; Android 소스·테스트·공유 규칙·워크플로·설명서 포함).
- 검증 요약: `release/VERIFICATION_SUMMARY_KO.md`.
- GitHub Actions artifact: `Guardian-AdBlock-Android-Full-Package-v0.7.7-dev`, artifact ID `14105371888`, archive SHA-256 `d5f4555603756f5b0cf97a6725ecc5ff942e98ede9c626ef91227bfa2cf12d3f`, 만료 예정 2026-12-14.
- APK는 Android debug 서명 빌드입니다. production 서명과 실기기 VPN/DNS 라우팅·Wi-Fi/모바일 전환·실제 광고 제거 수용 테스트는 수행하지 않았습니다. DNS 수준 차단 범위 및 앱 자체 DoH/DoT 우회 등 알려진 한계는 검증 문서에 명시했습니다.


## 0.7.8-dev — TCP/IP reserved-header validation (2026-11-14)
- Hardened the DNS TCP packet parser: reject IPv4 packets with the reserved fragmentation flag set, and reject TCP headers with nonzero reserved/NS bits instead of silently accepting unsupported header semantics.
- Added regression coverage for both malformed-header cases.
- Package versionCode 21 / `0.7.8-dev`.
- CI run: https://github.com/justinhblee-prog/guardian/actions/runs/43573908206 — pending at documentation update time. Do not treat this source change as verified until that run completes successfully.
- This is parser hardening only; it does not prove physical-device VPN routing or real ad-removal effectiveness.
