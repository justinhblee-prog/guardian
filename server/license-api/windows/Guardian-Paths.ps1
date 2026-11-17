# Shared paths for Guardian Windows operations. Keep secrets outside the Git/OneDrive checkout.
# Dot-source from scripts under server/license-api/windows.
$script:GuardianLicenseDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:GuardianRepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$requestedRoot = if (-not [string]::IsNullOrWhiteSpace($env:GUARDIAN_DATA_DIR)) { $env:GUARDIAN_DATA_DIR } else { Join-Path $env:LOCALAPPDATA 'Guardian' }
if ([string]::IsNullOrWhiteSpace($requestedRoot) -or -not [IO.Path]::IsPathRooted($requestedRoot)) {
  throw 'Guardian 데이터 경로는 절대 경로여야 합니다. GUARDIAN_DATA_DIR 또는 LOCALAPPDATA를 확인하세요.'
}
$script:GuardianDataRoot = [IO.Path]::GetFullPath($requestedRoot)
$repoFull = [IO.Path]::GetFullPath($script:GuardianRepoRoot).TrimEnd('\') + '\'
$dataFull = $script:GuardianDataRoot.TrimEnd('\') + '\'
if ($dataFull.StartsWith($repoFull, [StringComparison]::OrdinalIgnoreCase) -or $repoFull.StartsWith($dataFull, [StringComparison]::OrdinalIgnoreCase)) {
  throw "Guardian 데이터 경로는 저장소 바깥이어야 합니다: $script:GuardianDataRoot"
}
foreach ($oneDriveName in @('OneDrive','OneDriveCommercial','OneDriveConsumer')) {
  $oneDriveRoot = [Environment]::GetEnvironmentVariable($oneDriveName)
  if ($oneDriveRoot) {
    $oneDriveFull = [IO.Path]::GetFullPath($oneDriveRoot).TrimEnd('\') + '\'
    if ($dataFull.StartsWith($oneDriveFull, [StringComparison]::OrdinalIgnoreCase)) {
      throw "Guardian 데이터 경로를 OneDrive 외부로 지정하세요: $script:GuardianDataRoot"
    }
  }
}
$script:GuardianSecretsDir = Join-Path $script:GuardianDataRoot 'secrets'
$script:GuardianLicenseEnvPath = Join-Path $script:GuardianDataRoot 'license-api.env'
$script:GuardianBackupsDir = Join-Path $script:GuardianDataRoot 'backups'
$script:GuardianOutputsDir = Join-Path $script:GuardianDataRoot 'outputs'
function Initialize-GuardianPrivateDataDirectory {
  New-Item -ItemType Directory -Force -Path $script:GuardianDataRoot,$script:GuardianSecretsDir,$script:GuardianBackupsDir,$script:GuardianOutputsDir | Out-Null
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
  foreach ($protectedPath in @($script:GuardianDataRoot,$script:GuardianSecretsDir,$script:GuardianBackupsDir,$script:GuardianOutputsDir)) {
    & icacls.exe $protectedPath /inheritance:r /grant:r ($identity + ':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Guardian 로컬 데이터 폴더 ACL 설정 실패: $protectedPath" }
  }
}
