#Requires -Version 7.2
[CmdletBinding()]
param([switch]$SkipAutoStart)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$paths = Get-GuardianPaths -RepositoryRoot $repoRoot
Initialize-GuardianDataDirectories -Paths $paths
$serverDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$filterDir = (Resolve-Path (Join-Path $serverDir '..\filter-distribution')).Path
if (-not (Get-Command 'openssl' -ErrorAction SilentlyContinue)) { throw 'openssl 명령을 찾을 수 없습니다.' }
# Key generation and secure local configuration do not depend on Docker.
# Require a running Docker Desktop only when this script will start services.
if (-not $SkipAutoStart) {
  if (-not (Get-Command 'docker' -ErrorAction SilentlyContinue)) { throw 'docker 명령을 찾을 수 없습니다.' }
  docker info *> $null
  if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop을 먼저 시작하세요.' }
}
if ((Test-Path -LiteralPath $paths.LicenseEnv) -or (Test-Path -LiteralPath $paths.FilterEnv)) { throw '외부 운영 .env가 이미 있습니다. 기존 비밀값 보호를 위해 중단합니다.' }
$existingSecretFiles = @(Get-ChildItem -LiteralPath $paths.Secrets -File -Force -ErrorAction SilentlyContinue)
if ($existingSecretFiles.Count -gt 0) { throw '외부 비밀 폴더에 기존 파일이 있습니다. 기존 키를 덮어쓰지 않습니다.' }
$licensePrivate = Join-Path $paths.Secrets 'guardian-license-private.pem'
$licensePublic = Join-Path $paths.Secrets 'guardian-license-public.pem'
$filterPrivate = Join-Path $paths.Secrets 'guardian-filter-private.pem'
$filterPublic = Join-Path $paths.Secrets 'guardian-filter-public.pem'
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out $licensePrivate
if ($LASTEXITCODE -ne 0) { throw 'RSA 라이선스 개인키 생성 실패' }
openssl pkey -in $licensePrivate -pubout -out $licensePublic
if ($LASTEXITCODE -ne 0) { throw 'RSA 라이선스 공개키 생성 실패' }
$licenseDer = Join-Path $paths.Secrets 'guardian-license-public.der'
openssl pkey -pubin -in $licensePublic -outform DER -out $licenseDer
if ($LASTEXITCODE -ne 0) { throw '라이선스 공개키 DER 변환 실패' }
$licenseB64 = Join-Path $paths.Secrets 'guardian-license-public.x509.b64'
[IO.File]::WriteAllText($licenseB64,[Convert]::ToBase64String([IO.File]::ReadAllBytes($licenseDer)),[Text.UTF8Encoding]::new($false))
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out $filterPrivate
if ($LASTEXITCODE -ne 0) { throw '필터 서명 RSA 개인키 생성 실패' }
openssl pkey -in $filterPrivate -pubout -out $filterPublic
if ($LASTEXITCODE -ne 0) { throw '필터 서명 공개키 생성 실패' }
$filterPrivateText = Get-Content -Raw $filterPrivate
$filterProtected = ConvertFrom-SecureString (ConvertTo-SecureString -String $filterPrivateText -AsPlainText -Force)
[IO.File]::WriteAllText((Join-Path $paths.Secrets 'guardian-filter-private.dpapi'),$filterProtected,[Text.UTF8Encoding]::new($false))
Remove-Item -LiteralPath $filterPrivate -Force
$filterDer = Join-Path $paths.Secrets 'guardian-filter-public.der'
openssl pkey -pubin -in $filterPublic -outform DER -out $filterDer
if ($LASTEXITCODE -ne 0) { throw '필터 공개키 DER 변환 실패' }
$filterB64 = Join-Path $paths.Secrets 'guardian-filter-public.x509.b64'
[IO.File]::WriteAllText($filterB64,[Convert]::ToBase64String([IO.File]::ReadAllBytes($filterDer)),[Text.UTF8Encoding]::new($false))
function New-Secret([int]$Bytes=48) {
  $buffer=[byte[]]::new($Bytes);$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
  try {$rng.GetBytes($buffer)} finally {$rng.Dispose()}
  return [Convert]::ToHexString($buffer).ToLowerInvariant()
}
$pem = (Get-Content -Raw $licensePrivate).Trim() -replace "\r?\n", '\n'
$lines = @("POSTGRES_PASSWORD=$(New-Secret 48)","GUARDIAN_LICENSE_LOOKUP_SECRET=$(New-Secret 64)","GUARDIAN_LICENSE_PRIVATE_KEY=$pem",'PORT=8787','GUARDIAN_RATE_LIMIT_WINDOW_MS=60000','GUARDIAN_RATE_LIMIT_MAX=30')
[IO.File]::WriteAllText($paths.LicenseEnv,($lines -join [Environment]::NewLine)+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
Remove-Item -LiteralPath $licensePrivate -Force
$filterEnvText = 'HOST=0.0.0.0'+[Environment]::NewLine+'PORT=8788'+[Environment]::NewLine+'GUARDIAN_FILTER_DIST_DIR=/app/dist'+[Environment]::NewLine
[IO.File]::WriteAllText($paths.FilterEnv,$filterEnvText,[Text.UTF8Encoding]::new($false))
$sourcesExample = Join-Path $filterDir 'sources.example.json'
if (-not (Test-Path $paths.FilterSources)) { Copy-Item -LiteralPath $sourcesExample -Destination $paths.FilterSources }
Write-Host 'Guardian 운영 서버 초기 구성이 완료되었습니다.'
Write-Host "외부 데이터 경로: $($paths.Root)"
Write-Host "라이선스 공개키: $licenseB64"
Write-Host "필터 공개키: $filterB64"
Write-Host '개인키, .env, DB 백업 및 필터 런타임 파일은 Git 저장소/OneDrive 외부에 보관됩니다.'
if (-not $SkipAutoStart) {
  & (Join-Path $PSScriptRoot 'Start-All-GuardianServices.ps1')
  try {
    & (Join-Path $PSScriptRoot 'Register-GuardianStartupTask.ps1')
  } catch {
    Write-Warning "Guardian 서버 구성은 완료됐지만 자동 시작 작업 등록은 보류됐습니다. 나중에 Register-GuardianStartupTask.ps1을 실행해 복구할 수 있습니다. $($_.Exception.Message)"
  }
}
