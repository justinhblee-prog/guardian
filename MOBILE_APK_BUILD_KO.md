# Guardian AdBlock Android — 모바일 설치 안내 (0.6.4-dev)

이 저장소는 이미 GitHub Actions에 연결되어 있습니다. 저장소를 새로 만들거나 소스 파일을 수동으로 업로드할 필요가 없습니다.

## APK 빌드

1. GitHub 저장소 `justinhblee-prog/guardian`의 **Actions** 탭을 엽니다.
2. **Guardian AdBlock Android APK** workflow를 선택합니다.
3. 가장 최근 main 커밋의 실행 결과가 **Success**인지 확인합니다.
4. 성공한 실행의 **Artifacts**에서 `Guardian-AdBlock-Android-APK-v0.6.4-dev`를 선택해 다운로드합니다.
5. 내려받은 ZIP을 압축 해제하면 `app-debug.apk`와 `SHA256SUMS.txt`가 있습니다.

저장소에 Android 소스가 반영되면 push 기반 workflow가 자동 실행됩니다. 실패한 실행의 APK를 최종 설치본으로 사용하지 마십시오.

## Android 설치

1. `app-debug.apk`를 Android 기기에서 엽니다.
2. 설치가 차단되면 Android 안내에 따라 해당 파일 앱/브라우저의 '알 수 없는 앱 설치' 권한을 허용합니다.
3. Guardian AdBlock을 설치하고 실행합니다.
4. **보호 시작**을 누르고 Android VPN 연결 승인 창에서 허용합니다.
5. 홈 화면에서 보호 상태가 활성으로 표시되는지 확인합니다.

## 테스트 권장 순서

- 차단 규칙에 등록된 도메인 질의가 차단되는지 확인
- 허용 목록에 등록한 도메인이 차단 예외가 되는지 확인
- 일반 도메인이 정상 조회되는지 확인
- 보호 시작/중지 반복
- Wi-Fi와 모바일 데이터 전환
- 화면 잠금 후 복귀 및 앱 프로세스 재시작

## 주의

- 이 artifact는 Debug APK이며 Play 스토어 배포용 release 서명본이 아닙니다.
- Android는 사용자 프로필당 VPN을 하나만 활성화할 수 있으므로 다른 VPN 앱은 중지해야 할 수 있습니다.
- DoH/DoT, client-side TCP DNS, IPv6 extension header/fragment reassembly는 미지원입니다.
- CI 성공은 자동화 테스트 및 패키징 통과를 뜻하며, 실제 기기에서의 네트워크 전환/장시간 실행/광고 차단 효과는 사용 중인 기기에서 확인해야 합니다.
