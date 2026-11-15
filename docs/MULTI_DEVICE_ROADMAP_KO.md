# Guardian AdBlock 멀티 디바이스 제품 설계

기준일: 2026-11-15

## 2026-11-15 통합 상태 정정 — main 기준

- Android 기준: `com.guardian.adblock`, versionCode `21`, versionName `0.7.8-dev`.
- 최신 main: `ce2b0e144959c04f23b96a5a19c53e153afae7d1`.
- **병합 완료:** PR #43(Production workflow 설정/서명 gate), #46(publisher lock ownership), #47(filter cache hardening), #50(License API/PostgreSQL rate limit 및 HTTP/DB 보호), #51(Android wide-screen navigation rail), #52(VPN lifecycle race hardening), #54(만료 라이선스의 기기 해제), #55(1000dp 이상 대시보드 2열 배치).
- PR #54 Android CI run [43705549065](https://github.com/justinhblee-prog/guardian/actions/runs/43705549065) 전체 성공: unit tests, lint, debug APK, API 35 emulator 설치·실행, package/version 및 APK SHA-256 확인. 최종 빌드 전용 artifact 업로드는 의도적으로 skipped.
- PR #55 Android CI run [43705951073](https://github.com/justinhblee-prog/guardian/actions/runs/43705951073) 동일 검증 전체 성공. 최종 artifact 업로드는 의도적으로 skipped.
- PR #56은 Android 라이선스 refresh/deactivate 경합 직렬화와 서버 refresh 403 시 로컬 entitlement 폐기를 통합하는 후속 변경이며, CI run [43706736843](https://github.com/justinhblee-prog/guardian/actions/runs/43706736843) 진행 중입니다. 전체 성공 및 main 병합 전까지 미완료로 취급합니다.
- 이전 PR #48/#49의 라이선스 경합/403 변경은 최신 main 기준 통합 PR #56으로 대체 중입니다. #56 검증 전에는 해당 기능을 완료로 계산하지 않습니다.
- 이전 문서의 #41/#43/#44/#46/#47/#48/#49/#50/#51 미병합 기록은 이 상태 정정과 충돌할 경우 이 정정 내용을 우선합니다. #43/#46/#47/#50/#51은 이후 main에 병합되었습니다.
- 0.7.8-dev 최종 설치 APK 및 source package는 아직 최종 main 빌드에서 artifact로 생성·다운로드·해시 검증되지 않았습니다. 이전 0.7.7-dev APK를 최신본으로 표시하지 않습니다.
- Debug APK/에뮬레이터 성공은 production 서명, 실제 Android 기기 VPN/TUN 라우팅, 실제 광고 제거 효과를 증명하지 않습니다.
- 운영 keystore/secrets, 운영 License API/DB/TLS 및 서명 manifest 배포 endpoint의 실제 운영 배포 검증은 미완료입니다.
- Android 스마트폰/태블릿과 공통 필터·라이선스 작업을 먼저 마무리합니다. Windows 클라이언트/설치 프로그램은 마지막 단계로 보류합니다.



## 제품 목표

Guardian AdBlock을 Windows PC, Android 스마트폰, Android 태블릿에서 사용할 수 있게 한다. 각 기기에서 동일한 필터 정책과 계정/라이선스 상태를 사용하고, 사용자는 모바일 브라우저에서도 중앙 관리 화면에 접속할 수 있어야 한다.

## 플랫폼별 구현

| 플랫폼 | 제공 형태 | 네트워크 차단 방식 | 현재 상태 |
|---|---|---|---|
| Android 스마트폰 | 네이티브 APK | Android VpnService 기반 DNS 필터 | 기존 Android 앱 개발 중 |
| Android 태블릿 | 동일 APK, 반응형 레이아웃 | Android VpnService 기반 DNS 필터 | 태블릿 UI/실기기 검증 필요 |
| Windows 10/11 PC | 별도 설치형 데스크톱 앱/서비스 | Windows Filtering Platform(WFP) 또는 검증된 로컬 DNS 프록시 | 미구현 |
| 모바일 관리 | 반응형 웹 관리 콘솔 | HTTPS 관리 API | 미구현 |
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

## 단계별 개발 순서

1. Android 앱을 스마트폰·태블릿 공통으로 안정화하고 화면 크기별 UI 검증을 추가한다.
2. manifest 검증기를 실제 필터 다운로드/캐시 경로에 연결한다. 운영 공개키가 확정되기 전에는 정식 서명 배포를 활성화하지 않는다.
3. 필터 manifest 서버와 공개 배포 API를 구현하고 로컬 통합 테스트를 추가한다.
4. 라이선스/기기 등록 API와 모바일 관리 콘솔을 구현한다.
5. Windows 네트워크 서비스와 데스크톱 UI를 별도 모듈로 개발한다.
6. Android/Windows 클라이언트가 같은 필터 manifest와 계정/라이선스 정책을 사용하도록 통합한다.
7. Windows VM 및 Android 에뮬레이터/다양한 화면 크기에서 자동화 검증 후 실제 기기 수용 테스트를 진행한다.

## 출시 판정 기준

- Android와 Windows 각각 설치/업데이트/제거 및 네트워크 복구가 검증되어야 한다.
- 필터 서명 검증, 해시 불일치, 만료, 잘못된 서명, 네트워크 실패, 롤백을 자동화 테스트한다.
- Windows 서비스 권한·재부팅 복구·VPN 공존성 검증이 끝나야 한다.
- 실제 기기 수용 테스트를 하지 않은 플랫폼은 정식 지원 완료로 표시하지 않는다.

## 현재 상태의 정직한 구분

- Android DNS 필터 클라이언트: 구현이 진행 중이며 CI 단위 테스트/빌드 검증이 존재한다. 실제 기기 수용 테스트는 별도다.
- Android 태블릿 최적화: 별도 검증 전.
- Windows PC 클라이언트: 미구현.
- 공통 manifest/라이선스 서버 및 모바일 관리 콘솔: 설계 단계이며 운영 배포 전.
- iPhone/iPad: 범위 미확정이며 현재 지원을 약속하지 않는다.
