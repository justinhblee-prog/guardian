#Requires -Version 7.2
[CmdletBinding()]
param([switch]$SkipAutoStart)
$ErrorActionPreference = 'Stop'
$ServerDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
Initialize-GuardianSecureDirectories
Set-Location $ServerDir
foreach ($name in @('docker','openssl')) { if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "$name 명령을 찾을 수 없습니다." } }
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop을 먼저 시작하세요.' }
$envPath = $GuardianEnvPath
if (Test-Path $envPath) { throw '외부 Guardian 환경 파일이 이미 있습니다. 기존 비밀값 보호를 위해 중단합니다.' }
$secretDir = $GuardianSecretsDir
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
Set-GuardianSecureFileAcl $envPath
$FilterDir = (Resolve-Path (Join-Path $ServerDir '..\filter-distribution')).Path
$filterEnv = Join-Path $FilterDir '.env'
if (-not (Test-Path $filterEnv)) { [System.IO.File]::WriteAllText($filterEnv, "HOST=0.0.0.0`nPORT=8788`nGUARDIAN_FILTER_DIST_DIR=/app/dist`n", [System.Text.UTF8Encoding]::new($false)) }
New-Item -ItemType Directory -Force -Path (Join-Path $FilterDir 'dist') | Out-Null
$sourcesPath = Join-Path $FilterDir 'sources.json'
if (-not (Test-Path $sourcesPath)) { Copy-Item (Join-Path $FilterDir 'sources.example.json') $sourcesPath }
if (-not $SkipAutoStart) { & (Join-Path $PSScriptRoot 'Start-All-GuardianServices.ps1'); $taskName = 'Guardian Operations'; $action = New-ScheduledTaskAction -Execute 'PowerShell.exe' -Argument ('-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $PSScriptRoot 'Start-All-GuardianServices.ps1') + '"'); $trigger = New-ScheduledTaskTrigger -AtLogOn; $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero); Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description 'Start Guardian license API and signed filter distribution after Windows sign-in' -Force | Out-Null }
Write-Host 'Guardian 노트북 운영 서버 기본 구성이 완료되었습니다.'
Write-Host '관리자 화면: http://127.0.0.1:8787/admin'
Write-Host "Guardian 데이터 경로: $GuardianDataDir"
Write-Host "라이선스 공개키(Base64): $licensePublicBase64Path"
Write-Host "필터 공개키(Base64): $filterPublicBase64Path"
Write-Host '필터 서명 개인키는 현재 Windows 사용자 DPAPI로 보호해 저장했습니다.'
Write-Host '기본 API 접근은 노트북 내부(localhost)로 제한됩니다.'
