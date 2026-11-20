# Guardian Android 1.0 — 출시 준비 및 검증 현황

- 기준일: 2026-11-20 (KST)
- 저장소: https://github.com/justinhblee-prog/guardian
- 작업 브랜치: `feature/android-support-center-mainline`
- 최종 판정: **출시 미완료 — 검증 및 외부 증거 필요**
- 최신 브랜치 비교: `main...feature/android-support-center-mainline` = 207 commits ahead, 0 behind (2026-11-20 확인; 이 문서 수정 커밋 전 수치).

## 목표

Android 1.0에서 VPN/DNS 보호, 광고·추적 필터, 필터 무결성/갱신, 앱별 보호 제외, 라이선스 및 고객지원 기능을 안정적으로 제공하고, 사용자 소유 운영키로 서명한 설치 가능한 APK와 검증 자료를 확보한다. Android 완료 후 Windows 정식 버전을 진행한다.

## 확인된 소스 변경

- VPN/DNS 보호 시작 경로에서 필수 번들 DNS 규칙 파일 누락을 빌드 단계에서 검출하도록 Gradle 가드 추가.
- 선택 필터 캐시가 존재하더라도 이전 refresh 오류가 있으면 다음 실행에서 재시도하도록 feed별 정책 수정 및 회귀 테스트 추가.
- IPv4 UDP의 정상 non-zero checksum을 검증하는 테스트 추가, 테스트 helper의 Kotlin 범위 오류 수정.
- DNS-over-TCP 세션에서 out-of-window 세그먼트가 idle timeout을 연장하지 않도록 수정 및 회귀 테스트 추가.
- 앱별 보호 제외 저장·터널 재구성·적용/실패 상태 분리 로직 정적 검토.

## 추가 필터 무결성 정적 감사

- Android signed manifest verifier: exact UTF-8 payload signature, RSA 공개키 최소 2048-bit, SHA-256 콘텐츠 해시, manifest replay/future timestamp 차단, sourceId/sourceVersion 경로 문자 제한 및 동일 sourceVersion 콘텐츠 변경 차단을 확인했습니다.
- Signed HTTP client: HTTPS-only, 사용자정보/query/fragment 제한, redirect 정책, manifest/signature/filter 다운로드 크기 제한 및 detached signature source binding을 확인했습니다.
- Cache: 서명 해시 기반 target/backup 복구, 크기 제한, metadata rollback 경로를 확인했습니다.
- Distribution HTTP server: 경로 이탈, symlink parent/final component, 파일 크기, canonical signature base64 및 read-only GET 경로 방어를 확인했습니다.
- 위 항목은 코드 정적 감사이며 Android/Node 테스트 실행 성공이나 실제 공개 배포를 의미하지 않습니다.

## DNS 규칙 및 캐시 심층 감사

- Parser/composer: DNS 호스트 단위로 안전하게 표현할 수 있는 anchored rule만 수용하고 URL/path/cosmetic 등 DNS 계층에서 의미가 다른 규칙은 무시해 과도한 도메인 차단을 피합니다. `badfilter`는 각 원본 안에서만 적용되며 ordinary/important 규칙 변형도 구분합니다.
- Domain matching: suffix 경계 단위 매칭, 일반 allow 예외, important block 우선순위 및 규칙 집합의 원자적 교체 구조를 확인했습니다.
- Cache/update: 임시 파일 fsync 후 교체, 기존 캐시 backup, metadata commit 실패 시 bytes/metadata rollback, signed hash 복구, 크기 제한, feed별 실패 재시도 및 periodic network constraint 구성을 확인했습니다.
- 이 구간의 정적 감사에서는 추가 확정 결함을 찾지 못했습니다. 관련 테스트는 소스에 존재하지만 실제 실행은 하지 않았습니다.

## VPN 복구 및 서비스 생명주기 감사

- 부팅/앱 패키지 교체 시 사용자의 durable restore intent를 기준으로 복구를 요청하며, 명시적 중지는 오래된 active 플래그보다 우선합니다.
- sticky service restart는 이전 active 힌트만으로 UI에 보호 중이라고 표시하지 않고 실제 프로세스의 live TUN 상태와 결합합니다.
- 복구 시작 시 stale active 상태를 내리고 recovery pending으로 표시하며, `publishState`가 성공/실패 결과를 기록하고 pending 플래그를 정리합니다.
- 서비스 종료는 사용자 명시 중지와 구분해 복구 의도를 보존합니다. 관련 경로 정적 검토에서 추가 확정 결함은 발견하지 못했습니다. 테스트는 실행하지 않았습니다.

## 홈 대시보드 및 업데이트 UI 감사

- 홈 화면에는 단일 보호 시작/중지 버튼을 두고, 설정 화면은 보호 상태 표시 및 기능별 정책 조정으로 역할을 분리했습니다.
- 버튼 상태는 실제 TUN 활성, 자동 복구 대기, 호환 앱으로 인한 일시중지 상태에 따라 분기됩니다.
- 필터 업데이트는 원자적 중복 실행 방지 플래그와 진행 중 버튼 잠금을 사용합니다.
- APK 업데이트는 확인/다운로드 중 두 버튼 잠금, 검증된 APK가 없을 때 설치 비활성, 검증 완료 후 설치 활성 정책을 적용하며 관련 순수 정책 테스트가 존재합니다.
- 해당 UI/정책 코드는 정적으로 확인했으며 Android 테스트는 실행하지 않았습니다.

## Android 플랫폼·TUN·DNS 업스트림 감사

- `minSdk 26`, `targetSdk 35`; Android 14+ 특수 용도 foreground service 권한과 VPN 서비스 subtype 메타데이터를 선언했습니다.
- Android O 이상에서는 foreground service 시작 경로에서 알림/foreground 상태를 먼저 설정하고, 비동기 TUN 준비를 진행합니다.
- 실제 DNS preflight 성공 후 VPN TUN을 생성하며, DNS 업스트림 소켓은 `VpnService.protect()`로 VPN 경로 밖에 연결합니다.
- UDP/TCP/DoT/DoH 경로에 연결·읽기 타임아웃과 DNS 응답 검증이 있고, TUN 시작 취소 generation으로 중지된 이전 worker가 나중에 TUN을 커밋하지 못하게 합니다.
- 패킷 루프 예외·종료 시 descriptor와 활성 상태를 정리하는 경로를 확인했습니다. 추가 확정 결함은 발견하지 못했습니다. 이는 정적 감사이며 Android API별 빌드/에뮬레이터/실기기 검증은 미실행입니다.

## 검증 현황

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


## 2026-11-20 사용자 보고 결함 수정

- 프리미엄 화면의 라이선스 버튼 문구를 "설정에서 라이선스 관리"로 변경했습니다. 실제 라이선스 키 입력/활성화 폼은 설정 화면 한 곳에 유지합니다.
- UDP 업스트림 연결이 실패할 때 동일 DNS 서버에 TCP/53으로 재시도하도록 했습니다. 이는 UDP/53이 제한된 일부 모바일/공용 Wi-Fi 환경에 대한 복구 경로입니다. TCP/DoT/DoH를 사용자가 선택한 경우에는 자동으로 다른 전송 방식으로 바꾸지 않습니다.
- 소스 반영은 GitHub 파일 재조회로 확인했습니다. Android 빌드/테스트와 Samsung 실기기 검증은 아직 미실행이므로 DNS 연결 성공 또는 최종 원인 해결로 판정하지 않습니다.

