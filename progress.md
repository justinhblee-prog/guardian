# Guardian Android — PRO 병렬 트랙 진행 추적

- **트랙**: 보안 프로필 · issue #300
- **상태**: 개발 진행 중 — 완료/상용 공개 단계가 아닙니다.
- **브랜치**: `work/pro-security-profiles-20261124`
- **기준일**: 2026-11-24 (KST)
- **현재 구현**: 6개 정책 프로필과 DEBUG NetworkCallback 전환, 수동 프로필 선택 UI를 추가했습니다. 로밍 감지·실제 기기 네트워크 전환·PRO entitlement 게이트 검증은 미완료입니다.
- **공통 권한 정책**: 유효한 서명 entitlement만으로 기능을 열지 않습니다. Android 통합·회귀·단말 검증을 통과하고 parent entitlement policy의 production-ready 목록에 등록되기 전까지 Release 접근은 비활성입니다.
- **검증 제약**: 최신 코드에 대한 Android Gradle/JUnit·lint·에뮬레이터·실기기 검증은 별도로 수행한 증거가 없으면 미검증으로 표시합니다. GitHub Actions/runner/billing 설정 및 유료 실행은 사용자 승인 없이 시작하지 않습니다.

## 1. 워크플로우 체크리스트

- [x] Step 0: 목표·산출물·제약을 확정하고 저장소에 진행 추적 파일 생성
- [x] Step 1: 현재 브랜치, 기준 문서, 출시 게이트 및 검증 상태 조사
- [x] Step 2: Android VPN/DNS 엔진의 세션·패킷·앱 제외 경로 정적 감사
- [x] Step 3: 필터 캐시 갱신 실패 재시도, 필수 번들 규칙 파일 및 DNS 테스트 소스의 결함 수정/회귀 테스트 추가
- [ ] Step 4: BLOCKED — Android Gradle compile, unit tests, lint 및 License API HTTP/PostgreSQL 테스트 실행 (Actions runner/비용 제한, 로컬 checkout 실행환경 없음)
- [ ] Step 5: BLOCKED — API 35 emulator에서 신규 설치·업그레이드·VPN/DNS·앱 제외·복구 E2E 실행 (에뮬레이터 실행 환경 미연결)
- [ ] Step 6: BLOCKED — 필터 원본 재배포 권리 확인 및 서명 manifest/cache rollback 검증 (권리 승인 자료 미확보; 코드 테스트 실행도 미완료)
- [ ] Step 7: EXTERNAL — Samsung 실기기에서 Library file not found 원인 특정 및 VPN/DNS/네트워크 전환 수용시험 (단말 로그/실행 접근 필요)
- [ ] Step 8: EXTERNAL — 사용자 소유 Production 서명키와 공개 HTTPS endpoint로 release APK 생성, signer/version/package/SHA-256 검증
- [ ] Step 9: 신규 설치·업그레이드 검증, 한국어 배포/복구 문서 확정, 최종 증거 보고서 작성
- [ ] Step 10: Android 1.0 게이트 종료 후 Windows 보호 엔진·설치 패키지 작업 착수

## 2. 단계별 핵심 산출물 및 데이터 요약

- **Step 0 결과**: `progress.md` 생성. 출시 판정은 Issue #141 및 `docs/ANDROID_1_0_PARALLEL_EXECUTION_KO.md` 기준으로 통일.
- **Step 1 결과**: `docs/PROJECT_HANDOFF_KO.md`, `docs/REMAINING_WORKFLOW_KO.md`, `docs/ANDROID_1_0_PARALLEL_EXECUTION_KO.md` 확인. 현재 feature branch는 구현 통합 상태이나 CI/unit/lint/emulator/production APK는 미실행·미확보.
- **Step 2 결과**: DNS-over-TCP 유효하지 않은 시퀀스 패킷이 idle timeout을 연장하지 않도록 수정. 회귀 테스트 `outOfWindowSegmentsDoNotExtendSessionIdleTimeout` 추가. 앱별 제외는 저장 후 VPN tunnel 재구성, 실제 적용/실패 목록 분리 구조 확인. 추가로 부팅/패키지 교체/START_STICKY 복구, 명시적 중지 의도, 프로세스 종료 시 복구 플래그 보존, 실제 TUN 소유 여부 기반 활성 표시 및 시작 실패 상태 정리 경로를 정적으로 확인.
- **Step 3 결과**: 필터 갱신 오류 시 유효한 이전 캐시가 있어도 재시도하도록 feed별 error flag 정책 및 테스트 추가. `shared/filter_rules.txt` 누락/빈 파일 빌드 가드 추가. IPv4 UDP checksum 테스트 helper 범위 오류 수정 및 정상 non-zero checksum 테스트 추가.
- **Step 4~9 결과**: 아직 실제 실행/외부 증거 확보 전. 아래 의사결정 로그와 blocker 참조.
- **Step 10 결과**: Android 1.0 미완료이므로 착수 보류.

## 3. 자율 의사결정 및 이슈 로그

- [Step 0] 사용자가 목표 템플릿의 괄호 항목을 비워 두었으므로, 기존 프로젝트 인수인계의 고정 우선순위(Android 1.0 → Windows → 멀티 디바이스)를 실제 목표로 채택.
- [Step 1] 기존 사용자 지시의 Actions 비용 제한을 유지. 수동 workflow는 작성되어 있으나 승인 없이 실행하지 않음. PR 생성/merge도 자동 Actions 실행을 유발할 수 있어 수행하지 않음.
- [Step 2] 저장소 소스 정적 검토만으로 삼성 단말의 `Library file not found` 원인을 확정할 수 없음. 단말의 privacy-filtered stack summary와 실제 실행 증거가 필요.
- [Step 3] 필터 예시 7개 원본의 재배포 승인값이 false이고 승인 참조가 비어 있음. 권리 확인 없이 상용 필터 배포를 승인 처리하지 않음.
- [Step 4] GitHub-hosted runner가 job step 시작 전 `runner_id=0`, 로그 없음, `BlobNotFound`로 실패한 이전 이력이 있음. 동일한 Actions 재시도/runner/billing 설정 변경은 금지된 상태. 테스트 실행 결과를 추정하지 않음.
- [Step 5] 에뮬레이터는 외부 CI 또는 별도 실행환경이 필요. 실행하지 않은 에뮬레이터 검증을 통과로 간주하지 않음.
- [Step 7] 실제 Samsung 단말은 현재 실행 도구에서 접근 불가. 단말 수용시험은 미완료로 유지.
- [Step 8] 사용자 소유 Production keystore 및 해당 signing secrets가 제공/연결되었다는 확인이 없음. 임시 signer/Debug APK는 정식 산출물로 대체할 수 없음.
- [Step 9] 완료 판정은 실제 APK 파일과 SHA-256/인증서 지문 및 설치 검증 증거 확보 이후에만 수행.

