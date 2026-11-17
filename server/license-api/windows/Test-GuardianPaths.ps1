$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$original = @{
  DataDir = $env:GUARDIAN_DATA_DIR
  LocalAppData = $env:LOCALAPPDATA
  OneDrive = $env:OneDrive
  OneDriveConsumer = $env:OneDriveConsumer
  OneDriveCommercial = $env:OneDriveCommercial
}
function Assert-True([bool]$Condition,[string]$Message) { if(-not $Condition){throw "FAIL: $Message"} }
function Assert-Throws([scriptblock]$Action,[string]$Message) {
  $threw=$false
  try { & $Action } catch { $threw=$true }
  Assert-True $threw $Message
}
$tempRoot=Join-Path ([IO.Path]::GetTempPath()) ('guardian-path-test-'+[guid]::NewGuid().ToString('N'))
try {
  New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
  $repo=Join-Path $tempRoot 'checkout'
  New-Item -ItemType Directory -Force -Path $repo | Out-Null
  $env:LOCALAPPDATA=Join-Path $tempRoot 'LocalAppData'
  Remove-Item Env:OneDrive,Env:OneDriveConsumer,Env:OneDriveCommercial -ErrorAction SilentlyContinue

  Remove-Item Env:GUARDIAN_DATA_DIR -ErrorAction SilentlyContinue
  $default=Get-GuardianPaths -RepositoryRoot $repo
  Assert-True ($default.Root -eq (Join-Path $env:LOCALAPPDATA 'Guardian')) 'default path must use LOCALAPPDATA'
  Assert-True ($default.LicenseEnv -eq (Join-Path $default.Root 'license-api\.env')) 'license .env must be external'
  Assert-True ($default.FilterSources -eq (Join-Path $default.Root 'filter-distribution\sources.json')) 'filter config must be external'

  $env:GUARDIAN_DATA_DIR=Join-Path $tempRoot 'ExternalGuardian'
  $external=Get-GuardianPaths -RepositoryRoot $repo
  Assert-True ($external.Root -eq $env:GUARDIAN_DATA_DIR) 'custom absolute path must be accepted'

  $env:GUARDIAN_DATA_DIR='relative-guardian-data'
  Assert-Throws { Get-GuardianPaths -RepositoryRoot $repo } 'relative data path must be rejected'

  $env:GUARDIAN_DATA_DIR=Join-Path $repo 'GuardianData'
  Assert-Throws { Get-GuardianPaths -RepositoryRoot $repo } 'data path inside checkout must be rejected'

  $env:GUARDIAN_DATA_DIR=$tempRoot
  Assert-Throws { Get-GuardianPaths -RepositoryRoot $repo } 'data path that contains checkout must be rejected'

  $oneDriveRoot=Join-Path $tempRoot 'OneDrive'
  $env:OneDrive=$oneDriveRoot
  $env:GUARDIAN_DATA_DIR=Join-Path $oneDriveRoot 'Guardian'
  Assert-Throws { Get-GuardianPaths -RepositoryRoot $repo } 'configured OneDrive root must be rejected'

  Remove-Item Env:OneDrive -ErrorAction SilentlyContinue
  $env:GUARDIAN_DATA_DIR=Join-Path $tempRoot 'Documents\OneDrive - Personal\Guardian'
  Assert-Throws { Get-GuardianPaths -RepositoryRoot $repo } 'OneDrive path segment must be rejected'

  Write-Host 'Guardian runtime path safety tests passed.'
} finally {
  foreach($entry in $original.GetEnumerator()) {
    if($null -eq $entry.Value){Remove-Item ("Env:"+$entry.Key) -ErrorAction SilentlyContinue}
    else {Set-Item ("Env:"+$entry.Key) $entry.Value}
  }
  Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