- **후속 UI 정리**: 프리미엄 화면에 있던 설정 이동/라이선스 활성화 CTA도 제거했습니다. 프리미엄 화면은 상태와 기능 미리보기만 표시하고, 라이선스 키 입력·활성화·갱신·기기 해제는 설정의 라이선스 관리 한 곳에서 수행합니다. 고객지원 FAQ도 설정 경로로 수정했습니다.
- **DNS 업스트림 복구 경로 확장**: UDP 모드에서 선택 서버 UDP/53 실패 시 동일 서버 TCP/53 재시도 후, 물리 네트워크의 LinkProperties에서 가져온 시스템 DNS 서버들을 차례로 시도하고 마지막에 공용 DNS fallback을 시도합니다. VPN 네트워크 자체는 시스템 DNS 후보에서 제외하고 Guardian 가상 DNS 주소도 걸러냅니다. TCP/DoT/DoH 명시 선택은 임의 변경하지 않습니다.
- **회귀 테스트 소스 추가**: DNS 전송 정책에 UDP→TCP 순서, 기기 DNS→공용 fallback 순서/중복 제거, TCP 고정, DoT/DoH downgrade 금지 테스트를 추가했습니다. 테스트 실행은 하지 않았습니다.
- **검증 한계**: GitHub 파일 재조회로 UI·DNS 후보 경로·테스트 소스 반영만 확인했습니다. Android compile/unit/lint 및 Samsung 실기기 연결은 미검증이며 DNS 문제가 해결됐다고 확정하지 않습니다.
- **최신 compare (2026-11-20 재조회)**: `main...feature/android-support-center-mainline` = 201 commits ahead, 0 behind. Actions 실행, PR 생성, merge는 하지 않았습니다.

- **DNS 진단 추가 개선**: 업스트림 후보별 실패 원인(UDP/TCP 전송 오류, 소켓 VPN 우회 실패, 타임아웃, 응답 검증/RCODE 실패)을 최대 6개까지 연결해 기기 진단에 남기도록 변경했습니다. 마지막 서버의 오류 하나만 보여 원인 파악이 어려운 문제를 줄입니다.
- **DNS 후보 탐색 제한**: 시스템 네트워크 DNS 후보를 중복 제거 후 최대 4개로 제한해, 기기에서 stale/비활성 네트워크가 여러 개 보일 때 보호 시작이 불필요하게 오래 지연되는 것을 방지합니다. 선택 DNS 및 공용 fallback 순서는 유지합니다.
- **검증 상태**: GitHub 파일 재조회로 변경 소스 확인 예정/완료. Android 빌드, 자동 테스트, 실제 단말 연결은 미실행입니다.

- **시스템 DNS 후보 정밀화**: 인터넷 기능이 없는 네트워크와 VPN 네트워크를 후보에서 제외하고, 인터페이스 스코프가 필요한 링크-로컬 주소 및 루프백/멀티캐스트/미지정 주소를 제외합니다. 실제 소켓에서 도달할 수 없는 주소를 무의미하게 순회하는 일을 줄입니다. 네트워크의 DNS 주소는 숫자 IP로 검증한 뒤 후보에 추가합니다.
- **차단 통계 정합성 정적 점검**: UDP/TCP가 동일한 `DnsQueryPolicy`를 사용하고, 차단 규칙은 업스트림 호출 전에 NXDOMAIN을 생성하며, 허용/일반 도메인은 업스트림 응답 또는 SERVFAIL로 처리됩니다. 통계는 패킷 응답 생성 경로에서 blocked/forwarded/failed를 구분해 기록합니다. 이 결과는 코드 경로 정적 확인이며 실제 패킷 검증은 아닙니다.

- **라이선스 화면 정합성 보완**: Premium 상태의 서버 준비 여부가 빌드 기본 URL만 보던 문제를 수정했습니다. 이제 Settings에서 저장한 사용자 지정 HTTPS 라이선스 API 주소도 Premium 화면에 반영됩니다.
- **상태 문구 정확도 개선**: PRO 상태 문구를 실시간 서버 조회처럼 오해하지 않도록, 저장된 서버 서명 토큰과 기기 바인딩을 앱에서 로컬 검증한 상태라고 명시했습니다. Settings에는 실제 서명 토큰 만료 시각을 표시합니다.
- **활성화 UI 단일화 재확인**: 라이선스 키 입력 및 활성화 버튼은 Settings에만 있고 Premium에는 상태·기능 설명만 남아 있습니다.

- **실시간 라이선스 상태 확인 UI 추가**: 기존 활성/만료 서명 토큰이 있는 경우 Settings에서 서버의 현재 활성 상태와 기기 등록 상태를 직접 확인할 수 있습니다. 서버가 `free`를 반환하면 로컬 entitlement를 폐기하고 화면을 갱신하며, 네트워크/API 오류는 로컬 토큰을 임의로 삭제하지 않고 오류를 표시합니다.
- **라이선스 활성화 단일 위치 유지**: 키 입력/활성화는 Settings에만 존재하며, Premium 화면은 구독 상태와 만료 정보만 표시합니다.

- **Samsung 앱 선택창 가독성 수정**: VPN 자동 일시중지 앱 선택창은 기본 AlertDialog의 밝은 흰색 목록 배경 위에 흰색 앱 이름과 흰색 미선택 체크 표시를 그려 설치 앱 목록이 사실상 보이지 않는 문제가 있었습니다. 앱 이름을 짙은 남색으로 변경하고 미선택 체크 표시를 중간 회색으로 조정했으며, 선택 상태의 강조색은 기존 녹색을 유지했습니다.
- **검증 범위**: 변경된 색상 속성은 저장소 소스 재조회로 확인했습니다. Samsung 실기기 화면 재검증 및 Android 빌드는 아직 수행하지 않았습니다.

- **Android 설정 메뉴 재구성**: 하단 내비게이션을 홈 / 보호 / 앱 제외 / 활동 / 설정의 5개로 정리하고 별도 프리미엄 하단 메뉴를 제거했습니다.
- **설정 카테고리 추가**: 일반, 필터링, 라이선스 및 프리미엄, 고객 지원, 정보, 종료 항목을 설정 첫 화면에 배치했습니다. 필터링은 보호·DNS 설정 또는 사용자 규칙으로 이동하고, 라이선스 및 프리미엄은 기존 서버 서명 entitlement·활성화·갱신 화면으로 연결됩니다. 고객 지원은 FAQ·문의 접수·문의 조회로 연결됩니다.
- **중복 UI 정리**: 기존 설정 상세 영역에 중복 노출되던 고객지원 센터 카드를 제거했습니다. 프리미엄 전용 하단 탭 없이 설정 카테고리에서만 진입하도록 변경했습니다.
- **검증 범위**: 변경 파일 재조회로 카테고리·하단 탭·라이선스 화면 연결을 확인했습니다. Android 빌드, 앱 실행, Samsung 실기기 UI 검증은 아직 미실행입니다.

