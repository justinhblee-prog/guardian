# Guardian "Library file not found" 원인 분석

기준 커밋: `main` at `c5ee6ec8cc496d73c1eb30ed3cc96a620eab5f5a`
분석일: 2026-11-21

## 결론

현재 저장소의 Android 빌드 설정과 APK 패키징 검증에서는 `filter_rules.txt` 누락이 재현되지 않았다. 따라서 저장소 원본 파일 누락 또는 Gradle assets 경로 설정 오류를 최종 원인으로 지목할 근거가 없다.

실제 사용자 기기에서 표시된 오류의 최종 원인은 아직 확정할 수 없다. 그 기기의 설치 APK 버전/서명/패키지와 오류 당시 안전 진단 보고서가 없기 때문이다. 가장 먼저 구분해야 할 잔여 가설은 (1) 검증된 APK보다 오래된 APK 또는 다른 variant가 설치된 경우, (2) 기기에서 AssetManager가 해당 자산을 열지 못한 런타임 문제, (3) 자산을 연 뒤 파서/초기화 단계에서 발생한 예외다. 이는 가설이며 확정 사실이 아니다.

## 코드 경로

1. 홈 보호 버튼은 `MainActivity.onHomeProtectionButtonTapped()`에서 `requestProtection()`을 호출한다.
2. 별도 스레드 `guardian-protection-readiness`에서 `assets.open("filter_rules.txt")`로 내장 자산을 열고 `FilterRuleParser.parse`로 파싱한다.
3. `FilterStartupGate.hasUsableBundledBaseline`은 파싱된 차단 규칙 수와 중요 차단 규칙 수의 합이 1개 이상인지 확인한다.
4. 성공해야 VPN 권한 확인 후 `GuardianVpnService` 시작 요청으로 진행한다.
5. `Exception` 발생 시 현재 UI 문구는 "앱 내 기본 필터 라이브러리를 읽지 못했습니다"로 표시되며, `LinkageError`는 별도 문구로 처리된다. 따라서 UI의 "라이브러리"라는 표현만으로 실제 원인이 네이티브 .so 라이브러리라고 판단하면 안 된다.
6. 실패 시 예외 메시지/경로 대신 예외 타입과 제한된 스택 프레임 요약을 `KEY_STARTUP_TRACE`에 저장한다.

## 저장소 및 패키징 증거

- `android/app/build.gradle.kts`는 `sourceSets.getByName("main").assets.srcDir("../../shared")`를 선언한다. Android app 프로젝트 디렉터리 기준으로 저장소 루트의 `shared/` 디렉터리를 assets 입력으로 연결한다.
- `shared/filter_rules.txt`는 현재 main에 존재한다(355 bytes, Git blob `1bd11096a44044400517624adaf81f8d11688ebe`).
- 해당 파일에는 `block=` 규칙 10개가 있으며 현재 baseline gate 조건(규칙 1개 이상)을 만족한다.
- `.github/workflows/android-apk.yml`의 "Verify bundled DNS filter library inside APK" 단계는 APK에 `assets/filter_rules.txt`가 존재하는지 검사하고, APK에서 추출한 바이트가 `shared/filter_rules.txt`와 정확히 같은지 `cmp`로 비교한다.
- PR #231의 APK workflow run `44861518188`에서 "Verify bundled DNS filter library inside APK" 단계가 성공했다. 이 빌드에서는 APK 패키징 누락이 발생하지 않았다.
- 별도 이전 run `44861677336`도 동일한 자산 검증을 통과했지만 에뮬레이터 단계 전후가 취소되어 그 run을 완전한 런타임 검증으로 간주하지 않는다.

## 내장 파일과 다운로드 캐시의 구분

