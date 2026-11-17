$ErrorActionPreference = 'Stop'
$LicenseDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$FilterDir = (Resolve-Path (Join-Path $LicenseDir '..\filter-distribution')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
if (-not (Test-Path -LiteralPath $script:GuardianLicenseEnvPath -PathType Leaf)) { throw "License API 환경 파일이 없습니다: $script:GuardianLicenseEnvPath. Setup-GuardianServer.ps1을 먼저 실행하세요." }
foreach ($item in @(@{Name='License API';Dir=$LicenseDir},@{Name='Filter distribution';Dir=$FilterDir})) {
  if ($item.Name -eq 'Filter distribution' -and -not (Test-Path (Join-Path $item.Dir '.env'))) { throw 'Filter distribution .env가 없습니다. 설치 안내를 먼저 확인하세요.' }
  Push-Location $item.Dir
  try {
    if ($item.Name -eq 'License API') { docker compose --env-file $script:GuardianLicenseEnvPath up -d --build } else { docker compose up -d --build }
    if ($LASTEXITCODE -ne 0) { throw "$($item.Name) 컨테이너 시작 실패" }
  } finally { Pop-Location }
}
$checks = @(@{Name='License API';Url='http://127.0.0.1:8787/ready'},@{Name='Filter distribution';Url='http://127.0.0.1:8788/health'})
foreach ($check in $checks) {
  $ok = $false
  for ($i=0; $i -lt 24; $i++) {
    try { $response=Invoke-WebRequest -Uri $check.Url -TimeoutSec 5 -UseBasicParsing; if ($response.StatusCode -eq 200) { $ok=$true; break } } catch {}
    Start-Sleep -Seconds 5
  }
  if (-not $ok) { throw "$($check.Name) 준비 상태 확인 실패: $($check.Url)" }
  Write-Host "$($check.Name): OK ($($check.Url))"
}
Write-Host 'Guardian 운영 서비스가 모두 준비되었습니다. 외부 접속은 아직 localhost 전용입니다.'
