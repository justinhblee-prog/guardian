#Requires -Version 7.2
[CmdletBinding()]
param([switch]$SkipAutoStart)
$ErrorActionPreference = 'Stop'
$ServerDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
Initialize-GuardianPrivateDataDirectory
Set-Location $ServerDir
foreach ($name in @('docker','openssl')) { if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "$name 명령을 찾을 수 없습니다." } }
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop을 먼저 시작하세요.' }
$currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$envPath = $script:GuardianLicenseEnvPath
if (Test-Path -LiteralPath $envPath) { throw '외부 운영 환경 파일이 이미 있습니다. 기존 비밀값 보호를 위해 중단합니다.' }
$secretDir = $script:GuardianSecretsDir
$legacyEnvPath = Join-Path $ServerDir '.env'
$legacySecretsDir = Join-Path $ServerDir 'secrets'
$hasLegacyEnv = Test-Path -LiteralPath $legacyEnvPath -PathType Leaf
$hasLegacySecrets = Test-Path -LiteralPath $legacySecretsDir -PathType Container
if ($hasLegacyEnv -xor $hasLegacySecrets) {
  throw '기존 저장소 설치 데이터가 일부만 존재합니다. 키/환경 파일을 자동 생성하거나 덮어쓰지 않고 중단합니다. 기존 .env와 secrets 폴더를 함께 확인하세요.'
}
if ($hasLegacyEnv -and $hasLegacySecrets) {
  $legacyFiles = @(Get-ChildItem -LiteralPath $legacySecretsDir -File -Force)
  foreach ($file in $legacyFiles) {
    $destination = Join-Path $secretDir $file.Name
    if (Test-Path -LiteralPath $destination) { throw "기존 비밀 파일과 외부 파일이 충돌합니다. 자동 덮어쓰기를 중단합니다: $destination" }
  }
  foreach ($file in $legacyFiles) {
    $destination = Join-Path $secretDir $file.Name
    Copy-Item -LiteralPath $file.FullName -Destination $destination
    if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash) { throw "비밀 파일 외부 이전 검증 실패: $($file.Name)" }
  }
  Copy-Item -LiteralPath $legacyEnvPath -Destination $envPath
  if ((Get-FileHash -LiteralPath $legacyEnvPath -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $envPath -Algorithm SHA256).Hash) { throw '기존 .env 외부 이전 검증에 실패했습니다.' }
  Remove-Item -LiteralPath $legacySecretsDir -Recurse -Force
  Remove-Item -LiteralPath $legacyEnvPath -Force
  Write-Host "기존 Guardian 비밀정보를 외부 경로로 이전하고 SHA-256 검증했습니다: $script:GuardianDataRoot"
  Write-Host '기존 라이선스/필터 서명키와 DB 비밀번호를 보존했습니다. 새 키는 생성하지 않았습니다.'
  Write-Host '이전이 완료되었습니다. Start-All-GuardianServices.ps1로 기존 서비스를 시작하세요.'
  return
}
$currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$privateKeyPath = Join-Path $secretDir 'guardian-license-private.pem'
$publicKeyPath = Join-Path $secretDir 'guardian-license-public.pem'
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out $privateKeyPath
if ($LASTEXITCODE -ne 0) { throw 'RSA 개인키 생성 실패' }
openssl pkey -in $privateKeyPath -pubout -out $publicKeyPath
if ($LASTEXITCODE -ne 0) { throw 'RSA 공개키 생성 실패' }
$licensePublicDerPath = Join-Path $secretDir 'guardian-license-public.der'
openssl pkey -pubin -in $publicKeyPath -outform DER -out $licensePublicDerPath
if ($LASTEXITCODE -ne 0) { throw '라이선스 공개키 DER 변환 실패' }
$licensePublicBase64Path = Join-Path $secretDir 'guardian-license-public.x509.b64'
[System.IO.File]::WriteAllText($licensePublicBase64Path, [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($licensePublicDerPath)), [System.Text.UTF8Encoding]::new($false))

