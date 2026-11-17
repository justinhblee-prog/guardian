[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'

# Publish the laptop-hosted Guardian APIs only to the private Tailscale tailnet.
# This does not enable Tailscale Funnel, router port forwarding, or public access.
$tailscaleCommand = Get-Command 'tailscale' -ErrorAction SilentlyContinue
if (-not $tailscaleCommand) {
  throw 'Tailscale CLI를 찾을 수 없습니다. 노트북에 Tailscale을 설치하고 로그인한 뒤 다시 실행하세요.'
}
$tailscale = $tailscaleCommand.Source
if (-not $tailscale) { $tailscale = $tailscaleCommand.Name }

$checks = @(
  @{ Name = 'License API'; Url = 'http://127.0.0.1:8787/ready' },
  @{ Name = 'Filter distribution'; Url = 'http://127.0.0.1:8788/health' }
)
foreach ($check in $checks) {
  try {
    $response = Invoke-WebRequest -Uri $check.Url -TimeoutSec 5 -UseBasicParsing
    if ($response.StatusCode -ne 200) { throw "HTTP $($response.StatusCode)" }
    Write-Host "$($check.Name): local health OK"
  } catch {
    throw "$($check.Name)에 접속할 수 없습니다. 먼저 Start-All-GuardianServices.ps1을 실행하세요. ($($_.Exception.Message))"
  }
}

$statusText = (& $tailscale status --json | Out-String)
if ($LASTEXITCODE -ne 0) { throw 'Tailscale 연결 상태를 확인하지 못했습니다.' }
try { $tailStatus = $statusText | ConvertFrom-Json } catch { throw 'Tailscale status JSON을 해석하지 못했습니다.' }
$dnsName = ([string]$tailStatus.Self.DNSName).TrimEnd('.')
if ([string]::IsNullOrWhiteSpace($dnsName) -or -not $dnsName.EndsWith('.ts.net', [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Tailscale 기기의 MagicDNS 이름을 확인할 수 없습니다. Tailscale 연결 및 MagicDNS/HTTPS 인증서 설정을 확인하세요.'
}

# Keep both backend containers bound to localhost. Tailscale Serve terminates
# HTTPS and forwards privately to those loopback-only ports.
& $tailscale serve --bg --https=443 http://127.0.0.1:8787
if ($LASTEXITCODE -ne 0) { throw 'Tailscale Serve의 HTTPS 443 설정 실패' }
& $tailscale serve --bg --https=8443 http://127.0.0.1:8788
if ($LASTEXITCODE -ne 0) { throw 'Tailscale Serve의 HTTPS 8443 설정 실패' }

& $tailscale serve status
if ($LASTEXITCODE -ne 0) { throw 'Tailscale Serve 상태 확인 실패' }

Write-Host ''
Write-Host 'Guardian 노트북 비공개 접속 주소'
Write-Host ('라이선스 API: https://' + $dnsName)
Write-Host ('서명 필터 서버: https://' + $dnsName + ':8443')
Write-Host ''
Write-Host '모바일에서 같은 Tailscale 계정/네트워크에 연결한 뒤 위 두 HTTPS 주소를 Guardian 앱 설정에 각각 입력하세요.'
Write-Host '이 구성은 Tailscale tailnet 내부 전용입니다. Funnel, 공유기 포트포워딩, 공용 방화벽 개방은 사용하지 않습니다.'
Write-Host '노트북이 켜져 있고 절전 상태가 아니며 Tailscale에 연결되어 있어야 모바일에서 접속할 수 있습니다.'
