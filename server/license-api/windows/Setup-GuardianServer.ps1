[CmdletBinding()]
param([switch]$SkipAutoStart)
$ErrorActionPreference = 'Stop'
$ServerDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $ServerDir
foreach ($name in @('docker','openssl')) { if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "$name 명령을 찾을 수 없습니다." } }
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop을 먼저 시작하세요.' }
$envPath = Join-Path $ServerDir '.env'
if (Test-Path $envPath) { throw '.env가 이미 있습니다. 기존 비밀값 보호를 위해 중단합니다.' }
$secretDir = Join-Path $ServerDir 'secrets'
New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
$privateKeyPath = Join-Path $secretDir 'guardian-license-private.pem'
$publicKeyPath = Join-Path $secretDir 'guardian-license-public.pem'
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out $privateKeyPath
if ($LASTEXITCODE -ne 0) { throw 'RSA 개인키 생성 실패' }
openssl pkey -in $privateKeyPath -pubout -out $publicKeyPath
if ($LASTEXITCODE -ne 0) { throw 'RSA 공개키 생성 실패' }
function New-Secret([int]$Bytes = 48) { $buffer = New-Object byte[] $Bytes; $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create(); try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }; return [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+','A').Replace('/','B') }
$alphabet = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
$dbPassword = -join (1..48 | ForEach-Object { $alphabet[(Get-Random -Minimum 0 -Maximum $alphabet.Length)] })
$adminToken = New-Secret 64
$lookupSecret = New-Secret 64
$pem = (Get-Content -Raw $privateKeyPath).Trim() -replace "`r?`n", '\n'
$lines = @("POSTGRES_PASSWORD=$dbPassword", "GUARDIAN_ADMIN_TOKEN=$adminToken", "GUARDIAN_LICENSE_LOOKUP_SECRET=$lookupSecret", "GUARDIAN_LICENSE_PRIVATE_KEY=$pem", 'PORT=8787', 'GUARDIAN_RATE_LIMIT_WINDOW_MS=60000', 'GUARDIAN_RATE_LIMIT_MAX=30')
[System.IO.File]::WriteAllText($envPath, ($lines -join "`n") + "`n", [System.Text.UTF8Encoding]::new($false))
if (-not $SkipAutoStart) { & (Join-Path $PSScriptRoot 'Start-GuardianServer.ps1'); $taskName = 'Guardian License Server'; $action = New-ScheduledTaskAction -Execute 'PowerShell.exe' -Argument ('-NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $PSScriptRoot 'Start-GuardianServer.ps1') + '"'); $trigger = New-ScheduledTaskTrigger -AtLogOn; $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero); Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description 'Start Guardian local license API after Windows sign-in' -Force | Out-Null }
Write-Host 'Guardian 노트북 운영 서버 기본 구성이 완료되었습니다.'
Write-Host '관리자 화면: http://127.0.0.1:8787/admin'
Write-Host "공개키 파일: $publicKeyPath"
Write-Host '기본 API 접근은 노트북 내부(localhost)로 제한됩니다.'
