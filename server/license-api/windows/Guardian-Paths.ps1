# Shared secure storage locations for Guardian's Windows-hosted services.
# Secrets and runtime state must never live under the Git checkout or OneDrive.
$GuardianServerDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$GuardianRepoRoot = (Resolve-Path (Join-Path $GuardianServerDir '..\..')).Path
$GuardianDataDir = if ([string]::IsNullOrWhiteSpace($env:GUARDIAN_DATA_DIR)) {
  Join-Path $env:LOCALAPPDATA 'Guardian'
} else {
  [Environment]::ExpandEnvironmentVariables($env:GUARDIAN_DATA_DIR)
}
if ([string]::IsNullOrWhiteSpace($GuardianDataDir)) { throw 'LOCALAPPDATA 또는 GUARDIAN_DATA_DIR가 설정되어야 합니다.' }
$GuardianDataDir = [IO.Path]::GetFullPath($GuardianDataDir)
$repoFull = [IO.Path]::GetFullPath($GuardianRepoRoot).TrimEnd('\') + '\'
$dataFull = $GuardianDataDir.TrimEnd('\') + '\'
$oneDriveRoots = @($env:OneDrive, $env:OneDriveConsumer, $env:OneDriveCommercial) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
if ($dataFull.StartsWith($repoFull, [StringComparison]::OrdinalIgnoreCase) -or
    $oneDriveRoots.Where({ $dataFull.StartsWith(([IO.Path]::GetFullPath($_).TrimEnd('\') + '\'), [StringComparison]::OrdinalIgnoreCase) }).Count -gt 0) {
  throw "Guardian 데이터 경로는 Git 저장소 및 OneDrive 외부여야 합니다: $GuardianDataDir"
}
$GuardianSecretsDir = Join-Path $GuardianDataDir 'secrets'
$GuardianEnvPath = Join-Path $GuardianDataDir 'license-api.env'
$GuardianKeystorePath = Join-Path $GuardianSecretsDir 'guardian-release.jks'
$GuardianStorePasswordPath = Join-Path $GuardianSecretsDir 'guardian-release-store-password.dpapi'
$GuardianKeyPasswordPath = Join-Path $GuardianSecretsDir 'guardian-release-key-password.dpapi'
function Initialize-GuardianSecureDirectories {
  New-Item -ItemType Directory -Force -Path $GuardianDataDir, $GuardianSecretsDir | Out-Null
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
  foreach ($dir in @($GuardianDataDir, $GuardianSecretsDir)) {
    & icacls.exe $dir /inheritance:r /grant:r ($identity + ':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Guardian 보안 폴더 ACL 설정 실패: $dir" }
  }
}
function Set-GuardianSecureFileAcl([string]$Path) {
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
  & icacls.exe $Path /inheritance:r /grant:r ($identity + ':F') '*S-1-5-18:F' | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Guardian 비밀 파일 ACL 설정 실패: $Path" }
}
