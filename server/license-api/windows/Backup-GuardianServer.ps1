$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$paths=Get-GuardianPaths -RepositoryRoot $repoRoot
if (-not (Test-Path -LiteralPath $paths.LicenseEnv -PathType Leaf)) {throw '외부 운영 .env가 없습니다.'}
New-Item -ItemType Directory -Force -Path $paths.Backups | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$outFile=Join-Path $paths.Backups "guardian-license-$stamp.dump"
$serverDir=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $serverDir
try {
  $command='docker compose --env-file "'+$paths.LicenseEnv+'" exec -T db pg_dump -Fc -U guardian guardian'
  cmd.exe /d /s /c "$command > `"$outFile`""
  if ($LASTEXITCODE -ne 0 -or -not(Test-Path $outFile) -or (Get-Item $outFile).Length -lt 128) {
    Remove-Item $outFile -ErrorAction SilentlyContinue
    throw 'DB 백업 실패: Docker 상태와 PostgreSQL 컨테이너를 확인하세요.'
  }
} finally {Pop-Location}
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
icacls.exe $outFile /inheritance:r /grant:r ($identity+':F') '*S-1-5-18:F' | Out-Null
if ($LASTEXITCODE -ne 0) {Remove-Item $outFile -Force;throw '백업 파일 ACL 설정 실패'}
Write-Host "외부 보호 경로에 PostgreSQL 백업 완료: $outFile"
Write-Host '복구 전 pg_restore --list로 파일을 확인하고 별도 DB에서 복구 리허설을 수행하세요.'
