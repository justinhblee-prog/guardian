# Guardian AdBlock 멀티 디바이스 제품 설계

기준일: 2026-11-16

## 2026-11-16 통합 상태 정정 — main 기준

- Android 기준: `com.guardian.adblock`, versionCode `26`, versionName `0.7.13-dev`.
- 최신 기능 검증 커밋: `4ae0da811d579f2a1495fbc89c1e94641cd5348d` (README 상태 문서 갱신 커밋은 별도).
- **병합 완료:** PR #43(Production workflow 설정/서명 gate), #46(publisher lock ownership), #47(filter cache hardening), #50(License API/PostgreSQL rate limit 및 HTTP/DB 보호), #51(Android wide-screen navigation rail), #52(VPN lifecycle race hardening), #54(만료 라이선스의 기기 해제), #55(1000dp 이상 대시보드 2열 배치).
- PR #54 Android CI run [43705549065](https://github.com/justinhblee-prog/guardian/actions/runs/43705549065) 전체 성공: unit tests, lint, debug APK, API 35 emulator 설치·실행, package/version 및 APK SHA-256 확인. 최종 빌드 전용 artifact 업로드는 의도적으로 skipped.
- PR #55 Android CI run [43705951073](https://github.com/justinhblee-prog/guardian/actions/runs/43705951073) 동일 검증 전체 성공. 최종 artifact 업로드는 의도적으로 skipped.
- Android v0.7.13-dev CI run [#704](https://github.com/justinhblee-prog/guardian/actions/runs/43815516892) 전체 성공: unit tests, lint, 이전 개발 APK 업데이트 설치, APK 조립, DNS asset, API 35 에뮬레이터 설치·실행, 패키지/버전, SHA-256 및 Artifact 업로드.
- v0.7.13-dev APK: 3,080,949 bytes, SHA-256 `32a549ac197101c8b514bb39b8e31fa21030caa4ae587a7ab81b6ac296571e71`. Debug 개발 서명본이며 production 서명본이 아닙니다.
- 이전 문서의 #41/#43/#44/#46/#47/#48/#49/#50/#51 미병합 기록은 이 상태 정정과 충돌할 경우 이 정정 내용을 우선합니다. #43/#46/#47/#50/#51은 이후 main에 병합되었습니다.
- 검증 Artifact `guardian-android-debug`는 2026-11-30까지 보존됩니다. 실제 스마트폰/태블릿 VPN·광고 차단 E2E는 미검증입니다.
- Debug APK/에뮬레이터 성공은 production 서명, 실제 Android 기기 VPN/TUN 라우팅, 실제 광고 제거 효과를 증명하지 않습니다.
- 운영 keystore/secrets, 운영 License API/DB/TLS 및 서명 manifest 배포 endpoint의 실제 운영 배포 검증은 미완료입니다.
- Android 스마트폰/태블릿과 공통 필터·라이선스 작업을 먼저 마무리합니다. Windows 클라이언트/설치 프로그램은 마지막 단계로 보류합니다.



## 제품 목표

Guardian AdBlock을 Windows PC, Android 스마트폰, Android 태블릿에서 사용할 수 있게 한다. 각 기기에서 동일한 필터 정책과 계정/라이선스 상태를 사용하고, 사용자는 모바일 브라우저에서도 중앙 관리 화면에 접속할 수 있어야 한다.

## 플랫폼별 구현

| 플랫폼 | 제공 형태 | 네트워크 차단 방식 | 현재 상태 |
|---|---|---|---|
| Android 스마트폰 | 네이티브 APK | Android VpnService 기반 DNS 필터 | v0.7.13-dev 빌드·에뮬레이터 검증 성공, 실기기 수용 테스트 필요 |
| Android 태블릿 | 동일 APK, 반응형 레이아웃 | Android VpnService 기반 DNS 필터 | 반응형 레이아웃 구현, 화면 크기별/실기기 검증 필요 |
| Windows 10/11 PC | 별도 설치형 데스크톱 앱/서비스 | Windows DNS 기반 필터 프록시 | 엔진·UI·설치 스크립트 구현 일부, 설치 패키지 및 실기기 복구 검증 미완료 (Android 후순위) |
| 모바일 관리 | 반응형 웹 관리자 화면/API | HTTPS 관리 API | License API `/admin` 구현, 운영 배포 및 end-to-end 검증 필요 |
| iPhone/iPad | 추후 별도 검토 | Network Extension 및 Apple 승인 요건 필요 | 범위 미확정 |

Android APK를 Windows에 그대로 설치하거나 Android VPN 코드를 Windows에서 재사용할 수 있다고 가정하지 않는다. Windows는 별도 네트워크 서비스와 설치/업데이트/복구 경로가 필요하다.

## 공통 백엔드

플랫폼 간 공통으로 사용할 서비스 경계:

- 필터 manifest 배포 및 서명 검증
- 필터 파일 저장/버전/해시 관리
- 계정, 라이선스, 기기 활성화 및 비활성화
- 앱/서비스 버전 및 업데이트 채널 관리
- 관리 콘솔용 인증·권한·감사 이벤트 API
- 기기 상태, 마지막 동기화, 필터 버전 및 업데이트 오류 표시

클라이언트는 manifest의 서명과 파일 SHA-256을 검증한 후에만 새 필터를 활성화한다. 서버가 침해되더라도 서명 개인키 없이는 정상 클라이언트가 위조 manifest를 수락하지 않도록 한다.

## 모바일 중심 운영 원칙

- 계정 가입, 기기 등록, 라이선스 확인, 필터 배포 상태 확인, 업데이트 채널 선택, 기기 해제는 모바일 브라우저의 관리 콘솔에서 수행할 수 있게 한다.
- 개인 서명키/비밀키를 웹 화면이나 채팅에 노출하지 않는다. 개인키는 서버 측 비밀 저장소 또는 별도 오프라인 안전 보관에 둔다.
- DNS 필터 파일은 공개 배포 가능하지만, 라이선스·기기 관리 API는 인증과 속도 제한을 적용한다.
- 운영자는 휴대폰에서 필터 버전 게시/롤백을 실행할 수 있게 하되, 모든 변경은 감사 로그에 남긴다.
- 백엔드는 인터넷에 공개된 HTTPS 호스팅이 필요하다. 앱 내부에만 서버를 넣는 구조로 만들지 않는다.

## Windows 클라이언트 설계 원칙

- 사용자 UI와 권한이 필요한 네트워크 서비스는 분리한다.
- 서비스는 최소 권한으로 실행하고, DNS 리디렉션/필터링 경로를 명시적으로 설치·해제한다.
- 설치·업데이트·제거 시 기존 DNS 설정과 네트워크 연결을 보존하고, 실패 시 복구 절차를 제공한다.
- Windows Defender Firewall/WFP 정책과 충돌 여부, VPN·보안 프로그램 공존성을 테스트한다.
- 기본 기능은 도메인 기반 DNS 필터링으로 한정한다. 브라우저 요소 숨김이나 동일 도메인에서 제공되는 광고 제거를 보장한다고 표시하지 않는다.
- 설치 패키지는 서명된 설치 파일로 배포한다. 코드 서명 인증서가 준비되기 전에는 개발 빌드와 정식 배포 빌드를 구분한다.

## 태블릿 UI 요구사항

- Android 스마트폰과 태블릿은 같은 APK와 데이터 모델을 사용한다.
- 좁은 화면은 하단 탐색/단일 열, 넓은 화면은 탐색 레일+2열 대시보드로 재배치한다.
- 필터 목록·활동 로그·기기 상태는 태블릿에서 표/목록 밀도를 높이고, 터치 목표 크기는 유지한다.
- 화면 회전, 분할 화면, 키보드/마우스 입력 및 큰 글꼴 접근성을 점검한다.

## 단계별 개발 순서 및 현재 진행

1. **Android 공통 앱:** 스마트폰·태블릿 공통 APK, 반응형 내비게이션/대시보드, DNS VPN 필터 엔진 구현 및 API 35 에뮬레이터 검증 완료. 실기기 수용 테스트가 남아 있다.
2. **서명 필터 연동:** manifest 서명/해시/anti-rollback 검증기를 실제 다운로드·캐시 교체 경로에 연결했고 단위 테스트가 있다. 운영 공개키·HTTPS endpoint를 사용한 실배포 end-to-end 검증이 남아 있다.
3. **필터 배포 서버:** publisher/updater, immutable versioned file, 서명 manifest HTTP 서버 및 Docker 구성 구현. 운영 노트북 설치와 외부 HTTPS 공개 검증이 남아 있다.
4. **라이선스/기기 API:** Node.js/PostgreSQL API, signed entitlement, 기기 등록/해제 및 모바일 관리자 웹 소스 구현. 운영 DB·키·HTTPS 및 실제 앱 활성화 end-to-end 검증이 남아 있다.
5. **Android 출시 마감:** 실기기 네트워크/광고 차단 수용 시험, 운영 signing keystore/CI secrets 구성, production APK/AAB 서명·업데이트 검증.
6. **Windows 후순위:** Android 완료 후 Windows DNS 엔진·UI·설치 스크립트의 패키징, 설치/제거, 재부팅·네트워크 복구 및 실기기 검증.
7. **통합 출시:** Android/Windows 공통 필터·라이선스 정책을 운영 환경에서 함께 검증하고 플랫폼별 출시 판정을 분리한다.

## 출시 판정 기준

- Android와 Windows 각각 설치/업데이트/제거 및 네트워크 복구가 검증되어야 한다.
- 필터 서명 검증, 해시 불일치, 만료, 잘못된 서명, 네트워크 실패, 롤백을 자동화 테스트한다.
- Windows 서비스 권한·재부팅 복구·VPN 공존성 검증이 끝나야 한다.
- 실제 기기 수용 테스트를 하지 않은 플랫폼은 정식 지원 완료로 표시하지 않는다.

## 현재 상태의 정직한 구분

- Android DNS 필터 클라이언트: 기능 구현 및 CI/에뮬레이터 검증이 진행됐으며 v0.7.13-dev APK를 확보했다. 실제 기기 수용 테스트, production 서명, 운영 서버 연결은 별도다.
- Android 태블릿 최적화: 반응형 내비게이션/대시보드 구현. 화면 크기·회전·접근성 및 태블릿 실기기 검증은 남아 있다.
- Windows PC 클라이언트: DNS 엔진·데스크톱 UI·설치/복구 스크립트 개발은 존재하지만, 설치 패키지/설치·제거·재부팅/네트워크 복구 검증은 Android 완료 후 수행한다.
- 공통 signed manifest publisher와 License API/PostgreSQL 및 관리자 웹 소스는 구현·자동화 테스트가 있으나, 노트북 실설치/공개 HTTPS/실제 기기 E2E는 미완료.
- iPhone/iPad: 범위 미확정이며 현재 지원을 약속하지 않는다.

## 2026-11-16 Android 우선 완료 기준

Android v0.7.13-dev APK와 CI 에뮬레이터 검증은 확보했지만, 제품 출시 완료와는 구분한다. Android를 먼저 마무리하기 위한 잔여 게이트는 다음과 같다.

1. 실기기 VPN 권한 승인, DNS 트래픽 전달/차단/허용, 보호 시작 실패 시 복구 동작 확인.
2. Wi-Fi와 모바일 데이터 전환, IPv4/IPv6, Private DNS, 다른 VPN과 앱 제외 조합 확인.
3. 실제 공개 HTTPS 필터 배포 주소 및 APK에 고정된 공개키를 이용한 manifest·필터 다운로드/서명검증 end-to-end 확인.
4. 운영 License API/DB 연결 및 기기 활성화·갱신·해제 end-to-end 확인.
5. 운영 keystore와 보호된 CI secrets를 준비한 production APK/AAB 서명·설치·업데이트 검증.
6. Android 스마트폰/태블릿 검증 완료 후 Windows 설치형 패키지의 빌드·설치·제거·복구 검증 진행.

**현재 확보된 APK:** [Guardian Android v0.7.13-dev CI Artifact](https://github.com/justinhblee-prog/guardian/actions/runs/43815516892). Debug 서명 개발 빌드이며 Play Store/정식 상용 릴리스 파일이 아니다.