- **일반 설정 전용 화면 분리**: 설정 첫 화면의 일반 항목이 고정 스크롤 위치로 이동하던 임시 동작을 제거하고 `settings-general` 하위 화면으로 분리했습니다. 하위 화면에서 설정 카테고리로 돌아갈 수 있습니다. 기존 수동 업데이트, DNS 세부 설정, 호환성 설정 등은 기존 동작을 유지한 채 일반 화면에 배치했습니다.
- **정보 메뉴 실사용 연결**: 앱 버전/패키지 정보, 제3자 필터 라이선스 고지, Guardian 저장소, GNU GPL-3.0 및 MPL-2.0 공식 라이선스 링크를 선택할 수 있도록 연결했습니다. 제3자 필터의 상용 재배포 승인이 완료됐다는 의미가 아니며, 기존 고지 문서의 미승인 상태를 그대로 안내합니다.
- **라이선스 FAQ 경로 정정**: FAQ의 활성화 안내를 새 설정 분류인 설정 → 라이선스 및 프리미엄으로 변경했습니다.
- **검증 범위**: GitHub에서 수정 소스를 재조회해 화면 분기·뒤로가기·정보 링크·FAQ 문구를 확인했습니다. Android 컴파일 및 실기기 실행은 미실행입니다.

- **설정 하위 화면 정리**: 필터링과 고객 지원도 임시 팝업 목록에서 전용 하위 화면으로 분리했습니다. 필터링 하위 화면은 보호/DNS 설정, 사용자 필터 규칙, 앱별 보호 제외로 이동합니다. 고객 지원 하위 화면은 FAQ, 문의 접수, 문의 답변 조회로 이동합니다.
- **메뉴 이동 구조**: 일반·필터링·고객 지원은 각각 전용 설정 하위 화면이며 각 화면에 설정으로 돌아가는 항목을 제공합니다. 라이선스 및 프리미엄은 기존 PRO/라이선스 화면으로 연결됩니다.
- **정보 링크**: 앱 버전·패키지, 제3자 필터 라이선스 고지, Guardian 저장소, GPL-3.0 및 MPL-2.0 공식 페이지 연결을 추가했습니다. 필터 재배포 법률 승인은 여전히 미완료입니다.
- **검증 한계**: 소스 재조회로 화면 라우팅과 링크 문자열을 확인했습니다. Android 빌드·런타임·Samsung 실기기 검증은 수행하지 않았습니다.

- **최종 메뉴 구조 확인**: 하단은 홈·보호·앱 제외·활동·설정으로 유지합니다. 설정 첫 화면에는 일반·필터링·라이선스 및 프리미엄·고객 지원·정보·종료가 있습니다. 일반·필터링·고객 지원은 각각 전용 하위 화면과 설정 복귀 동작을 제공합니다.
- **정보 화면 문자열 수정**: 앱 버전/패키지 표시는 실제 줄바꿈으로 출력되도록 정리했습니다. 제3자 고지와 GPL-3.0/MPL-2.0 공식 링크는 외부 브라우저로 엽니다.

- **라이선스/프리미엄 설정 통합 보완**: 설정의 '라이선스 및 프리미엄' 항목이 프리미엄 소개 화면만 열고 실제 라이선스 입력 폼은 일반 설정 하단에 남아 있던 경로 불일치를 수정했습니다. 라이선스·프리미엄 단일 하위 화면에 기존 PRO 소개/상태와 실제 라이선스 활성화·서버 상태 확인·갱신·기기 해제를 함께 배치했고, 일반 설정에서는 라이선스 폼을 제거했습니다.
- **라이선스 동작 후 화면 유지**: 활성화·갱신·기기 해제 및 서버 상태 확인 결과 화면이 통합 라이선스 페이지를 유지하도록 수정했습니다.
- **하위 화면 뒤로가기**: 일반·필터링·고객 지원·라이선스 하위 화면에서 Android 뒤로가기를 누르면 설정 목록으로 복귀하도록 정리했습니다. 프리미엄 내부 화면은 통합 라이선스 화면으로 돌아갑니다.
- **검증 현황 재확인**: GitHub Actions의 해당 개발 브랜치 workflow run 조회 결과 0건입니다. Android 빌드·unit test·lint·APK 생성이 수행된 기록이 없으며, Samsung 실기기 접근/실행도 이 세션에서 제공되지 않습니다. 수동 실행 전용 병렬 검증 workflow는 workflow_dispatch만 허용하고, Android APK workflow에는 main push/PR 자동 실행 조건이 있으므로 feature 브랜치 직접 push로 테스트를 유발하지 않았습니다.
- **노트북 라이선스 운영 준비 구분**: 저장소의 Windows 운영 안내에 따르면 노트북에서 운영 공개키/API/DB/개인키를 유지하고, Android Debug APK는 노트북에서 공개키를 주입해 빌드합니다. 정식 Release signing은 별도 keystore 및 GitHub secret 등록 스크립트가 필요하며, 전체 운영 설정은 실제 Tailscale /ready와 필터 재배포 승인 증빙을 검사합니다. 현재 세션은 노트북 파일시스템·Docker·DPAPI·keytool·gh CLI에 접속할 수 없어 해당 작업 완료 여부를 실측할 수 없습니다. 비밀키를 채팅이나 저장소로 옮기지 않습니다.


## 2026-11-20 Windows 라이선스 설치 스크립트 보완

- 기존 DB migration 스크립트가 고객 디렉터리 migration `004_customers.sql`을 누락하던 문제를 수정했습니다.
- 적용 순서는 002 → 003 → 004이며, 완료 조건은 `api_rate_limit_buckets`, `support_tickets`, `customers` 테이블 및 `licenses.customer_id/order_ref` 컬럼 확인입니다.
- PostgreSQL integration test에도 004 반복 적용 및 고객 스키마 assertion을 추가했습니다.
- 변경 소스는 GitHub feature branch에 반영했습니다. 노트북 DB에서의 실제 적용·API 준비 상태·고객 API 검증은 미실행이며, 기존 데이터/키를 건드리지 않았습니다.
- Android compile/test/lint/APK, emulator 화면 및 Samsung 실기기 검증도 아직 실행 증거가 없습니다. Actions workflow는 실행하지 않았습니다.


## 2026-11-20 Android 실기기 설치/화면 캡처 자동화 추가

- Windows `Build-GuardianAndroid-Debug.ps1`에 선택적 `-InstallToConnectedDevice` 옵션을 추가했습니다.
- 기본 빌드 동작은 기존대로 단위 테스트 → Lint → Debug APK 조립 → SHA-256 생성입니다.
- 옵션 사용 시 ADB 연결 기기가 정확히 1대인지 확인하고, 제조사/모델/Android 버전을 읽은 뒤 APK 설치, `MainActivity` 실행, 메인 화면 PNG 캡처, 기기 검증 텍스트 보고서를 `%LOCALAPPDATA%\\Guardian\\outputs\\DeviceCapture-*`에 저장합니다. Samsung 외 기기면 제조사 경고를 기록합니다.
- 캡처는 앱 시작/화면 렌더링 확인이며 VPN·DNS 차단·라이선스·결제 기능 E2E를 대체하지 않는다고 보고서에 명시했습니다.
- Windows README에 실행 명령을 추가했습니다. 실제 빌드/설치/캡처는 노트북과 USB 연결 기기에 접근할 수 없어 아직 실행되지 않았습니다.
- GitHub Actions 실행/PR/merge는 수행하지 않았습니다.


## 2026-11-20 Android 실기기 자동화 스크립트 자체 결함 수정

