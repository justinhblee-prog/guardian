# Guardian License Manager (Windows)

Guardian 사용자 앱과 분리된 관리자 전용 Windows 데스크톱 앱입니다. WPF / .NET 8 기반이며 기존 Guardian License API와 PostgreSQL을 사용합니다.

## 화면 구성
- Windows 관리자 앱 기본 창을 980×700으로 축소하고 760×560까지 크기 조절 가능하도록 구성했습니다.
- 모바일은 반응형 PWA 관리자 앱으로 제공하며, Android Chrome/iOS Safari에서 홈 화면에 추가해 실행합니다.
- Windows 헤더/창 아이콘과 모바일 PWA는 Guardian 방패-G 주 아이콘을 공유합니다.

## 구현 범위
- HTTPS License API 관리자 연결 (관리자 Bearer 토큰은 메모리에만 유지)
- 라이선스 현황 요약 및 최근 200건 목록
- 고객명·이메일·주문번호, 기간, 기기 수 기반 라이선스 발급
- 발급 키 최초 1회 표시 및 복사
- 라이선스 정지 (확인 대화상자 포함)
- 연결 해제 시 토큰과 화면 데이터 제거

## 빌드
Windows 10/11 및 .NET 8 SDK:
```powershell
dotnet restore apps/GuardianLicenseManager/GuardianLicenseManager.csproj
dotnet build apps/GuardianLicenseManager/GuardianLicenseManager.csproj -c Release
dotnet publish apps/GuardianLicenseManager/GuardianLicenseManager.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o artifacts/GuardianLicenseManager
```

## 보안
- API 주소는 HTTPS만 허용합니다. HTTP는 loopback 개발 테스트에만 허용합니다.
- 관리자 토큰을 파일, Windows 레지스트리, 브라우저 저장소에 저장하지 않습니다.
- RSA 개인키는 앱에 포함하지 않습니다. 서명은 서버에서 수행합니다.
- 운영 API 도메인과 관리자 자격증명은 별도 배포 설정에서 입력합니다.

## 현재 제한
- 이 첫 구현은 API 연동형 발급·조회·정지 데스크톱 MVP입니다.
- 고객 검색/편집, 상세 기기 목록과 개별 기기 해제, 감사 로그, 관리자 RBAC/MFA, 자동 업데이트, 코드 서명 및 설치 프로그램은 후속 단계입니다.
- 실제 고객 발급은 운영 DB, HTTPS, 운영 RSA 키 및 구매 확인 절차를 검증한 뒤에만 활성화해야 합니다.


## 모바일 관리자 앱

- 운영 API의 `/admin` 화면은 반응형 모바일 레이아웃을 제공합니다.
- Android Chrome 또는 iOS Safari에서 HTTPS로 접속한 뒤 브라우저 메뉴의 `홈 화면에 추가`를 선택하면 앱처럼 실행할 수 있습니다.
- Guardian 주 아이콘(방패/G 심볼)을 관리자 웹앱 아이콘과 화면 헤더에 사용합니다.
- 관리자 토큰과 발급 키는 브라우저 저장소 및 서비스 워커 캐시에 저장하지 않습니다. 서비스 워커는 네트워크 전용입니다.