`FilterListManager`가 관리하는 다운로드 캐시는 `filesDir` 아래의 `adguard_dns_filter.txt`, `hagezi_threat_intelligence.txt`, `awavenue_android_ads.txt`이다. 이 파일들은 내장 `assets/filter_rules.txt`와 서로 다른 저장소/수명주기를 가진다. 다운로드 캐시가 없거나 오래되었더라도 앱은 내장 기본 규칙으로 먼저 VPN을 시작할 수 있도록 설계되어 있다. 따라서 다운로드 캐시 부재를 내장 라이브러리 누락으로 해석해서는 안 된다.

## 남은 확정 절차

실기기 오류 당시의 앱 내 "개인정보 제외 진단 정보 복사" 보고서에서 다음 항목을 확인해야 한다.

- 앱 버전 및 Android API
- 내장 DNS 규칙 파일 상태와 바이트 수
- 최근 보호 시작 오류의 단계 및 예외 타입 체인
- 최근 오류 호출 스택(메시지/경로 제외)
- 기기 ABI 및 네이티브 라이브러리 디렉터리 상태

판정 기준:

- `bundledRuleAssetAvailable=false` 및 `bundledRuleAssetBytes=-1`: 해당 설치본에서 자산을 열지 못한 것. 앱 버전/설치 APK의 SHA-256 및 서명 확인으로 구버전/잘못된 variant인지 먼저 구분한다.
- 자산이 정상(양수 bytes)인데 startup trace가 파서/초기화 예외를 가리키는 경우: 파일 누락이 아니라 파싱 또는 초기화 결함이다.
- trace가 `LinkageError` 또는 `UnsatisfiedLinkError`이면 네이티브/런타임 로딩 문제로 별도 조사한다.
- 자산 정상 및 파싱 성공 이후 오류라면 VPN 서비스 시작/포그라운드 서비스/업스트림 DNS 단계로 분류한다.

현재 진단 보고서는 이 항목들을 수집하도록 이미 구현되어 있다. 보고서가 없는 상태에서 특정 실기기 원인을 확정하거나 해결 완료로 표시하지 않는다.

## Windows self-hosted Ollama 작업 미배정 분석

동일 시점의 PR #230 로컬 Ollama 검증 run `44862052589`는 GitHub Actions API에서 `status=pending`이고 jobs 목록이 빈 배열(`jobs=[]`)이었다. 따라서 실행된 job의 step 실패가 아니라, GitHub가 runner에 할당한 job 자체가 아직 만들어지지 않은 상태다.

워크플로는 `runs-on: [self-hosted, Windows]`를 요구한다. 이 상태와 일치하는 온라인 runner가 현재 작업을 받지 못한 것은 확인되지만, 연결 도구가 저장소 Actions runner 관리자 목록 API를 제공하지 않아 다음 원인 중 무엇인지는 구별할 수 없다.

- Windows runner 서비스가 중지/오프라인
- runner가 해제되었거나 저장소에 등록되지 않음
- 등록 runner의 OS/custom labels가 `self-hosted` 및 `Windows`와 불일치
- runner group의 저장소 접근 허용 범위 문제

이 run은 Ollama endpoint에 도달하지 않았으므로 실제 로컬 모델 추론을 실행했다고 기록하지 않는다. 저장소 Actions 설정, billing 또는 usage는 변경하지 않았다. runner 관리자 API/서비스 로그 없이는 이 중 하나를 최종 원인으로 단정할 수 없다.

## 판정 요약

| 항목 | 판정 |
|---|---|
| main의 shared 기본 규칙 파일 존재 | 확인 |
| Gradle shared assets 연결 | 확인 |
| APK 내 assets 경로 및 원본과의 바이트 동일성 | PR #231 CI 통과 |
| 이 코드/빌드에서 기본 파일 누락 재현 | 재현되지 않음 |
| 사용자 기기의 실제 설치 APK와 오류 당시 예외 | 증거 미확보 |
| 실기기 최종 원인 | 미확정 |
| PR #230 Ollama job 미배정 | 확인 (`pending`, `jobs=[]`) |
| 미배정의 세부 원인(오프라인/라벨/그룹) | runner 관리자 증거 미확보 |