- 최신 feature branch의 `Build-GuardianAndroid-Debug.ps1`를 재검토하던 중 ADB 기기 목록 필터의 정규식 문자열이 줄바꿈으로 끊겨 PowerShell 구문 오류를 일으킬 수 있는 확정 결함을 발견했습니다.
- 해당 필터를 한 줄 정규식(`^\S+\s+device(?:\s|$)`)으로 수정하고, 중복된 저장소 루트 경로 계산을 제거했습니다.
- GitHub에 반영한 커밋: `fc750a767853fd51df6072d6e063461bb87fe152`. 파일 재조회로 수정된 한 줄과 기존 잘못된 줄바꿈 패턴의 제거를 확인했습니다.
- 이 검증은 저장소 소스 문자열 점검입니다. PowerShell Parser 실행, 노트북 Gradle 빌드, ADB 기기 연결 및 Samsung 화면 캡처는 현재 실행 환경에서 수행하지 않았습니다.
- Android 프로젝트에 Gradle Wrapper 파일이 없고 빌드 스크립트가 시스템 Gradle 8.9 설치를 요구하는 점도 확인했습니다. 재현 가능한 빌드를 위해 Wrapper 배포 파일 확보/추가가 필요하지만, 현재 도구에서는 바이너리 wrapper JAR을 안전하게 생성·검증할 수 없어 임의 파일을 추가하지 않았습니다.


## 2026-11-20 ADB 실기기 검증 단계 강화

- 자동 설치 옵션에서 기기 제조사/모델/Android 버전을 각각 독립적으로 확인해 개별 ADB 오류가 뒤이은 명령 성공으로 가려지지 않도록 수정했습니다.
- 앱 실행은 `am start -W` 결과의 `Status: ok`를 확인하고, 5초 후 앱 프로세스가 유지되는지 `pidof` 및 `ps -A` 대체 경로로 확인하도록 강화했습니다.
- 화면 캡처를 가져온 뒤 PNG 시그니처를 검사하고, 성공/실패와 관계없이 기기 임시 캡처 파일을 제거하도록 변경했습니다.
- 스크립트 변경 커밋: `125001ea6707367bc188d1e27c7e4e6001b7c78e`, 프로세스 fallback 정규식 보정: `56171e70edcbcd99599227da65a969ae25ee5078`.
- GitHub에서 수정된 소스를 재조회해 각 검증 단계와 정규식을 확인했습니다. PowerShell 실행, 실제 APK 빌드, Samsung 설치/실행/화면 캡처는 이 환경에서 실행하지 않았습니다.

## 2026-11-20 로컬 Android 빌드 증거 수집 보강

- 루트 BUILD_ANDROID.ps1을 보강했습니다. 기존에는 Gradle test/assemble 실행과 APK 존재 여부만 확인했으나, 이제 JDK 17/21, Android SDK Platform 35, Build Tools 34.0.0, Gradle 8.9를 사전 확인합니다.
- 빌드 단계에서 clean, testDebugUnitTest, lintDebug, assembleDebug를 순서대로 실행하고 각 실행 로그를 보존합니다. 실패 시 해당 로그 경로와 종료 코드로 중단합니다.
- 산출 APK의 패키지 ID, versionCode, versionName을 aapt dump badging으로 검증하고, 비어 있지 않은 APK를 %LOCALAPPDATA%\Guardian\outputs\Android-Debug-<timestamp>에 복사합니다.
- 해당 폴더에 Gradle 버전 출력, 전체 빌드 로그, APK SHA-256 및 검증 요약을 함께 저장합니다. 단말 검증은 이 스크립트가 수행하지 않았다고 명시합니다.
- 기본 기대 버전은 현재 앱 Gradle 설정과 병렬 실행 문서에 기록된 0.7.30-dev / 43이며, GUARDIAN_VERSION_NAME 및 GUARDIAN_VERSION_CODE 환경 변수로 명시적 변경이 가능합니다.
- GitHub 브랜치 파일 재조회로 스크립트 반영을 확인했습니다. 이 세션에는 Windows PowerShell, Android SDK 및 Gradle이 없어 PowerShell 파서 검사, 실제 Gradle 실행, APK 생성은 아직 수행하지 않았습니다. 따라서 이 수정은 빌드 성공 증거가 아니라 로컬 실행 시 증거를 빠짐없이 수집하기 위한 준비입니다.

## 2026-11-20 보호 시작 및 필터 초기 상태 심층 점검

- 앱별 보호 제외는 SharedPreferences 저장 후 `ACTION_RECONFIGURE_EXCLUDED_APPS`로 기존 TUN을 종료하고 새 VPN Builder에 `addDisallowedApplication()`을 적용하는 구조입니다. 적용된 패키지와 Android가 거부했거나 설치되지 않은 패키지를 별도 저장합니다. 이 확인은 코드 정적 추적이며 Samsung의 실제 패키지별 라우팅 검증은 아닙니다.
- 번들 `shared/filter_rules.txt`에는 현재 10개의 차단 도메인만 있습니다. 이 파일은 빌드 시 에셋 포함 여부를 검사하는 최소 복구용 기준 규칙이며, AdGuard 권장 프로필(최소 1,000개 규칙)이나 활성화한 선택 필터 캐시를 대체하는 충분한 상용 필터 목록이 아닙니다.
- 첫 실행에서 기본/활성화 선택 필터 캐시가 없으면 `checkStartupFilterReadiness()`가 필터 업데이트를 요청합니다. 자동 주기 업데이트 설정이 꺼져 있어도 초기 캐시 누락은 별도 복구 조건입니다. 다운로드가 실패하면 기본 규칙만으로 VPN이 시작될 수 있으므로, VPN 연결 성공과 광고 차단 목록의 충분한 준비 상태를 동일하게 취급하면 안 됩니다.
- `Library file not found`라는 문구는 현재 점검한 `MainActivity`, `GuardianVpnService`, 필터 파서/로더 경로에서 직접 생성되는 메시지로 확인되지 않았습니다. 현재 저장소 코드만으로 원인을 확정할 수 없으며, Samsung 단말의 전체 오류 스택과 비개인정보 진단 보고서가 있어야 해당 호출 지점을 특정할 수 있습니다.
- 이 점검은 코드 정적 분석입니다. 필터 URL 다운로드 성공, 실기기 DNS 차단, 앱별 제외 라우팅, Android 빌드 및 테스트 실행을 의미하지 않습니다.

## 2026-11-20 필터 준비 상태 표시 정합성 보완

- 기존 VPN 상태는 선택 필터가 준비되지 않은 경우에도 '기본 보호 규칙으로 시작'만 표시해 실제 내장 규칙 규모가 보이지 않았습니다. 현재 번들 DNS 기준 규칙은 10개입니다.
- VPN 규칙 로딩 시 실제 파싱된 번들 차단 규칙 수를 계산하고, 선택 필터가 빠진 경우 '기본 규칙 N개만 우선 적용 중' 및 누락된 필터 이름을 상태에 표시하도록 수정했습니다. 선택 필터가 준비된 경우에도 기본 규칙 수를 함께 표시합니다.
- 사용자 규칙 재적용 경로도 새 준비 상태 문자열을 인식하도록 맞췄습니다. 상태 표시만 변경하며 VPN 시작 게이트나 차단 정책을 느슨하게 변경하지 않았습니다.
- 순수 정책 함수의 회귀 테스트 소스를 추가했습니다. 테스트 실행은 아직 하지 않았습니다.
- 반영 커밋: `a731f2a`, `e08712b`, `a64aa19`, `7a13ab4`, 문서 `a7b974a`. GitHub 파일 재조회로 소스·테스트·문서 반영을 확인했습니다.


