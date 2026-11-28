# Guardian 필터 원본 라이선스 감사

검토일: 2026-11-26

이 문서는 `server/filter-distribution/sources.example.json`에 등록된 7개 필터 원본의 공개 라이선스 표시를 정리한 것입니다. 법률 자문이나 최종 배포 승인 문서가 아닙니다. 원본 저장소의 LICENSE/FAQ와 실제 다운로드한 산출물의 헤더·출처를 배포 시점마다 다시 확인해야 합니다.

## 현재 예제 소스와 공개 라이선스 표시

| Guardian sourceId | 원본 | 공개 저장소/라이선스 표시 | 운영 주의 |
|---|---|---|---|
| `adguard` | AdGuard DNS Filter (`filter_1.txt`) | GPL-3.0 — [AdGuardSDNSFilter](https://github.com/AdguardTeam/AdGuardSDNSFilter) | HostlistsRegistry가 제공하는 실제 파일의 구성·출처와 원본 라이선스 고지를 함께 확인합니다. |
| `1hosts_lite` | 1Hosts Lite (`filter_24.txt`) | MPL-2.0 — [1Hosts](https://github.com/badmojr/1Hosts) | MPL 적용 파일과 수정 여부를 분리 추적하고 고지를 보존합니다. |
| `oisd_big` | OISD Big (`filter_27.txt`) | GPL-3.0 — [OISD FAQ](https://oisd.nl/?p=faq) | OISD는 목록 자체의 GPL-3.0 표시를 FAQ에서 명시합니다. |
| `hagezi_normal` | HaGeZi Normal (`filter_34.txt`) | GPL-3.0 — [HaGeZi DNS Blocklists](https://github.com/hagezi/dns-blocklists) | GPL-3.0 라이선스와 최신 disclaimer의 재배포 조건을 확인합니다. |
| `hagezi_pro` | HaGeZi Pro (`filter_48.txt`) | GPL-3.0 — [HaGeZi DNS Blocklists](https://github.com/hagezi/dns-blocklists) | Normal과 Pro는 별도 버전·해시·고지로 관리합니다. |
| `security` | HaGeZi TIF Medium | GPL-3.0 — [HaGeZi DNS Blocklists](https://github.com/hagezi/dns-blocklists) | 직접 jsDelivr URL을 쓰므로 원본 프로젝트의 최신 라이선스·면책 고지를 확인합니다. |
| `android_ads` | AWAvenue Ads Rule (`filter_53.txt`) | GPL-3.0 — [AWAvenue-Ads-Rule](https://github.com/TG-Twilight/AWAvenue-Ads-Rule) | 원본 LICENSE와 파일 내 출처·버전·라이선스 주석을 보존합니다. |

## 라이선스와 재배포 게이트의 의미

GPL-3.0 또는 MPL-2.0이라는 표시만으로 상용 앱에 포함한 재배포가 금지되는 것은 아닙니다. 반대로 공개 저장소에 목록이 있다는 사실만으로 모든 포함 데이터의 권리와 의무가 자동 해결되는 것도 아닙니다. GPL-3.0은 조건을 지키는 경우 유료·무료 복제와 배포를 허용하지만, 적용되는 저작물의 라이선스 사본·고지·대응 소스 등 조건을 준수해야 합니다. MPL-2.0은 적용되는 파일과 수정 파일을 식별하고 해당 라이선스의 파일 단위 의무를 확인해야 합니다.

Guardian의 `redistributionApproved`는 원저작권자의 별도 허가를 받았다는 의미가 아닙니다. 각 소스의 라이선스와 데이터 출처를 검토하고, 제품 배포 방식에 적용되는 의무를 충족할 수 있다고 내부적으로 확인한 경우에만 켜는 배포 게이트입니다. `redistributionApprovalRef`는 그 내부 검토 기록을 가리켜야 하며, 임의 문자열이나 단순한 라이선스 URL을 승인 증거로 쓰면 안 됩니다.

업데이트 프로그램은 이제 다운로드 전에 각 소스에 대해 `upstreamRepository`, `licenseUrl`, `upstreamVersion`, `thirdPartyProvenanceReviewed=true`, `complianceEvidenceRef`를 요구합니다. 그 다음에도 별도의 `redistributionApproved=true`와 `redistributionApprovalRef`가 있어야 합니다. 현재 예제 설정은 일곱 소스 모두 provenance 검토와 배포 승인을 false/빈 값으로 유지하므로 실제 다운로드·서명·게시가 차단됩니다.

현재 HostlistsRegistry 메타데이터에서 확인되는 버전은 AdGuard DNS 1.0.86.55, 1Hosts Lite 1.0.26.81, OISD Big 1.1.50.60, HaGeZi Normal 1.0.33.47, HaGeZi Pro 1.0.33.52, AWAvenue 1.0.15.85입니다. 이는 해당 registry가 표시한 목록 버전이지, 모든 upstream 구성 요소의 고정 commit이나 라이선스 승인 증거는 아닙니다. HaGeZi TIF Medium은 현재 설정 URL이 `@latest`를 사용하므로 `rolling-unpinned`로 표시하며, 재현 가능한 고정 버전으로 바꾸기 전까지 특히 주의가 필요합니다.

## 배포 전 필수 확인

1. 각 다운로드 파일의 원본 URL, 다운로드 시각, 원본 프로젝트 커밋/버전, SHA-256, 라이선스 식별자와 고지 파일을 릴리스 단위로 보관합니다.
2. GPL-3.0 또는 MPL-2.0 자료를 APK, Windows 설치 패키지 또는 서버를 통해 재배포할 때 필요한 라이선스 사본, 저작권/출처 고지, 수정 표시, 해당 자료의 대응 소스 제공 요건을 확인합니다.
3. 필터 파일은 가능하면 sourceId별로 원본 그대로 별도 저장·서명·배포합니다. 여러 라이선스 자료를 하나의 변형된 목록으로 합치면 출처와 라이선스 경계를 추적하기 어려워집니다.
4. Guardian의 자체 코드, 필터 데이터, 제3자 원본을 별도 구성요소로 관리하고, 단순 집합물인지 파생/결합 저작물인지에 대한 판단을 출시 전 법률 검토로 확정합니다. 이 문서는 Guardian 전체 코드의 라이선스가 자동으로 바뀐다거나, 반대로 어떤 의무도 없다고 단정하지 않습니다.
5. upstream 프로젝트의 FAQ/라이선스/출처가 바뀌거나 원본 파일에 새로운 제3자 목록이 포함되면 재검토합니다. upstream이 GPL로 배포한 목록이라도 그 안의 입력 데이터가 모두 동일한 라이선스를 가진다고 가정하지 않습니다.
6. 검토가 끝나지 않은 source는 상용 운영 배포 설정에서 활성화하지 않습니다. `sources.example.json`은 예시 설정이며 상용 재배포 승인을 뜻하지 않습니다.

## 현재 판정

- 공개 원본 저장소/FAQ에서 GPL-3.0 또는 MPL-2.0 표시를 확인한 항목이 있습니다.
- 이 저장소에는 운영 필터 원본의 법률 검토 결과, 각 upstream의 고지/라이선스 사본, 릴리스별 corresponding-source 패키지 확인 증거가 아직 없습니다.
- 따라서 7개 소스는 기술적으로 다운로드·서명할 수 있어도 **상용 제품에 포함해 배포하도록 법률 승인된 상태는 아닙니다.**
- 실제 운영에서 필터를 게시하기 전, 해당 라이선스 조건을 반영한 attribution/notice/source 패키지를 함께 준비하고 승인 기록을 남겨야 합니다.
