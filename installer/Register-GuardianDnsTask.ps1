param(
    [Parameter(Mandatory = $true)]
    [string]$ExecutablePath
)
$ErrorActionPreference = 'Stop'
$exe = [System.IO.Path]::GetFullPath($ExecutablePath)
if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) {
    throw "Guardian DNS executable does not exist: $exe"
}
$workingDirectory = Split-Path -Parent $exe
$action = New-ScheduledTaskAction -Execute $exe -WorkingDirectory $workingDirectory
$trigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero)
Register-ScheduledTask -TaskName 'GuardianDnsProxy' -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
$task = Get-ScheduledTask -TaskName 'GuardianDnsProxy'
$registered = $task.Actions | Select-Object -First 1
if ($registered.Execute -ne $exe -or $registered.WorkingDirectory -ne $workingDirectory) {
    throw "Guardian DNS task registration mismatch. Execute='$($registered.Execute)' WorkingDirectory='$($registered.WorkingDirectory)'"
}
Write-Output "Registered GuardianDnsProxy: $($registered.Execute) (Start in: $($registered.WorkingDirectory))"
