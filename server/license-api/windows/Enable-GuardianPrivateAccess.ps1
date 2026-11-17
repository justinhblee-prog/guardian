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
  throw 'Tailscale 기기의 MagicDNS 이름을 확인할 수 없습니다. Tailscale 연결 및 HTTPS 인증서 설정을 확인하세요.'
}

# Configure two HTTPS listeners while the Docker backends remain loopback-only.
& $tailscale serve --bg --https=443 http://127.0.0.1:8787
if ($LASTEXITCODE -ne 0) { throw 'Tailscale Serve의 HTTPS 443 설정 실패' }

# The signed manifest embeds absolute filter URLs. Update the publisher base URL
# and republish all sources so the mobile app receives the correct private URL.
$filterDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..\filter-distribution')).Path
$sourcesPath = Join-Path $filterDir 'sources.json'
if (-not (Test-Path $sourcesPath)) { throw 'sources.json이 없습니다. Setup-GuardianServer.ps1을 먼저 실행하세요.' }
$config = Get-Content -Raw $sourcesPath | ConvertFrom-Json
if (-not $config.sources -or $config.sources.Count -lt 1) { throw 'sources.json에 필터 공급원이 없습니다.' }
$originalConfig = Get-Content -Raw $sourcesPath
$backupPath = Join-Path $filterDir '.sources.json.before-private-access.bak'
Copy-Item -LiteralPath $sourcesPath -Destination $backupPath -Force
$config.baseUrl = 'https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data'
$tempPath = $sourcesPath + '.tmp'
try {
  $serialized = $config | ConvertTo-Json -Depth 20
  [System.IO.File]::WriteAllText($tempPath, $serialized + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
  Move-Item -LiteralPath $tempPath -Destination $sourcesPath -Force
  & (Join-Path $PSScriptRoot 'Invoke-GuardianFilterUpdate.ps1')
  if ($LASTEXITCODE -ne 0) { throw '필터 다운로드/서명/manifest 발행 작업이 실패했습니다.' }
  & (Join-Path $PSScriptRoot 'Publish-GuardianFilterToCloud.ps1')
  if ($LASTEXITCODE -ne 0) { throw '서명된 필터 파일의 클라우드 게시가 실패했습니다.' }
} catch {
  [System.IO.File]::WriteAllText($sourcesPath, $originalConfig, [System.Text.UTF8Encoding]::new($false))
  Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
  throw
}

& $tailscale serve status
if ($LASTEXITCODE -ne 0) { throw 'Tailscale Serve 상태 확인 실패' }

Write-Host ''
Write-Host 'Guardian 노트북 비공개 접속 주소'
Write-Host ('라이선스 API: https://' + $dnsName)
Write-Host ('서명 필터 서버: https://' + $dnsName + ':8443')
Write-Host ''
Write-Host '필터 manifest와 서명 파일을 GitHub 정적 클라우드 원본에 게시했습니다.'
Write-Host '모바일의 운영 서버 주소에는 위 Tailscale HTTPS 주소를, 서명 필터 배포 서버에는 출력된 GitHub 정적 클라우드 주소를 입력하세요. 필터 업데이트는 노트북이 꺼져 있어도 받을 수 있습니다.'
Write-Host '이 구성은 Tailscale tailnet 내부 전용입니다. Funnel, 공유기 포트포워딩, 공용 방화벽 개방은 사용하지 않습니다.'
Write-Host '노트북이 켜져 있고 절전 상태가 아니며 Tailscale에 연결되어 있어야 모바일에서 접속할 수 있습니다.'