## 2026-11-20 선택형 필터 최초 다운로드 실패 복구 수정

- 최초 다운로드에 실패한 선택형 보안 / Android 앱 광고 필터를 자동 비활성화하지 않도록 변경했다. 설정은 활성 상태로 유지하고, 캐시가 없으면 VPN 시작 시 해당 필터를 미준비 항목으로 표시한다.
- 백그라운드 업데이트에서 선택형 필터 다운로드가 실패하면 캐시 유무와 관계없이 오류를 보존하고 `Result.retry()`를 반환한다. 다음 실행은 저장된 오류를 기준으로 즉시 재시도 대상으로 판정한다.
- 설정 화면의 오류 요약은 필터 활성 여부와 관계없이 보안 / Android 앱 광고 오류 키를 읽어 표시한다. 따라서 오류가 사용자에게 숨겨지지 않는다.
- 회귀 테스트에 캐시가 없는 최초 다운로드 실패 후 재시도 대상 판정을 추가했다.
- GitHub 원격 소스 재조회로 변경 내용을 확인했다. 이 수정은 정적 소스 확인이며 Gradle 단위 테스트, 컴파일, APK, Samsung 실기기 검증은 아직 실행되지 않았다.


## 2026-11-20 manifest anti-rollback 동시성 및 내구성 보완

- 필터 manifest timestamp 저장소는 다운로드 경로와 캐시 복구 경로가 서로 다른 인스턴스를 생성할 수 있습니다. 기존 인스턴스별 `@Synchronized`만으로는 동일 properties 파일의 read-modify-write가 직렬화되지 않아, 동시에 서로 다른 source의 timestamp를 기록할 때 한쪽 갱신이 유실될 수 있었습니다.
- 모든 `ManifestTimestampStore` 인스턴스가 공유하는 프로세스 잠금으로 조회·기록·복구를 직렬화했습니다. 기록뿐 아니라 rollback/restore 저장에도 `FileDescriptor.sync()`를 적용해 임시 파일 내용을 교체 전에 디스크로 flush하도록 맞췄습니다.
- 별도 인스턴스 4개가 4개 source timestamp를 병렬 갱신하는 회귀 테스트를 추가했습니다.
- GitHub 소스 반영 커밋: `73cc83c888f53b381a32c645d860266062c6efa9`, 회귀 테스트: `903d36c3ab2ed1cf39c9a64f44c9f665ed3b7d39`. 현재 compare는 main 대비 217 commits ahead / 0 behind입니다.
- Android Gradle/JUnit 실행은 아직 하지 않았습니다. 이 변경은 소스 반영 및 재조회 수준이며 실제 컴파일·테스트 성공으로 간주하지 않습니다.


## 2026-11-20 서명 manifest timestamp ledger 손상 복구 보완

- 캐시 복구 중 anti-rollback timestamp ledger의 특정 source 값이 손상되면, 기존 코드는 해당 캐시와 manifest 메타데이터를 폐기하면서 timestamp ledger의 손상 값은 남겨 이후 업데이트도 계속 실패할 수 있었습니다.
- 캐시 복구 시작 시 SharedPreferences에 커밋된 마지막 manifest 생성 시각을 보존하고, 캐시 무결성 오류로 해당 세대를 폐기할 때도 그 시각으로 ledger를 복구하도록 수정했습니다. 손상된 source 값만 교체하며 다른 필터 source의 timestamp는 유지합니다. 저장된 committed timestamp가 없으면 ledger를 임의로 초기화하지 않습니다.
- 회귀 테스트: 손상된 source timestamp를 복구하면서 다른 source의 timestamp가 유지되는지 확인하는 테스트를 추가했습니다.
- GitHub 반영 커밋: `55d0d79a4284d4e9a424728e1bb5d2da6ef093fb`, 테스트 커밋: `7c35f0a905810857de5bb885d2f324942ba22949`.
- Android Gradle/JUnit 실행은 아직 하지 않았으며, 소스 재조회 정적 검증만 수행합니다.

## 2026-11-20 manifest timestamp ledger의 malformed Properties 복구 및 JVM 실행 검증

- ManifestTimestampStore는 정상적인 숫자 값 손상은 복구할 수 있었지만, Properties 파일에 잘못된 Unicode escape가 들어가면 Properties.load()가 IllegalArgumentException을 던졌습니다. 이 예외는 기존 캐시 복구 분기에서 처리되지 않아 손상 ledger가 복구되지 않는 결함이었습니다.
- 엄격 조회/기록은 malformed Properties를 IllegalStateException으로 변환해 fail-closed 동작을 유지합니다. 복구 경로만 파일을 줄 단위로 검사해 이 클래스가 기록하는 ASCII sourceId/숫자 timestamp 형식의 정상 항목을 살리고, 손상된 행은 제외한 뒤 대상 source timestamp를 복원합니다.
- 회귀 테스트는 malformed Unicode escape가 포함된 파일에서 정상 source timestamp가 보존되고 손상 source만 복구되는지 확인합니다. 기존 corruption fixture의 줄바꿈/escape도 실제 별도 행을 만들도록 수정했습니다.
- 독립 JVM 검증: JDK 21 + kotlinc로 ManifestTimestampStore를 컴파일하고 실행했습니다. 8개 독립 source × 250회 병렬 기록, malformed Unicode ledger 복구 및 다른 source 보존, 동일/과거 timestamp 거부와 새 timestamp 허용, sourceId 검증을 모두 통과했습니다.
- 추가 JVM 검증: FilterCacheStore를 별도 컴파일해 metadata commit 실패 시 기존 캐시 바이트 복원, 교체 중단 후 백업 복구, 서명 해시 불일치 시 커밋된 백업 선택을 실행했고 모두 통과했습니다.
- 검증 범위 제한: 위 실행은 저장소의 클래스 로직을 분리한 JVM 검증입니다. Android Gradle 빌드, Android JUnit 전체 실행, APK 생성/설치, Samsung 실기기 VPN·DNS 차단 검증을 대체하지 않습니다. GitHub Actions는 실행하지 않았습니다.
## 2026-11-20 선택 필터 프로필 변경 후 초기 갱신 누락 수정

- 시작 시 기존 캐시 파일이 있어도 캐시의 프로필과 사용자가 선택한 프로필이 다르면 새 프로필의 캐시를 준비해야 합니다.
- 기존 로직은 프로필 변경을 예약 업데이트 신호에만 포함해 자동 업데이트가 꺼진 경우 시작 시 새 프로필 다운로드가 누락될 수 있었습니다.
- 선택 프로필 불일치를 초기 캐시 누락과 동일하게 취급하도록 수정했습니다. 자동 업데이트 설정은 예약 갱신만 제어하고, 선택한 프로필 적용에 필요한 최초 다운로드는 계속 수행합니다.
- 회귀 테스트 2건(프로필 변경 시 갱신, 변경이 없을 때 비예약 갱신 방지)을 추가했습니다. 테스트 실행은 아직 하지 않았습니다.
- 반영 커밋: `f9da1d3`, `d3de3e6`, `2790e73`.

## 2026-11-20 자동 업데이트 OFF 상태의 실패 복구 누락 수정