## 4. 최신 저장소 기준점\n\n- GitHub compare: `main...feature/android-support-center-mainline` = 수치는 과거 스냅샷이므로 최신 GitHub compare 조회 결과를 기준으로 한다.\n- 새 문서: `docs/GUARDIAN_ANDROID_1_0_FINAL_READINESS_KO.md`.\n- 필터 무결성 정적 감사: 서명 검증/캐시 복구/배포 HTTP 서버의 주요 방어 코드 및 테스트 소스를 확인했으나 실제 테스트 실행이나 운영 키/배포 검증을 대체하지 않음.\n\n- 추가 심층 감사 대상: `FilterRuleParser.kt`, `FilterRuleComposer.kt`, `DomainFilter.kt`, `FilterCacheStore.kt`, `FilterUpdateScheduler.kt`, `FilterUpdateWorker.kt` 및 관련 테스트.\n\n- 추가 감사 대상: `ProtectionRestoreReceiver.kt`, `ProtectionRestorePolicy.kt`, `ProtectionState.kt`, `GuardianVpnService.kt`의 `startProtectionInternal`, `publishState`, `stopProtection`, `onDestroy` 및 관련 테스트. 복구 상태는 실제 TUN 활성과 구분되며 실패 시 UI 상태 플래그를 정리하는 구조를 확인. 추가 확정 결함은 발견하지 못함.\n\n- 추가 UI 감사: 홈 대시보드의 단일 보호 토글은 실제 TUN 활성/호환 일시중지/복구 대기 상태에 따라 동작하며, 설정 화면은 상태 표시와 기능 설정으로 분리됨을 확인. 필터 업데이트 중 중복 실행 방지와 관련 버튼 잠금, APK 다운로드 전 설치 비활성화 및 검증 APK 확보 후 설치 활성화 정책/회귀 테스트를 확인. 버튼 경로에서 추가 확정 결함은 발견하지 못함.\n\n- Android 플랫폼 감사: minSdk 26 / targetSdk 35, `FOREGROUND_SERVICE_SPECIAL_USE` 권한 및 VPN 서비스의 `specialUse` subtype 선언 확인. API 26+ 시작 경로는 `startForegroundService` 직후 서비스 진입 시 foreground notification을 설정. TUN 생성 전 실제 DNS preflight, 업스트림 UDP/TCP/DoT/DoH 소켓의 `protect()` 우회, 연결/읽기 타임아웃, 응답 주소·포트/질문 검증, 취소 generation 기반 stale start 방지, 패킷 루프 예외 시 터널 종료 및 비활성 상태 반영을 정적으로 확인. 이 검토에서 추가 확정 결함은 발견하지 못함. Android 빌드 및 OS별 실기기 테스트는 미실행.\n\n## 5. 현재 외부 차단 요인

1. **CI 실행**: 사용량/runner 제약으로 Actions 실행 금지. 별도 로컬 실행 결과도 현재 저장소 연결 환경에서는 확인되지 않음.
2. **필터 배포 권리**: 원본별 재배포 승인 증거 없음.
3. **Samsung 수용시험**: 실기기 접근 및 오류 stack 증거 없음.
4. **Production 서명**: 사용자 소유 운영 keystore/서명 secrets 확인 필요.
5. **공개 API**: Tailscale Serve 주소의 외부 고객 공개 접근성은 검증되지 않음. 공개 endpoint라고 간주하지 않음.

## 6. 완료 판정 원칙

- 소스 구현, 테스트 소스 작성, 테스트 실행, 에뮬레이터, Samsung 실기기, 운영환경, Production APK를 별도 증거 단계로 관리한다.
- 체크박스는 증거가 확인된 단계만 완료 처리한다.
- 외부 blocker가 남아 있으면 상태를 진행 중으로 유지하고 100%/완료라고 보고하지 않는다.
- 마지막 확인된 커밋/비교 결과는 GitHub feature branch에서 재조회 후 갱신한다.


## 2026-11-20 추가 사용자 보고 및 수정

- **프리미엄/설정 라이선스 진입 중복 표현**: 프리미엄 화면의 버튼은 별도 활성화 폼이 아니라 설정 화면으로 이동하는 CTA였지만, 버튼 문구가 활성화/관리로 표현되어 설정의 실제 입력 폼과 중복처럼 보였습니다. 프리미엄 버튼 문구를 "설정에서 라이선스 관리"로 통일해 단일 활성화 폼이 설정에 있음을 명확히 했습니다.
- **DNS 업스트림 연결 실패 대응**: 기존 UDP 모드에서는 선택 DNS 및 대체 DNS 모두 UDP/53만 시도했습니다. 이동통신사/관리형 Wi-Fi가 UDP/53을 차단하고 TCP/53은 허용하는 환경에서 보호 시작 preflight가 실패할 수 있어, UDP 우선 시도 후 동일 서버에 TCP/53으로 재시도하도록 수정했습니다. TCP/DoT/DoH 선택은 기존 지정 전송방식을 유지합니다.
- **검증 한계**: 변경 소스는 GitHub에서 재조회해 반영을 확인했습니다. Android compile/unit/lint, 실기기 DNS 연결 및 실제 네트워크별 성공 여부는 아직 실행하지 않았으며, DNS 오류의 단말별 최종 원인은 실기기 진단 전까지 확정하지 않습니다.
- **최신 compare**: `main...feature/android-support-center-mainline` = 130 commits ahead, 0 behind. Actions 실행/PR/merge는 수행하지 않았습니다.

- **후속 UI 정리**: 프리미엄 화면에 있던 설정 이동/라이선스 활성화 CTA도 제거했습니다. 프리미엄 화면은 상태와 기능 미리보기만 표시하고, 라이선스 키 입력·활성화·갱신·기기 해제는 설정의 라이선스 관리 한 곳에서 수행합니다. 고객지원 FAQ도 설정 경로로 수정했습니다.
- **DNS 업스트림 복구 경로 확장**: UDP 모드에서 선택 서버 UDP/53 실패 시 동일 서버 TCP/53 재시도 후, 물리 네트워크의 LinkProperties에서 가져온 시스템 DNS 서버들을 차례로 시도하고 마지막에 공용 DNS fallback을 시도합니다. VPN 네트워크 자체는 시스템 DNS 후보에서 제외하고 Guardian 가상 DNS 주소도 걸러냅니다. TCP/DoT/DoH 명시 선택은 임의 변경하지 않습니다.
- **회귀 테스트 소스 추가**: DNS 전송 정책에 UDP→TCP 순서, 기기 DNS→공용 fallback 순서/중복 제거, TCP 고정, DoT/DoH downgrade 금지 테스트를 추가했습니다. 테스트 실행은 하지 않았습니다.
- **검증 한계**: GitHub 파일 재조회로 UI·DNS 후보 경로·테스트 소스 반영만 확인했습니다. Android compile/unit/lint 및 Samsung 실기기 연결은 미검증이며 DNS 문제가 해결됐다고 확정하지 않습니다.
- **최신 compare**: `main...feature/android-support-center-mainline` = 수치는 과거 스냅샷이므로 최신 GitHub compare 조회 결과를 기준으로 한다. Actions 실행, PR 생성, merge는 하지 않았습니다.

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


