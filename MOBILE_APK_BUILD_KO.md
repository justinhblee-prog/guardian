# 휴대폰만으로 Guardian AdBlock APK 만들고 설치하기

이 패키지는 Android Studio/PC 없이 **GitHub Actions의 클라우드 빌드**를 휴대폰 브라우저에서 실행하도록 준비했습니다. 휴대폰 자체에서 Gradle/Android SDK를 컴파일하는 방식은 아니며, GitHub의 빌드 서버가 APK를 만들고 휴대폰으로 내려받습니다.

## 준비물
- GitHub 계정 및 로그인 가능한 모바일 브라우저
- 이 ZIP 파일
- GitHub 저장소를 만들 수 있는 권한

## 1회 설정 (휴대폰 브라우저)
1. `https://github.com/new` 에서 새 저장소를 만듭니다. 예: `guardian-adblock-android` (Public 또는 Private).
2. 저장소 화면에서 **Add file → Upload files** 를 선택하고, 이 패키지 ZIP을 저장소 루트에 업로드한 뒤 Commit 합니다.
3. 저장소에서 **Add file → Create new file** 을 선택합니다.
4. 파일 이름을 정확히 `.github/workflows/android-apk.yml` 로 입력합니다.
5. 이 패키지 안의 `.github/workflows/android-apk.yml` 파일 내용을 전체 복사해 편집기에 붙여넣고 Commit 합니다.

> 업로드한 ZIP은 소스 백업용입니다. 실제 빌드용 파일은 다음 단계에서 ZIP을 풀어 저장소에 올려야 하므로, 가장 간단한 방법은 GitHub 웹 편집기에서 ZIP을 풀 수 있는 Codespaces를 사용하는 것입니다. Codespaces가 없다면 아래 대체 방법을 사용하세요.

## 권장 방법: GitHub Codespaces에서 ZIP 풀기
1. 저장소에서 **Code → Codespaces → Create codespace on main** 을 선택합니다.
2. Codespaces의 파일 탐색기에서 업로드한 ZIP을 열거나 터미널에서 다음 명령을 실행합니다 (파일명이 다르면 실제 파일명으로 바꾸세요).

```bash
unzip Guardian_AdBlock_v0.6.3-dev_APK_MOBILE_BUILD.zip
```

3. 압축이 풀린 `Guardian_AdBlock_v0.6.3-dev` 폴더의 파일과 폴더를 저장소 루트로 복사합니다. `.github/workflows/android-apk.yml`도 저장소 루트에 둡니다.
4. 변경 사항을 Commit & Push 합니다.

## 2. 클라우드 빌드 실행
1. 저장소의 **Actions** 탭으로 이동합니다.
2. 왼쪽에서 **Guardian AdBlock Android APK** 를 선택합니다.
3. **Run workflow → Run workflow** 를 누릅니다.
4. 실행이 끝나면 성공한 실행 기록을 열고 **Artifacts** 에서 `Guardian-AdBlock-Android-APK-v0.6.3-dev` 를 누릅니다.
5. 내려받은 Artifact ZIP을 휴대폰 파일 앱에서 압축 해제합니다. 안에 `app-debug.apk`가 있습니다.

## 3. Android 휴대폰에 설치
1. `app-debug.apk`를 누릅니다.
2. 설치가 차단되면 안내에 따라 브라우저 또는 파일 앱에 대해 **이 출처 허용**을 켭니다.
3. 설치 화면으로 돌아와 설치를 완료합니다.
4. Guardian AdBlock을 열고 **보호 시작**을 누른 뒤 Android VPN 연결 요청에서 허용합니다.
5. 앱 화면에 DNS 보호 실행 중이 표시되는지 확인합니다.

## 중요한 제한 및 검증 범위
- 빌드 산출물은 우선 **debug 서명 APK**입니다. Play 스토어 배포용 release 서명 APK가 아닙니다.
- Actions의 `testDebugUnitTest`가 실패하면 APK 업로드 단계까지 진행하지 않습니다.
- APK 설치 성공만으로 광고 차단의 모든 앱/브라우저 동작이 검증되는 것은 아닙니다. DNS 필터링, VPN 유지, IPv4/IPv6, 절전/재부팅 후 동작은 실기기에서 별도로 확인해야 합니다.
- Android VPN 앱은 다른 VPN과 동시에 사용할 수 없습니다. 기존 VPN/보안 앱의 VPN 기능을 끄고 테스트하세요.
- GitHub 계정 생성, 저장소 생성, Codespaces 사용 가능 여부/요금은 계정 설정에 따릅니다.