- 수동/최초 필터 업데이트에서 실패한 선택 필터는 화면에 '재시도 대기'로 표시되지만, 기존 주기 Worker가 자동 업데이트 OFF일 때 즉시 성공 종료하여 실제 재시도가 실행되지 않는 문제가 있었습니다.
- 주기 업데이트와 별개인 고유 OneTimeWork 요청(`guardian_filter_failure_recovery`)을 추가했습니다. 네트워크 연결 제약과 지수형 재시도를 적용하고, 실패 복구 요청만 자동 업데이트 OFF 상태에서도 실행되도록 Worker 입력 플래그로 분리했습니다.
- 수동 업데이트에서 기본 목록 실패 또는 활성화된 선택 목록 실패가 발생하면 복구 작업을 예약합니다. 정상적으로 갱신된 기본 목록은 기존 개별 소스별 시각/오류 상태를 이용해 불필요하게 다시 다운로드하지 않습니다.
- 반영 커밋: `7483ad2`, `edef90e`, `2fd49ce`. 소스 재조회로 예약 경로와 Worker의 강제 복구 분기를 확인했습니다. WorkManager 런타임/Android 빌드 테스트는 아직 실행하지 않았습니다.

- 후속 보완: 자동 업데이트 OFF/ON 및 강제 복구 요청 여부를 분리한 `FilterRecoveryPolicy`를 추출하고, 복구 Worker 진입 조건에 대한 단위 테스트 3건을 추가했습니다. 반영 커밋 `cf83400`, `011d7e5`, `47deb21`.
- 최종 소스 재조회에서 수동 기본/선택 필터 실패 양쪽의 복구 예약 호출 2곳, Worker 강제 복구 분기, 회귀 테스트 3건을 확인했습니다. `main...feature/android-support-center-mainline` 비교는 238 ahead, 0 behind입니다. Android Gradle/JUnit/WorkManager 런타임 실행은 아직 수행하지 않았습니다.

## 2026-11-20 백그라운드 필터 상태의 화면 간 동기화 수정

- 백그라운드 필터 업데이트 Worker가 완료 브로드캐스트를 보내도 기존 Activity 수신기는 설정 화면의 `filterStatusText`만 갱신했습니다. 홈 및 보호 화면의 상태 문구는 이전 성공/실패 결과를 계속 보여줄 수 있었습니다.
- 브로드캐스트 수신 시 공통 `setFilterUpdateStatus(filterStatusDescription())`를 호출해 홈·보호·설정 화면을 동일한 저장 상태로 갱신하도록 수정했습니다.
- Activity가 중지된 동안 Worker가 완료되면 브로드캐스트 수신기가 등록되어 있지 않습니다. `onStart()`에서도 공통 상태 갱신 함수를 호출해 화면 재진입 시 최신 오류·캐시 상태를 복원하도록 수정했습니다.
- GitHub 원격 파일 재조회로 두 경로의 변경을 확인했습니다. 반영 커밋: `7019446c462967f3b461096c858e4d6c3ade10ad`, `32a7e1fdf9d24abd3705d4661f79d769998d6603`.
- 현재 브랜치 비교: `main` 대비 242 commits ahead / 0 behind. Android Gradle 컴파일, JUnit, APK, UI 런타임 및 Samsung 실기기 검증은 아직 수행되지 않았습니다.

## 2026-11-20 필터 캐시 읽기/교체 동시성 보완

- 기존 캐시 교체는 FilterCacheStore 내부에서 임시 파일 → 기존 파일 백업 → 새 파일 이동 순서로 실행됩니다. 교체 함수는 동기화되어 있었지만, 호출자가 recover() 반환 후 파일을 여는 사이 다른 스레드가 교체를 시작할 수 있어 짧은 파일 부재 구간과 읽기 실패 가능성이 남아 있었습니다.
- FilterCacheStore.withLock을 추가해 recoverCache()의 검증·손상 파일 정리·복구 전체, 존재/유효성 확인, 전체 파일 읽기 및 파싱을 교체와 동일한 객체 잠금 안에서 수행하도록 변경했습니다. 서명 캐시의 손상 파일 삭제 경로도 읽기/교체와 직렬화되어 복구 중인 파일을 다른 스레드가 읽거나 제거하지 않습니다. 따라서 파일을 실제로 읽는 동안에는 replace()가 기존 파일을 백업 경로로 이동할 수 없습니다.
- VPN 규칙 재로드에서도 loadConfiguredRules() 전체를 동기화했습니다. WorkManager 주기 갱신, 실패 복구 Worker, 수동 갱신이 연이어 재적용을 요청하더라도 이전 스냅샷을 늦게 읽은 재로드가 최신 규칙 교체 뒤에 도착해 최신 메모리 규칙을 덮어쓰는 순서 경합을 방지합니다.
- FilterCacheStoreTest.cacheReplacementWaitsUntilReaderFinishesItsSnapshot 회귀 테스트를 추가해 reader가 잠금을 보유한 동안 writer가 완료되지 않고 reader 종료 후 교체가 진행되는지 검증하도록 했습니다.
- 소스 반영 커밋: b21de112851ffe2718d4ae45648fded952b2e7e4, 380b897d73af62f3a0a6b6260c9fa85044346965, 013e7a9479aa1e401f785b9ffcbf44a9b897c2f7, f2069febb3647f14876869b8b7c62adc688122ff, 3d8fce78fe550653d2dce1d83a8dde6c7208cd62.
- GitHub 파일 재조회로 잠금 적용 위치, VPN reload 동기화, 회귀 테스트 존재를 확인했습니다. Android Gradle/JUnit 실행, APK, 실기기 검증은 아직 하지 않았으며 GitHub Actions도 실행하지 않았습니다.
- 브랜치 비교 상태: main 대비 ahead / 0 behind. 정확한 ahead 수는 후속 소스·문서 커밋으로 변하므로 GitHub compare 결과를 기준으로 확인합니다.

## 2026-11-20 필터 갱신 전체 트랜잭션 직렬화

- 기존에는 WorkManager 주기 갱신과 실패 복구 작업이 서로 다른 고유 작업 이름을 사용했고, Activity 수동 갱신은 별도 스레드에서 실행됐습니다. 각 파일 다운로드는 FilterListManager 내부 동기화로 보호했지만, 기본·보안·Android 광고 목록을 순서대로 처리하는 전체 갱신 흐름과 오류/성공 SharedPreferences 갱신은 서로 겹칠 수 있었습니다. 이 경우 한 작업이 성공한 직후 다른 작업의 늦은 실패 기록이 남아 불필요한 재시도를 만들거나 화면에 오래된 오류 상태가 남을 수 있었습니다.
- FilterUpdateExecutionLock을 추가해 Activity 수동 갱신, 주기 Worker, 실패 복구 Worker가 각각의 전체 갱신 트랜잭션을 동일한 프로세스 내 모니터로 직렬 실행하도록 변경했습니다. 캐시 단위 잠금과 다운로드 단위 잠금은 그대로 유지해 파일 원자성·복구 보호도 함께 유지합니다.
- FilterUpdatePolicyTest.manualAndBackgroundRefreshTransactionsCannotOverlap 회귀 테스트를 추가해 첫 갱신이 잠금을 보유한 동안 두 번째 갱신이 진입하지 못하고 첫 갱신 종료 후 진행되는지 검증하도록 했습니다.
- 반영 커밋: b70709d5663fc79eaebb8400ee283675a901356a, 008fe5a52347aee9e7eddb6d9de5125acbc0e380, 17b5dcdfaa3838b667113324cb161bdc71d19f96, 2cae5516da1ff3739a2785852d280192fab68438.
- GitHub 원격 파일 5개 재조회로 잠금 객체, Worker·Activity 적용 지점, 회귀 테스트 및 문서 반영을 확인했습니다. 별도 Kotlin/JVM 하네스에서 동일한 @Synchronized 제네릭 잠금 패턴의 컴파일 및 두 스레드 직렬화 동작은 PASS했습니다. 다만 Android Gradle/JUnit 프로젝트 테스트 실행·APK·실기기 검증은 아직 하지 않았습니다. 현재 작업 컨테이너에는 Gradle 실행 파일이 없고 저장소의 Android Gradle Wrapper도 확인되지 않아 여기서 Android 프로젝트 빌드를 실행할 수 없습니다. 이 잠금은 동일 앱 프로세스 내 갱신 경합을 막으며, Android 프로세스가 강제 종료된 경우에는 WorkManager 재실행과 캐시 복구 절차가 처리합니다. 브랜치 비교: main 대비 256 commits ahead / 0 behind.