## 2026-11-20 병렬 작업 후속 — Windows 라이선스 migration 누락 수정

- **발견한 결함**: `Apply-GuardianLicenseMigrations.ps1`가 기존 DB에 migration 002/003만 적용하고, 고객 디렉터리 migration 004(`customers`, `licenses.customer_id`, `licenses.order_ref`)를 누락하고 있었습니다. 새 고객/라이선스 관리 기능을 노트북에 배포할 때 고객 디렉터리 API가 DB 스키마와 맞지 않을 수 있는 실제 운영 설치 결함입니다.
- **수정**: Windows migration 스크립트 순서에 `004_customers.sql`을 추가하고, 완료 검증에서 `customers` 테이블과 `licenses`의 두 컬럼 존재까지 검사하도록 강화했습니다.
- **회귀 테스트 소스**: PostgreSQL integration test의 반복 적용 migration 목록에 004를 포함하고, 고객 테이블/컬럼 존재 assertion을 추가했습니다.
- **반영 커밋**: `facafdc1384af15e85cdc4d83c6af9ec676c94c0` (Windows migration script), `4c71c03e6d3ae4bebea4313e61904457840d9607` (PostgreSQL integration test).
- **실행 상태**: migration 스크립트와 테스트 소스 변경은 GitHub에 반영했으나, 노트북 Docker/PostgreSQL에서 실제 migration 재실행 및 `/ready`, 고객 API 확인은 이 실행 환경에서 노트북에 접근할 수 없어 미실행입니다. 기존 DB 데이터는 삭제/초기화하지 않았습니다.
- **Android 검증 상태**: Android Gradle compile/unit/lint, APK 조립, 화면 실행 및 Samsung 실기기 검증은 미실행입니다. Actions는 사용자 비용/runner 제한을 유지하기 위해 실행하지 않았습니다.


## 2026-11-20 Android 실기기 설치/화면 캡처 자동화 추가

- Windows `Build-GuardianAndroid-Debug.ps1`에 선택적 `-InstallToConnectedDevice` 옵션을 추가했습니다.
- 기본 빌드 동작은 기존대로 단위 테스트 → Lint → Debug APK 조립 → SHA-256 생성입니다.
- 옵션 사용 시 ADB 연결 기기가 정확히 1대인지 확인하고, 제조사/모델/Android 버전을 읽은 뒤 APK 설치, `MainActivity` 실행, 메인 화면 PNG 캡처, 기기 검증 텍스트 보고서를 `%LOCALAPPDATA%\\Guardian\\outputs\\DeviceCapture-*`에 저장합니다. Samsung 외 기기면 제조사 경고를 기록합니다.
- 캡처는 앱 시작/화면 렌더링 확인이며 VPN·DNS 차단·라이선스·결제 기능 E2E를 대체하지 않는다고 보고서에 명시했습니다.
- Windows README에 실행 명령을 추가했습니다. 실제 빌드/설치/캡처는 노트북과 USB 연결 기기에 접근할 수 없어 아직 실행되지 않았습니다.
- GitHub Actions 실행/PR/merge는 수행하지 않았습니다.

## 2026-11-20 병렬 트랙 후속 — DNS 진단 순서 및 활동 화면 일관성

- **트랙 A — VPN/DNS 엔진**: 비동기 업스트림 연결 테스트나 시작 전 DNS 사전검사가 늦게 끝나면 이후 패킷 진단 또는 사용자의 기록 초기화를 오래된 결과로 덮어쓸 수 있는 경합을 수정했습니다. `DnsDiagnosticSequence` 토큰을 도입하고 토큰 예약/무효화를 DNS 통계 공용 잠금 안에서 직렬화했습니다. 진단을 실제로 기록하는 검사와 SharedPreferences 반영도 같은 잠금 안에서 수행하므로 확인 직후 다른 작업이 토큰을 바꾸는 경쟁 구간을 제거했습니다.
- **트랙 A — 활동 UI**: 활동 화면 생성 및 상태 갱신이 DNS 질의/차단/전달/실패/무시, 광고·추적·기타 차단, 최근 기록과 마지막 진단을 각각 따로 읽어 서로 다른 시점의 값을 한 화면에 섞을 수 있었습니다. 공용 잠금으로 한 번에 읽는 `DnsStatsSnapshot`을 추가하고 통계 카드·그래프·보호 상세·최근 기록·진단 표시가 동일 snapshot을 사용하도록 변경했습니다.
- **회귀 테스트**: 오래된 비동기 진단 거부, 기록 초기화 후 pending 결과 무효화, 최신 진단 토큰 유지 테스트 3건을 추가했습니다. 독립 Kotlin/JVM 하네스 실행 결과 3개 시나리오 PASS입니다.
- **트랙 C — 라이선스/고객지원 정적 감사**: 고객 문의 제출/조회는 독립 요청 제한 경로와 본문 크기 제한(413), UTF-8/JSON 객체 형태 검증, 조회용 256-bit 난수 토큰의 SHA-256 저장, 관리자 답변의 인증 및 기기별 공유 DB rate limit 경로가 있는 것을 소스와 HTTP 테스트에서 확인했습니다. API/PostgreSQL 테스트 실행은 하지 않았습니다.
- **트랙 D — 빌드/배포 감사**: 현재 Android 프로젝트는 compileSdk/targetSdk 35, JDK target 17, Gradle 8.9 요구이며 Android Gradle Wrapper 파일은 없습니다. Windows 빌드 스크립트는 SDK Platform 35 및 Build Tools 34.0.0을 요구하고, APK metadata/package/version/SHA-256 기록을 구현했으나 Windows PowerShell/SDK/Gradle이 없는 이 환경에서 실행하지 않았습니다.
- **검증 한계**: GitHub 원격 소스 재조회로 변경을 확인했습니다. Android Gradle 컴파일, Android JUnit 전체, lint, APK, 앱 UI 실행, emulator, Samsung 실기기, 노트북 Docker/PostgreSQL/DPAPI/실제 라이선스 발급은 미실행입니다. GitHub Actions/PR/merge는 실행하지 않았습니다.

