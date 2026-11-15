# Guardian AdBlock Android — 모바일 설치 안내 (0.7.8-dev)

이 문서는 Android 스마트폰과 태블릿용 설치 검증 APK 안내입니다. 현재 CI 기본 검증은 APK를 빌드하고 검사하지만 일반 push에서는 artifact를 업로드하지 않습니다.

## 최신 Debug APK artifact 생성

1. GitHub 저장소 `justinhblee-prog/guardian`의 **Actions** 탭에서 **Guardian AdBlock Android APK** workflow를 엽니다.
2. **Run workflow**를 선택하고 `main` 브랜치로 실행합니다. 이 수동 실행만 설치용 Debug APK와 소스 패키지를 artifact로 업로드합니다.
3. 실행이 **Success**가 된 뒤 Artifacts에서 `Guardian-AdBlock-Android-Full-Package-v0.7.8-dev`를 다운로드합니다.
4. ZIP을 압축 해제하고 `app-debug.apk`, `SHA256SUMS.txt`, `Guardian-AdBlock-Source-v0.7.8-dev.zip`, `VERIFICATION_SUMMARY_KO.md`를 확인합니다.
5. 설치 전에 SHA-256을 확인합니다. Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
Get-FileHash .\app-debug.apk -Algorithm SHA256
```

출력한 SHA-256 값이 `SHA256SUMS.txt`의 APK 항목과 일치해야 합니다. artifact가 없거나 검증 실행이 실패했다면 해당 APK를 사용하지 마십시오.

## Android 설치

1. 검증된 `app-debug.apk`를 Android 기기에서 엽니다.
2. 설치가 차단되면 Android 안내에 따라 해당 파일 앱/브라우저의 '알 수 없는 앱 설치' 권한을 허용합니다.
3. Guardian AdBlock을 설치하고 실행합니다.
4. **보호 시작**을 누르고 Android VPN 연결 승인 창에서 허용합니다.
5. 보호 화면에서 DNS 질의·차단·전달 통계가 실제로 증가하는지 확인합니다. 터널이 켜졌다는 표시만으로 광고 차단이 검증된 것은 아닙니다.

## 기기 수용 테스트

- 차단 목록에 있는 도메인과 사용자 허용 목록 도메인을 각각 조회해 결과 비교
- 일반 웹사이트의 DNS 조회가 정상 동작하는지 확인
- 보호 시작/중지 반복 및 다른 VPN 앱과의 충돌 확인
- Wi-Fi와 모바일 데이터 전환, 화면 잠금, 기기 재부팅 후 복구 확인
- Private DNS 설정이 자동/호스트명으로 지정된 경우 DNS 필터 우회 가능성을 확인
- DNS 활동 화면에서 실제 질의·차단·전달·실패 수치를 확인

## 지원 범위와 제한

- 이 artifact는 Android debug 서명 APK이며 Play 스토어 배포용 production 서명본이 아닙니다.
- Android는 사용자 프로필당 VPN을 하나만 활성화할 수 있으므로 다른 VPN 앱은 중지해야 할 수 있습니다.
- Guardian이 전달하는 업스트림 DNS에는 UDP/53, TCP/53, DNS-over-TLS(DoT/853), DNS-over-HTTPS(DoH/443)를 선택할 수 있습니다.
- 앱이 자체적으로 VPN을 우회해 직접 전송하는 DoH/DoT 요청은 가로채지 못할 수 있습니다. 앱이 직접 사용하는 암호화 DNS까지 강제로 필터링하려면 Android Private DNS 및 앱별 동작에 대한 별도 검증이 필요합니다.
- DNS-over-TCP(53)는 지원하지만 IPv6 extension header와 IP fragmentation/reassembly는 지원하지 않습니다.
- DNS 필터링은 웹페이지 요소 숨김, 동일 도메인(first-party) 광고, 앱 화면 내부 광고 제거를 보장하지 않습니다.
- CI 성공은 자동화 테스트·APK 패키징·에뮬레이터 실행 검증이며, 실제 기기 VPN 라우팅이나 광고 제거 효과를 증명하지 않습니다.
- 정식 production APK/AAB는 별도의 운영 공개키·API 주소·서명키 및 production 배포 설정이 완료된 후 생성해야 합니다.
