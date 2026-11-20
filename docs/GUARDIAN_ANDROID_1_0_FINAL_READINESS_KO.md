# Guardian Android 1.0 — 출시 준비 및 검증 현황

- 기준일: 2026-11-20 (KST)
- 저장소: https://github.com/justinhblee-prog/guardian
- 작업 브랜치: `feature/android-support-center-mainline`
- 최종 판정: **출시 미완료 — 검증 및 외부 증거 필요**\n- 최신 브랜치 비교: `main` 대비 120 commits ahead / 0 behind / 31 files changed (2026-11-20 조회).

## 목표

Android 1.0에서 VPN/DNS 보호, 광고·추적 필터, 필터 무결성/갱신, 앱별 보호 제외, 라이선스 및 고객지원 기능을 안정적으로 제공하고, 사용자 소유 운영키로 서명한 설치 가능한 APK와 검증 자료를 확보한다. Android 완료 후 Windows 정식 버전을 진행한다.

## 확인된 소스 변경

- VPN/DNS 보호 시작 경로에서 필수 번들 DNS 규칙 파일 누락을 빌드 단계에서 검출하도록 Gradle 가드 추가.
- 선택 필터 캐시가 존재하더라도 이전 refresh 오류가 있으면 다음 실행에서 재시도하도록 feed별 정책 수정 및 회귀 테스트 추가.
- IPv4 UDP의 정상 non-zero checksum을 검증하는 테스트 추가, 테스트 helper의 Kotlin 범위 오류 수정.
- DNS-over-TCP 세션에서 out-of-window 세그먼트가 idle timeout을 연장하지 않도록 수정 및 회귀 테스트 추가.
- 앱별 보호 제외 저장·터널 재구성·적용/실패 상태 분리 로직 정적 검토.

## 추가 필터 무결성 정적 감사\n\n- Android signed manifest verifier: exact UTF-8 payload signature, RSA 공개키 최소 2048-bit, SHA-256 콘텐츠 해시, manifest replay/future timestamp 차단, sourceId/sourceVersion 경로 문자 제한 및 동일 sourceVersion 콘텐츠 변경 차단을 확인했습니다.\n- Signed HTTP client: HTTPS-only, 사용자정보/query/fragment 제한, redirect 정책, manifest/signature/filter 다운로드 크기 제한 및 detached signature source binding을 확인했습니다.\n- Cache: 서명 해시 기반 target/backup 복구, 크기 제한, metadata rollback 경로를 확인했습니다.\n- Distribution HTTP server: 경로 이탈, symlink parent/final component, 파일 크기, canonical signature base64 및 read-only GET 경로 방어를 확인했습니다.\n- 위 항목은 코드 정적 감사이며 Android/Node 테스트 실행 성공이나 실제 공개 배포를 의미하지 않습니다.\n\n## DNS 규칙 및 캐시 심층 감사\n\n- Parser/composer: DNS 호스트 단위로 안전하게 표현할 수 있는 anchored rule만 수용하고 URL/path/cosmetic 등 DNS 계층에서 의미가 다른 규칙은 무시해 과도한 도메인 차단을 피합니다. `badfilter`는 각 원본 안에서만 적용되며 ordinary/important 규칙 변형도 구분합니다.\n- Domain matching: suffix 경계 단위 매칭, 일반 allow 예외, important block 우선순위 및 규칙 집합의 원자적 교체 구조를 확인했습니다.\n- Cache/update: 임시 파일 fsync 후 교체, 기존 캐시 backup, metadata commit 실패 시 bytes/metadata rollback, signed hash 복구, 크기 제한, feed별 실패 재시도 및 periodic network constraint 구성을 확인했습니다.\n- 이 구간의 정적 감사에서는 추가 확정 결함을 찾지 못했습니다. 관련 테스트는 소스에 존재하지만 실제 실행은 하지 않았습니다.\n\n## VPN 복구 및 서비스 생명주기 감사\n\n- 부팅/앱 패키지 교체 시 사용자의 durable restore intent를 기준으로 복구를 요청하며, 명시적 중지는 오래된 active 플래그보다 우선합니다.\n- sticky service restart는 이전 active 힌트만으로 UI에 보호 중이라고 표시하지 않고 실제 프로세스의 live TUN 상태와 결합합니다.\n- 복구 시작 시 stale active 상태를 내리고 recovery pending으로 표시하며, `publishState`가 성공/실패 결과를 기록하고 pending 플래그를 정리합니다.\n- 서비스 종료는 사용자 명시 중지와 구분해 복구 의도를 보존합니다. 관련 경로 정적 검토에서 추가 확정 결함은 발견하지 못했습니다. 테스트는 실행하지 않았습니다.\n\n## 홈 대시보드 및 업데이트 UI 감사\n\n- 홈 화면에는 단일 보호 시작/중지 버튼을 두고, 설정 화면은 보호 상태 표시 및 기능별 정책 조정으로 역할을 분리했습니다.\n- 버튼 상태는 실제 TUN 활성, 자동 복구 대기, 호환 앱으로 인한 일시중지 상태에 따라 분기됩니다.\n- 필터 업데이트는 원자적 중복 실행 방지 플래그와 진행 중 버튼 잠금을 사용합니다.\n- APK 업데이트는 확인/다운로드 중 두 버튼 잠금, 검증된 APK가 없을 때 설치 비활성, 검증 완료 후 설치 활성 정책을 적용하며 관련 순수 정책 테스트가 존재합니다.\n- 해당 UI/정책 코드는 정적으로 확인했으며 Android 테스트는 실행하지 않았습니다.\n\n## Android 플랫폼·TUN·DNS 업스트림 감사\n\n- `minSdk 26`, `targetSdk 35`; Android 14+ 특수 용도 foreground service 권한과 VPN 서비스 subtype 메타데이터를 선언했습니다.\n- Android O 이상에서는 foreground service 시작 경로에서 알림/foreground 상태를 먼저 설정하고, 비동기 TUN 준비를 진행합니다.\n- 실제 DNS preflight 성공 후 VPN TUN을 생성하며, DNS 업스트림 소켓은 `VpnService.protect()`로 VPN 경로 밖에 연결합니다.\n- UDP/TCP/DoT/DoH 경로에 연결·읽기 타임아웃과 DNS 응답 검증이 있고, TUN 시작 취소 generation으로 중지된 이전 worker가 나중에 TUN을 커밋하지 못하게 합니다.\n- 패킷 루프 예외·종료 시 descriptor와 활성 상태를 정리하는 경로를 확인했습니다. 추가 확정 결함은 발견하지 못했습니다. 이는 정적 감사이며 Android API별 빌드/에뮬레이터/실기기 검증은 미실행입니다.\n\n## 검증 현황