## 2026-11-20 병렬 작업 이어서 — DNS/UI 및 고객지원 정리
- DNS 활동 화면·보호 상세 화면의 모든 표시 값을 동기화 snapshot으로 통일하고, snapshot 외부에서 통계를 개별 읽는 UI 경로가 없는지 재검사했습니다.
- 오래된 DNS 진단 결과가 초기화 또는 최신 패킷 결과를 덮어쓰지 않도록 순서 토큰을 공용 잠금과 함께 적용했습니다. 독립 Kotlin/JVM 컴파일/실행 3개 시나리오 PASS.
- 미사용 고객지원 카드 구현을 제거했습니다. 현재 Settings > Support 단일 진입점의 FAQ/문의 접수/답변 조회 기능은 유지됩니다.
- GitHub 원격 파일 재조회 및 정적 감사 완료. Android Gradle/JUnit/APK/실기기 검증은 미실행, Actions/PR/merge는 실행하지 않았습니다.

## 2026-11-20 병렬 트랙 D — Android SDK Build Tools 버전 충돌 수정
- 루트 `BUILD_ANDROID.ps1`은 APK 메타데이터 검사 도구로 Android Build Tools 34.0.0의 `aapt.exe`를 요구했지만, 앱 빌드 전용 `Build-GuardianAndroid-Debug.ps1`은 35.0.0을 요구하고 AGP 8.7.3/compileSdk 35 프로젝트의 실제 빌드 도구 기본값도 35.0.0 계열입니다. 두 빌드 진입점이 서로 다른 SDK 도구를 요구하는 설정 불일치를 확인했습니다.
- 루트 빌드 스크립트의 aapt 경로와 오류 안내를 35.0.0으로 통일하고 `BUILD_ANDROID_KO.md`도 함께 수정했습니다.
- GitHub 원격 파일 재조회로 스크립트/문서 일치 여부를 확인했습니다. PowerShell Parser, Android SDK, Gradle이 없는 현재 실행 환경에서는 실제 PowerShell 실행·APK 검증은 미실행입니다.

## 2026-11-20 병렬 트랙 C — 최초 라이선스 활성화 시 기기 바인딩 경합 수정
- 라이선스가 처음 활성화되는 설치에서 두 요청이 동시에 실행되면 두 스레드가 SharedPreferences의 빈 설치 ID를 각각 읽고 서로 다른 UUID를 저장/전송할 수 있었습니다. 이 경우 서버에는 첫 번째 기기 바인딩이 등록됐지만 앱 저장 시점에는 두 번째 ID가 읽혀 서명 토큰 검증이 실패하고 서버 활성화 슬롯이 남을 수 있습니다.
- `InstallationIdStore`를 추가해 최초 설치 ID 읽기·생성·영구 저장을 하나의 동기화 트랜잭션으로 묶었습니다. 기존 설치 ID는 절대 교체하지 않고, 저장 실패 시 미저장 ID를 반환하지 않습니다. `LicenseEntitlementStore.deviceBinding()`이 이 저장소를 사용하도록 연결했습니다.
- 회귀 테스트 3건 추가: 24개 동시 최초 호출이 하나의 ID/저장 1회로 수렴, 기존 ID 보존, 영구 저장 실패 시 중단.
- 독립 Kotlin/JVM 컴파일/실행 결과 세 시나리오 모두 PASS. Android JUnit/Gradle, 실제 서버 활성화 및 Android 단말 테스트는 미실행입니다.

## 2026-11-20 병렬 트랙 A — VPN 세션·DNS 동시성 핵심 회귀 재실행
- 원격 브랜치의 실제 순수 Kotlin 구현을 기준으로 별도 JVM harness를 구성해 `VpnStartCancellationGate`, `PacketSessionOwnership`, `DnsFailureContext`를 컴파일·실행했습니다.
- PASS: 오래된 VPN 시작 세대의 상태 게시/커밋 차단, 이전/중지된 패킷 세션의 DNS 통계 기록 차단, 동시 DNS 요청의 실패 진단 ThreadLocal 격리.
- 이 하네스는 해당 순수 Kotlin 클래스의 동작만 확인합니다. Android Service lifecycle, Android API, Gradle/JUnit 전체, 실제 VPN TUN/네트워크, Samsung 기기 테스트를 대체하지 않습니다.

## 2026-11-20 병렬 트랙 D — Windows Android 빌드 SDK 자동 탐색 통일
- 저장소 루트 빌드 스크립트는 `ANDROID_SDK_ROOT` → `ANDROID_HOME` → Windows 기본 SDK 경로를 탐색했지만, 라이선스 공개키 주입/기기 캡처용 빌드 스크립트는 환경변수만 확인해 기본 경로 설치를 놓칠 수 있었습니다.
- `Build-GuardianAndroid-Debug.ps1`도 동일한 SDK 탐색 순서로 맞추고 Windows 운영 README에 SDK Platform 35, Build Tools 35.0.0, Gradle 8.9 및 탐색 우선순위를 명시했습니다.
- PowerShell 실행/실제 빌드는 Windows 노트북 접근이 없어 미실행입니다. 원격 소스 재조회로 변경을 확인했습니다.

## 2026-11-20 병렬 트랙 C — 필터/라이브러리 시작 오류 FAQ 추가
- 고객지원 FAQ에 기본 필터/라이브러리 파일 오류 항목을 추가했습니다. 앱 삭제로 데이터/라이선스 상태를 잃지 않도록 기존 설치 위에 최신 APK를 업데이트 설치하도록 안내하고, 재현 시 개인정보 제외 진단 요약을 첨부하도록 안내합니다.
- FAQ는 진단 정보만으로 원인을 단정하지 않고 실제 호출 위치는 진단 기록으로 확인해야 한다고 명시합니다. FAQ 질문/답변 배열 항목 수를 맞춰 정적 확인했습니다.
- 실제 오류 재현 및 Samsung 실기기 원인 확인은 미실행입니다.

## 2026-11-20 병렬 트랙 D — APK 내 필수 기본 DNS asset 검증 추가
- Guardian 시작 경로가 `assets.open("filter_rules.txt")`에 의존하므로, 소스 파일 존재만 확인하는 것으로는 최종 APK에 asset이 실제 포함되었는지 보장할 수 없습니다.
- 루트 `BUILD_ANDROID.ps1`과 노트북 공개키 주입용 `Build-GuardianAndroid-Debug.ps1`에 `aapt list` 검사를 추가해 APK 내부 `assets/filter_rules.txt` 존재를 확인하고, 누락 시 APK 검증을 실패 처리합니다. 노트북 빌드 경로에는 APK 크기와 `com.guardian.adblock` package ID 검사도 추가했습니다.
- 실제 APK를 만들거나 실행한 것은 아닙니다. Windows PowerShell/Android SDK/Gradle 미접근 상태이며, 스크립트 소스만 원격 재조회했습니다.

