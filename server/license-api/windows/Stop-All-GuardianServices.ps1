$ErrorActionPreference = 'Stop'
$LicenseDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$FilterDir = (Resolve-Path (Join-Path $LicenseDir '..\filter-distribution')).Path
foreach ($dir in @($FilterDir,$LicenseDir)) { Push-Location $dir; try { if ($dir -eq $LicenseDir) { docker compose --env-file $GuardianEnvPath stop } else { docker compose stop }; if ($LASTEXITCODE -ne 0) { throw "서비스 중지 실패: $dir" } } finally { Pop-Location } }
Write-Host 'Guardian API와 필터 배포 서버가 중지되었습니다. DB 볼륨과 배포 파일은 유지됩니다.'
