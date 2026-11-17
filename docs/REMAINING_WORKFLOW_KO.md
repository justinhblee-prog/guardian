# Guardian 잔여 업무 순차 진행 계획

기준 커밋: `99ad460bf309fe55b7503fa828d8eb4be25386ea`
기준 PR: [#73 — optional filter fallback and Android VPN startup](https://github.com/justinhblee-prog/guardian/pull/73)
기준 Android CI: [run 43907322511](https://github.com/justinhblee-prog/guardian/actions/runs/43907322511)
상태 갱신: 2026-11-17

## 운영 원칙

이 문서는 Guardian의 남은 일을 하나의 순차 작업 큐로 관리한다.

1. 한 번에 가장 앞에 있는 미완료 단계 하나를 진행한다.
2. 코드 수정 후 관련 단위 테스트 → lint → APK 빌드 → 필요한 통합/E2E 테스트 순으로 검증한다.
3. 성공 근거(커밋, CI 실행, 산출물, 테스트 로그)를 확인한 뒤에만 해당 항목을 완료 처리하고 다음 단계로 이동한다.
4. 일반적인 코드·테스트·빌드 오류는 원인을 찾아 수정하고 재검증한다. 같은 오류가 반복되면 우회로 성공 처리하지 않고 근본 원인을 해결한다.
5. 아래의 중대 중단 조건이 아닌 한 사용자에게 매번 승인이나 '다음' 입력을 요구하지 않는다.
6. GitHub Actions 요금제·결제·사용량 한도를 변경하거나 유료 외부 AI API를 새로 호출하지 않는다.
7. CI 이벤트 감사는 자동화할 수 있지만, 현재 저장소의 GitHub Actions만으로 AI가 임의 코드를 설계·수정·커밋하는 무인 개발 에이전트가 만들어지는 것은 아니다. 실제 코드 작업은 사용 가능한 개발 도구와 저장소 권한 안에서 수행하며, 실행하지 않은 작업을 백그라운드에서 진행 중이라고 표현하지 않는다.

## 업무 흐름도

```mermaid
flowchart TD
    A["현재 상태·미완료 목록 확인"] --> B["1. PR #73 검토 및 Android VPN E2E 결과 확정"]
    B --> C{"단계 검증 통과?"}
    C -->|아니오| D["오류 분류·원인 수정·회귀 테스트"]
    D --> C
    C -->|예| E["2. 실제 Android 기기 수용 검증 준비/수행"]
    E --> F{"실기기 게이트 통과?"}
    F -->|아니오| G["기기·네트워크 조건별 결함 수정"]
    G --> E
    F -->|예| H["3. Wi-Fi/모바일·IPv4/IPv6·Private DNS·앱 제외 안정성"]
    H --> I{"네트워크 매트릭스 통과?"}
    I -->|아니오| J["네트워크 상태 복구·VPN 수명주기 수정"]
    J --> H
    I -->|예| K["4. 서명 필터 배포 서버·공개키·HTTPS E2E"]
    K --> L{"서명·해시·롤백·복구 통과?"}
    L -->|아니오| M["키/manifest/캐시 갱신 오류 수정"]
    M --> K
    L -->|예| N["5. License API·DB·기기 활성화/갱신/해제 E2E"]
    N --> O{"라이선스 E2E 통과?"}
    O -->|아니오| P["서버/API/앱 토큰 검증 수정"]
    P --> N
    O -->|예| Q["6. 운영 서명키·보호된 CI 설정 및 production APK/AAB"]
    Q --> R{"서명·업데이트·설치 검증 통과?"}
    R -->|아니오| S["릴리스 설정·서명·업그레이드 경로 수정"]
    S --> Q
    R -->|예| T["7. Android 출시 후보 확정"]
    T --> U["8. Windows EXE/설치 프로그램 빌드·설치·복구 검증"]
    U --> V{"Windows 검증 통과?"}
    V -->|아니오| W["설치·DNS 복구·재부팅 결함 수정"]
    W --> U
    V -->|예| X["플랫폼별 검증 보고서 및 통합 릴리스 판정"]
```

## 명령 실행 루프

아래 반복 루프를 모든 단계에 공통 적용한다. 단계 완료는 실제 검증 증거가 확인된 경우에만 기록한다.

```mermaid
flowchart TD
    A["전체 미완료 업무 수집"] --> B["의존관계 및 우선순위 분석"]
    B --> C["다음 작업 1개 선정"]
    C --> D["코드 및 테스트 구현"]
    D --> E["테스트 실행"]
    E --> F{"오류 발생?"}
    F -- "아니오" --> G["CI 및 회귀 검증"]
    F -- "일반 오류" --> H["원인 분석 및 수정"]
    H --> D
    F -- "심각한 오류" --> X["작업 중단 및 사용자 보고"]
    G --> I{"검증 통과?"}
    I -- "아니오" --> H
    I -- "예" --> J["Draft PR 및 문서 갱신"]
    J --> K["완료 상태 기록"]
    K --> L{"남은 업무 존재?"}
    L -- "예" --> B
    L -- "아니오" --> M["통합 최종 검증"]
    M --> N["최종 결과 및 미검증 항목 보고"]
```

## 순차 작업 큐

| 순서 | 작업 | 완료 기준 | 상태 |
|---:|---|---|---|
| 1 | PR #73 Android 필터 메모리 최적화 및 VPN 시작 fallback 검토 | 단위 테스트, lint, APK, 에뮬레이터 VPN E2E 성공; PR diff 검토 및 통합 | 완료 · squash merge `99ad460` |
| 2 | 실제 Android 스마트폰 수용 테스트 | VPN 승인/시작/중지, 허용 DNS 응답, 차단 DNS 응답, 일반 인터넷 접속 확인 | 대기 |
| 3 | Android 네트워크 안정성 매트릭스 | Wi-Fi↔모바일 전환, IPv4/IPv6, Private DNS, 앱 제외, 다른 VPN 조합과 복구 확인 | 대기 |
| 4 | 서명 필터 운영 연동 | 클라이언트·DPAPI 서명/게시 도구·distribution CI 통합; 실제 Windows key 생성/HTTPS 게시/앱 검증은 운영 환경에서 확인 | 코드·CI 완료 / 운영 배포 대기 |
| 5 | 라이선스 운영 연동 | API/DB·Windows 운영 스크립트 통합 및 PostgreSQL integration CI; 실제 Tailscale 서버 배포와 기기 E2E는 운영 환경에서 확인 | 코드·CI 완료 / 운영 배포 대기 |
| 6 | Android production 빌드 | 임시 키 release build 검증 완료; 운영 keystore/secrets 기반 production APK/AAB 서명 및 설치·업데이트 확인 | release 설정 검증 / 운영 키 대기 |
| 7 | Android 출시 판정 | 실제 기기/네트워크/서버/서명 증거와 알려진 제한을 문서화 | 대기 |
| 8 | Windows 클라이언트 패키징 | EXE·설치 프로그램 빌드, 설치·실행·제거, DNS 설정 백업/복구, 재부팅 후 복구 검증 | 대기 |
| 9 | 통합 릴리스 문서화 | Android/Windows별 검증 결과와 미지원·미검증 범위를 분리해 최종 보고 | 대기 |

## 중대 오류 — 진행 중단 조건

다음 상황은 일반적인 수정 가능 오류와 구분해 즉시 중단하고 원인 및 영향 범위를 보고한다.

- 운영 개인키, 인증서, 관리자 비밀값 또는 사용자 데이터의 노출 가능성
- DNS/VPN 복구 실패로 사용자의 네트워크 연결이 지속적으로 끊기거나 복구 경로가 없는 문제
- 기존 사용자 데이터·설정·라이선스 상태의 비가역적 손상 또는 데이터 마이그레이션 위험
- 서명 검증 우회, 위조 필터 수락, 권한 상승 등 보안 경계가 무너지는 문제
- 저장소의 보호되지 않은 파괴적 변경, 복구 불가능한 릴리스/배포 작업
- 유료 사용량·결제·외부 서비스 비용 발생이 필요한데 사용자 동의가 없는 경우

그 외의 단위 테스트 실패, lint 경고/오류, 컴파일 오류, 다운로드 재시도 실패, CI 러너 일시 장애, 에뮬레이터 환경 문제는 로그와 단계 정보를 근거로 분류하고 가능한 수정·재검증을 진행한다. 인프라 장애를 앱 코드 결함으로 잘못 분류하지 않는다.

## 증거 및 완료 판정 규칙

- **코드 반영:** 커밋 SHA와 변경 파일로 확인한다.
- **자동화 검증:** 실행 ID와 각 단계 결과로 확인한다.
- **APK 확보:** APK 파일 존재, 크기, SHA-256 및 패키지/versionCode/versionName을 확인한다.
- **에뮬레이터 E2E:** 앱 설치/실행만으로 통과 처리하지 않는다. VPN 활성화, 허용 DNS 실제 응답, 차단 DNS 무응답 및 앱 통계 변화까지 확인한다.
- **실기기 수용 테스트:** 에뮬레이터 결과와 분리해 기록한다.
- **운영 서버:** 로컬 mock/단위 테스트와 실제 HTTPS 운영 환경 E2E를 구분한다.
- **production 릴리스:** debug 서명 APK를 production 산출물로 표시하지 않는다. 운영 서명키가 준비되지 않으면 해당 단계는 보류한다.

## 현재 기준 상태

- PR #73 squash merge: `99ad460bf309fe55b7503fa828d8eb4be25386ea`; PR #77 squash merge: `30a765cf539885f94a5924680a8d055d083afc79`.
- 최신 main 커밋: `28abc77342f34898dd03c198fef942c0b49a7fe0`.
- Android APK workflow [run 44060999851](https://github.com/justinhblee-prog/guardian/actions/runs/44060999851): **success**. Unit tests, lint, debug APK assembly, bundled DNS filter, package/version, checksum, source ZIP integrity, emulator install/launch 및 VPN DNS E2E가 모두 통과했다.
- APK: `com.guardian.adblock`, versionCode `27`, versionName `0.7.14-dev`, 3,119,618 bytes. SHA-256: `24db2c1db46a66d2117cb35f7541c893b3321ee7de46363b615fd1a585319c78`.
- 실제 에뮬레이터 로그: `protection_active=true`, `dns_queries=8`, `dns_forwarded=7`, `dns_blocked=1`, `dns_ad_blocked=1`, `dns_failed=0`. `example.com`은 `104.20.23.154`로 해석됐고, `guardian-ci-44060999851.doubleclick.net`은 `unknown host`로 거부됐다. ICMP ping 응답 손실은 DNS 실패로 간주하지 않는다.
- 최종 runtime report artifact: `guardian-android-final-verification`, artifact ID `14318322870`; APK/source artifact ID `14317588578`.
- 실기기 스마트폰/태블릿 VPN 승인, 실제 네트워크 경로, 광고 제거 수용 테스트는 아직 미실시다. 에뮬레이터 E2E 결과로 대체하지 않는다. 다음 게이트는 실기기 수용 테스트이며, 현재 CI runner만으로 실제 기기 검증을 완료했다고 표시할 수 없다.
- Signed Filter Distribution CI [run 44052665884](https://github.com/justinhblee-prog/guardian/actions/runs/44052665884) 및 License API/PostgreSQL integration [run 44059072829](https://github.com/justinhblee-prog/guardian/actions/runs/44059072829): 성공. 실제 운영 HTTPS/TLS 도메인·서명 개인키·DB 호스팅 및 Android 앱과의 운영 E2E는 미완료.
- Production release configuration [run 44052664040](https://github.com/justinhblee-prog/guardian/actions/runs/44052664040): 설정 검증과 임시 키 서명 release APK 검증 성공. `production-release` job은 운영 keystore/secrets 부재로 의도적으로 skipped; production APK/AAB는 생성되지 않았다.
- APK는 debug 서명 개발 빌드이며, 실기기 검증과 운영 서명키/서비스 설정 전에는 출시 후보 또는 production release로 표시하지 않는다.
- Windows 클라이언트 및 설치 프로그램은 Android 검증/운영 준비 이후 단계로 유지한다.

## 변경 기록

- 2026-11-17: 남은 Android → 운영 필터/라이선스 → production signing → Windows 패키징 순서를 명시하고, 중대 오류 기준과 단계별 완료 증거 규칙을 정의했다.

- 2026-11-17: run `43917110473`의 APK SHA-256과 실제 VPN 활성화/허용 DNS/차단 DNS 및 통계 결과를 반영했다. PR #73의 자동 검증은 통과했으나 실기기 수용 테스트와 통합은 별도다.


- 2026-11-17: PR #73을 검토 후 squash merge했고, Android VPN E2E 로그에서 허용 DNS 응답·차단 DNS 실패 및 서비스 통계를 확인했다. 병합 후 main CI run `44047861059` 검증 대기 상태를 기록했다.

- 2026-11-17: PR #77 was squash-merged as `30a765c`; Android 0.7.14-dev PR E2E and signed filter/license API CI evidence recorded. Main-branch post-merge workflows remain in progress at this update.


- 2026-11-17: main run `44060999851`에서 Android 에뮬레이터 설치/실행 및 실제 VPN DNS E2E를 통과했다. 허용 도메인 응답, doubleclick 차단, DNS 통계(`8/7/1/1/0`)를 job log에서 확인하고 APK/source/report artifact를 재검증했다. 실기기 수용 검증은 별도 대기 상태로 유지한다.