| 영역 | 현재 증거 | 판정 |
|---|---|---|
| 소스 정적 감사/수정 | feature branch 소스 및 회귀 테스트 반영 | 반영 |
| Android unit/compile/lint | 새 통합 branch에서 실행 증거 없음 | 미검증 |
| License API HTTP/PostgreSQL/Admin | 통합 branch 테스트 실행 증거 없음 | 미검증 |
| API 35 emulator | 통합 branch 최신 E2E 실행 증거 없음 | 미검증 |
| 필터 배포 권리 | 예시 7개 원본의 redistributionApproved=false, 승인 참조 없음 | 차단 |
| Samsung 실기기 | Library file not found stack 및 DNS 수용 증거 없음 | 미검증 |
| Production 서명 APK | 사용자 소유 Production keystore 서명 APK 없음 | 미확보 |
| 설치/업그레이드 | 최신 Production APK로 검증한 증거 없음 | 미검증 |

## 운영 및 보안 결정

- GitHub Actions runner/billing/usage 설정은 변경하지 않는다. workflow 실행/PR 생성으로 비용을 유발하지 않는다.
- 사용자 소유 운영 개인키, 토큰, 고객 데이터는 저장소에 올리지 않는다.
- Tailscale Serve URL을 공개 고객용 endpoint로 간주하지 않는다.
- 원본 필터의 재배포 권리 확인 전에는 공개 배포하지 않는다.
- Debug 또는 임시 서명 APK를 Production APK로 표기하지 않는다.

## 다음 게이트

1. 제한 조건을 위반하지 않는 로컬/승인된 환경에서 Gradle compile, unit tests, lint 및 서버 테스트를 실행하고 원시 결과를 보존한다.
2. API 35 emulator에서 VPN/DNS, app exclusions, restore, update/upgrade 시나리오를 수행한다.
3. 필터 원본 재배포 승인 및 서명 manifest/cache rollback을 확인한다.
4. Samsung 실기기에서 시작/중지, 허용/차단 DNS, 앱 제외, 네트워크 전환 및 Library file not found 원인을 확인한다.
5. 사용자 소유 운영 서명키와 외부 고객이 접근 가능한 HTTPS endpoint가 준비된 뒤 release APK를 생성하고 package/version/signer fingerprint/SHA-256 및 신규 설치/업그레이드를 검증한다.

## 완료 선언 금지 조건

위 필수 게이트 중 하나라도 미검증 또는 차단이면 Android 1.0을 완료로 선언하지 않는다. 현재는 코드 변경이 누적된 통합 개발 브랜치이며 최종 APK 산출 단계가 아니다.
