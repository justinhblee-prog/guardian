$ErrorActionPreference = 'Stop'
$ServerDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $ServerDir
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
docker compose --env-file $GuardianEnvPath stop
if ($LASTEXITCODE -ne 0) { throw 'Guardian 서버 중지 실패' }
Write-Host 'Guardian 서버가 중지되었습니다. 데이터 볼륨은 유지됩니다.'
