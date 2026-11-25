# Guardian License Manager (Windows)

Guardian 사용자 앱과 분리된 관리자 전용 Windows 데스크톱 앱입니다. WPF / .NET 8 기반이며 기존 Guardian License API와 PostgreSQL을 사용합니다.

## Windows 설치 프로그램
GitHub Actions에서 `guardian-license-manager-installer-win-x64` 산출물로 Windows 10/11 x64용 설치 파일을 생성합니다.
- 설치 파일: `Guardian-License-Manager-Setup-1.2.0-win-x64.exe`
- 기본 설치 위치: `C:\Program Files\Guardian License Manager`
- 시작 메뉴 바로 가기 및 선택형 바탕 화면 바로 가기
- Windows 앱 목록에서 제거 지원
- CI에서 무인 설치 후 실행 파일 존재 확인 및 제거 스모크 테스트
- 현재 코드 서명 전이므로 SmartScreen 경고가 표시될 수 있습니다.

## 화면 및 기능
- 관리자 앱 창 900×650, 최소 680×520
- Guardian 방패-G 주 아이콘 적용
- HTTPS License API 연결, 메모리 내 관리자 토큰
- 라이선스 현황, 7/30/90/365일 및 영구(PERMANENT) 발급, 키 복사, 정지, 목록 새로고침\n- 제품 업데이트 관리 창: Android/Windows, Stable/Beta, 버전, HTTPS 배포 URL, SHA-256, 최소 지원 버전, 필수 업데이트, 릴리스 노트 등록 및 조회\n- 설치 파일은 별도 HTTPS 배포 저장소에 업로드한 뒤 URL과 SHA-256을 등록합니다. 관리자 도구가 바이너리 파일을 직접 업로드하지는 않습니다.\n- 공개 업데이트 확인 API: `GET /v1/updates/latest?platform=android|windows&channel=stable|beta`\n- 관리자 업데이트 API: `GET/POST /v1/admin/updates` (관리자 Bearer 인증 필수)
- 영구 라이선스도 허용 기기 수 제한과 관리자 정지/회수 적용
- 모바일 관리자 화면은 별도 반응형 PWA(`/admin`) 제공

## 보안 및 운영 제한
- RSA 개인키는 앱에 포함하지 않고 서버에서만 서명합니다.
- 실제 발급 전 운영 HTTPS API, DB, 키 관리, 구매 확인 절차를 검증해야 합니다.
- 고객 검색/편집, 상세 기기 관리, 감사 로그, RBAC/MFA, 자동 업데이트 및 코드 서명은 후속 작업입니다.
