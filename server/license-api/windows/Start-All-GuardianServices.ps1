$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$paths = Get-GuardianPaths -RepositoryRoot $repoRoot
Set-GuardianComposeEnvironment -Paths $paths
$licenseDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$filterDir = (Resolve-Path (Join-Path $licenseDir '..\filter-distribution')).Path
$services = @(
  @{ Name='License API'; Dir=$licenseDir; Env=$paths.LicenseEnv; Extra=@() },
  @{ Name='Filter distribution'; Dir=$filterDir; Env=$paths.FilterEnv; Extra=@() }
)
foreach ($service in $services) {
  if (-not (Test-Path -LiteralPath $service.Env -PathType Leaf)) { throw "$($service.Name) 외부 .env가 없습니다. Setup-GuardianServer.ps1을 먼저 실행하세요." }
  Push-Location $service.Dir
  try {
    & docker compose --env-file $service.Env up -d --build
    if ($LASTEXITCODE -ne 0) { throw "$($service.Name) 컨테이너 시작 실패" }
  } finally { Pop-Location }
}
$checks = @(@{Name='License API';Url='http://127.0.0.1:8787/ready'},@{Name='Filter distribution';Url='http://127.0.0.1:8788/health'})
foreach ($check in $checks) {
  $ok=$false
  for ($i=0; $i -lt 24; $i++) {
    try { $response=Invoke-WebRequest -Uri $check.Url -TimeoutSec 5 -UseBasicParsing; if ($response.StatusCode -eq 200) {$ok=$true;break} } catch {}
    Start-Sleep -Seconds 5
  }
  if (-not $ok) { throw "$($check.Name) 준비 상태 확인 실패: $($check.Url)" }
  Write-Host "$($check.Name): OK ($($check.Url))"
}
Write-Host "Guardian 운영 서비스 준비 완료. 외부 데이터: $($paths.Root)"
