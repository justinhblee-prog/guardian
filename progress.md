# Guardian Android 1.0 — Progress Tracker

- **전체 상태**: 진행 중 — 외부 실행/운영 증거 대기 (완료율: 30%)
- **마지막 업데이트**: Step 3 — 필터 무결성 정적 감사 및 외부 실행 차단 요인 기록
- **기준일**: 2026-11-20 (KST)
- **저장소**: https://github.com/justinhblee-prog/guardian
- **작업 브랜치**: `feature/android-support-center-mainline`
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

## 4. 최신 저장소 기준점\n\n- GitHub compare: `main...feature/android-support-center-mainline` = 122 commits ahead, 0 behind, 31 files changed (2026-11-20 조회).\n- 새 문서: `docs/GUARDIAN_ANDROID_1_0_FINAL_READINESS_KO.md`.\n- 필터 무결성 정적 감사: 서명 검증/캐시 복구/배포 HTTP 서버의 주요 방어 코드 및 테스트 소스를 확인했으나 실제 테스트 실행이나 운영 키/배포 검증을 대체하지 않음.\n\n- 추가 심층 감사 대상: `FilterRuleParser.kt`, `FilterRuleComposer.kt`, `DomainFilter.kt`, `FilterCacheStore.kt`, `FilterUpdateScheduler.kt`, `FilterUpdateWorker.kt` 및 관련 테스트.\n\n- 추가 감사 대상: `ProtectionRestoreReceiver.kt`, `ProtectionRestorePolicy.kt`, `ProtectionState.kt`, `GuardianVpnService.kt`의 `startProtectionInternal`, `publishState`, `stopProtection`, `onDestroy` 및 관련 테스트. 복구 상태는 실제 TUN 활성과 구분되며 실패 시 UI 상태 플래그를 정리하는 구조를 확인. 추가 확정 결함은 발견하지 못함.\n\n- 추가 UI 감사: 홈 대시보드의 단일 보호 토글은 실제 TUN 활성/호환 일시중지/복구 대기 상태에 따라 동작하며, 설정 화면은 상태 표시와 기능 설정으로 분리됨을 확인. 필터 업데이트 중 중복 실행 방지와 관련 버튼 잠금, APK 다운로드 전 설치 비활성화 및 검증 APK 확보 후 설치 활성화 정책/회귀 테스트를 확인. 버튼 경로에서 추가 확정 결함은 발견하지 못함.\n\n- Android 플랫폼 감사: minSdk 26 / targetSdk 35, `FOREGROUND_SERVICE_SPECIAL_USE` 권한 및 VPN 서비스의 `specialUse` subtype 선언 확인. API 26+ 시작 경로는 `startForegroundService` 직후 서비스 진입 시 foreground notification을 설정. TUN 생성 전 실제 DNS preflight, 업스트림 UDP/TCP/DoT/DoH 소켓의 `protect()` 우회, 연결/읽기 타임아웃, 응답 주소·포트/질문 검증, 취소 generation 기반 stale start 방지, 패킷 루프 예외 시 터널 종료 및 비활성 상태 반영을 정적으로 확인. 이 검토에서 추가 확정 결함은 발견하지 못함. Android 빌드 및 OS별 실기기 테스트는 미실행.\n\n## 5. 현재 외부 차단 요인

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
