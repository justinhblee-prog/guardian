[CmdletBinding()]
param([string]$DailyAt = '04:17')
$ErrorActionPreference = 'Stop'
$FilterDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..\filter-distribution')).Path
$SecretDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'secrets'
$protectedPath = Join-Path $SecretDir 'guardian-filter-private.dpapi'
$sourcesPath = Join-Path $FilterDir 'sources.json'
if (-not (Test-Path $protectedPath)) { throw '보호된 필터 서명키가 없습니다. Setup-GuardianServer.ps1을 먼저 실행하세요.' }
if (-not (Test-Path $sourcesPath)) { throw 'sources.json이 없습니다. sources.example.json을 복사하고 실제 HTTPS 배포 주소를 설정하세요.' }
try { $scheduleTime = [DateTime]::ParseExact($DailyAt, 'HH:mm', [Globalization.CultureInfo]::InvariantCulture) }
catch { throw 'DailyAt은 24시간제 HH:mm 형식이어야 합니다. 예: 04:17' }
$config = Get-Content -Raw $sourcesPath | ConvertFrom-Json
$base = [Uri]$config.baseUrl
if ($base.Scheme -ne 'https' -or [string]::IsNullOrWhiteSpace($base.Host)) {
  throw '필터 배포 baseUrl은 유효한 HTTPS 주소여야 합니다.'
}
if ($base.Host -eq 'example.com' -or $base.Host.EndsWith('.example.com', [StringComparison]::OrdinalIgnoreCase)) {
  throw 'sources.json의 예시 도메인을 실제 HTTPS 도메인으로 바꾸세요.'
}
if (-not $config.sources -or $config.sources.Count -lt 1) { throw 'sources.json에 필터 공급원이 없습니다.' }
if (-not (Get-Command node -ErrorAction SilentlyContinue) -or -not (Get-Command npm -ErrorAction SilentlyContinue)) {
  throw '필터 자동 업데이트에는 Node.js 22 이상과 npm이 필요합니다.'
}
$nodeMajorText = & node -p "process.versions.node.split('.')[0]"
if ($LASTEXITCODE -ne 0 -or [int]$nodeMajorText -lt 22) { throw 'Node.js 22 이상을 설치한 뒤 다시 등록하세요.' }
try { $null = ConvertTo-SecureString -String (Get-Content -Raw $protectedPath) }
catch { throw '현재 Windows 계정에서 DPAPI 필터 서명키를 복호화할 수 없습니다.' }
$scriptPath = Join-Path $PSScriptRoot 'Update-And-Publish-GuardianFilters.ps1'
$action = New-ScheduledTaskAction -Execute 'PowerShell.exe' -Argument ('-NoProfile -ExecutionPolicy Bypass -File "' + $scriptPath + '"') -WorkingDirectory $FilterDir
$triggers = @(
  (New-ScheduledTaskTrigger -Daily -At $scheduleTime),
  (New-ScheduledTaskTrigger -AtLogOn)
)
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::FromHours(1)) -MultipleInstances IgnoreNew
$principal = New-ScheduledTaskPrincipal -UserId ("$env:USERDOMAIN\$env:USERNAME") -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName 'Guardian Signed Filter Update' -Action $action -Trigger $triggers -Settings $settings -Principal $principal -Description 'Daily signed Guardian DNS filter update and static cloud publication using current-user DPAPI protected signing key' -Force | Out-Null
Write-Host "Guardian 필터 업데이트 작업 등록 완료: 매일 $DailyAt 및 로그인 시 실행"
Write-Host '작업은 현재 Windows 사용자 계정의 DPAPI 보호키를 사용하고, 서명/해시 검증 후 GitHub 정적 클라우드 브랜치에 게시합니다. 노트북이 꺼져 있거나 절전이면 실행되지 않습니다.'
