# Guardian AdBlock 멀티 디바이스 제품 설계

기준일: 2026-11-21

## 2026-11-21 최신 통합 상태 — main 기준

- Android 최신 기능 검증 빌드: `0.7.39-dev`, versionCode `52`, commit `1da247e0eef04d0ea3d4f4a764d9dd2f1e9a7c01`. [CI run 44890022912](https://github.com/justinhblee-prog/guardian/actions/runs/44890022912)에서 unit tests, lint, APK build 및 API 35 emulator VPN/DNS E2E 통과. 실기기 검증은 별도입니다.
- Windows: `0.1.0-dev` EXE/설치 프로그램 build와 install smoke test 통과. [CI run 44887248268](https://github.com/justinhblee-prog/guardian/actions/runs/44887248268). Authenticode 서명은 없는 개발 패키지입니다.
- Windows와 Android 공통 제품 ID는 `guardian-pro`입니다. Android는 기존 `guardian-android` 토큰도 하위 호환으로 허용합니다.
- License API CI [44886969350](https://github.com/justinhblee-prog/guardian/actions/runs/44886969350), Windows DNS/license CI [44887248317](https://github.com/justinhblee-prog/guardian/actions/runs/44887248317) 통과. 실제 Production API/private key/secrets 배포는 미완료입니다.
- 로컬 Ollama 멀티플로우와 Windows bootstrap이 main에 통합되었습니다. `scripts/Install-GuardianLocalOllamaRunner.cmd`는 현재 Windows 사용자 계정에서 GitHub runner와 15분 주기 local Git agent를 설치합니다. 사용자의 노트북에서 한 번 실행하고 GitHub runner Online 상태를 확인해야 합니다.
- 7개 upstream filter source는 재배포 권리 승인 증빙이 없어 미승인 상태입니다. Production 배포 gate는 이를 차단합니다.
- Production release configuration run [44889767841](https://github.com/justinhblee-prog/guardian/actions/runs/44889767841)은 설정 검증만 통과했고 실제 `production-release` job은 Production secrets 부재로 skip되었습니다.
- 따라서 현재는 Android/Windows 개발 패키지와 공통 라이선스 구현이 검증된 상태이며, Production 1.0 출시 완료 상태가 아닙니다.

## 2026-11-17 통합 상태 — historical snapshot

- Android: `com.guardian.adblock`, versionCode `27`, versionName `0.7.14-dev`, main commit `28abc77342f34898dd03c198fef942c0b49a7fe0`.
- PR #78 운영자 라이선스 대시보드 개선 및 PR #79 APK/source ZIP artifact 검증 자동화가 main에 병합되었습니다.
- Android CI [44060999851](https://github.com/justinhblee-prog/guardian/actions/runs/44060999851) 성공: unit tests, lint, APK update install, API 35 emulator VPN/DNS E2E.
- APK artifact: 3,119,618 bytes, SHA-256 `24db2c1db46a66d2117cb35f7541c893b3321ee7de46363b615fd1a585319c78`.
- Source ZIP: 446,281 bytes, SHA-256 `a22d74b994b919e19de4125a719243786b76fa50dbb2d19d85200345afdcee17`.
- Event artifact audit [44062116800](https://github.com/justinhblee-prog/guardian/actions/runs/44062116800) 성공: APK/source checksum 및 ZIP 무결성 확인.
- License API/PostgreSQL CI [44059072829](https://github.com/justinhblee-prog/guardian/actions/runs/44059072829), signed filter distribution CI [44052664109](https://github.com/justinhblee-prog/guardian/actions/runs/44052664109) 및 [44052665884](https://github.com/justinhblee-prog/guardian/actions/runs/44052665884) 성공.
- Production release 설정 검증 [44052664040](https://github.com/justinhblee-prog/guardian/actions/runs/44052664040) 성공. 운영 keystore/secrets가 없어 production 서명 작업은 skipped이며 정식 production APK/AAB는 없습니다.
- 실제 스마트폰/태블릿 VPN/TUN, 광고 제거 효과, Wi-Fi↔모바일 전환, 운영 HTTPS/TLS 및 License API 서버 배포는 미검증입니다.
- Android와 Windows 개발 패키지 및 공통 `guardian-pro` 라이선스 경로가 구현되었습니다. 두 플랫폼의 Production release는 동일한 운영 라이선스/API/서명 체계를 사용하며, 운영키·서버 배포·필터 권리 승인·실기기 수용 테스트가 완료될 때까지 Production 1.0은 보류합니다.
## 제품 목표

Guardian AdBlock을 Windows PC, Android 스마트폰, Android 태블릿에서 사용할 수 있게 한다. 각 기기에서 동일한 필터 정책과 계정/라이선스 상태를 사용하고, 사용자는 모바일 브라우저에서도 중앙 관리 화면에 접속할 수 있어야 한다.

## 플랫폼별 구현

| 플랫폼 | 제공 형태 | 네트워크 차단 방식 | 현재 상태 |
|---|---|---|---|
| Android 스마트폰 | 네이티브 APK | Android VpnService 기반 DNS 필터 | v0.7.39-dev 빌드 및 API 35 에뮬레이터 VPN/DNS E2E 성공, 실기기 수용 테스트 필요 |
| Android 태블릿 | 동일 APK, 반응형 레이아웃 | Android VpnService 기반 DNS 필터 | 반응형 레이아웃 구현, 회전/화면 크기별 실기기 검증 필요 |
| Windows 10/11 PC | 별도 설치형 데스크톱 앱/서비스 | Windows DNS 기반 필터 프록시 | 0.1.0-dev EXE/설치 프로그램 build·install smoke test 성공, Authenticode/Production signing 필요 |
| 모바일/PC 관리 | 반응형 웹 관리자 화면/API | HTTPS 관리 API | `guardian-pro` 공통 라이선스 구현 및 CI 통과, Production API/TLS/private key 배포 대기 |
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

1. **Android 공통 앱·라이선스:** 스마트폰·태블릿 공통 APK, 반응형 UI, DNS VPN 엔진, entitlement 및 기기 등록/해제 코드/CI 완료. 실기기 수용 시험은 개발 완료 후 진행한다.
2. **서명 필터 연동:** manifest 서명/해시/anti-rollback 검증기와 다운로드·캐시 교체 경로, publisher/updater 및 distribution CI 완료. 운영 개인키 생성과 공개 HTTPS 게시 검증은 남아 있다.
3. **License API·관리자 화면:** Node.js/PostgreSQL API, 플랫폼 공통 `guardian-pro`, signed entitlement, Android/Windows 클라이언트 활성화·갱신·기기 해제, 기기 관리 및 감사 로그 대시보드 구현. 공통 License API CI와 Windows 클라이언트 단위 테스트가 통과했다. Windows 개발 EXE/설치 프로그램 설치 smoke test도 통과했다. 운영 DB·키·HTTPS/Tailscale 및 실제 두 플랫폼 E2E 검증은 남아 있다.
4. **Android production signing:** release 설정 검증과 임시 키 smoke test 완료. 운영 keystore/secrets가 없어 production APK/AAB 서명은 대기.
5. **실기기/운영 수용 시험:** Android VPN/DNS, 광고 차단, 네트워크 전환, 운영 서버 E2E는 개발 항목 완료 후 수행한다.
6. **Windows 후순위:** Android와 공통 기능 완료 후 Windows EXE/설치 프로그램, 설치/제거, DNS 복구, 재부팅 및 실기기 검증을 마지막에 수행한다.
7. **통합 출시:** Android/Windows 공통 필터·라이선스 정책을 운영 환경에서 함께 검증하고 플랫폼별 출시 판정을 분리한다.

## 출시 판정 기준

- Android와 Windows 각각 설치/업데이트/제거 및 네트워크 복구가 검증되어야 한다.
- 필터 서명 검증, 해시 불일치, 만료, 잘못된 서명, 네트워크 실패, 롤백을 자동화 테스트한다.
- Windows 서비스 권한·재부팅 복구·VPN 공존성 검증이 끝나야 한다.
- 실제 기기 수용 테스트를 하지 않은 플랫폼은 정식 지원 완료로 표시하지 않는다.

## 현재 상태의 정직한 구분

- Android DNS 필터 클라이언트: v0.7.14-dev APK와 전체 소스 ZIP, CI/에뮬레이터 VPN/DNS E2E 및 자동 artifact 감사가 성공했다. 실제 기기 수용 테스트, production 서명, 운영 서버 연결은 별도다.
- Android 태블릿 최적화: 반응형 내비게이션/대시보드 구현. 화면 크기·회전·접근성 및 태블릿 실기기 검증은 남아 있다.
- Windows PC 클라이언트: DNS 엔진·데스크톱 UI·설치/복구 스크립트 개발은 존재하지만, 설치 패키지/설치·제거·재부팅/네트워크 복구 검증은 Android 완료 후 수행한다.
- 공통 signed manifest publisher와 License API/PostgreSQL 및 운영자 관리 대시보드는 구현·자동화 테스트가 통과했으나, 운영 노트북 실설치/공개 HTTPS/실제 기기 E2E는 미완료.
- iPhone/iPad: 범위 미확정이며 현재 지원을 약속하지 않는다.

## 2026-11-17 Android 우선 완료 기준

Android v0.7.14-dev APK와 전체 소스 ZIP, API 35 에뮬레이터 VPN/DNS E2E 및 artifact 감사는 확보했습니다. 이는 실제 제품 출시 완료나 실기기 광고 제거 검증과는 구분합니다.

1. Android 라이선스·기기 관리, signed filter client/publisher, License API 및 관리자 대시보드의 코드/CI 잔여 작업을 마감합니다.
2. 운영 HTTPS 필터 게시와 License API/DB/TLS 연결을 위한 배포 구성·운영 절차를 안전하게 정리합니다. 운영 개인키는 저장소/채팅에 올리지 않습니다.
3. 운영 keystore/secrets가 준비되면 production APK/AAB 서명·설치·업데이트를 검증합니다. 현재 production signing workflow는 설정 검증만 통과했고 실제 서명 작업은 skipped 상태입니다.
4. 개발 항목 완료 후 Android 스마트폰/태블릿에서 VPN 권한, 실제 DNS 전달/차단/허용, 보호 시작·중지 및 실패 복구를 검증합니다.
5. Wi-Fi↔모바일, IPv4/IPv6, Private DNS, 다른 VPN, 앱 제외, 화면 잠금/재부팅 및 장시간 안정성을 확인합니다.
6. Android 및 공통 기능이 끝난 뒤 Windows EXE/설치 프로그램의 빌드·설치·제거·DNS 복구·재부팅 검증을 마지막으로 진행합니다.

**현재 확보된 개발 artifact:** [Guardian Android v0.7.14-dev APK + source ZIP](https://github.com/justinhblee-prog/guardian/actions/runs/44060999851). Debug 서명 개발 빌드이며 Play Store/정식 production 릴리스 파일이 아니다.


## 2026-11-21 공통 라이선스 구현 업데이트

- 신규 라이선스 제품 코드는 플랫폼 공통 `guardian-pro`이며 기존 `guardian-android-pro` 레코드는 삭제하지 않습니다. 기존 DB에는 idempotent migration `005_shared_guardian_product.sql`을 적용합니다.
- 서버가 발급하는 신규 RSA-SHA256 entitlement는 `product=guardian` claim을 사용합니다. Android 검증기는 신규 공통 claim과 기존 `guardian-android` claim을 모두 수용합니다.
- Windows `windows_engine/license_client.py`는 HTTPS activate/refresh/deactivate, 설치별 deviceBinding, RSA 서명·만료·기기 바인딩 검증 및 로컬 entitlement 저장을 구현했습니다. 데스크톱 UI의 라이선스 메뉴에서 API 주소/공개키 설정, 활성화, 갱신, 기기 해제를 제공합니다.
- License API CI: [run 44886969350](https://github.com/justinhblee-prog/guardian/actions/runs/44886969350) 성공.
- Windows Linux/Windows 2022 단위 테스트: [run 44886846462](https://github.com/justinhblee-prog/guardian/actions/runs/44886846462) 성공.
- Windows 개발 EXE/설치 프로그램 빌드, 설치 smoke test 및 패키지 검사: [run 44886846472](https://github.com/justinhblee-prog/guardian/actions/runs/44886846472) 성공. Artifact는 unsigned 개발 패키지이며 정식 코드 서명본이 아닙니다.
- Android verifier의 공통 claim 수용 테스트가 포함된 Android workflow [run 44886819129](https://github.com/justinhblee-prog/guardian/actions/runs/44886819129)는 현재 runner 대기 상태이며 성공으로 판정하지 않습니다.
- 실제 운영 RSA 개인키/Android release keystore 생성·등록, License API/DB/TLS 배포, Windows와 Android 양쪽의 실제 라이선스 발급·활성화 E2E는 아직 검증되지 않았습니다.


## 2026-11-21 최신 구현/검증 상태 보충

- Android main [workflow 44892153515](https://github.com/justinhblee-prog/guardian/actions/runs/44892153515): versionName `0.7.40-dev`, versionCode `53`, APK 3,213,935 bytes, SHA-256 `8634e59d73baa59cb103c3534afe027b877eac3e610e6cb0d1edb17c94e99fa4`; unit tests, lint, assemble 및 source ZIP 검증 통과. API 35 emulator VPN/DNS runtime stage는 진행 중이며 완료로 간주하지 않는다.
- Shared license product `guardian-pro` is implemented for Android and Windows clients. License API CI [44886969350](https://github.com/justinhblee-prog/guardian/actions/runs/44886969350), Windows license tests [44887248317](https://github.com/justinhblee-prog/guardian/actions/runs/44887248317), and Windows development installer build/install smoke test [44887248268](https://github.com/justinhblee-prog/guardian/actions/runs/44887248268) passed.
- Android/Windows production use is not yet enabled: production private keys and Android release keystore/secrets are not registered; License API/PostgreSQL/TLS has not been deployed and tested against real devices. Production signing job remains skipped.
- Local runner bootstrap workflow [44892153506](https://github.com/justinhblee-prog/guardian/actions/runs/44892153506) only parses the PowerShell installer and checks safety guards; it does not register or prove a persistent self-hosted runner.
- The privacy fix now stores safe filter-update error codes rather than raw exception messages; the UI maps those codes to safe Korean descriptions.
- Do not label the project 1.0.0 complete until production signing, signed filter distribution, Android physical-device VPN/DNS, Windows physical-PC DNS install/recovery, and shared cross-platform license activation/refresh/deactivation E2E have passed.


### 2026-11-21 19:12 KST 검증 상태 정정

- Android [run 44892153515](https://github.com/justinhblee-prog/guardian/actions/runs/44892153515) 최종 완료 상태는 **success**입니다. API 35 emulator upgrade/install/launch 및 VPN DNS E2E가 성공했고 최종 검증 보고서 artifact ID는 `14703101227`입니다.
- 이 결과는 에뮬레이터 검증입니다. 실제 Android 스마트폰/태블릿에서의 VPN 라우팅·네트워크 전환·광고 제거 수용 테스트는 여전히 별도입니다.
