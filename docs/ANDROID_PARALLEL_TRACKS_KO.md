# Android 무료/PRO 병렬 개발 관리

기준 PR: #290 (열린 상태 유지, 자동 병합 금지)

## 우선순위와 통합 순서

1. Android 무료 기본 제품을 먼저 출시 후보까지 완성하고 검증합니다.
2. PRO 기능은 무료 트랙과 분리된 브랜치에서 계속 개발합니다.
3. 무료 트랙의 Android Gradle/JUnit, VPN/DNS emulator E2E, APK 설치/업데이트 검증이 끝나기 전에는 Windows 제품 작업으로 전환하지 않습니다.
4. PRO 하위 브랜치는 각각의 엔진·화면·테스트를 개발한 뒤 단일 PRO 통합 브랜치에서 순차 통합합니다. 서로 같은 파일을 수정하므로 하위 브랜치를 main에 직접 병합하지 않습니다.
5. PRO 기능은 entitlement 검증, runtime gate, Android 통합 테스트가 모두 통과하기 전까지 production-ready로 등록하지 않습니다.

## 작업 브랜치 현황

| 트랙 | 브랜치 | 상태 확인 시점 HEAD (2026-11-24) | 주요 범위 |
|---|---|---|---|
| 무료 Android | `work/android-free-release-followup-20261124` | `055ab469a5d11c29bf2d62c5111570e789ffd6ac` (프로필 복구 기능 변경 커밋) | 복구/진단, 보호 시작 fallback, 정식 updater 검증, 필터 업데이트 UI 세대 보호, 프로필 변경 원자성·복구 |
| PRO 공통 기반 | `work/android-pro-premium-20261124` | `bc025fcfbd0677c6004677791c8f21c671dbe4bc` | fail-closed entitlement, 일별 DNS 리포트 |
| 고급 필터 | #297 | 별도 엔진/정책 작업 | 필터 범위·출처·갱신 정책 |
| 앱별 보호 | `work/pro-app-policy-20261124` | `a2802d6beca848f6e1dd42804730895c8541e1e4` | UDP DNS UID 귀속, 앱별 집계/추가 차단 규칙 |
| 위협 분석 | `work/pro-threat-analytics-20261124` | `15f1254bb2fec70c8302938816f32ad3f8186daf` | 필터 출처 기반 evidence 분류 |
| 보안 프로필 | `work/pro-security-profiles-20261124` | `65c93cf4880f3a7b6fe4dda6ad202c716409cefd` | 네트워크 callback 기반 DEBUG 자동 전환 |
| 피싱·앱 검사 | `work/pro-phishing-scan-20261124` | `c2385ec70d3cb7e962460f941d86d781c0ab765c` | URL 구조 분석, APK DEX 정적 검사 |

## 통합 충돌이 예상되는 공통 파일

- `MainActivity.kt`: Premium 미리보기/설정 UI, URL/APK 검사, 앱별 정책, 위협 분석, 보안 프로필
- `GuardianVpnService.kt`: DNS 이벤트, 앱 귀속, 위협 분류, 네트워크 프로필 재적용
- `DomainFilter.kt`: 앱별 추가 규칙, 필터 출처 provenance
- `AndroidManifest.xml`: 네트워크 상태 권한
- `CHANGELOG.md`, `progress.md`: 각 브랜치의 변경 기록

이 때문에 병렬 브랜치의 코드를 각각 main으로 직접 병합하지 않습니다. 무료 트랙 검증 후 공통 PRO 통합 브랜치에서 파일별 변경을 합치고, 각 통합 단계마다 회귀 테스트를 수행합니다.

## 검증/출시 게이트

- 현재 확인된 Kotlin/JVM 및 Android API/SharedPreferences stub 검사는 Android Gradle/JUnit 또는 실제 단말 테스트를 대체하지 않습니다.
- 신규 브랜치 APK는 아직 생성·설치·서명 지문·SHA-256 검증이 완료되지 않았습니다.
- PRO 앱별 UID 귀속/정책, 위협 분석, 보안 프로필 자동 전환은 개발 미리보기/DEBUG 전용입니다.
- 피싱 URL 및 APK DEX 검사는 정적 휴리스틱입니다. 전체 악성코드 탐지/백신이라고 표시하지 않습니다.
- PRO entitlement production-ready 목록은 Android 통합 검증 전까지 비워 둡니다.
- PR #290은 사용자의 명시적 승인 전까지 열어 두고 병합하지 않습니다.

## 최근 무료 Android 회귀 점검 (2026-11-25)

- 필터 수동 업데이트 완료/실패 콜백에 시도 세대(generation)를 부여했습니다. 이전 다운로드의 지연된 UI 콜백이 새 업데이트의 상태 문구나 버튼 활성 상태를 덮어쓰지 않도록 세대 일치 여부를 확인합니다.
- `FilterUpdateUiGenerationPolicyTest`는 동일 세대 허용, 이전·미래 세대 거부, 초기 세대 일치의 네 경우를 포함합니다.
- 실제 정책 Kotlin 소스를 `kotlinc`로 컴파일하고 독립 하네스 4개 사례를 실행해 통과했습니다.
- 프로필 변경은 이전 프로필 키를 먼저 영속화한 뒤 새 프로필을 선택합니다. 새 피드 검증에 실패하면 이전 프로필로 되돌리고, 기존 캐시의 갱신 시각을 보존합니다.
- 앱/서비스 프로세스가 다운로드 도중 종료된 경우 프로세스당 한 번만 pending marker와 실제 cached profile을 대조합니다. 새 캐시가 이미 커밋된 경우 새 프로필을 유지하고, 그렇지 않으면 이전 프로필로 복구합니다. 화면 회전 등 Activity 재생성은 진행 중인 다운로드를 되돌리지 않습니다.
- `FilterProfileRollbackPolicyTest` 및 실제 정책 소스를 이용한 `kotlinc` 독립 하네스(경계 6개 + 결정적 사례 100,000개)가 통과했습니다.
- Android Gradle/JUnit 및 UI 계측 검증은 별도 릴리스 게이트로 남아 있습니다.
