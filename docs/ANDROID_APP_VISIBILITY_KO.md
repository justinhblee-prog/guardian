# Android 설치 앱 전체 조회 및 앱별 보호 제외

## 배포 전략과 기능 목적

Guardian은 서명된 APK 직접 배포를 기본 경로로 준비하되, Google Play 등록 가능성도 열어 둡니다. 직접 배포와 Google Play 배포는 서로 배타적인 선택이 아닙니다. Google Play 등록 여부와 무관하게 앱별 보호 제외 기능은 런처 아이콘이 없는 앱과 시스템 앱을 포함한 설치 앱 전체를 검색·선택할 수 있어야 합니다.

앱별 보호 제외 기능은 사용자가 선택한 앱에 대해서만 Guardian의 DNS 보호 VPN 적용을 제외하도록 합니다. Android 11(API 30) 이상의 기본 패키지 가시성 필터만으로는 전체 목록을 제공할 수 없습니다.

## 구현

- `android.permission.QUERY_ALL_PACKAGES`를 선언해 설치된 앱 전체를 조회합니다.
- 앱 목록은 `PackageManager.getInstalledApplications(0)`로 조회합니다.
- Guardian 자체 앱도 전체 목록에 표시하되, VPN 자체 앱이므로 선택할 수 없도록 비활성화합니다. 시스템 앱, 런처가 없는 앱, 기타 설치 앱은 숨기지 않습니다.
- 앱 이름과 패키지명 모두 검색 대상으로 사용합니다.
- 선택한 패키지명은 기기 로컬 SharedPreferences에 저장하고, 보호 VPN이 실행 중이면 VPN 구성을 재생성하여 Android의 앱별 제외 설정에 반영합니다.
- 최초 앱 목록 조회 전에 설치 앱 이름·패키지명을 기기 안에서 읽는 목적과 로컬 전용 사용 범위를 안내하고, 사용자가 동의한 뒤에만 전체 목록을 조회합니다. 동의는 기기에 저장됩니다.
- 앱 인벤토리 전체를 서버로 전송하거나 광고·분석 목적으로 공유하지 않습니다. 앱 목록은 선택 UI 표시와 로컬 VPN 설정에만 사용합니다.

## Android VPN 제외의 플랫폼 동작과 한계

- `VpnService.Builder.addDisallowedApplication()`에 등록된 앱의 네트워크 트래픽은 Guardian VPN을 통과하지 않고 Android 시스템 네트워크를 사용합니다.
- 앱별 제외는 앱의 네트워크 경로를 우회시키는 기능이지, 기기에서 실행 중인 VPN 서비스 자체를 끄거나 Android의 VPN 연결 상태 표시를 숨기는 기능은 아닙니다.
- 따라서 일부 금융·보안 앱이 트래픽 경로가 아니라 기기 전체의 VPN 연결 상태를 검사한다면, 해당 앱을 제외 목록에 넣어도 자체 VPN 경고가 남을 수 있습니다. Guardian은 이런 감지를 숨기거나 우회하지 않습니다.
- Samsung Wallet의 경고가 계속되는 경우에는 실기기에서 제외 적용 상태와 Wallet의 경고를 별도로 확인해야 합니다. 제외 적용 성공과 앱 자체의 VPN 경고 해소는 서로 다른 검증 항목입니다.
- Android 공식 문서: https://developer.android.com/reference/android/net/VpnService.Builder#addDisallowedApplication(java.lang.String)

### Samsung Wallet package 확인

- Samsung Wallet의 대표 패키지 ID는 `com.samsung.android.spay`입니다.
- 일부 Galaxy 펌웨어에는 별도 시스템 구성요소인 Samsung PaymentFramework `com.samsung.android.spayfw`도 설치됩니다. 이 구성요소는 런처 아이콘이 없을 수 있습니다.
- 앱별 제외는 패키지 단위이므로 Wallet 본체를 제외해도 별도 PaymentFramework 패키지까지 자동 제외되지는 않습니다. 해당 패키지가 설치된 기기에서는 두 패키지를 각각 검색하고, 각각 제외 적용 상태를 확인합니다.
- 두 패키지를 모두 제외해도 Wallet 경고가 남는다면 실제 기기 로그로 원인을 구분해야 합니다. 앱 자체의 기기 전체 VPN 상태 확인일 가능성도 있지만, 이를 아직 확정하지 않습니다.
- 패키지 참고: [Samsung Galaxy Store — Samsung Wallet](https://galaxystore.samsung.com/detail/com.samsung.android.spay), [Samsung Knox — Samsung PaymentFramework package list](https://docs.samsungknox.com/CCMode/SC-02K_P.pdf)

## 배포 경로별 고려사항

### 직접 APK 배포

- APK 서명과 SHA-256을 릴리스마다 확인하고, 기존 설치본과 동일한 서명 키로 업데이트 가능하도록 관리합니다.
- 설치 앱 목록은 앱별 보호 제외 기능을 위해서만 기기 안에서 사용합니다.
- Android 버전 및 제조사별 패키지 조회 결과가 달라질 수 있으므로 실제 삼성 기기에서 전체 목록 수와 주요 앱의 표시 여부를 확인합니다.

### Google Play 등록 가능성

- Google Play 등록은 배제하지 않습니다. 다만 `QUERY_ALL_PACKAGES`는 Google Play에서 제한적으로 허용되는 민감 권한이므로, 등록을 추진할 경우 최신 Play 정책과 권한 선언 절차를 확인하고 앱의 핵심 기능 및 전체 앱 조회의 필요성을 근거로 심사를 받아야 합니다.
- 보안·VPN 기능이라는 이유만으로 승인이 보장되는 것은 아닙니다. Play 심사 결과에 따라 등록용 빌드에서 허용되는 권한·기능 구성이 달라질 수 있습니다.
- 직접 배포용 APK와 Play 등록용 빌드가 달라져야 한다면 빌드 변형을 분리하고, 기능 차이·개인정보 안내·테스트 결과를 각각 검증합니다.
- 앱 인벤토리는 두 배포 경로 모두에서 기기 내 앱별 제외 기능에만 사용하며 서버 전송·광고 타기팅·분석·판매 또는 제3자 공유에 사용하지 않습니다.

## 검증 기준

1. Android 11 이상에서 런처 아이콘이 없는 설치 앱이 목록에 나타나는지 확인합니다.
2. 시스템 앱과 사용자 설치 앱이 모두 목록에 포함되는지 확인합니다.
3. 앱 이름 및 패키지명 검색 결과가 전체 목록에서 일치하는지 확인합니다.
4. 앱을 제외한 직후 VPN 재구성이 완료되고, 해제하면 해당 앱이 다시 VPN 보호 대상에 포함되는지 확인합니다.
5. Samsung Wallet 및 Android Auto뿐 아니라 여러 일반 앱을 대상으로 동일한 동작을 반복 검증합니다.
6. 에뮬레이터 검증과 실제 삼성 기기 검증을 별도로 기록합니다.
7. Google Play 등록을 추진할 경우 권한 정책 검토와 Play 심사 결과를 별도 출시 게이트로 검증합니다.
