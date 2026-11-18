# Guardian AdBlock — 제3자 필터 라이선스 예비 목록

검토 기준일: 2026-11-18  
적용 범위: `server/filter-distribution/sources.example.json`의 7개 필터 원본  
상태: **예비 라이선스 목록이며 재배포 승인서가 아닙니다.**

이 문서는 각 upstream 저장소/공식 배포 페이지에서 확인 가능한 라이선스 표기와 출처를 기록합니다. 저장소에 표시된 SPDX 라이선스만으로 해당 데이터에 포함된 모든 제3자 원본의 권리까지 확인되는 것은 아닙니다. 법률 검토와 실제 배포 버전 기준의 권리 확인이 끝나기 전까지 `redistributionApproved`는 `false`로 유지합니다.

## 필터별 출처 및 라이선스

| Source ID | 필터 | 배포 원본 | upstream 프로젝트 / 라이선스 표기 | 예비 검토 메모 |
|---|---|---|---|---|
| `adguard` | AdGuard DNS Filter | [HostlistsRegistry filter_1.txt](https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt) | [AdGuardSDNSFilter](https://github.com/AdguardTeam/AdGuardSDNSFilter) — GPL-3.0 | upstream README는 AdGuard Base, Tracking Protection, Mobile Ads, EasyList, EasyPrivacy 등을 결합한 목록이라고 설명합니다. 각 구성 원본의 별도 라이선스/조건 및 재배포 가능 범위를 확인해야 합니다. |
| `1hosts_lite` | 1Hosts Lite | [1Hosts Lite adblock list](https://cdn.jsdelivr.net/gh/badmojr/1Hosts@master/Lite/adblock.txt) | [1Hosts](https://github.com/badmojr/1Hosts) — MPL-2.0 | 현재 LICENSE 파일에는 MPL 2.0 Exhibit B의 “Incompatible With Secondary Licenses” 고지가 포함되어 있습니다. GPL 계열 필터와 같은 패키지로 제공하는 방식 및 제품 약관과의 관계를 별도 법률 검토 없이 판단하지 않습니다. |
| `oisd_big` | OISD Blocklist Big | [HostlistsRegistry filter_27.txt](https://adguardteam.github.io/HostlistsRegistry/assets/filter_27.txt), upstream [big.oisd.nl](https://big.oisd.nl/) | [oisd](https://github.com/sjhgvr/oisd) — GPL-3.0 | upstream 저장소의 LICENSE 파일에 GPL-3.0 전문이 있습니다. HostlistsRegistry는 이 목록을 OISD Blocklist Big으로 식별합니다. 실제 재배포본의 동일성과 upstream 데이터 출처/고지를 점검해야 합니다. |
| `hagezi_normal` | HaGeZi Normal | [HostlistsRegistry filter_34.txt](https://adguardteam.github.io/HostlistsRegistry/assets/filter_34.txt) | [HaGeZi DNS Blocklists](https://github.com/hagezi/dns-blocklists) — GPL-3.0 | upstream README는 목록을 GPL-3.0으로 배포하고 라이선스 사본을 재배포물에 포함하라고 안내합니다. 별도 upstream 입력 데이터는 그 자체의 권리 조건을 확인하라고 명시합니다. |
| `hagezi_pro` | HaGeZi Pro | [HostlistsRegistry filter_48.txt](https://adguardteam.github.io/HostlistsRegistry/assets/filter_48.txt) | [HaGeZi DNS Blocklists](https://github.com/hagezi/dns-blocklists) — GPL-3.0 | Normal과 동일한 upstream 저장소/라이선스 검토가 필요합니다. 각 릴리스/원본 버전의 provenance를 보존해야 합니다. |
| `security` | HaGeZi TIF Medium | [upstream TIF Medium](https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/tif.medium.txt) | [HaGeZi DNS Blocklists](https://github.com/hagezi/dns-blocklists) — GPL-3.0 | HaGeZi의 별도 Threat Intelligence Feed입니다. 일반 목록과는 별도 원본으로 취급하고, 해당 feed에 포함된 제3자 threat-intelligence 데이터의 출처 및 이용 조건을 확인해야 합니다. |
| `android_ads` | AWAvenue Ads Rule | [HostlistsRegistry filter_53.txt](https://adguardteam.github.io/HostlistsRegistry/assets/filter_53.txt), upstream [AWAvenue-Ads-Rule.txt](https://raw.githubusercontent.com/TG-Twilight/AWAvenue-Ads-Rule/main/AWAvenue-Ads-Rule.txt) | [AWAvenue-Ads-Rule](https://github.com/TG-Twilight/AWAvenue-Ads-Rule) — GPL-3.0 | upstream LICENSE는 GPL-3.0입니다. 공식 지원 페이지는 문서(CC BY-NC-SA 4.0)와 필터 규칙(GPLv3)을 구분해 표시합니다. 문서 콘텐츠를 제품에 복사하지 말고, 필터 파일의 원본 고지와 포함된 제3자 규칙을 별도 확인해야 합니다. |

## 검토 시 확인할 배포 의무

### GPL-3.0 원본

GPL-3.0의 복사/배포 조건에는 저작권 및 라이선스 고지를 유지하고 라이선스 사본을 제공하는 요구가 포함됩니다. GPL 적용 대상물을 실행 파일 형태로 전달하는 경우에는 해당 대상물에 적용되는 Corresponding Source 제공 요건도 함께 분석해야 합니다. 이 파일이 Guardian의 독립적인 데이터 목록으로 배포되는지, 앱과 결합된 저작물인지, 목록 자체에 포함된 외부 자료가 어떤 권리를 갖는지는 배포 구조와 원본별 사실관계를 바탕으로 검토해야 합니다.

공식 문서: [GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.html).

### MPL-2.0 원본

MPL-2.0은 Covered Software의 소스 형태와 실행 형태를 배포할 때 적용되는 고지 및 소스 접근 조건이 있습니다. 1Hosts의 LICENSE에는 Exhibit B의 “Incompatible With Secondary Licenses” 문구가 포함되어 있으므로, GPL 필터들과 함께 하나의 묶음으로 제공하는 구조가 MPL의 Larger Work/aggregate 구분에 어떻게 해당하는지 법률 검토가 필요합니다.

공식 문서: [Mozilla Public License 2.0](https://www.mozilla.org/en-US/MPL/2.0/), [MPL 2.0 FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/).

## 운영 승인 기준

각 source는 다음 자료가 모두 확보되기 전까지 승인하지 않습니다.

1. 배포할 실제 원본 파일의 immutable 버전/commit, 다운로드 URL 및 SHA-256.
2. 해당 버전에 적용되는 LICENSE 원문과 저작권/출처 고지.
3. 목록에 포함된 주요 upstream 입력 데이터의 출처와 각각의 조건 확인 결과.
4. Guardian 앱 APK 안에 포함하고, 서명된 정적 필터 CDN으로 재배포하고, 유료 앱에서 제공하는 형태가 라이선스 조건에 맞는지 검토한 기록.
5. 검토 담당자/일자/결정과 근거 문서 또는 법무 티켓 ID.

승인 기록은 실제 검토 문서 ID를 `redistributionApprovalRef`에 적습니다. 라이선스 이름이나 이 문서의 URL을 승인 증빙 대신 입력하지 않습니다. 승인 불가능하거나 조건을 이행하기 어려운 목록은 정식 제품의 필터 선택지에서 제외하고, 별도 URL을 통해 사용자가 원본 제공자에게서 직접 구독하게 하는 방식을 검토합니다.

## 현재 승인 상태

- 7개 source 모두 `redistributionApproved=false`.
- 7개 source 모두 `redistributionApprovalRef=""`.
- 이 문서 작성으로 승인 상태는 변경되지 않았습니다.
- 정식 릴리스 워크플로는 실제 운영 승인 자료가 확인되기 전까지 fail-closed 상태를 유지해야 합니다.

이 문서는 기술 운영을 위한 예비 조사입니다. 특정 상용 배포 형태가 법적으로 허용된다는 보증이나 법률 자문을 제공하지 않습니다.
