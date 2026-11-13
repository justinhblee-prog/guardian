# 빌드 환경 보고서 — v0.6.3-dev

- Kotlin compiler 및 JDK 21: 사용 가능
- Python / pytest: 사용 가능
- Node.js: 사용 가능
- Android SDK (`android.jar`), sdkmanager: 확인되지 않음
- Gradle executable: 확인되지 않음
- PyInstaller: 설치되지 않음

검증 가능 범위는 Kotlin 순수 DNS 핵심부(IPv4/IPv6), Python 테스트/컴파일, 브라우저 Node 회귀 테스트입니다. Android 앱 전체 컴파일/JUnit/APK 및 Windows EXE 빌드는 수행할 수 없습니다. Windows 서비스·어댑터 실제 동작은 Windows 환경에서 별도 승인 검증이 필요합니다.