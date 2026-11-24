# Guardian Android — Progress Tracker

- **전체 상태**: 개발 진행 중. 무료 Android 출시와 PRO 기능 완성 모두 미완료입니다.
- **무료 트랙**: `work/android-free-release-followup-20261124` — 최신 GitHub compare 기준 292 commits ahead, 0 behind. 기존 `work/android-free-release-20261124`의 후속 개발 브랜치입니다.
- **PRO 병렬 트랙**: `work/android-pro-premium-20261124` 및 #297~#301 전용 브랜치. Android 통합·단말 검증 전까지 Release 권한은 비활성입니다.
- **PR #290**: open, unmerged 상태 유지. 무료/PRO 작업은 별도 브랜치입니다.
- **격리 Kotlin/JVM 검증**: DNS 업스트림 진단 ThreadLocal 3/3, DNS TCP 정책/타이머/프레임/시퀀스 하네스 기록, 필터/앱 목록 정리 정책, DEX 041 헤더/컨테이너 11/11, 앱별 정리 12/12 등. 각 결과는 해당 순수 정책만 검증하며 Android 앱 빌드 통과를 의미하지 않습니다.
- **최근 수정**: `설정 > 고객 지원`에 개인정보를 제외한 최근 오류 내용 복사 기능과 UI/정책 회귀 테스트를 추가했습니다. 코드 반영은 완료했으나 Android Gradle/JUnit 실행 전입니다.
- **미실행 검증**: 최신 브랜치 Android Gradle/JUnit 전체, lint, emulator, 실기기 네트워크 및 UI, APK 생성·크기·SHA-256·서명·설치 검증은 미실행입니다.
- **실행 제약**: GitHub Actions 실행, runner/billing 설정 변경 및 유료 사용량 발생 작업은 사용자 승인 없이 하지 않습니다.
- **완료 게이트**: Android 빌드·JUnit·lint·에뮬레이터·Samsung 실기기 검증, APK 파일/서명/해시/설치 증거 및 한국어 배포·복구 문서 확보 전에는 완료로 판정하지 않습니다.

## 2026-11-24 — DNS TCP 수신 윈도 순서 검증 강화
- 클라이언트의 다음 수신 시퀀스 위치와 일치하는 세그먼트만 광고 수신 윈도 업데이트에 반영하도록 제한했습니다.
- 미래/순서가 어긋난 ACK가 윈도 업데이트 순서를 선점해 정상 ACK의 응답 전송을 막는 상황을 방지합니다.
- 미래 시퀀스 ACK 뒤에 정상 윈도 업데이트가 응답 데이터를 배출하는 회귀 테스트를 추가했습니다.
- 원격 소스·테스트·변경 이력 반영만 확인했습니다. Android Gradle/JUnit 및 기기 실행 검증은 미실행입니다.

## 2026-11-24 — DNS TCP 누적 ACK 이후 지연 중복 응답 방지
- 클라이언트가 DNS 응답 전체를 누적 ACK한 뒤 원래 요청 세그먼트가 늦게 중복 도착하면, 이미 확인된 응답 패킷을 다시 보내던 동작을 수정했습니다.
- 재전송 요청에는 미확인(outstanding) 서버 패킷만 재전송하고, 모두 ACK된 경우 현재 수신 위치를 담은 ACK만 반환합니다.
- 누적 ACK 후 지연 중복 요청이 응답 payload를 재생하지 않는 회귀 테스트를 추가했습니다.
- 소스/테스트 정적 반영만 수행했습니다. Android Gradle/JUnit, 에뮬레이터 및 실기기 검증은 미실행이며 Actions 실행은 시작하지 않았습니다.