## 2026-11-20 병렬 트랙 C — 기존 활성 라이선스 슬롯 보존
- 기존에 이 기기에 활성 PRO 라이선스가 저장되어 있는데 새 라이선스 키를 바로 활성화하면 서버에는 이전 라이선스의 기기 슬롯이 남고 앱은 새 토큰만 저장해 이전 슬롯의 참조를 잃을 수 있었습니다.
- `LicenseApiClient.activate()`에 기존 유효한 서명 entitlement 확인을 추가해 활성 PRO가 있으면 서버 요청 전에 중단합니다. 설정 화면에서도 새 키 입력/활성화 버튼을 비활성화하고, 새 키를 사용하려면 먼저 '이 기기 라이선스 해제'를 수행하도록 안내합니다. 만료 토큰은 기존 정책대로 새 키 활성화를 막지 않습니다.
- 원격 소스 재조회로 API 가드와 UI 비활성화/안내 문구를 확인했습니다. 실제 라이선스 API 및 Android UI 실행은 미실행입니다.

## 2026-11-20 라이선스 슬롯 보호 정책 보강
- 최초 슬롯 보호는 활성 entitlement만 검사했지만, 오프라인 상태가 길어 서명 토큰이 만료된 경우에도 서버의 기존 기기 등록은 남아 있을 수 있습니다.
- 새 키 활성화 전 확인 대상을 `current()`에서 `currentForRefresh()`로 확장했습니다. 따라서 활성/만료 여부와 무관하게 현재 기기에 보존된 유효 서명 등록 정보가 있으면 새 키 활성화를 막고, 기존 등록을 명시적으로 해제하도록 합니다. UI도 만료된 보존 토큰이 있을 때 입력란과 활성화 버튼을 비활성화하고 해제 안내를 표시합니다.

## 2026-11-20 병렬 트랙 D — 노트북 JDK 사전검사 일치
- 공개키 주입/ADB 캡처용 Windows 빌드 스크립트가 JDK 17 이상을 폭넓게 허용해 루트 빌드 스크립트의 JDK 17/21 허용 정책과 달랐습니다.
- 두 진입점이 동일한 JDK 17 또는 21 정책을 사용하도록 통일하고 Windows 운영 README도 갱신했습니다. PowerShell 실행 검증은 미실행입니다.

## 2026-11-20 병렬 트랙 D — APK 검증 스크립트 복구 및 소스 재검토
- APK asset 검증 추가 후 두 PowerShell 빌드 스크립트의 후속 구문이 중복/절단된 것을 소스 재검토에서 발견했습니다. 실제 노트북 실행 전이지만, 이 상태로는 빌드 스크립트 파싱/실행이 실패할 수 있어 즉시 복구했습니다.
- `BUILD_ANDROID.ps1`의 APK 검증 후반부를 단일 연속 블록으로 재작성했습니다: aapt 목록 exit code → 필수 `assets/filter_rules.txt` → badging → package/version 메타데이터 → APK 복사/SHA-256/검증 보고서.
- `Build-GuardianAndroid-Debug.ps1`도 동일하게 asset/package 검증 후 APK 복사/SHA-256 및 선택적 ADB 설치·실행·화면 캡처 흐름이 한 번만 존재하도록 복구했습니다.
- 원격 파일 재조회로 두 파일의 줄 수, 정규식 검사 행, APK 복사 및 checksum 흐름, 중복된 후행 블록 제거를 확인했습니다. PowerShell 파서/실행은 이 환경에 pwsh가 없어 미실행입니다.

## 2026-11-20 병렬 트랙 A/C — 라이선스 식별자·고객지원 재검증
- 원격 브랜치에서 실제 `InstallationIdStore.kt` 구현을 가져와 별도 Kotlin/JVM으로 다시 컴파일·실행했습니다. PASS: 24개 동시 최초 호출이 하나의 저장 ID로 수렴, 기존 ID 불변, 저장 실패 시 미저장 ID 반환 금지.
- 고객지원 FAQ 질문 8개/답변 8개 일치 확인.
- VPN/DNS 진단 writer 4개 경로의 순서 토큰 검사와 `DNS_STATS_LOCK` 동기화 위치를 재검토했습니다. 확인한 경로에서 DNS 통계 잠금 안에서 진단 시퀀스 확인 후 기록하며, startup failure는 시작 세대 게이트 후 DNS 통계 잠금 순서입니다. 이 정적 점검은 Android 런타임 교착/경합 검증을 대체하지 않습니다.

## 2026-11-20 병렬 트랙 C — 활성화 후 토큰 저장 실패 시 기기 슬롯 보상 해제
- Android 라이선스 활성화는 서버가 먼저 기기 등록 슬롯을 할당하고, 앱이 응답 토큰의 서명/기기 바인딩을 검증한 뒤 SharedPreferences에 저장합니다. 검증 또는 로컬 영구 저장이 실패하면 서버 슬롯은 활성 상태로 남지만 앱은 licenseRef를 보존하지 못해 다른 키를 선택했을 때 이전 슬롯을 해제할 방법이 없었습니다.
- `LicenseApiClient.activate()`에서 서버 응답 수신 후 토큰 검증/저장이 실패하면 응답의 bounded licenseRef 형식을 검사하고 동일 deviceBinding으로 `/v1/licenses/deactivate`를 best-effort 호출하도록 보완했습니다. 보상 해제도 실패하면 원래 활성화/저장 오류를 유지하고 해제 오류는 suppressed exception으로 보존합니다.
- `LicenseApiClientTest`에 보상 해제 참조값의 허용/거부 테스트를 추가했습니다. 실제 API·Android JUnit은 아직 실행하지 않았습니다.


## 2026-11-20 라이선스 활성화 보상 해제 범위 명확화
- 기존 등록 차단은 만료된 서명 entitlement도 보존된 기기 등록으로 취급합니다. API 오류 문구가 '활성 PRO'로만 표현되어 만료 등록이 왜 차단되는지 오해할 수 있어 '기존 라이선스 등록 정보(만료 포함)'로 수정했습니다.
- 보상 해제는 서버의 활성화 응답을 앱이 수신한 뒤 토큰 검증/저장에 실패한 경우에 적용됩니다. 서버가 DB 커밋한 직후 응답이 네트워크에서 유실되거나 응답 JSON을 읽기 전에 연결이 끊긴 경우에는 앱이 licenseRef를 알 수 없어 이 경로로 해제할 수 없습니다.
- 이 불확실한 결과 구간을 완전히 해결하려면 activation request idempotency/pending-attempt recovery를 API와 앱 저장소에 함께 설계해야 합니다. 현재는 해당 미구현 구간을 완료로 표시하지 않으며, 임의의 'device binding 전체 해제' API를 추가해 다른 활성 등록을 부주의하게 끊는 방식은 채택하지 않았습니다.
- 소스 정적 검토만 수행했습니다. API/PostgreSQL 및 Android JUnit/실기기 검증은 미실행입니다.


