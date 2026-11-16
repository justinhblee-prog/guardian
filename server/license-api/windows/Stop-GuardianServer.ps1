$ErrorActionPreference = 'Stop'
$ServerDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $ServerDir
docker compose stop
if ($LASTEXITCODE -ne 0) { throw 'Guardian 서버 중지 실패' }
Write-Host 'Guardian 서버가 중지되었습니다. 데이터 볼륨은 유지됩니다.'