# Filter signing private material is protected with Windows DPAPI for the
# current user; only its public X.509 key is intended for the Android build.
$filterPrivateKeyPath = Join-Path $secretDir 'guardian-filter-private.pem'
$filterPublicKeyPath = Join-Path $secretDir 'guardian-filter-public.pem'
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out $filterPrivateKeyPath
if ($LASTEXITCODE -ne 0) { throw '필터 서명 RSA 개인키 생성 실패' }
openssl pkey -in $filterPrivateKeyPath -pubout -out $filterPublicKeyPath
if ($LASTEXITCODE -ne 0) { throw '필터 서명 공개키 생성 실패' }
$filterPrivateText = Get-Content -Raw $filterPrivateKeyPath
$filterSecureString = ConvertTo-SecureString -String $filterPrivateText -AsPlainText -Force
$filterProtectedText = ConvertFrom-SecureString -SecureString $filterSecureString
[System.IO.File]::WriteAllText((Join-Path $secretDir 'guardian-filter-private.dpapi'), $filterProtectedText, [System.Text.UTF8Encoding]::new($false))
Remove-Item $filterPrivateKeyPath -Force
$filterPublicDerPath = Join-Path $secretDir 'guardian-filter-public.der'
openssl pkey -pubin -in $filterPublicKeyPath -outform DER -out $filterPublicDerPath
if ($LASTEXITCODE -ne 0) { throw '필터 공개키 DER 변환 실패' }
$filterPublicBase64Path = Join-Path $secretDir 'guardian-filter-public.x509.b64'
[System.IO.File]::WriteAllText($filterPublicBase64Path, [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($filterPublicDerPath)), [System.Text.UTF8Encoding]::new($false))
function New-Secret([int]$Bytes = 48) { $buffer = New-Object byte[] $Bytes; $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create(); try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }; return [BitConverter]::ToString($buffer).Replace('-', '').ToLowerInvariant() }
$dbPassword = New-Secret 48
$adminToken = New-Secret 64
$lookupSecret = New-Secret 64
$pem = (Get-Content -Raw $privateKeyPath).Trim() -replace "`r?`n", '\n'
$lines = @("POSTGRES_PASSWORD=$dbPassword", "GUARDIAN_ADMIN_TOKEN=$adminToken", "GUARDIAN_LICENSE_LOOKUP_SECRET=$lookupSecret", "GUARDIAN_LICENSE_PRIVATE_KEY=$pem", 'PORT=8787', 'GUARDIAN_RATE_LIMIT_WINDOW_MS=60000', 'GUARDIAN_RATE_LIMIT_MAX=30')
[System.IO.File]::WriteAllText($envPath, ($lines -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
& icacls.exe $envPath /inheritance:r /grant:r ($currentIdentity + ':F') '*S-1-5-18:F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '운영 환경 파일의 Windows 접근 권한 제한 실패' }
$FilterDir = (Resolve-Path (Join-Path $ServerDir '..\filter-distribution')).Path
$filterEnv = Join-Path $FilterDir '.env'
if (-not (Test-Path $filterEnv)) { [System.IO.File]::WriteAllText($filterEnv, "HOST=0.0.0.0`nPORT=8788`nGUARDIAN_FILTER_DIST_DIR=/app/dist`n", [System.Text.UTF8Encoding]::new($false)) }
New-Item -ItemType Directory -Force -Path (Join-Path $FilterDir 'dist') | Out-Null
$sourcesPath = Join-Path $FilterDir 'sources.json'
if (-not (Test-Path $sourcesPath)) { Copy-Item (Join-Path $FilterDir 'sources.example.json') $sourcesPath }
if (-not $SkipAutoStart) { & (Join-Path $PSScriptRoot 'Start-All-GuardianServices.ps1'); $taskName = 'Guardian Operations'; $action = New-ScheduledTaskAction -Execute 'PowerShell.exe' -Argument ('-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $PSScriptRoot 'Start-All-GuardianServices.ps1') + '"'); $trigger = New-ScheduledTaskTrigger -AtLogOn; $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero); Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description 'Start Guardian license API and signed filter distribution after Windows sign-in' -Force | Out-Null }
Write-Host 'Guardian 노트북 운영 서버 기본 구성이 완료되었습니다.'
Write-Host "비밀정보 저장 위치(저장소/OneDrive 외부): $script:GuardianDataRoot"
Write-Host '관리자 화면: http://127.0.0.1:8787/admin'
Write-Host "라이선스 공개키(Base64): $licensePublicBase64Path"
Write-Host "필터 공개키(Base64): $filterPublicBase64Path"
Write-Host '필터 서명 개인키는 현재 Windows 사용자 DPAPI로 보호해 저장했습니다.'
Write-Host '기본 API 접근은 노트북 내부(localhost)로 제한됩니다.'