## 2026-11-20 활성화 응답 유실 복구 — 멱등성 구현
- API 활성화 요청에 UUID requestId를 지원하고, 트랜잭션 내 pg_advisory_xact_lock으로 동일 요청의 동시 재전송을 직렬화합니다.
- 최초 활성화 시 요청 ID, 기기 바인딩, 라이선스 키 HMAC lookup fingerprint, 최초 서명 응답을 activation_requests에 원자적으로 저장합니다. DB 커밋 후 HTTP 응답이 유실되어도 같은 requestId·기기·키로 재시도하면 최초 응답을 재생합니다. 다른 키/기기 조합으로 requestId를 재사용하면 409를 반환합니다.
- Android는 활성화 요청 ID와 key/device fingerprint를 SharedPreferences에 먼저 commit하고, 서명 토큰 검증 및 저장이 성공할 때까지 같은 ID를 재사용합니다. 성공 시 pending request ID를 제거합니다. 네트워크 오류/응답 유실 시 다음 시도에서 동일 요청을 복구합니다.
- 기존 클라이언트 호환을 위해 requestId 누락은 허용하되, 해당 호출에는 응답 유실 재시도 보장이 없습니다.
- 신규 DB schema와 기존 DB용 migration 005를 추가하고 README에 적용/운영 주의사항을 기록했습니다. PostgreSQL 통합 테스트에 동일 응답 replay, 단일 activation/event 보장, ID 오용 409 케이스를 추가했습니다.
- 아직 미검증: 실제 PostgreSQL migration 적용, Node test suite 실행, Android JUnit/Gradle, 실제 네트워크 응답 차단 재현. migration 005 적용 전 운영 API에 새 Android 클라이언트를 배포하면 activation_requests 테이블 부재로 활성화가 실패하므로 서버 migration 선적용이 필수입니다.
- activation_requests 보존 레코드의 정리/TTL 정책은 미구현이며 운영 배포 전 확정이 필요합니다.

- 추가 반영: Windows 운영 migration runner가 005를 002~004 뒤에 적용하고 `activation_requests` 테이블 존재까지 검증하도록 수정했습니다. README의 기존 DB 적용 절차도 002~005 순서로 바로잡았습니다.
- GitHub 소스 재조회 결과 서버 멱등성 로직, Android pending request ID 보존, PostgreSQL replay 테스트, migration 및 운영 runner가 브랜치에 존재함을 확인했습니다. 실행 검증은 여전히 수행하지 않았습니다.

## 2026-11-20 — 활성화 멱등성 재시도와 해제 상태 일관성 보강

- 동일 requestId로 저장된 활성화 응답을 재생할 때도 현재 라이선스 상태, 상품 활성 상태, 해당 기기의 활성화 슬롯이 모두 유효한지 DB에서 다시 확인하도록 보강했습니다. 해제·정지된 슬롯의 오래된 캐시 응답이 새 활성화처럼 재사용되는 경로를 차단합니다(HTTP 409).
- PostgreSQL 통합 테스트에 정상 응답 재생, 중복 활성화/감사 이벤트 1건 유지, 요청 ID의 다른 키 재사용 거부, 기기 해제 후 동일 요청 재생 거부를 추가했습니다. migration 통합 테스트도 005를 반복 적용하고 activation_requests 테이블을 검증하도록 확장했습니다.
- Android은 저장된 pending activation ID가 서버와 동일한 UUID v4 형식인지 확인합니다. 토큰 저장 성공 뒤 pending 기록 정리 실패가 이미 저장된 entitlement의 활성화 성공을 예외로 바꾸지 않도록 정리 동작을 비차단으로 변경했습니다. 명시적 기기 해제 성공 후에는 entitlement와 pending 요청 기록을 함께 정리합니다.
- Windows 운영 README에 기존 DB migration 002~005 적용 스크립트, 백업/검증 단계, 005의 보존·정리 주의사항을 추가했습니다.
- 원격 GitHub 소스 재조회와 정적 조건 확인은 통과했습니다. PostgreSQL 통합 테스트, 실제 migration, Android Gradle/JUnit, 네트워크 응답 유실 재현, APK 설치 및 Samsung 실기기 검증은 실행하지 않았습니다. activation_requests 자동 TTL 정리는 아직 없으며, 재시도 복구를 깨뜨리지 않는 보존 정책이 결정되기 전 임의 삭제하지 않습니다.

- 후속 상태 일관성 보강: Android에서 서버가 403으로 기존 등록을 무효화하거나 status=free를 반환한 경우, entitlement 로컬 삭제가 실제 저장된 뒤에만 pending activation 요청 ID도 제거합니다. 명시적 해제 시에도 entitlement 삭제가 실패하면 pending 복구 키를 보존합니다. 저장 실패로 복구 키까지 먼저 잃는 상황을 피하도록 했습니다.
- Android 활성화 요청 ID 검증을 서버의 UUID v4 형식과 동일한 내부 함수로 분리하고, 정상 소문자/대문자 UUID v4 허용 및 UUID v1·잘못된 variant·비 UUID 거부 JUnit 케이스를 추가했습니다. 테스트 소스만 추가했으며 Android Gradle/JUnit 실행은 미실행입니다.

## 2026-11-20 — 필터 배포 문서 정합성 수정

- 필터 배포 운영 README의 파일 시작부에 남아 있던 잘못된 라이선스 문자열 조각을 제거해 제목이 문서 첫 줄에서 시작하도록 복구했습니다.
- 필터 원본별 라이선스 표의 중복 헤더와 열 수 불일치를 바로잡았습니다.
- 실제 제3자 필터 재배포 권한은 확인되지 않았으므로 예제 source의 `redistributionApproved=false` 잠금은 그대로 유지했습니다. 법적 승인 없는 상태에서 upstream 목록을 실제 운영 배포물로 수집·서명하지 않았습니다.
- updater/publisher는 HTTPS·크기·UTF-8·서명키·재배포 승인 및 배치 잠금 검증을 수행하도록 구현되어 있음을 소스에서 확인했습니다. 다만 Node 테스트 실행이나 실제 upstream 다운로드/게시를 수행한 것은 아닙니다.
- 문서 재검토에서 라이선스 표의 열 구분선도 3열로 남아 있던 것을 발견해 4열 전체를 다시 정렬했습니다. GitHub 원격 파일 재조회로 제목 시작, 표의 4개 헤더/4개 데이터 열을 확인했습니다.