- **2026-11-24 TCP 핸드셰이크 검증**: SYN-ACK 직후 오래된 ACK 번호로 전송된 DNS 데이터가 resolver에 도달하지 않도록 정확한 ACK 일치를 요구하도록 수정하고 회귀 테스트를 추가했습니다. Gradle/JUnit·실기기 검증 미실행.
- **2026-11-24 IPv6 파서 재확인**: 확장 헤더 파서는 누락된 것이 아니라 `DnsTcpPacket.kt`에 이미 공유 구현으로 존재했습니다. 별도 중복 파일 추가 시도는 제거했습니다. 컴파일 차단 이슈로 분류하지 않습니다.
- **2026-11-24 UDP 업스트림 보완**: 업스트림 UDP DNS 응답 수신 버퍼도 65,535바이트로 확장했습니다. 클라이언트 측 UDP 응답은 1,232바이트 안전 한도를 유지하고 초과 시 TC 응답으로 TCP 재시도를 유도합니다. 소켓 통합/실기기 검증은 미실행입니다.
- **2026-11-24 DNS TCP 크기 감사**: DNS 표준 최대 길이(65,535바이트)를 지원하도록 앱 내 TCP/DoT/DoH 경로와 TCP 세션 프레이밍을 확장했습니다. TCP 응답은 1,200바이트 단위로 분할합니다. 관련 회귀 테스트를 추가했으나 Android Gradle/JUnit·에뮬레이터·실기기 검증은 미실행 상태입니다.
- **저장소**: https://github.com/justinhblee-prog/guardian
- **작업 브랜치**: `work/android-free-release-20261124` (issue #293)
- **제품 목표**: Android 1.0의 VPN/DNS 광고·추적 차단, 필터 무결성/갱신, 앱별 보호 제외, 라이선스·고객지원, 복구 동작을 통합하고 검증 가능한 운영 서명 APK와 설치·복구 문서를 확보한다. 이후 Windows 정식 버전을 진행한다.
- **최종 산출물**: 운영 서명 APK 및 SHA-256/서명 지문/버전 기록, 소스 커밋, 테스트·에뮬레이터·Samsung 실기기 검증 기록, 한국어 설치·복구 가이드, 본 진행 추적 파일.
- **제약**: GitHub Actions 실행·유료 사용량 발생·runner/billing 설정 변경 금지. 운영 개인키/토큰/고객정보를 저장소에 기록하지 않는다. 외부 실기기·노트북에서만 가능한 작업은 실행한 것처럼 표시하지 않는다. 검증하지 않은 테스트는 통과로 표기하지 않는다.

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


## 2026-11-24 — Android 무료 기본 제품 트랙 분리
- 작업 브랜치: work/android-free-release-20261124 (기준: PR #290 HEAD e1a1b4f).
- 최신 변경: 4096ba6ab8dc1ab72a0e615658b7eb7c01b2561a.
- 라이선스 활성화 진입 후 Premium 복귀 버튼, 1차 Back 키보드 닫기, 2차 Back Premium 복귀, 회전/재생성 복귀 상태 보존을 추가했습니다.
- filter_rules.txt asset 누락 시 시작 전 검사와 VPN 서비스 내부 로더가 내장 긴급 규칙으로 전환합니다. 정상 전체 필터가 아닌 제한 모드임을 표시하고 원인을 진단 상태에 기록합니다.
- Android emulator 스크립트에 Back/복귀 E2E 단계를 추가했고 새 셸 블록은 bash -n 통과했습니다.
- 아직 미검증: Android Gradle/JUnit, 전체 emulator VPN/DNS E2E, 실제 단말, 최신 branch APK 및 updater download/install.
- 이전 기준 커밋 e1a1b4f의 Android workflow #2573은 build/unit/lint/emulator 성공이지만, 이 branch의 새 커밋을 검증한 결과로 재사용하지 않습니다.


## 2026-11-24 — 무료 진단의 baseline fallback 표시 보강
- 브랜치 `work/android-free-release-20261124` 최신 커밋: `a82e28edc9cd448d6702feabf001867bfa60c8d8` 이후 `ProtectionDiagnostics.filterReadinessSummary`가 baseline asset 누락 원인을 진단에서 숨기던 문제를 수정했습니다.
- 내장 긴급 DNS 규칙 사용과 기본 필터 파일 복구 필요를 명시하고, 옵션 필터 상태와 함께 표시합니다.
- 예외 클래스/원시 문자열/임의 도메인은 진단 결과에 복사하지 않습니다.
- 독립 `kotlinc` Kotlin/JVM smoke 통과: baseline 누락, baseline+선택 필터 누락, 민감 문자열 제거.
- Android Gradle/JUnit, 전체 VPN/DNS emulator E2E, 실기기, APK 및 updater download/install 검증은 미완료입니다.


## 2026-11-24 — 정식 Android updater 보안·버전 선택 보강
- 최신 무료 브랜치 커밋: `036be1f442b4a558ea7f9c39d624dd5e4674eaa9`.
- 정식 APK 다운로드 후 최종 URL이 HTTPS GitHub 공식 Release asset 호스트(`github.com`, `release-assets.githubusercontent.com`, `objects.githubusercontent.com`)인지 확인합니다. manifest/API 확인 응답도 공식 HTTPS GitHub 호스트만 허용합니다.
- Release API 반환 순서에 의존하지 않고 semver tag를 숫자로 비교해 가장 높은 stable tag를 선택합니다. 최종 설치 가능 여부는 manifest의 versionCode가 현재 앱보다 높은지 확인하고, tag/versionName 일치를 검증합니다.
- 독립 Kotlin/JVM smoke 통과: `v1.10.0 > v1.9.9`, 메이저 버전 비교, 동등/역순, 공식 asset redirect allowlist.
- 미완료: 실제 HTTP redirect, APK hash/signer/package 검증, 설치 화면/인플레이스 업데이트, Android Gradle/JUnit 및 emulator update E2E.


## 2026-11-24 — updater semver 입력 상한
- 최신 무료 브랜치 커밋 `08e9424a3b8e8d8d1d97810ff0ad7ae2c172e95c`.
- Release tag 숫자 구성요소가 9자리를 넘으면 stable 후보에서 제외해 비정상적인 BigInteger 입력을 제한합니다.
- Kotlin/JVM smoke 통과: 숫자 semver 정렬, prerelease 제외, 과도한 구성요소 거부. Android Gradle/JUnit 및 실제 Release API/redirect/설치 검증은 미완료입니다.


## 2026-11-24 — updater redirect 사전 검증
- 최신 무료 브랜치 커밋 `b281f463eec616604db20f30bb558fc3640e31e7`.
- 자동 redirect를 끄고 3xx Location을 수동으로 처리합니다. 다음 서버에 연결하기 전에 HTTPS scheme와 GitHub 공식 API/release asset host allowlist를 확인하고, 최대 5회까지만 허용합니다.
- 기존 사후 최종 URL 검사보다 안전하게, 비허용 host에 HTTP 요청을 보내기 전에 차단합니다. 실제 GitHub CDN redirect 및 APK 설치는 아직 검증되지 않았습니다.


## 2026-11-24 — updater redirect port allowlist
- 최신 무료 브랜치 커밋 `673c4991b9d6d8dbf8825ce3f6c46c152604006a`.
- GitHub 공식 호스트라도 HTTPS 기본 포트 443 이외의 포트는 redirect target으로 허용하지 않습니다.
- Kotlin/JVM smoke 통과: 공식 asset host, HTTP/비허용 host/userinfo/비표준 포트 차단.


## 2026-11-24 — emergency filter fallback regression tests
- Branch `work/android-free-release-20261124` now includes two additional `DomainFilterTest` cases.
- Verifies `resetToBootstrapRules()` replaces the prior active set (including custom rules), returns the expected 10-rule count, preserves subdomain blocking, and clears allow rules.
- Verifies every built-in bootstrap line parses into a valid block rule and can be loaded by the production rule engine.
- Test source and Changelog update committed; Android Gradle/JUnit execution remains pending. No CI run or APK build was triggered.


## 2026-11-24 Android 무료 버전 후속 — 기본 필터 손상 감지

- 보호 시작 사전 점검과 VPN 서비스 규칙 로딩에서 `filter_rules.txt`의 실제 유효 차단 규칙 수를 확인하도록 보강했습니다.
- 파일 누락뿐 아니라 빈 파일·주석만 있는 파일·파싱 후 유효 차단 규칙이 없는 손상 파일도 내장 긴급 규칙 10개로 대체합니다.
- MainActivity는 제한된 내장 규칙 모드임을 사용자에게 안내하고, 서비스는 필터 준비 진단에 손상/누락 사유를 남깁니다.
- `FilterStartupGateTest`에 손상된 기본 자산 거부 및 긴급 규칙 사용 가능성 회귀 테스트를 추가했습니다.
- 검증 한계: 소스 수정 및 원격 반영만 완료. Android Gradle/JUnit, lint, APK, 에뮬레이터, 실기기 검증은 미실행이며 Actions 비용이 발생하는 실행은 시작하지 않았습니다.


## 2026-11-24 후속 검수 — 기본 필터 규칙 수 오버플로

- `FilterStartupGate.hasUsableBundledBaseline`의 덧셈 기반 판정을 개별 양수 비교로 변경해 정수 오버플로 가능성을 제거했습니다.
- 극단 카운트 회귀 테스트 추가. Android Gradle/JUnit은 실행하지 않았습니다.


## 2026-11-24 — 정식 앱 업데이트 자산 경로 검증 강화
- Free branch: `work/android-free-release-20261124`.
- `GuardianStableUpdatePolicy.isOfficialReleaseAssetUrl`를 추가해 Release URL의 HTTPS/443, github.com, 저장소 경로, 선택된 semantic tag, 정확한 자산 파일명을 함께 검사합니다.
- Manifest와 APK URL 각각에 검증을 적용하고, 태그 불일치·잘못된 파일명·경로 추가·query·비공식 host·prerelease tag를 거부하는 테스트를 추가했습니다.
- 검증: 원격 소스 재조회 완료, Kotlin/JVM smoke 6/6 통과. Android Gradle/JUnit 및 전체 앱 빌드는 아직 실행하지 않았습니다. GitHub Actions는 시작하지 않았습니다.


## 2026-11-24 — 앱 업데이트의 미게시 상태와 최신 상태 구분
- 정식 Release API에 stable 후보가 없으면 '최신 버전'으로 오인시키지 않고 오류로 분리합니다.
- 설치된 versionCode가 최신 공개 정식 버전 이상일 때만 '최신 공개 버전 이상'으로 안내합니다.
- 소스 반영 완료. Android Gradle/JUnit, lint, APK 및 기기 UI 검증 미실행.


## 2026-11-24 — 필터 최소 규칙 수 정수 오버플로 방지
- Optional feed와 다운로드된 일반/서명 필터의 차단 규칙 수 비교를 Long 합산으로 변경했습니다.
- 음수/0 최소값 및 Int 상한 경계 테스트를 추가했습니다.
- 소스 반영 완료, Android JUnit/빌드 미실행.


## 2026-11-24 — DNS TCP receive-window flow control
- Free branch `work/android-free-release-20261124`: `DnsTcpSessionManager` now queues response bytes and sends only within the client's advertised 16-bit receive window after accounting for unacknowledged bytes.
- Subsequent ACK/window updates drain queued response bytes in <=1,200-byte IPv6-safe segments; zero-window responses remain queued rather than being discarded. TCP window scaling is not negotiated, so the implementation deliberately uses the raw 16-bit window.
- Client FIN processing is ordered after pending DNS response bytes; the server FIN is deferred until queued data has been sent and window capacity is available.
- Added a regression test for a 5KB response held at zero window and delivered over three 2,048-byte window openings.
- Source/test/docs committed remotely. Android Gradle/JUnit, lint, emulator, and physical-device tests have not run. No Actions run or APK build was started.


## 2026-11-24 — DNS TCP 윈도우 업데이트 순서 보호
- `DnsTcpSessionManager`에 마지막으로 수락한 peer window의 SEG.SEQ/SEG.ACK 기준을 저장하고, TCP serial-number ordering에 따라 오래된 윈도우 광고를 무시하도록 보완했습니다.
- 낮은 SEG.SEQ 및 동일 SEG.SEQ + 낮은 SEG.ACK 조합으로 수신 윈도우가 축소되지 않는 회귀 테스트를 추가했습니다.
- 원격 반영 확인 후 Android Gradle/JUnit 실행은 하지 않았습니다. CI/비용 발생 작업도 실행하지 않았습니다.


## 2026-11-24 — DNS TCP 응답 재전송 보존
- 송신한 TCP 응답 패킷을 누적 ACK 전까지 보관하고, 원래 질의 세그먼트가 재전송되면 미확인 응답 패킷을 그대로 재전송하도록 보완했습니다.
- ACK로 확인된 응답은 재전송 목록에서 정리합니다. 회귀 테스트를 추가했습니다.
- 소스/테스트/문서의 원격 반영만 확인했으며 Android 테스트/빌드 및 실기기 검증은 수행하지 않았습니다.


## 2026-11-24 — DNS TCP 대기 응답 큐 상한
- 단일 TCP payload에 여러 DNS 요청이 포함되어 각 응답이 커지는 경우에도 대기 응답 메모리가 무제한 증가하지 않도록 65,537바이트 상한을 적용했습니다.
- 큐 상한 초과 시 세션을 안전하게 종료하는 회귀 테스트를 추가했습니다. Android 테스트/빌드 미실행.


## 2026-11-24 — DNS 통계 그래프와 실패 카운터 일치
- 활동 그래프에 `KEY_DNS_FAILED` 업스트림 실패 카운터를 포함했습니다. 기존 실시간 통계 숫자와 그래프에서 실패가 누락되는 표시 불일치를 해소했습니다.
- 5개 행을 동적으로 배치하고 그래프 제목을 DNS 처리 결과로 변경했습니다. Android 빌드/화면 검증은 미실행입니다.

- DNS 통계 상단에 실패(SERVFAIL)와 무시(형식/질문 검증 실패)의 정의를 명시했습니다.


## 2026-11-24 — DNS TCP 미래 순번 ACK 거부
- 무료 Android 브랜치에서 TCP 수신 순번보다 앞선 out-of-order 세그먼트가 유효해 보이는 ACK를 함께 보내면, 기존 처리 순서상 서버 응답의 미확인 재전송 패킷이 먼저 제거될 수 있음을 확인했습니다.
- ACK를 반영하기 전에 미래 클라이언트 순번을 거부하도록 순서를 수정하고, 미래 순번 ACK 이후 원래 DNS 요청 재전송 시 모든 미확인 응답 세그먼트가 그대로 재전송되는 회귀 테스트를 추가했습니다.
- 소스/테스트/문서 원격 반영만 수행했습니다. Android Gradle/JUnit, lint, emulator, 실기기, APK 검증은 수행하지 않았습니다. Actions 실행 및 비용 발생 작업은 시작하지 않았습니다.


## 2026-11-24 — DNS TCP ACK 수용 순번 엄격화
- 현재 수신 순번과 다르고 마지막 수락 세그먼트의 정확한 재전송도 아닌 오래된/미래 순번 패킷은 ACK 상태를 변경하지 않도록 제한했습니다.
- 정상적인 마지막 요청 재전송만 미확인 응답 재전송 트리거로 허용합니다. 회귀 테스트는 future ACK와 stale ACK가 응답 패킷을 제거하지 않는지 검증하도록 확장했습니다.
- 소스·테스트·문서 원격 반영 완료. Android Gradle/JUnit, lint, emulator, 실기기 및 APK 검증은 미실행입니다.


## 2026-11-24 — DNS TCP 최대 프레임과 다음 질의 병합 처리
- 최대 크기 65,535바이트 DNS 질의의 마지막 TCP 조각과 다음 DNS 질의가 같은 세그먼트에 함께 도착할 때, 단일 미완성 프레임 상한을 전체 임시 버퍼에 잘못 적용해 RST하던 경로를 수정했습니다.
- 입력은 프레임 단위로 모두 파싱한 뒤, 남은 미완성 프레임에만 65,537바이트 제한을 적용합니다. 출력 대기 큐 상한과 세그먼트별 상한은 유지합니다.
- 최대 DNS 프레임 + 다음 질의를 결합 전송하는 회귀 테스트를 추가했습니다. Android Gradle/JUnit·lint·emulator·실기기 검증은 미실행입니다.


## 2026-11-24 — DNS TCP 회귀 검증 정밀화
- 최대 프레임 + 다음 질의 병합 테스트에서 resolver가 받은 첫 메시지의 전체 길이와 두 번째 메시지의 바이트 일치를 확인하도록 강화했습니다.
- ACK 순번 설명 주석의 중복 텍스트를 정리했습니다. Android Gradle/JUnit·단말 검증은 미실행입니다.


## 2026-11-24 — DoT/DoH 업스트림 실패 진단 구분
- DoT invalid response length 및 transaction/question mismatch와 DoH invalid TLS host/request format/HTTP status-header-content-type-body framing/DNS response mismatch를 구분해 lastUpstreamFailure에 기록합니다.
- TCP upstream의 DNS 질의 길이 검증 실패 진단도 추가했습니다. TLS host verification, SNI, manual HTTP response parser, redirect rejection 및 payload bound는 변경하지 않았습니다.
- Android Gradle/JUnit·에뮬레이터·실기기 업스트림 연결 검증은 미실행입니다.


## 2026-11-24 — DNS TCP 중복 ACK 빠른 재전송
- 세 번의 중복 누적 ACK가 현재 serverAcknowledged에서 반복되고 미확인 서버 응답이 남아 있으면 첫 outstanding packet을 재전송합니다. ACK 진행 시 중복 횟수는 초기화됩니다.
- 최초 손실 세그먼트와 부분 ACK 이후 다음 세그먼트 재전송 테스트를 추가했습니다. 당시 RTO는 별도 미완료였으나, 후속 `retransmitDue()`·서비스 스케줄러 구현 항목에서 보강했습니다. Android Gradle/JUnit·실기기 검증은 미실행입니다.


## 2026-11-24 — Fast retransmit 1회 발사 제한
- 같은 누적 ACK에 대해 세 번째 duplicate ACK에서만 빠른 재전송을 실행하고, 네 번째 이후 반복 ACK로 같은 패킷을 연속 송신하지 않도록 카운터를 4에서 제한합니다.
- 네 번째 duplicate ACK 무재전송 회귀 검증을 추가했습니다. Android Gradle/JUnit 미실행입니다.


## 2026-11-24 — DNS TCP duplicate ACK 조건 정밀화
- 빠른 재전송 duplicate ACK 조건에 동일한 광고 수신 윈도와 payload/FIN 부재를 추가했습니다. 윈도 변경 또는 데이터 포함 ACK는 duplicate ACK 연속 횟수를 초기화합니다.
- 윈도 변경 패킷 자체는 카운트하지 않고 이후 동일 윈도 ACK 3회 이후 재전송하는 테스트를 추가했습니다. Android Gradle/JUnit 미실행입니다.


## 2026-11-24 — DNS TCP timer-based retransmission
- 세션별 oldest outstanding response/FIN의 송신 시각을 기록하고 1s -> 2s -> 4s -> 8s RTO backoff를 적용합니다. 최대 5회 재전송 후 해당 TCP 세션을 정리합니다.
- 세션당 한 번에 가장 오래된 미확인 segment만 재전송하며, fast retransmit도 타이머/attempt 상태를 공유합니다. 누적 ACK는 확인된 패킷을 제거합니다.
- Android VPN packet loop와 별도 daemon scheduler(250ms tick)를 연결하고, 같은 TUN descriptor output을 synchronized block으로 직렬화합니다. loop 종료 때 scheduler를 종료합니다.
- RTO deterministic clock 테스트 2개를 추가했습니다. Android Gradle/JUnit·lint·emulator·실기기 검증은 아직 미실행입니다.


## 2026-11-24 — TCP ACK progress에 따른 RTO timer restart
- 서버가 여러 segment를 동시에 보낸 경우 누적 ACK 진행 후 다음 oldest outstanding의 timer 기준을 ACK 시각으로 재설정합니다. 전송 직후 첫 segment가 ACK되었다고 다음 segment를 즉시 오재전송하지 않도록 했습니다.
- deterministic test를 1,999ms까지 무재전송, 2,000ms에서 다음 segment 재전송으로 보정했습니다. scheduler write lambda 및 background failure 우선순위도 정리했습니다.


## 2026-11-24 — DNS TCP RST sequence validation
- RST 세그먼트가 4-tuple만 일치해도 세션을 삭제하던 경로를 현재 clientNext sequence 일치 조건으로 제한했습니다.
- stale RST 이후 세션 및 outstanding answer 재전송이 유지되는 테스트와 exact-current-sequence RST 종료 테스트를 추가했습니다. Android Gradle/JUnit·단말 검증은 미실행입니다.


## 2026-11-24 — DNS TCP 타이머 기반 RTO 및 서비스 스케줄러 반영 확인
- 현재 `DnsTcpSessionManager`는 세션별 oldest outstanding 응답/FIN의 송신 시각과 재전송 횟수를 보관하며 1s→2s→4s→8s backoff를 사용합니다. 최대 5회 재전송 후 세션을 정리합니다.
- `GuardianVpnService.packetLoop`는 250ms 간격 스케줄러로 `retransmitDue()`를 호출하고, 패킷 출력 스트림 접근을 동기화합니다. packet loop 종료 시 스케줄러를 종료합니다.
- deterministic clock 테스트는 RTO backoff/재시도 한도와 누적 ACK 이후 기존 타이머 취소 및 다음 미확인 세그먼트 타이머 재시작을 확인합니다. 소스/테스트 정적 재조회만 수행했으며 Android Gradle/JUnit·실기기 실행은 미실행입니다.


## 2026-11-24 — 하단 내비게이션 시스템 인셋 단일 소유
- 휴대전화·콤팩트 태블릿에서 root와 bottomNav가 bottom system inset을 이중 반영하는 경로를 제거했습니다. bottom navigation은 839dp 이하에서만 인셋을 처리하고, navigation rail 사용 840dp 이상은 root가 처리합니다.
- API 30+ systemBars 및 레거시 systemWindowInset 구현에 동일 정책을 적용하고 breakpoint 단위 테스트를 추가했습니다. 실제 화면/에뮬레이터 검증은 미실행입니다.


## 2026-11-24 — 재전송 스케줄러 종료 동기화
- packet loop finally에서 RTO scheduler를 취소한 뒤 최대 500ms 종료 대기를 수행하고, InterruptedException 시 현재 스레드 인터럽트 플래그를 복원합니다.
- 종료 직후 TUN descriptor write 경합을 줄이기 위한 cleanup이며, Android service lifecycle·단말 검증은 미실행입니다.


## 2026-11-24 — DnsTcpPacket 버전별 응답 길이 상한
- 응답 빌더가 IPv4 total_length와 IPv6 payload_length를 구분하지 않고 payload 65,515바이트를 공통 허용하던 상한을 수정했습니다. IPv4 65,495, IPv6 65,515바이트가 최대입니다.
- 최대 경계 응답 생성/길이 필드/재파싱과 각각 1바이트 초과 거부 테스트를 추가했습니다. Android Gradle/JUnit·실기기 검증은 미실행입니다.


## 2026-11-24 — DnsTcpPacket IP별 응답 최대 길이 Kotlin/JVM smoke
- 현재 수정한 `DnsTcpPacket.response` 경계와 TCP/IP checksum/parser helper를 Kotlin/JVM으로 컴파일해 실행했습니다.
- PASS 7/7: IPv4 65,495-byte TCP payload로 total_length=65,535 생성 및 재파싱, IPv4 초과 1바이트 거부, IPv6 65,515-byte TCP payload로 payload_length=65,535 생성 및 재파싱, IPv6 초과 1바이트 거부.
- 이는 DnsTcpPacket codec의 isolated Kotlin/JVM 검증이며 Android Gradle/JUnit, GuardianVpnService 통합 및 실기기 패킷 검증은 미실행입니다.


## 2026-11-24 — DNS TCP codec/session 독립 Kotlin/JVM smoke
- 현재 free branch의 DnsTcpPacket codec 및 DnsTcpSessionManager 동작을 Android 의존성 없이 kotlinc로 컴파일해 deterministic packet/clock harness에서 실행했습니다. DomainFilter.makeServfail만 비상 경로 stub이며 이 smoke에서는 resolver가 정상 응답을 반환합니다.
- PASS 25/25: SYN/SYN-ACK handshake/session, MSS 1,200바이트 응답 분할, duplicate ACK 3회 fast retransmit/4번째 억제, 1·2·4·8초 RTO backoff 5회, retry budget 소진 시 세션 종료, 부분 누적 ACK 후 다음 outstanding 타이머 재시작, zero-window 응답 대기/2KiB window open 배출, 최대 65,535-byte DNS 프레임과 다음 질의 동시 프레이밍 및 응답 생성.
- 결과는 순수 Kotlin/JVM codec/session smoke PASS입니다. Android Gradle/JUnit, GuardianVpnService 250ms scheduler·TUN write 통합, 실제 IPv4/IPv6 VPN 트래픽 및 실기기 수용시험은 미실행입니다.


## 2026-11-24 — DNS TCP 응답 Parsed 주소 길이 검증
- DnsTcpPacket.response에서 IP 버전에 따른 source/destination 길이를 모두 명시적으로 검증합니다. malformed Parsed 입력이 배열 복사 예외로 처리되지 않도록 테스트를 추가했습니다. Android Gradle/JUnit 미실행입니다.


## 2026-11-24 — DNS TCP 응답 길이/주소 검증 확장 Kotlin/JVM smoke
- DnsTcpPacket.response의 버전별 최대 길이와 source/destination 주소 길이 검증을 반영한 코덱을 Kotlin/JVM으로 재실행했습니다.
- PASS 9/9: IPv4/IPv6 최대 IP length 필드 및 checksum/parser 재수용, 양 버전 최대 payload 초과 거부, malformed IPv4 destination/IPv6 source 주소 크기 거부.
- 이 결과는 독립 코덱 smoke이며 Android Gradle/JUnit·VPN service 통합 및 실기기 패킷 검증은 미실행입니다.


## 2026-11-24 — DNS TCP codec/session 독립 Kotlin/JVM 재검증
- 현재 무료 브랜치의 `DnsTcpPacket` 및 `DnsTcpSessionManager` 순수 Kotlin 코드를 Android 의존성 없이 kotlinc로 컴파일하고 deterministic packet/clock harness에서 실행했습니다. resolver 성공 응답 경로를 사용했고 SERVFAIL 예비 경로는 stub 처리했습니다.
- PASS 25/25: SYN/SYN-ACK 세션, MSS 1,200바이트 분할, 세 번째 중복 ACK fast retransmit 및 반복 억제, RTO 1·2·4·8초 backoff와 5회 재시도 한도, 부분 ACK 이후 다음 outstanding timer 재시작, zero-window 응답 대기 및 2KiB window open 배출, 최대 65,535바이트 DNS 프레임과 다음 질의 coalescing.
- 이어서 `DnsTcpPacket.response` 코덱 경계도 PASS 9/9: IPv4/IPv6 최대 길이 필드·체크섬·재파싱, 양 버전 최대 길이 초과 거부, malformed IPv4/IPv6 Parsed source/destination 주소 길이 거부.
- 이 결과는 isolated Kotlin/JVM codec/session smoke입니다. Android Gradle/JUnit·lint, `GuardianVpnService` RTO scheduler/TUN 통합, emulator, 실제 IPv4/IPv6 VPN 트래픽 및 Samsung 단말 검증은 미실행입니다. GitHub Actions는 실행하지 않았습니다.


## 2026-11-24 — DNS 통계 카운터 overflow 방어
- `recordDnsActivity`와 `recordIgnoredPacket`의 모든 Long 누적을 포화형 non-negative `DnsStatsCounterPolicy`로 통일했습니다.
- 손상/음수 저장값은 0 기준으로 복구하고 Long.MAX_VALUE를 넘지 않도록 합니다. pure policy 테스트를 추가했으며 Android SharedPreferences/JUnit은 미실행입니다.


## 2026-11-24 — DNS 통계 카운터 독립 Kotlin/JVM smoke
- 현재 `DnsStatsCounterPolicy.kt`를 kotlinc로 컴파일하고 실행했습니다.
- PASS 8/8: 0→1 증가, 일반 증가, zero increment, 음수 저장값 복구, Long.MIN_VALUE 복구, Long.MAX_VALUE 유지, 상한 근접 포화, 음수 증가량 무시.
- GuardianVpnService의 `recordIgnoredPacket` 및 `recordDnsActivity`가 모두 해당 helper를 사용하도록 연결된 것을 원격 소스에서 재확인했습니다. Android SharedPreferences/JUnit·실기기 통계 검증은 미실행입니다.


## 2026-11-24 — 기본 필터 asset Gradle preBuild guard
- Android assets가 `../../shared`를 사용하므로 필수 파일은 repo root `shared/filter_rules.txt`입니다. Gradle `preBuild` 전에 존재·크기·유효한 DNS label을 가진 `block=`/`block-important=` 규칙 포함 여부를 확인하도록 validation task를 연결했습니다.
- 파일 누락/빈 파일/유효 block 규칙 부재는 빌드 오류가 됩니다. sourceSet 및 task 소스는 반영했으나 Android Gradle 실행·APK asset 내부 확인은 미실행입니다.


## 2026-11-24 — 기본 DNS 필터 asset 빌드 가드 독립 검증
- `android/app/build.gradle.kts`의 `preBuild` guard가 repo root `shared/filter_rules.txt`를 asset 경로와 같은 상대 경로로 확인하고, 파일 존재/비어 있지 않음/유효한 DNS block 도메인을 검사합니다.
- Gradle Kotlin validator와 동일한 정규식·BOM 제거 로직을 Kotlin/JVM으로 시험했습니다. PASS 5/5: 공유 asset 10개 규칙, UTF-8 BOM 포함 규칙, 주석/allow만 존재하는 파일 거부, 공백 포함 잘못된 도메인 거부, 연속 점 도메인 거부.
- `GuardianVpnService` 및 `MainActivity`는 asset 부재/손상 시 emergency baseline으로 보호 시작을 시도하고, optional 다운로드 캐시는 시작 필수 조건으로 취급하지 않음을 재확인했습니다. 실제 Gradle `preBuild`, APK 내부 asset 존재, Android 단말 동작은 아직 미실행입니다.


## 2026-11-24 — 필수 필터 asset validator 도메인 길이 일치
- Gradle preBuild guard가 각 label 63자뿐 아니라 정규화된 DNS 도메인 전체 253자 제한도 확인하도록 보완했습니다. 런타임 FilterRuleParser의 도메인 길이 검증과 일치합니다.


## 2026-11-24 — 공유 DNS asset 빌드 가드 정책 Kotlin/JVM 검증 확장
- Gradle validator와 동일한 BOM 제거, `block=`/`block-important=` 인식, label별 DNS syntax 및 전체 253자 제한을 사용해 kotlinc harness를 실행했습니다.
- PASS 6/6: 현재 공유 baseline 10개 규칙, UTF-8 BOM이 붙은 정상 규칙, 주석/allow만 있는 asset 거부, 공백 포함 invalid domain 거부, 연속 점 거부, 전체 253자 초과 거부.
- Android Gradle `preBuild` 실행이나 APK 내부 assets 검사는 하지 않았으며, 이 결과는 validator 로직의 isolated Kotlin/JVM smoke입니다.


## 2026-11-24 — 필터 캐시 교체/복구 Kotlin/JVM smoke
- 현재 `FilterCacheStore` 순수 파일 교체/복구 코드를 Kotlin/JVM으로 컴파일해 임시 파일 시스템에서 실행했습니다.
- PASS 9/9: 정상 신규 교체, 메타데이터 커밋 실패 후 기존 캐시/rollback 보존, 교체 중 프로세스 중단 시 backup 복구, 서명 hash 불일치 target에서 backup 복구, hash 일치 target 유지 및 backup 제거, hash 불일치 단독 target fail-closed, stale backup 정리, oversized unsigned target에서 bounded backup 복구, oversized target+backup 없음 거부.
- 결과는 로컬 JVM 파일시스템 smoke입니다. Android SharedPreferences 동시성·실제 앱 내부 저장소/OEM 파일시스템·Android Gradle/JUnit은 미실행입니다.


## 2026-11-24 — DNS TCP sequence wrap-around 테스트 추가
- 클라이언트와 서버 순번을 0xFFFFFFFF 부근에서 시작해 양쪽 sequence space가 0을 넘는 DNS 응답 및 부분 누적 ACK/fast retransmit 동작을 검증하는 JUnit 소스를 추가했습니다.
- Android Gradle/JUnit 실행은 미실행입니다.


## 2026-11-24 — DNS TCP wrap-around 보조 Kotlin/JVM 실험
- client ISN=`0xFFFFFFFC`, server ISN=`0xFFFFFFFE`로 고정해 clientNext가 28로, server response sequence가 `0xFFFFFFFF`에서 0을 지나도록 구성했습니다.
- PASS 11개 wrap 전용 assertion 포함 전체 36/36: 3개 MSS 응답 분할, 첫 세그먼트 끝 ACK가 1199로 wrap, 해당 ACK 수락, 첫 outstanding segment 제거, 중복 ACK 세 번 뒤 두 번째 segment 재전송.
- 이 harness는 이전에 로컬 추출한 Kotlin/JVM codec/session 스냅샷으로 실행했으며 최신 원격 blob과 바이트 동일성은 확인하지 않았습니다. 최신 branch에는 같은 시나리오의 JUnit 테스트 소스를 추가했지만 Android Gradle/JUnit·emulator·실기기 테스트는 미실행입니다.


## 2026-11-24 — DNS TCP exact request replay RTO 동기화
- 원본 DNS 요청 재전송에 따른 응답 replay도 RTO retry count/time을 갱신하도록 했습니다. retry 한도가 지난 미확인 세그먼트가 있으면 세션을 종료해 repeated request로 timer budget을 우회하지 못하게 합니다.
- fake clock 테스트에서 500ms 시점 exact replay 이후 기존 1초 RTO가 발화하지 않고 2초 backoff가 2,500ms에 도래하는지 확인하도록 했습니다. Android Gradle/JUnit 미실행입니다.


## 2026-11-24 — exact DNS 요청 재전송 예산 상한 테스트
- 원본 DNS 요청 중복 재생 5회까지는 캐시 응답 재전송, 여섯 번째 요청에서는 세션 종료 및 무응답을 확인하는 regression test를 추가했습니다.
- Android Gradle/JUnit·실기기 테스트는 미실행입니다.


## 2026-11-24 — 최초 필터 캐시 실패 자동 복구
- 메인 필터 캐시가 없는 최초 다운로드 실패에서만 one-time WorkManager 복구 작업을 30초 뒤 예약합니다. NetworkType.CONNECTED 및 exponential 30초 backoff가 적용되며 동일 작업은 unique KEEP으로 중복되지 않습니다.
- 이 작업은 forceInitialRefresh 입력값으로 recurring auto-update opt-out과 분리됩니다. 사용자가 자동 업데이트를 꺼도 최초 필수 메인 캐시 복구는 시도하고, 기존 캐시가 있는 단순 refresh 실패는 예약하지 않습니다.
- 메인 수동 업데이트가 성공하면 대기 중 복구 작업을 취소합니다. 정책/지연 테스트 소스 추가. Android Gradle/JUnit·WorkManager 통합·네트워크 전환 단말 검증은 미실행입니다.


## 2026-11-24 — 최초 필터 캐시 복구 재시도 상한
- WorkManager 최초 복구 작업을 최초 실행 + 최대 5회 재시도(총 6회)로 제한하고, 예산을 소진하면 `Result.failure()`로 종료합니다. 일반 periodic refresh 동작은 변경하지 않습니다.
- 수동/전경 업데이트가 새로 시작되면 대기 중인 initial retry를 취소하며, 실패 후 메인 캐시가 없을 때만 새 복구 작업을 예약합니다.
- 재시도 budget helper 테스트를 추가했습니다. Android Gradle/JUnit·WorkManager 통합·네트워크 전환 기기 테스트는 미실행입니다.


## 2026-11-24 — 최초 필터 캐시 복구 정책 Kotlin/JVM smoke
- `FilterUpdatePolicy`의 실제 새 helper 로직을 Kotlin/JVM으로 컴파일·실행했습니다.
- PASS 12/12: auto-update opt-out에서 강제 최초 복구 허용, 일반 periodic opt-in/opt-out, 같은 프로필 cache만 재사용, profile mismatch/파일 누락 처리, 첫 attempt+5 retries 상한, 일반 periodic work는 initial retry budget에 영향받지 않음, invalid runAttempt/maxAttempts 방어.
- WorkManager constraints/unique work scheduling, Android Gradle/JUnit, emulator/실기기 네트워크 복귀는 미실행입니다.


## 2026-11-24 — 앱 업데이트 독립 설정 메뉴
- `설정 > 앱 업데이트`를 별도 카테고리로 추가해 휴대전화 설정 목록 및 태블릿/넓은 화면 사이드바에서 직접 접근하게 했습니다. 기존 정보 화면 바로가기와 업데이트 확인·다운로드/검증·설치 기능은 유지합니다.
- 설정 화면 상태 복원에도 `settings-updates` 경로를 포함했습니다. Android UI/emulator/실기기 검증은 미실행입니다.


## 2026-11-24 — 업데이트 설정 메뉴 및 배포 유형별 안내 정합성
- `설정 > 앱 업데이트`를 휴대전화 메뉴/태블릿 사이드바의 독립 진입점으로 추가하고 설정 복원 경로를 연결했습니다. 정보 화면의 기존 바로가기도 유지합니다.
- 직접 배포(direct)는 서명 manifest 검증 업데이트를 앱 안에서 확인하도록 하고, 스토어 release는 설치한 공식 스토어에서 관리된다는 문구를 표시합니다. REQUEST_INSTALL_PACKAGES 권한은 기존대로 debug/direct source manifest에만 존재하고 공통 main/release manifest에는 포함되지 않습니다.
- 화면 소스 재조회로 메뉴·상태 복원·버튼 상태를 확인했습니다. Android UI/emulator/실기기 및 manifest merge 검증은 미실행입니다.


## 2026-11-24 — 최초 필터 복구 종료 상태 및 cache availability 진단
- 초기 cache retry 예산을 소진하면 Worker가 전용 error code를 저장하고 filter state broadcast를 보내도록 했습니다.
- UI는 selected profile과 cached profile key가 일치하는 유효 캐시가 있을 때만 '이전 목록 유지'라고 표시합니다. 선택한 main cache가 없으면 내장 10개 기본 규칙 기반 제한 보호 상태를 명확히 표시합니다.
- retry exhausted/cache present/cache absent/no error 표시 정책 테스트 추가. Android Gradle/JUnit/WorkManager/단말 검증은 미실행입니다.


## 2026-11-24 — 초기 필터 복구 종료 상태·캐시 진단 JVM smoke
- 초기 필터 복구 재시도 소진 시 Worker가 전용 진단 코드를 저장하고 UI 상태 broadcast를 보냅니다. UI는 선택한 필터 캐시가 없을 때 '이전 목록 유지'라고 오인시키지 않고, 내장 기본 규칙 기반 제한 보호 상태로 표시합니다.
- FilterUpdatePolicy의 실제 helper 로직을 반영한 kotlinc 1.9.0/JRE 21 harness PASS 19/19: opt-out 강제 최초 복구, 동일/불일치 profile cache, 6회 실행 예산, optional cache 보존/비활성화, last error clear conditions, due policy, 캐시 유무별 UI 상태 문구.
- Android Gradle/JUnit, WorkManager scheduling, MainActivity compile/render, network restore 및 실기기 테스트는 미실행입니다.


## 2026-11-24 — DNS 업스트림 진단 스레드 분리
- preflight, 수동 업스트림 연결 테스트, VPN packet worker가 공유하던 `lastUpstreamFailure` 전역 변수의 동시 쓰기 오염 가능성을 수정했습니다.
- 기존 call-site는 유지하고 ThreadLocal backing context를 사용합니다. `forwardDns()` 작업 초기화는 현재 스레드 값만 삭제하므로 다른 DNS 작업의 오류 원인을 보존합니다.
- 동시 작업 간 오류 격리·스레드별 clear 테스트를 추가했습니다. Android service thread, 실기기 네트워크, Android Gradle/JUnit은 미실행입니다.


## 2026-11-24 — DNS 업스트림 진단 ThreadLocal JVM smoke 3/3
- `DnsThreadFailureContext` 실제 구현과 동일한 코드를 kotlinc/JRE 환경에서 컴파일·실행했습니다. PASS 3/3: 동시 요청의 실패 원인 격리, 스레드별 clear 격리, null 제거 동작.
- Android Service 통합 컴파일, actual packet/preflight/upstream-test thread 실행 및 Android Gradle/JUnit은 미실행입니다.


## 2026-11-24 — 정식 Android 업데이트 정책 JVM smoke 16/16

- `GuardianStableUpdatePolicy`에 대해 kotlinc/JRE 21 독립 컴파일·실행 PASS 16/16: stable/draft/prerelease 구분, semantic tag 순서, versionCode 비교, SHA-256 형식, 정확한 공식 저장소/tag/manifest·APK 자산 경로, tag·asset·query 불일치 거부.
- 이는 순수 버전·URL 정책 검증입니다. Android HTTPS 연결·리디렉션 실제 서버 통신, manifest 서명/키 운영, APK 다운로드·서명/설치 및 Android Gradle/JUnit은 미실행입니다.


## 2026-11-24 — 정식 APK FileProvider 경로 오류 수정
- `GuardianStableUpdateManager`가 cache/stable-updates에 APK를 저장했지만 FileProvider XML에는 cache/updates만 등록된 불일치를 수정했습니다. `guardian_stable_updates` cache-path를 등록했고 개발/정식 업데이트 디렉터리 이름을 공통 상수화했습니다.
- 다운로드 후 FileProvider URI 생성/Android 시스템 설치 화면은 Android Gradle·기기 실행 환경이 없어 미검증 상태입니다.


## 2026-11-24 — Android 업데이트 캐시 경로 정책 JVM smoke 3/3

- 공통 `GuardianUpdateCachePaths`를 kotlinc/JRE 21로 분리 컴파일·실행했습니다. PASS 3/3: 개발판 `updates`, 정식판 `stable-updates`, 두 경로의 분리.
- XML resource의 실제 FileProvider URI 처리, Android 설치 시스템 화면 및 APK 재설치는 Android 환경/실기기에서 아직 미검증입니다.


## 2026-11-24 — DNS 업스트림 후보별 실패 사유 보존
- DNS fallback 각 후보를 시도하기 전 현재 작업 스레드의 오류를 비우고 후보별 실패 사유를 수집합니다. 모든 후보가 실패하면 순번이 있는 최대 6개 원인을 합쳐서 전달하도록 수정했습니다.
- 서버 IP는 로그에 포함하지 않으며 항목 240자, 전체 1,200자 상한을 둡니다. 요약 정책 테스트 4건 추가. Android UDP/TCP/DoT/DoH 네트워크 실측 및 Android Gradle/JUnit은 미실행입니다.


## 2026-11-24 — DNS 업스트림 요약 길이 테스트 픽스처 정정
- 6개 항목/240자/전체 1,200자 상한을 검증해야 하는 테스트에서 240자 항목을 2개만 넣어 1,200자 도달을 기대하던 픽스처를 6개 항목으로 수정했습니다. Android Gradle/JUnit은 미실행입니다.


## 2026-11-24 — DNS 업스트림 실패 요약 정책 JVM smoke 5/5

- `DnsUpstreamFailurePolicy` 실제 순수 Kotlin 소스를 kotlinc/JRE 21로 분리 컴파일·실행했습니다. PASS 5/5: 후보별 원인 병합, 공백 제거, 최대 6개 후보, 항목 240자/전체 1,200자 상한, 원인 없는 경우 null 반환.
- Android VPN packet loop에서 실제 업스트림 fallback 및 SharedPreferences 진단 표시까지 통합 검증한 것은 아닙니다. Android Gradle/JUnit·실기기 DNS는 미실행입니다.



## 2026-11-24 — 정식 APK 검증 실패 캐시 삭제
- 다운로드 후 package ID/version/signature 검사에서 실패한 APK는 캐시에 남지 않도록 즉시 삭제하고 원래 예외를 다시 전달합니다. SHA-256 실패 시 기존 삭제 경로도 유지합니다.
- Android PackageManager archive parsing·실제 파일/설치 흐름은 미검증입니다.


## 2026-11-24 — DNS packet worker 진단 ThreadLocal 정리
- `recordDnsActivity`가 최근 DNS 실패 진단을 저장한 뒤 해당 worker의 ThreadLocal 값을 삭제해 장수명 packet thread에 오래된 오류 문자열이 남지 않게 했습니다. 이 정리는 현재 스레드에만 적용됩니다.
- Android VPN packet worker 실기기 실행 검증은 미실행입니다.


## 2026-11-24 — 앱 업데이트 상태/검증 APK의 화면 재생성 간 유지
- 앱 업데이트 확인 작업을 화면 지역 변수에서 process-wide AtomicReference 상태로 옮겼습니다. 한 프로세스에서 중복 확인을 막고, 메뉴 전환/Activity 재생성 뒤에도 다운로드된 개발/정식 APK 경로와 검증 결과를 복원합니다.
- MainActivity는 상태 변경 브로드캐스트를 받아 현재 화면만 갱신하고, 작업 스레드는 applicationContext로 다운로드해 이전 Activity를 보유하지 않습니다. 검증된 APK는 빌드 종류가 일치할 때만 설치 가능하며, 서버가 최신이라고 응답하면 오래된 pending APK 정보를 비웁니다.
- 상태 전이 순수 JVM 테스트 4건 추가. Android Activity recreation/DownloadManager/FileProvider/PackageInstaller·Gradle/JUnit 검증은 미실행입니다.


## 2026-11-24 — 앱 업데이트 상태 조정 JVM smoke 5/5

- `GuardianAppUpdateState`의 실제 상태/AtomicReference 전이 소스를 kotlinc/JRE 21로 분리 컴파일·실행했습니다. PASS 5/5: 중복 확인 차단, 재생성된 화면에서 검증 APK 경로 복원, 개발/정식 빌드 타입 일치 검사, 최신 응답 시 stale 설치 대상 제거, 재확인 실패 시 이전의 유효한 검증 APK 유지, 동시 12개 탭 요청 중 한 작업만 획득.
- Android Activity lifecycle/브로드캐스트 receiver/실제 다운로더/FileProvider·PackageInstaller 연결은 미실행이며 Android Gradle/JUnit도 실행하지 않았습니다.


## 2026-11-24 — 업데이트 작업 스레드 Activity 캡처 제거
- background update worker에서 Activity helper를 호출해 Activity 참조를 보유할 수 있던 경로를 제거했습니다. 다운로드와 상태 브로드캐스트는 시작 시 캡처한 applicationContext로 수행합니다.
- 화면/Activity 수명과 업데이트 작업을 분리했습니다. Android rotation/destroy lifecycle·메모리 및 실제 다운로드 테스트는 미실행입니다.


## 2026-11-24 — Android 고객 지원의 최근 오류 복사 복구
- 기존 모바일 고객 지원 메뉴에는 Q&A/문의 기능만 있었고, 오류 발생 시 최근 보호 시작 원인과 호출 경로를 직접 복사하는 버튼이 없었습니다. 활동 화면의 일반 진단 복사와 별개로 고객 지원 카드에 최근 오류 복사 액션을 추가했습니다.
- `ProtectionDiagnostics.recentErrorReport`가 최근 서비스 시작 오류를 안전한 분류로 변환하고, 필터 오류 코드를 최대 3개까지 설명과 함께 포함합니다. 스택은 클래스·메서드·라인 토큰만 허용하며 경로 조각은 통째로 거부합니다.
- Kotlin 회귀 테스트 2건과 `scripts/test_android_navigation_compat.py`의 고객 지원 버튼→핸들러→보고서 연결 검사를 추가했습니다.
- 검증 상태: GitHub 원격 파일/커밋 diff 재확인 완료. Android Gradle/JUnit·Python 정적 테스트 실행, lint, emulator, 실기기 및 APK 검증은 미실행입니다.

## 2026-11-24 — 고객지원 진단 복사 실패 복구
- 진단 정보 수집 중 캐시/필터 상태 조회 등에서 예외가 발생하면 백그라운드 스레드가 종료되며 복사 버튼이 영구 비활성화될 수 있는 경로를 수정했습니다.
- 수집 및 클립보드 처리 예외를 사용자에게 알리고, 성공/실패와 관계없이 UI 스레드에서 버튼 활성화와 원래 문구 복원을 수행합니다.
- Python 정적 회귀 테스트를 추가했습니다. Android Gradle/JUnit, Python 테스트 실행, lint, 에뮬레이터 및 실기기 검증은 미실행입니다.

## 2026-11-24 — 최근 오류 복사 예외 처리 보강
- 최근 오류 보고서의 preference 조회/보고서 생성 단계에서 예외가 발생해도 앱 충돌로 이어지지 않도록 보호 범위를 확장했습니다.
- 고객지원 진단 복사와 최근 오류 복사 모두 예외를 안내하고, 진단 복사 버튼은 성공/실패에 관계없이 원상 복구합니다.
- Python 정적 회귀 검사 2건을 추가했습니다. 테스트 실행, Android Gradle/JUnit, lint, 에뮬레이터, 실기기 및 APK 검증은 미실행입니다.

## 2026-11-24 — Android 정식 APK 업데이트 캐시 원자성 보강
- 기존 다운로드는 정식 업데이트 APK를 최종 설치 캐시 파일에 직접 기록해 다운로드 도중 프로세스가 종료되면 부분 파일이 최종 경로에 남을 수 있었습니다.
- 이제 별도 .part 파일에 다운로드하고 크기·SHA-256·APK package/version·manifest signer·현재 설치 signer를 검증한 후에만 최종 APK 경로로 원자적 이동합니다.
- 이전 정상 APK는 백업 경로에 보존하고, 교체가 중단된 경우 다음 커밋에서 복구합니다. 실패 시 임시 파일을 제거합니다.
- GuardianApkCacheStoreTest 3건과 다운로드 검증 순서를 확인하는 Python 정적 회귀 검사를 추가했습니다. Android Gradle/JUnit 및 Python 테스트 실행, lint, 에뮬레이터, 실기기/APK 배포 검증은 미실행입니다.

## 2026-11-24 — APK 캐시 교체 실패 롤백 테스트 보강
- 원자적 이동 실패 상황을 주입할 수 있도록 캐시 helper 내부에 이동 전략을 주입 가능하게 했습니다(기본 실행은 기존 atomic move 구현 유지).
- 교체 중 임시 APK 이동이 실패하면 이전 정상 APK가 원래 경로로 복원되고 백업/임시 파일이 남지 않는지 검증하는 Kotlin 테스트 1건을 추가했습니다.
- Kotlin 테스트는 총 4건으로 확장했습니다. Android Gradle/JUnit 및 Python 테스트 실행은 아직 하지 않았습니다.

## 2026-11-24 — APK 캐시 파일시스템 실패 복구 보강
- 백업 파일 이동 자체가 실패하거나, 파일시스템 이동이 완료된 뒤 오류가 보고되는 경우까지 복구 처리 범위를 확장했습니다.
- 이전 APK가 없는 최초 캐시 생성 중 실패해 부분 target이 남으면 이를 제거하고 `.part` 파일도 정리합니다.
- 회귀 테스트 2건 추가: 백업 이동 후 예외 시 이전 APK 복원, 최초 캐시 생성 실패 시 부분 파일 제거. APK 캐시 helper Kotlin 테스트 총 6건.
- 실제 Gradle/JUnit 및 Android 빌드/실기기 검증은 미실행입니다.

## 2026-11-24 — DNS 필터 캐시 교체 실패 복구
- 필터 파일 백업 이동이 파일시스템에서 완료된 뒤 예외가 발생해도 백업 경로를 기준으로 기존 캐시를 복구하도록 수정했습니다.
- 신규 캐시 생성 중 부분 target이 생기는 실패 경로를 정리하고, 모든 교체 경로에서 `.tmp`를 제거하도록 변경했습니다.
- 실패 주입 테스트 2건 추가. FilterCacheStore JUnit 테스트 총 10건. 테스트/Gradle 빌드/실기기 검증은 미실행입니다.

## 2026-11-24 — DNS 질의 헤더 검증 강화
- 클라이언트 DNS QUERY에 응답 전용 AA/TC/RA 및 예약 Z 플래그가 설정된 경우 upstream으로 전달하지 않고 형식 오류로 거부합니다.
- RD, AD, CD 플래그는 정상적으로 유지됩니다. 회귀 테스트 2건 추가. JUnit/Gradle/실기기 검증은 미실행입니다.

## 2026-11-24 — DNS 질문 타입/클래스 검증
- 예약된 QTYPE=0, QCLASS=0을 거부합니다. AAAA/IN과 QTYPE=255/QCLASS=3 조합은 허용하도록 했습니다.
- 관련 회귀 테스트 2건 추가. JUnit/Gradle/실기기 검증은 미실행입니다.


## 2026-11-24 — Upstream DNS response reserved-bit validation
- Updated `DomainFilter.isMatchingResponse` to reject upstream DNS replies with the reserved Z bit set.
- Added `rejectsUpstreamDnsResponseWithReservedZFlag` regression coverage.
- Source and test changes committed. Kotlin tests, Android Gradle build, emulator/device DNS validation, and APK generation remain unexecuted; GitHub Actions was not run.


## 2026-11-24 — DNS policy invalid-upstream fallback coverage
- Added `upstreamResponseWithReservedDnsZFlagIsRejectedAndReturnsServfail` to verify the shared UDP/TCP policy does not forward an upstream reply carrying the reserved DNS Z bit and returns a valid SERVFAIL instead.
- This complements the low-level response validator regression test. Kotlin test execution, Android build, emulator/device verification, and APK generation remain unexecuted; GitHub Actions was not run.


## 2026-11-24 — DNS 업스트림 사전 점검 응답 구조 검증
- 성공 응답의 ANCOUNT만 확인하던 판정을 강화했습니다. 실제 answer/authority/additional RR의 owner name(압축 포인터 포함), 고정 헤더, RDLENGTH 및 패킷 경계를 끝까지 검증해야 DNS 사전 점검 성공으로 인정합니다.
- 잘린 RR, RDLENGTH가 패킷을 벗어나는 응답, 전방/순환 압축 포인터를 거부하도록 했습니다. 유효한 압축 QNAME을 사용하는 A 레코드 회귀 테스트를 추가했습니다.
- 원격 소스와 테스트 반영을 확인했습니다. Kotlin/JUnit 및 Android Gradle/lint, 에뮬레이터·실기기, APK 검증은 미실행이며 GitHub Actions는 실행하지 않았습니다.


## 2026-11-24 — 서명 필터 anti-rollback 복구 내구성
- manifest timestamp의 정상 기록 경로와 롤백 복구 경로가 서로 다른 저장 방식을 사용하던 부분을 통합했습니다.
- 복구 시에도 임시 파일 전체를 기록하고 fsync한 뒤 원자적 rename으로 게시하도록 수정했습니다. 복구는 다른 필터 source의 timestamp를 보존합니다.
- source 간 상태 보존 및 stale 임시 파일 정리 회귀 테스트를 추가했습니다. Kotlin/JUnit·Android 빌드·실기기 검증은 미실행이며 Actions는 실행하지 않았습니다.


## 2026-11-24 — 모바일 설정 시스템 뒤로가기 복구
- 휴대폰의 설정 상세 화면은 내부적으로 `settings` 탭과 상세 표시 상태를 사용하므로 Android 시스템 뒤로가기가 카테고리 목록이 아닌 홈으로 빠질 수 있었습니다.
- 휴대폰 상세 화면에서 시스템 뒤로가기를 누르면 설정 카테고리 목록으로 돌아가도록 정책을 분리하고, 태블릿 분할 화면 및 다른 탭에는 적용하지 않도록 회귀 테스트를 추가했습니다.
- 소스/테스트 반영 확인. Android 빌드 및 기기 내비게이션 검증은 미실행입니다.


## 2026-11-24 — VPN TUN 세션 소유권 경쟁 조건 수정
- 보호 중지 직후 이전 DNS 패킷 스레드가 늦게 종료될 때, 새 TUN 세션이 이미 시작됐는데도 이전 스레드의 `finally`가 공유 `running` 플래그를 해제하고 새 보호 상태를 중지시킬 수 있는 경쟁 조건을 확인했습니다.
- 세션 세대별 소유권 가드를 추가했습니다. 현재 세션 세대만 종료 상태를 게시할 수 있고, 중지/호환성 일시중지/앱 제외 재구성은 기존 소유권을 무효화합니다. 세션 소유권 확인과 시작 가능 여부 판단은 동일한 잠금으로 직렬화했습니다.
- 이전 세션 종료가 새 세션 상태를 변경하지 못하는지, 명시적 중지 후 이전 종료 처리가 무시되는지, 현재 세션 종료가 한 번만 반영되는지 회귀 테스트를 추가했습니다.
- 패킷 루프 종료 요청도 해당 세션의 `startId`에 한정해, 종료 처리 중 새 `ACTION_START`가 접수되면 이전 세션의 `stopSelf`가 새 요청까지 종료하지 않도록 했습니다.
- 독립 Kotlin smoke test 실행 결과: PASS. Android Gradle/JUnit, lint, 에뮬레이터·실기기 검증은 미실행입니다.



## 2026-11-24 — 필터 업데이트 구성요소 로딩 실패 복구
- 필터 다운로드 중 Android 런타임 구성요소의 `LinkageError`가 발생하면 기존에는 백그라운드 작업 스레드가 비정상 종료되면서 업데이트 진행 플래그가 남고 다운로드 버튼 및 선택 필터 컨트롤이 비활성 상태로 고정될 수 있었습니다.
- 보안 필터와 Android 앱 광고 필터의 선택적 다운로드 경로에 구성요소 로딩 실패 처리를 추가했습니다. 캐시가 있으면 기존 정상 캐시를 유지하고, 캐시가 없으면 해당 선택 필터만 비활성화합니다.
- 기본 필터 처리 중 구성요소 로딩 실패도 공통 실패 복구 경로로 보내 진행 플래그를 해제하고 재시도 UI 및 컨트롤을 복구하도록 했습니다. 첫 기본 캐시가 없던 경우 기존 초기 재시도 정책도 유지합니다.
- 원격 소스 정적 회귀 조건 확인: PASS. Android Gradle/JUnit 및 실기기 검증은 미실행입니다.



## 2026-11-24 — 예약 필터 업데이트 구성요소 오류 복구
- WorkManager 예약 필터 갱신에서도 Android 런타임 구성요소 로딩 실패(`LinkageError`)가 선택 필터의 기존 캐시 처리 및 실패 기록/브로드캐스트를 건너뛰던 경로를 보완했습니다.
- 보안·Android 앱 광고 선택 필터는 기존 캐시를 유지하고 캐시가 없으면 기존 정책대로 해당 필터만 비활성화합니다. 기본 업데이트 경로의 구성요소 오류는 공통 오류 기록·상태 브로드캐스트 후 WorkManager 재시도로 반환합니다.
- 원격 소스 정적 조건 확인: PASS. WorkManager/Android Gradle/JUnit 및 기기 검증은 미실행입니다.



## 2026-11-24 — VPN TUN 커밋 후 패킷 스레드 시작 실패 롤백
- TUN 소유권을 활성화한 직후 활성 상태 저장 또는 DNS 패킷 스레드 생성이 실패하면, 기존 코드가 실행 중 플래그를 유지한 채 패킷 처리기가 없는 VPN을 활성으로 남길 수 있는 경로를 확인했습니다.
- 상태 게시/패킷 스레드 시작을 커밋 후 보호 구간으로 감싸고 예외 또는 런타임 링크 오류 발생 시 해당 세대의 소유권만 해제한 뒤 TUN descriptor를 닫도록 롤백을 추가했습니다. 이후 기존 시작 실패 진단 경로가 오류를 사용자 상태에 기록합니다.
- 소스 반영 및 원격 재조회 확인. Android Gradle/JUnit 및 실기기 검증은 미실행입니다.



## 2026-11-24 — 취소된 VPN 시작 작업의 실패 정리 격리
- 이전 시작 요청의 DNS preflight/필터 로드/TUN 생성이 늦게 실패하면, 그 실패 처리에서 새 시작 요청의 foreground 알림·상태 표시·TUN descriptor까지 정리할 수 있는 경합을 확인했습니다.
- 시작 세대가 현재 요청인 경우에만 실패 메시지·진단·foreground 제거·서비스 종료를 원자적으로 커밋하도록 변경했습니다. 이미 취소된 작업은 새 요청 상태를 덮어쓰거나 TUN을 닫지 않고 종료합니다.
- 포그라운드 서비스 시작, 필터 규칙 로드, DNS preflight, TUN 생성 실패에도 동일한 세대 게이트를 적용했습니다. 예기치 않은 예외의 descriptor 정리도 세대 게이트 안에서 수행합니다.
- 순수 세대 게이트 회귀 테스트 2개 추가(이전 세대 실패 처리 거부, 현재 세대 실패 처리 허용). 테스트 소스 반영 확인만 완료했으며 Android Gradle/JUnit 실행은 미실행입니다.



## 2026-11-24 — VPN 시작 실패 처리의 동시성 회귀 검증 보강
- 시작 세대 게이트의 현재 요청 실패 처리와 새 시작 요청이 동일한 잠금으로 직렬화되는 동시성 테스트를 추가했습니다. 이전 시작의 실패 정리가 새 요청 generation 발급을 앞지를 수 없음을 검증하는 테스트 소스입니다.
- 저장소 테스트 클래스에는 VPN 시작 세대 게이트 테스트 8개가 포함됩니다. Android JUnit 실행은 아직 미실행입니다.
- 별도 Kotlin/JVM smoke harness로 동일한 순수 세대 게이트 잠금/세대 순서를 컴파일·실행했고 `VPN_START_GATE_CONCURRENCY_SMOKE=PASS`를 확인했습니다. 이 결과는 독립 정책 하네스 검증이며 Android 앱 빌드 또는 저장소 JUnit 통과를 의미하지 않습니다.



## 2026-11-24 — DNS 업스트림 UDP/TCP·기기 DNS 복구 경로
- 무료 Android 소스 재점검에서 기본 UDP 업스트림이 실패할 때 동일 서버 TCP/53 재시도와 물리 네트워크 DNS 후보 탐색이 실제 `GuardianVpnService` 호출 경로에 연결되어 있지 않은 것을 확인해 복구했습니다.
- 현재 순서는 선택한 DNS의 UDP/53 → 동일 DNS의 TCP/53 → 물리 네트워크 DNS 최대 2개 → 공용 DNS fallback(Cloudflare/Google)입니다. 각 후보는 주소/응답 검증을 거치며, Guardian 가상 DNS 주소(IPv4/IPv6), loopback, multicast, link-local 및 잘못된 주소는 후보에서 제외합니다.
- 사용자가 TCP/DoT/DoH를 명시 선택한 경우 UDP로 강등하지 않습니다. UDP의 TC 응답도 중복 TCP 소켓을 열지 않고 상위 fallback 순서에서 TCP를 한 번만 시도하도록 정리했습니다.
- Android `ConnectivityManager`에서 물리 DNS를 읽기 위해 `ACCESS_NETWORK_STATE` 권한을 선언했습니다. 네트워크 정보 조회 실패는 보호 패킷 처리 중단 대신 기기 DNS fallback 생략으로 처리합니다.
- 순수 DNS 후보/순서 정책 테스트 4개를 추가했고 별도 Kotlin/JVM smoke harness에서 주소 정규화·IPv6 가상 DNS 제외·후보 순서·전송방식 보존을 실행 확인했습니다(`DNS_UPSTREAM_FALLBACK_POLICY_SMOKE=PASS`). Android Gradle/JUnit, 실제 Wi-Fi/모바일 DNS 연결은 미검증입니다.
