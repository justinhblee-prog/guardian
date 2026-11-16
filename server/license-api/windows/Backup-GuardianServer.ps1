$ErrorActionPreference = 'Stop'
$ServerDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $ServerDir
$backupDir = Join-Path $ServerDir 'backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outFile = Join-Path $backupDir "guardian-license-$stamp.sql"
docker compose exec -T db pg_dump -U guardian guardian | Set-Content -Encoding utf8 $outFile
if ($LASTEXITCODE -ne 0) { Remove-Item $outFile -ErrorAction SilentlyContinue; throw 'DB 백업 실패' }
Write-Host "DB 백업 완료: $outFile"