## 2026-11-20 — 필터 워커 및 노트북 APK 검증 리포트 보강

- WorkManager 필터 워커의 synchronized transaction lambda 안에서 발생할 수 있는 Kotlin 비지역 return 컴파일 오류를 수정했습니다. early return은 `return@withLock`로 명시하고 try/catch를 lambda 최종 결과로 유지했습니다.
- 노트북 Debug APK 빌드 스크립트에 package ID뿐 아니라 versionName/versionCode 일치 검증을 추가했습니다. 빌드 성공 시 Git commit, JDK/Gradle/SDK, APK 크기, SHA-256, unit test/lint/assemble task 결과를 `build-verification.txt`에 남깁니다.
- 원격 소스 재조회로 워커의 비지역 return 제거, APK 자산 확인, 패키지/버전 검증 및 빌드 리포트 작성 경로를 정적으로 확인했습니다.
- PowerShell 구문 분석/실행, Gradle 빌드, JUnit/Lint, 실제 APK 생성 및 Samsung 설치·화면 확인은 이 세션에서 실행하지 않았습니다.

## 2026-11-20 — 고객 지원 입력 사전 검증

- 문의 접수 시 선택 이메일을 서버의 형식·길이 규칙(최대 254자, 공백/@/도메인 구분자, NUL 거부)에 맞춰 앱에서 먼저 검증하도록 추가했습니다. 잘못된 주소는 요청을 보내기 전에 입력란 오류로 안내합니다.
- 문의 조회 접수 번호는 서버가 생성하는 `GDN-` + 12자리 16진수 형식으로 정규화하고, 형식이 맞지 않으면 조회 API를 호출하지 않도록 했습니다.
- `SupportInputPolicyTest`에 이메일 선택/유효/무효/길이 경계와 접수 번호 대소문자 정규화·형식 검증을 추가했습니다.
- 독립 Kotlin/JVM 하네스에서 이메일 검증과 접수 번호 검증 시나리오 2개를 통과했습니다. Android Gradle/JUnit 실행은 하지 않았습니다.
## 2026-11-20 — 고객 지원 조회 코드 계약 검증 강화

- 서버가 발급하는 문의 조회 코드는 32바이트 base64url 값(통상 43자)이며 API 계약상 32~100자 범위입니다. 앱에서 기존의 공백 여부만 확인하던 것을 32~100자 및 base64url 문자 집합(A-Z, a-z, 0-9, -, _) 검증으로 강화했습니다.
- 문의 조회 화면뿐 아니라 `LicenseApiClient.lookupSupportTicket()` 진입점에서도 접수 번호와 조회 코드를 검증해 다른 호출 경로에서 잘못된 요청이 전송되지 않도록 했습니다.
- 테스트에 조회 코드 최소/최대 길이, 잘못된 패딩 및 공백 문자를 추가했습니다.
- 실제 `SupportInputPolicy.kt`와 동등한 독립 Kotlin/JVM 하네스를 `kotlinc`로 컴파일·실행해 이메일, 접수 번호, 조회 코드 검증 3개 시나리오를 통과했습니다. Android Gradle/JUnit 및 실제 API/DB는 미실행입니다.


## 2026-11-24 — 보안 프로필 네트워크 자동 전환 연결
- 최신 branch 커밋: `830febf46caabad468a00738d246e5042a55b4e0`.
- Android ConnectivityManager `registerDefaultNetworkCallback`로 네트워크 capability를 감시하고 Wi-Fi/모바일/이더넷, VALIDATED, CAPTIVE_PORTAL, NOT_METERED에 따라 SecurityProfilePolicy를 자동 적용합니다.
- 선택한 프로필의 DNS filter profile/security/Android ads 옵션을 FilterListManager 설정에 반영하고, VPN 보호 중이면 ACTION_RELOAD_RULES를 호출합니다.
- SSID/BSSID/네트워크 식별자는 저장하지 않습니다. 신뢰 여부를 확인하지 못한 Wi-Fi는 public Wi-Fi로 처리하고, captive/unvalidated/기타 네트워크는 strict로 처리합니다.
- 종량제 네트워크에서는 automaticFilterUpdates=false 정책을 저장하지만 실제 updater scheduler 연동은 미완료입니다. roaming 자동 감지도 아직 false 고정이며 Travel 프로필은 현재 자동 판정되지 않습니다.
- Premium DEBUG 미리보기에서 자동 전환 토글을 제공합니다. Release는 PRO entitlement 및 Android device validation 전까지 자동 전환이 비활성입니다.
- 독립 Kotlin/JVM smoke 통과: public Wi-Fi, captive portal, metered cellular, roaming policy, unknown transport.
- 미완료: Android Gradle/JUnit, emulator/physical-device network callback, OEM capability behavior, trusted Wi-Fi 식별/사용자 지정, roaming, updater scheduler integration, PRO gate.


## 2026-11-24 — Guardian VPN 하위 물리 네트워크 선택
- 최신 security profile branch 커밋 `42f50c5ac8de2d6bdbcdbfad14e206cc795787e9`.
- 기본 네트워크 callback이 Guardian VPN 인터페이스(TRANSPORT_VPN)를 반환할 경우, `ConnectivityManager.allNetworks`의 비-VPN 물리 후보가 정확히 하나일 때만 자동 프로필 분류에 사용합니다. 후보가 0개 또는 여러 개면 실제 기본 경로가 불명확하므로 VPN capability/OTHER를 유지해 strict로 처리합니다.
- Android API signature stub 기반 Kotlin compile smoke 통과. 실제 VPN 활성 중 물리 network enumeration과 OEM별 callback은 아직 검증되지 않았습니다.


## 2026-11-24 — 보안 프로필 수동 적용 UI
- 개발 미리보기에 여섯 가지 수동 프로필 선택/적용 UI를 추가했습니다.
- 수동 프로필 적용 시 저장된 자동 전환 설정을 해제하고 NetworkCallback을 정리한 뒤 실제 필터 설정 및 실행 중 VPN 규칙 reload 경로에 반영합니다.
- Release/PRO entitlement는 아직 닫혀 있습니다. Android Gradle/JUnit·에뮬레이터·실기기 검증 전입니다.


