# Guardian Chrome 확장 프로그램 (Manifest V3)

## 현재 구현 범위

- Chrome Manifest V3 서비스 워커 기반 확장 프로그램
- `declarativeNetRequest` 동적 규칙으로 도메인 차단
- `shared/filter_rules.txt`를 단일 원본으로 사용하고 빌드 스크립트가 확장 프로그램용 `rules.txt`를 생성
- 허용 규칙은 차단 규칙보다 높은 우선순위로 변환
- 팝업에서 보호 시작/중지, 상태, 적용 규칙 수 및 갱신 시각 표시
- 보호 활성화 상태를 브라우저 재시작 후에도 유지
- Node.js 테스트로 Manifest V3 선언, 팝업 파일, 공유 규칙 동기화, DNR 변환 및 잘못된 규칙 거부를 검사

## 개발 패키지

GitHub Actions의 **Guardian Chrome Manifest V3** 워크플로 성공 실행에서 `Guardian-Chrome-MV3-Dev-Package` artifact를 내려받습니다. ZIP과 `SHA256SUMS.txt`가 함께 제공됩니다.

수동 빌드 및 테스트(Node.js 22 이상):

```bash
node scripts/build_chrome_extension.mjs
node scripts/test_chrome_mv3.mjs
```

Chrome에서 `chrome://extensions`를 열고 개발자 모드를 켠 다음 압축해제된 확장 프로그램 로드에서 ZIP을 푼 `chrome-extension` 폴더를 선택합니다.

## 제한 및 다음 작업

- 현재는 공통 기본 도메인 규칙 10개를 번들링합니다. Android/Windows의 전체 구독 필터를 자동으로 모두 변환하지 않습니다.
- 서명된 원격 manifest 다운로드, 공개키 고정, 버전 anti-rollback 및 원자적 규칙 교체는 다음 통합 단계입니다.
- 예외 목록 편집, 상세 통계, 사이트별 허용/차단 및 Chrome 실브라우저 E2E가 아직 없습니다.
- 동일 출처 광고·페이지 요소 숨김은 별도 cosmetic filtering이 필요합니다.
- Firefox/Safari 호환성을 의미하지 않습니다.
- 이 개발 ZIP은 Chrome Web Store 게시용으로 심사·서명된 배포본이 아닙니다.
