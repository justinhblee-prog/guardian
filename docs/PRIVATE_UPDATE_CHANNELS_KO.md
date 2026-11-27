# Guardian Android 비공개 업데이트 채널

## 채널 정책

| 채널 | 대상 | 배포 경로 |
|---|---|---|
| Stable | 모든 사용자 | 기존 정식 GitHub Release 업데이트 경로 |
| Beta | 관리자 인증을 마친 기기 | GitHub Actions 수동 실행에서 `beta` 선택 → 비공개 Neon 저장소 |
| Dev | 관리자 인증을 마친 기기 | `main`의 검증된 Android 빌드 → 비공개 Neon 저장소 |

일반 사용자의 앱에는 Stable만 표시됩니다. Beta와 Dev는 서버가 관리자 Bearer 인증을 승인한 뒤에만 앱의 채널 선택기에 나타납니다. 앱의 APK나 소스 코드에는 관리자 토큰을 포함하지 않습니다.

## Android 관리자 인증

1. 설정의 프로그램 업데이트 화면에서 **관리자 채널 인증**을 선택합니다.
2. 서버에 이미 등록된 Guardian 관리자 토큰을 입력합니다.
3. 앱은 HTTPS로 `GET /v1/updates/dev`를 호출해 토큰을 검증합니다.
4. 성공한 토큰은 Android Keystore의 AES-GCM 키로 암호화한 값만 앱 전용 설정에 저장합니다.
5. 인증 해제 시 암호화된 값과 Keystore 키를 함께 폐기합니다.

관리자 토큰은 채팅, 저장소, APK 빌드 인수 또는 로그에 기록하지 않습니다. 기기를 초기화하거나 Keystore 키를 잃으면 앱에서 다시 인증해야 합니다.

## 비공개 APK 배포

- APK와 manifest 메타데이터는 Neon Object Storage의 `guardian-private-updates` private bucket에 저장합니다.
- 업로드 URL은 10분, 앱 다운로드 URL은 5분 후 만료됩니다.
- 서버는 APK 업로드 후 실제 객체를 다시 읽어 크기와 SHA-256을 확인한 뒤 manifest를 게시합니다.
- 앱은 다운로드 후 SHA-256, 패키지 ID, 버전, APK 서명 인증서를 현재 설치 앱과 비교한 다음 Android 설치 화면을 엽니다.
- Beta/Dev manifest와 다운로드 URL은 관리자 인증이 없는 요청에 제공하지 않습니다.

## 배포 실행

- Dev: `main`에 검증된 변경이 반영되면 Android APK workflow가 비공개 Dev 채널로 게시합니다.
- Beta: GitHub Actions의 **Guardian AdBlock Android APK** workflow를 `main`에서 수동 실행하고 `channel=beta`를 선택합니다.
- 두 경로 모두 서버의 `/v1/admin/updates`, `/v1/admin/updates/upload-url`, `/v1/admin/updates/publish` API와 `GUARDIAN_ADMIN_TOKEN` GitHub Secret을 사용합니다.
- 채널 간 versionCode는 서버 manifest 목록을 기준으로 전역 단조 증가시킵니다.

## 운영 전제 및 미완료 검증

이 기능을 실제 운영하기 전에 Neon Function의 정상 배포, DB migration 적용, private bucket 접근, GitHub Secret 및 API URL 설정이 필요합니다. 운영 API 배포가 완료되기 전에는 Beta/Dev 인증 및 게시가 실제 기기에서 성공한다고 간주하지 않습니다.

기존에 공개 GitHub Releases로 게시된 과거 Dev APK는 이 변경만으로 삭제되거나 비공개로 바뀌지 않습니다. 신규 Dev 빌드부터 비공개 저장소 경로를 사용합니다.