## 2026-11-24 — VPN underlying physical network 판정 보수화
- VPN capability에서 여러 물리 네트워크 중 임의의 최고점 후보를 선택하던 코드를 제거했습니다. allNetworks는 실제 default underlying route를 보장하지 않으므로, 후보가 정확히 1개일 때만 자동 프로필을 분류합니다.
- 물리 후보가 0개 또는 2개 이상이면 reported VPN capability를 유지해 OTHER/strict로 처리합니다. 수동 HOME/WORK 등 직접 선택은 그대로 유지합니다.
- candidate count 0/1/2/negative 검증 테스트 추가. 실제 Android API/OEM/실기기 network handover 테스트는 미실행입니다.


## 2026-11-24 — 보안 프로필 정책 Kotlin/JVM smoke
- 최신 SecurityProfilePolicy의 자동 분류·종량제·underlying-network ambiguity·manual selection 경로를 kotlinc 1.9.0/JRE 21로 컴파일·실행했습니다.
- PASS 16/16: Wi-Fi trust, captive/unvalidated, roaming, cellular/ethernet, metered update disable, unknown strict, physical network count 0/1/2/negative, 수동 6개 프로필 모두 automatic filter downloads disabled.
- Android ConnectivityManager/NetworkCallback, VPN underlying route, OEM 전환, Android Gradle/JUnit·실기기 검증은 미실행입니다.


## 2026-11-24 — 보안 프로필 UI live state 동기화
- DNS/VPN status broadcast 및 화면 재진입 시 active profile label, auto-switch checkbox, automatic-filter-update flag 표시를 갱신합니다.
- Spinner의 미적용 사용자 선택은 dirty flag로 보호해 자동 profile callback이 선택값을 덮어쓰지 않습니다. 수동 Apply 시 자동 전환을 끄고, metering 정보가 없는 수동 선택은 automatic filter downloads를 허용하지 않습니다.
- 순수 SecurityProfilePolicy JVM smoke PASS 16/16. Android Gradle/JUnit, Android NetworkCallback 및 OEM/실기기 검증은 미실행입니다.


## 2026-11-24 — SecurityProfileChanged 브로드캐스트 추가
- 자동 network profile 전환에서 필터 설정이 같아 VPN reload가 생략되더라도, profile ID 또는 automatic updates permission이 바뀌면 별도 package-scoped `ACTION_PROFILE_CHANGED` broadcast를 전송하도록 했습니다.
- MainActivity는 해당 action을 등록해 현재 프로필/업데이트 허용 표시를 갱신합니다. HOME→MOBILE처럼 filter key가 동일한 ID 전환도 알립니다.
- 동일 상태에는 알림이 없고 profile ID/automatic-update permission 차이만 알리는 순수 policy 테스트 추가. Android Broadcast/OEM 기기 검증은 미실행입니다.


## 2026-11-24 — 프로필 상태 알림 정책 JVM smoke
- 최신 SecurityProfilePolicy helpers를 kotlinc 1.9.0/JRE 21로 컴파일·실행했습니다.
- PASS 19/19: 자동 프로필 분류, captive/unvalidated strict, roaming, metered download 제한, physical candidate ambiguity, manual profile auto-download opt-out, 같은 filter key 내 profile ID 변경 알림, 상태 불변 무알림, automatic update permission 변화 알림.
- Android `ACTION_PROFILE_CHANGED` broadcast 수신, ConnectivityManager, VPN 활성 중 OEM network handover, Android Gradle/JUnit·실기기 검증은 미실행입니다.


## 2026-11-24 — 자동 보안 프로필 판정 범위 공개
- UI가 현재 실제 연결 상태를 과장하지 않도록 trusted Wi-Fi/업무망 및 roaming은 자동 감지되지 않는다는 제한을 명시했습니다. 집·업무·여행 프로필은 수동 선택이며, 자동 필터 다운로드 스케줄러도 미연결입니다.


## 2026-11-24 — 수동 보안 프로필 적용 경쟁 상태 방지
- 수동 프로필 Apply가 automatic switch를 끈 직후 늦게 도착한 NetworkCallback이 선택을 덮어쓰지 않도록, switch 변경/수동 적용/자동 적용 메서드를 같은 store monitor로 직렬화했습니다.
- 자동 적용은 switch가 현재 false면 즉시 반환하고, 수동 적용만 공통 profile write/reload 함수를 직접 호출합니다. 순수 gate policy 테스트 추가. Android callback race 및 실기기 network handover 검증은 미실행입니다.


## 2026-11-24 — 자동 전환 스위치 변경 동기화 보강
- 사용자 체크박스 ON/OFF setter도 자동 적용/수동 적용과 같은 `SecurityProfileStore` monitor를 사용하도록 수정했습니다. setter OFF가 late callback의 상태 검사보다 먼저 완료되면 콜백은 적용되지 않습니다.
- 실제 ConnectivityManager callback race 재현 및 Android 기기 검증은 미실행입니다.


## 2026-11-24 — 보안 프로필 잠금 순서 교착 방지
- NetworkCallback monitor lock과 profile store lock이 반대 순서로 중첩될 수 있어 수동 Apply가 store lock을 보유한 상태에서 callback unregister를 호출하지 않도록 잠금 구간을 분리했습니다.
- manual OFF 상태를 먼저 store lock 아래 확정, callback 해제, 사용자 선택 프로필 적용 순서로 처리합니다. 대기 콜백은 automatic OFF gate에서 거부합니다. 실기기 concurrency 검증은 미실행입니다.


## 2026-11-24 — 보안 프로필 자동 필터 업데이트 게이트 연결
- SecurityProfileStore가 자동 업데이트 허용 플래그를 변경하면 FilterUpdateScheduler.sync를 호출해 예약 작업을 중단하거나 재등록합니다.
- Scheduler와 Worker에서 사용자 opt-in 및 profile permission을 모두 확인하도록 수정했습니다. Worker는 실행 시작과 base/security/Android-app-ad feed 시작 전 정책을 재확인해 metered/manual 전환 후 새 feed를 시작하지 않습니다.
- 초기 보호 시작을 위한 필수 cache population은 recurring scheduler와 별도이므로 계속 허용됩니다. 사용자 설정/프로필 조합 4가지 테스트를 추가했습니다. Android WorkManager·실기기 검증 미실행입니다.


## 2026-11-24 — 미설정 보안 프로필의 무료 업데이트 기본값 보존
- profile update permission key가 아직 없는 기존 사용자/초기 설치 상태에서는 active profile(기본 STRICT)의 permission을 기본값으로 사용하도록 변경했습니다. 기본 STRICT가 허용인 동안 기존 무료 auto-update 동작은 유지됩니다.
- 수동 프로필 적용으로 `false`가 명시 저장된 경우에는 그 값이 우선해 예약 작업을 취소합니다. 미설정 기본 허용/수동 명시 제한 테스트 추가; Android SharedPreferences·WorkManager 검증은 미실행입니다.