## 2026-11-20 명시적 중지와 지연 VPN 재연결 경합 보완

- VPN 서비스 생명주기 경로를 추가 점검하면서 실제 재연결 경합을 확인했습니다. 앱별 보호 제외 재적용은 이전 DNS 패킷 스레드 종료를 기다린 뒤 새 시작 세대를 발급합니다. 그 대기 중 사용자가 보호 중지를 누르면 중지 요청이 기존 세대를 취소하고 복원 의도를 false로 바꾸지만, 기존 코드가 대기 후 복원 의도를 다시 확인하지 않고 새 시작 세대를 발급할 수 있었습니다. 이 순서에서는 명시적 중지 뒤 제외 재적용이 보호를 다시 켤 수 있었습니다.
- 자동 일시중지 해제 후 재연결에도 같은 형태의 틈이 있었습니다. 복원 의도/일시중지 상태 확인과 새 시작 세대 발급 사이에 명시적 중지가 들어오면 지연 재연결이 중지보다 최신 세대가 될 수 있었습니다.
- 두 경로 모두 최종 복원 의도 확인, 일시중지 상태 확인, 필요 시 일시중지 플래그 변경 및 새 시작 세대 발급을 `synchronized(this@GuardianVpnService)` 안에서 수행하도록 수정했습니다. `stopProtection()`도 `@Synchronized`이므로 중지와 재연결의 최종 결정이 같은 서비스 모니터에서 순서화됩니다. 이 임계 구역은 짧게 유지하고 DNS 사전 검사/터널 생성 동안에는 서비스 모니터를 점유하지 않습니다.
- `VpnStartCancellationGateTest.explicitStopBeforeDelayedResumeCheckCannotCreateANewStartGeneration` 회귀 테스트를 추가했습니다.
- 반영 커밋: `60aac5ad43333ce6b60cf01d84a58b973632b30e`, `27166f490466c7d80a9fa7481a745afdc9746cf3`.
- GitHub 원격 재조회로 두 재연결 경로의 최종 상태 확인/세대 발급 잠금과 테스트 존재를 확인했습니다. Android Gradle/JUnit 실행, APK, Samsung 실기기 검증은 아직 수행하지 않았습니다. 별도 Kotlin/JVM 컴파일/동시성 하네스는 이번 수정에 대해 실행하지 않았습니다.


## 2026-11-20 VPN 패킷 세션 소유권 경합 수정

- **확정 결함**: 이전 TUN의 packet reader가 종료되는 시점에 STOP 후 새 START가 이미 새 TUN을 활성화했다면, 이전 reader의 `finally`가 공유 `running` 플래그를 `true → false`로 바꾸고 새 foreground notification까지 제거할 수 있었습니다. 기존 `running.compareAndSet(true, false)`만으로는 세션 소유권을 구분하지 못했습니다.
- **수정**: `PacketSessionOwnership`을 추가해 TUN descriptor 객체 identity를 기준으로 세션 활성화·종료·중지를 직렬화했습니다. 이전 descriptor의 종료 콜백은 새 descriptor가 owner가 된 뒤에는 공유 보호 상태를 변경할 수 없습니다.
- **적용 경로**: 새 TUN 커밋, 패킷 루프 `finally`, 명시적 STOP/서비스 종료, 호환 앱 자동 일시중지, 앱 제외 설정 재적용 경로가 동일한 ownership guard를 사용합니다. 이전 세션의 descriptor는 stale 상태에서도 개별 close 처리합니다.
- **회귀 테스트 소스**: 새 세션 활성화 후 이전 세션 cleanup 차단, 명시적 STOP 후 이전 cleanup 무효화, 동일 세션 cleanup 1회만 허용하는 JUnit 테스트 3개를 추가했습니다.
- **실행 증거**: 동일 ownership helper 구현을 독립 Kotlin/JVM harness로 컴파일하고 세 시나리오를 실행해 PASS를 확인했습니다. 저장소 Android JUnit/Gradle 빌드는 실행하지 않았으며, 이 harness 결과는 Android 컴파일 또는 단말 검증을 의미하지 않습니다.
- **남은 생명주기 점검**: network callback/네트워크 변경 시 DNS upstream 재선택과 `onRevoke`/`onDestroy` 이후 실제 프로세스 재생성 동작은 별도 정적 검토 및 Android 기기 검증이 필요합니다.
- GitHub Actions, PR, merge는 실행하지 않았습니다.


## 2026-11-20 VPN 호환 앱 일시중지 종료 확인 보강

- **추가 결함**: 호환 앱 자동 일시중지 경로는 DNS packet worker에 5초 join을 수행했지만, join timeout 뒤에도 worker가 살아 있는지 확인하지 않고 보호 재개 경로로 진행할 수 있었습니다. 세션 소유권 guard가 새 세션 상태 오염은 막더라도, 살아 있는 이전 reader와 새 reader가 동시에 존재하는 상태는 막지 못합니다.
- **수정**: join이 인터럽트되면 인터럽트 상태를 복원하고 즉시 중단합니다. 5초 후에도 이전 worker가 살아 있으면 재연결하지 않고 보호 비활성/복구 대기 상태와 진단 메시지를 기록한 뒤 반환합니다. 앱 제외 재적용 경로의 기존 worker 생존 확인과 같은 안전 기준입니다.
- **검증 범위**: 소스 재조회와 분기/상태 기록 확인은 수행합니다. Android Gradle 빌드, 기기에서 worker 종료 지연을 재현하는 테스트는 아직 실행하지 않았습니다.


## 네트워크 변경 경로 정적 점검 결과

- `forwardDns()`는 DNS 질의마다 `systemDnsResolvers()`를 다시 호출하며, 해당 함수는 매번 `ConnectivityManager.allNetworks`, 네트워크 capability 및 link properties의 DNS 서버 목록을 다시 조회합니다. 업스트림 UDP/TCP 소켓도 질의 단위로 생성·종료되어 이전 네트워크의 영구 소켓이나 resolver 목록 캐시를 유지하지 않습니다.
- 따라서 현 구조에는 별도 network callback으로 VPN 전체를 매번 재생성해야 하는 근거가 확인되지 않았습니다. 네트워크 전환 직후 진행 중인 질의가 기존 경로에서 실패할 수는 있으나, 다음 resolver 후보/다음 질의에서 최신 네트워크 목록과 fallback을 사용합니다.
- 이 결론은 소스 구조에 대한 정적 점검이며, Wi-Fi↔모바일 데이터 전환, 비행기 모드, captive portal, 삼성 절전/백그라운드 조건의 실기기 검증을 대체하지 않습니다.


