#Requires -Version 7.2
[CmdletBinding()]
param(
  [string]$Repository = 'justinhblee-prog/guardian',
  [switch]$RegisterGitHubVariable
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Configure only the private Tailscale endpoint for the laptop-hosted license API.
# This script deliberately does not publish filter data or enable Tailscale Funnel.
$tailscaleCommand = Get-Command 'tailscale' -ErrorAction SilentlyContinue
if (-not $tailscaleCommand) { throw 'Tailscale CLI를 찾을 수 없습니다. 노트북에 Tailscale을 설치하고 로그인한 뒤 다시 실행하세요.' }
$tailscale = $tailscaleCommand.Source
if (-not $tailscale) { $tailscale = $tailscaleCommand.Name }

$healthUrl = 'http://127.0.0.1:8787/ready'
$serveTarget = 'http://127.0.0.1:8787'
try {
  $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 8
  if (-not $health.ok -or $health.database -ne 'ready' -or $health.signer -ne 'ready') {
    throw 'API readiness payload did not confirm database and signer.'
  }
} catch {
  throw 'Guardian License API가 준비되지 않았습니다. DB 연결과 RSA 서명키가 포함된 /ready 응답을 확인하세요. Start-All-GuardianServices.ps1 실행 상태도 점검하세요.'
}

. (Join-Path $PSScriptRoot 'Get-GuardianTailscaleStatus.ps1')
$tailStatus = Get-GuardianTailscaleStatus -Executable $tailscale
$selfProperty = $tailStatus.PSObject.Properties['Self']
if ($null -eq $selfProperty -or $null -eq $selfProperty.Value) {
  $backendProperty = $tailStatus.PSObject.Properties['BackendState']
  $backendState = if ($null -ne $backendProperty) { [string]$backendProperty.Value } else { 'unknown' }
  throw "Tailscale CLI가 정상 응답했지만 현재 노드 정보(Self)가 없습니다. 상태: $backendState. Tailscale 앱에서 노트북 로그인을 완료하고 연결 상태를 확인한 뒤 다시 실행하세요."
}
$dnsName = ([string]$selfProperty.Value.DNSName).TrimEnd('.')
if ([string]::IsNullOrWhiteSpace($dnsName) -or -not $dnsName.EndsWith('.ts.net', [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Tailscale MagicDNS 이름을 확인할 수 없습니다. Tailscale 로그인과 MagicDNS/HTTPS 설정을 확인하세요.'
}

# Serve only the local license API over the private tailnet.
& $tailscale serve --bg --https=443 $serveTarget
if ($LASTEXITCODE -ne 0) { throw 'Tailscale Serve 설정에 실패했습니다. 기존 HTTPS 443 Serve 구성을 확인하세요.' }
& $tailscale serve status
if ($LASTEXITCODE -ne 0) { throw 'Tailscale Serve 상태 확인 실패' }

$licenseApiUrl = 'https://' + $dnsName
$privateReadyUrl = $licenseApiUrl + '/ready'
$privateHealth = $null
for ($attempt = 1; $attempt -le 5; $attempt++) {
  try {
    $privateHealth = Invoke-RestMethod -Uri $privateReadyUrl -TimeoutSec 10
    if ($privateHealth.ok -and $privateHealth.database -eq 'ready' -and $privateHealth.signer -eq 'ready') {
      break
    }
    throw 'API readiness payload did not confirm database and signer.'
  } catch {
    if ($attempt -eq 5) {
      throw "Tailscale HTTPS /ready 검증에 실패했습니다. Serve, MagicDNS, TLS, DB 및 RSA 서명키 준비 상태를 확인하세요. ($($_.Exception.Message))"
    }
    Start-Sleep -Seconds 2
  }
}

if ($RegisterGitHubVariable) {
  $gh = Get-Command 'gh' -ErrorAction SilentlyContinue
  if (-not $gh) { throw 'GitHub CLI(gh)가 필요합니다. GUARDIAN_LICENSE_API_BASE_URL은 변경되지 않았습니다.' }
  $ghExecutable = $gh.Source
  if (-not $ghExecutable) { $ghExecutable = $gh.Name }
  & $ghExecutable auth status --hostname github.com *> $null
  if ($LASTEXITCODE -ne 0) { throw 'GitHub CLI 인증이 필요합니다. gh auth login 후 다시 실행하세요.' }
  & $ghExecutable variable set GUARDIAN_LICENSE_API_BASE_URL --body $licenseApiUrl --repo $Repository
  if ($LASTEXITCODE -ne 0) { throw 'GUARDIAN_LICENSE_API_BASE_URL GitHub variable 등록에 실패했습니다.' }
  Write-Host 'GitHub GUARDIAN_LICENSE_API_BASE_URL variable 등록 완료.'
  Write-Host '필터 재배포 승인 변수는 변경하지 않았으며, Production release는 계속 승인 gate에 의해 차단됩니다.'
}

Write-Host ''
Write-Host 'Guardian 비공개 라이선스 API 주소'
Write-Host $licenseApiUrl
Write-Host '필터 배포 공개 브랜치 게시, 필터 서명/갱신 및 예약 작업은 수행하지 않았습니다.'
Write-Host 'Tailscale Funnel, 공유기 포트포워딩, 공용 방화벽 개방은 사용하지 않았습니다.'
Write-Host '다음 단계: Prepare-GuardianProductionRelease.ps1 -SigningOnly'
