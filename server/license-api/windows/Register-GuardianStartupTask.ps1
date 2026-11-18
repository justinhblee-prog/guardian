#Requires -Version 7.2
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$taskName = 'Guardian Operations'
$startupScript = Join-Path $PSScriptRoot 'Start-All-GuardianServices.ps1'
if (-not (Test-Path -LiteralPath $startupScript -PathType Leaf)) { throw "시작 스크립트를 찾을 수 없습니다: $startupScript" }
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$action = New-ScheduledTaskAction -Execute 'pwsh.exe' -Argument ('-NoProfile -ExecutionPolicy Bypass -File "' + $startupScript + '"')
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $identity
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero)
$principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
try {
  Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description 'Start Guardian license API and signed filter distribution after Windows sign-in' -Force | Out-Null
  Write-Host "Guardian 자동 시작 작업 등록 완료: $taskName ($identity)"
} catch {
  throw "현재 계정의 자동 시작 작업 등록에 실패했습니다. Guardian 서버 구성은 유지됩니다. 오류: $($_.Exception.Message) 관리자 PowerShell에서 이 스크립트를 다시 실행하거나 작업 스케줄러 정책을 확인하세요."
}