## 2026-11-20 서비스 복구·전면 앱 감시 생명주기 정적 감사

- **복구 정책 확인**: `ProtectionRestoreReceiver`는 BOOT_COMPLETED/MY_PACKAGE_REPLACED만 허용하고, `KEY_RESTORE_REQUESTED`가 명시적으로 false이면 오래된 `KEY_ACTIVE=true`가 남아 있어도 복구하지 않습니다. 복구 요청 시 기존 active 비트를 먼저 false로 내리고 recovery pending을 설정한 뒤 foreground service를 요청합니다.
- **START_STICKY 확인**: null intent 재생성 경로는 durable restore intent(구버전 설정에는 persisted active)를 기준으로 TUN 복구를 시도합니다. 명시적 STOP은 durable intent와 recovery pending을 동기적으로 해제합니다. `onRevoke()`는 명시적 중지 처리로 재승인을 자동 시도하지 않으며, `onDestroy()`는 restore intent를 보존합니다.
- **Foreground monitor 확인**: 감시 스레드는 daemon이며 stop 시 running=false 및 interrupt 후 제한 시간 join을 수행합니다. 종료 join이 완전하지 않아도 서비스의 monitor generation 검사가 이전 감시 스레드의 늦은 callback을 무효화합니다. Usage Events 조회 실패/잠금 상태에서는 tracker를 reset해 자동 일시중지 상태를 고집하지 않고 보호 재개를 기본값으로 합니다.
- **추가 코드 변경 여부**: 위 경로에서 즉시 수정이 필요한 확정 결함은 이번 정적 검토 범위에서 확인하지 못해 불필요한 lifecycle 변경은 추가하지 않았습니다. Android 버전별 백그라운드 실행 제한과 제조사 절전 정책은 실기기 검증 대상입니다.
- **독립 JVM 확인**: `PacketSessionOwnership` helper를 Kotlin/JVM으로 별도 컴파일하고 stale replacement cleanup, STOP 후 cleanup 무효화, current-session one-shot cleanup 3개 시나리오를 실제 실행해 PASS를 확인했습니다. 이는 Android Gradle 빌드/JUnit 실행이 아닙니다.


## 2026-11-20 VPN 시작 실패 처리의 세대 소유권 경합 수정

- **확정 결함**: 시작 worker가 취소되거나 더 새로운 START가 발급된 뒤에도 포그라운드 서비스 시작/필터 규칙 로드의 예외 처리 블록이 이전 오류 진단을 저장하고, foreground notification을 제거하고, 서비스 상태를 실패로 기록한 뒤 `stopSelf(startId)`를 호출할 수 있었습니다. 예기치 않은 예외 경로도 현재 세대 확인보다 먼저 stack summary를 저장하고 있어 오래된 오류가 최신 상태에 남을 수 있었습니다.
- **수정**: 모든 시작 실패 정리·진단 저장을 `reportStartupFailure()`로 통합하고 `VpnStartCancellationGate.tryCommitStart()` 안에서 현재 요청 세대가 유효할 때만 수행하도록 변경했습니다. 세대 검증과 실패 상태 반영/notification 제거/부분 TUN 정리/`stopSelf(startId)`가 STOP 또는 새 START의 세대 변경과 직렬화됩니다. 이미 새 TUN이 실행 중이면 실패 정리를 건너뜁니다.
- **회귀 테스트 소스**: 취소된 이전 시작의 진단 저장 및 서비스 종료 side effect가 차단되는지, 동일 세대에서 cleanup이 한 번만 허용되는지 테스트 2건을 추가했습니다.
- **독립 JVM 실행**: `VpnStartCancellationGate` 동작을 별도 Kotlin/JVM harness로 컴파일·실행해 stale failure side effect 차단과 current-generation cleanup one-shot을 PASS 확인했습니다. Android Gradle/JUnit 실행은 아닙니다.
- **검증 한계**: GitHub 원격 소스 재조회와 diff 확인은 수행합니다. Android 프로젝트 컴파일, JUnit, APK, 실제 Android 서비스 재시작/실기기 검증은 미실행입니다.


## 2026-11-20 VPN 시작 중단·신규 시작 간 stale worker 정리 경합 추가 수정

- **추가 확정 결함**: DNS 사전 연결 실패, `VpnService.Builder.establish()` 실패, TUN 커밋 거부 및 시작 중 취소 감지 분기에서 이전 시작 worker가 `stopForeground()`/`stopSelf()`를 호출할 수 있었습니다. 사용자가 새 START를 발행해 새 요청이 foreground notification을 준비한 직후 이전 worker가 늦게 돌아오면 새 알림/서비스를 제거할 수 있는 경합입니다. DNS preflight 실패 상태와 진단도 세대 변경 직전에 오래된 요청이 기록할 수 있었습니다.
- **수정**: 사전 연결 실패와 VPN 인터페이스 생성 실패를 공통 generation-guarded failure handler로 이동했습니다. 이 handler는 현재 시작 세대일 때만 진단/상태/notification/service 정리를 수행합니다. 시작 중 취소 감지 분기는 이전 worker에서 foreground/service를 정리하지 않고 즉시 반환합니다. TUN commit이 거부된 worker는 자신이 생성한 descriptor만 닫고 공유 서비스 상태는 건드리지 않습니다.
- **회귀 테스트 소스**: stale preflight failure의 상태 게시/foreground 제거/서비스 종료 차단, stale descriptor 정리의 공유 teardown 차단 테스트 2건을 추가했습니다.
- **검증 한계**: 앞선 generation gate 독립 Kotlin/JVM harness는 PASS했습니다. 이번 추가 분기별 Android 서비스 실행 테스트는 아직 수행하지 않았으며, Android Gradle/JUnit/APK/Samsung 실기기 검증은 계속 미완료입니다.


## 2026-11-20 VPN 시작 초기 공유 상태 초기화의 세대 보호

- **추가 경합 지점**: `startProtection()`의 첫 세대 확인과 `startProtectionInternal()` 진입 사이에 STOP/새 START가 들어올 수 있습니다. 내부 진입 직후 수행하던 `tunnelActive=false` 및 필터 준비 진단 삭제는 세대 보호 밖에 있어, 이미 취소된 worker가 공용 상태를 변경할 여지가 있었습니다.
- **수정**: 초기 공유 상태 초기화도 `VpnStartCancellationGate.tryCommitStart(requestGeneration)` 안에서 실행하도록 이동했습니다. 세대가 더 이상 유효하지 않으면 필터 준비 진단과 터널 상태를 건드리지 않고 즉시 반환합니다.
- **회귀 테스트 소스**: `staleStartupWorkerCannotResetSharedReadinessBeforePreparing`를 추가해 stale generation의 readiness 초기화 및 tunnel inactive side effect가 차단되는 조건을 검증하도록 했습니다.
- **검증 범위**: GitHub 원격 파일 재조회와 커밋 반영 확인 대상입니다. 이 테스트 소스는 Android JUnit으로 실행하지 않았으며 Android Gradle 빌드/APK/Samsung 실기기 검증도 수행하지 않았습니다.
