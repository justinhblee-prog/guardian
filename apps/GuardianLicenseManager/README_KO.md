# Guardian License Manager (Windows)

Guardian 사용자 앱과 분리된 관리자 전용 Windows 데스크톱 앱입니다. WPF / .NET 8 기반이며 기존 Guardian License API와 PostgreSQL을 사용합니다.

## 설치 프로그램
GitHub Actions 산출물 `guardian-license-manager-installer-win-x64`에 Windows 10/11 x64용 설치 파일이 생성됩니다.
- `Guardian-License-Manager-Setup-1.1.0-win-x64.exe` 실행
- 기본 설치 경로: `C:\Program Files\Guardian License Manager`
- 시작 메뉴 바로 가기 제공, 바탕 화면 바로 가기는 설치 중 선택 가능
- Windows 설정의 앱 목록에서 제거 가능
- CI에서 무인 설치·제거 스모크 테스트 수행

## 화면 구성
- Windows 관리자 앱 창 기본 크기 900×650, 최소 680×520
- 모바일은 반응형 PWA 관리자 화면으로 제공하며 Android Chrome/iOS Safari에서 홈 화면에 추가해 실행합니다.
- Windows 헤더/창 아이콘과 모바일 PWA는 Guardian 방패-G 주 아이콘을 공유합니다.

## 구현 범위
- HTTPS License API 관리자 연결 (관리자 Bearer 토큰은 메모리에만 유지)
- 라이선스 현황 요약 및 최근 200건 목록
- 고객명·이메일·주문번호, 기간, 기기 수 기반 라이선스 발급
- 발급 키 최초 1회 표시 및 복사
- 라이선스 정지 (확인 대화상자 포함)
- 연결 해제 시 토큰과 화면 데이터 제거

## 보안
- API 주소는 HTTPS만 허용합니다. HTTP는 loopback 개발 테스트에만 허용합니다.
- 관리자 토큰을 파일, Windows 레지스트리, 브라우저 저장소에 저장하지 않습니다.
- RSA 개인키는 앱에 포함하지 않습니다. 서명은 서버에서 수행합니다.
- 운영 API 도메인과 관리자 자격증명은 별도 배포 설정에서 입력합니다.

## 현재 제한
- API 연동형 발급·조회·정지 데스크톱 MVP입니다.
- 고객 검색/편집, 상세 기기 목록과 개별 기기 해제, 감사 로그, 관리자 RBAC/MFA, 자동 업데이트, 코드 서명 및 운영 API 검증은 후속 과제입니다.
- 실제 고객 발급은 운영 DB, HTTPS, 운영 RSA 키 및 구매 확인 절차를 검증한 뒤에만 활성화해야 합니다.
- 설치 프로그램은 현재 코드 서명되지 않아 Windows SmartScreen 경고가 표시될 수 있습니다.

## 모바일 관리자 앱
- 운영 API의 `/admin` 화면은 반응형 모바일 레이아웃을 제공합니다.
- HTTPS로 접속한 뒤 브라우저 메뉴의 `홈 화면에 추가`를 선택하면 앱처럼 실행할 수 있습니다.
- 관리자 토큰과 발급 키는 브라우저 저장소 및 서비스 워커 캐시에 저장하지 않습니다.
