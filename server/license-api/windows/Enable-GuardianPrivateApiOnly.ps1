#Requires -Version 7.2
[CmdletBinding()]
param()
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
  $health = Invoke-WebRequest -Uri $healthUrl -TimeoutSec 8 -UseBasicParsing
  if ($health.StatusCode -ne 200) { throw 'not ready' }
} catch {
  throw 'Guardian License API가 준비되지 않았습니다. 먼저 Start-All-GuardianServices.ps1을 실행하고 /ready 응답을 확인하세요.'
}

$statusText = (& $tailscale status --json | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'Tailscale 연결 상태를 확인하지 못했습니다.' }
try { $tailStatus = $statusText | ConvertFrom-Json } catch { throw 'Tailscale status JSON을 해석하지 못했습니다.' }
$dnsName = ([string]$tailStatus.Self.DNSName).TrimEnd('.')
if ([string]::IsNullOrWhiteSpace($dnsName) -or -not $dnsName.EndsWith('.ts.net', [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Tailscale MagicDNS 이름을 확인할 수 없습니다. Tailscale 로그인과 MagicDNS/HTTPS 설정을 확인하세요.'
}

# Serve only the local license API over the private tailnet.
& $tailscale serve --bg --https=443 $serveTarget
if ($LASTEXITCODE -ne 0) { throw 'Tailscale Serve 설정에 실패했습니다. 기존 HTTPS 443 Serve 구성을 확인하세요.' }
& $tailscale serve status
if ($LASTEXITCODE -ne 0) { throw 'Tailscale Serve 상태 확인 실패' }

Write-Host ''
Write-Host 'Guardian 비공개 라이선스 API 주소'
Write-Host ('https://' + $dnsName)
Write-Host '필터 공개 브랜치 게시, 필터 서명/갱신 및 예약 작업은 수행하지 않았습니다.'
Write-Host 'Tailscale Funnel, 공유기 포트포워딩, 공용 방화벽 개방은 사용하지 않았습니다.'
Write-Host '다음 단계: Prepare-GuardianProductionRelease.ps1 -SigningOnly'
