# Guardian runtime paths are deliberately kept outside the Git checkout and OneDrive.
function Get-GuardianPaths {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$RepositoryRoot)
  $repo = [IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\')
  $configured = [Environment]::GetEnvironmentVariable('GUARDIAN_DATA_DIR')
  if ([string]::IsNullOrWhiteSpace($configured)) {
    if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) { throw 'LOCALAPPDATA를 확인할 수 없습니다.' }
    $configured = Join-Path $env:LOCALAPPDATA 'Guardian'
  }
  $data = [IO.Path]::GetFullPath($configured).TrimEnd('\')
  $repoPrefix = $repo + '\'
  $dataPrefix = $data + '\\'
  if ($data.Equals($repo,[StringComparison]::OrdinalIgnoreCase) -or $data.StartsWith($repoPrefix,[StringComparison]::OrdinalIgnoreCase) -or $repo.StartsWith($dataPrefix,[StringComparison]::OrdinalIgnoreCase)) {
    throw "Guardian 데이터 경로는 Git 저장소와 분리된 독립 경로여야 합니다: $data"
  }
  if ($data -match '(?i)(^|\\)OneDrive(?: - [^\\]+)?(\\|$)') { throw "Guardian 운영 데이터는 OneDrive 외부에 있어야 합니다: $data" }
  $oneDriveRoots = @($env:OneDrive,$env:OneDriveConsumer,$env:OneDriveCommercial) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
  foreach ($root in $oneDriveRoots) {
    $od = [IO.Path]::GetFullPath($root).TrimEnd('\')
    if ($data.Equals($od,[StringComparison]::OrdinalIgnoreCase) -or $data.StartsWith(($od+'\'),[StringComparison]::OrdinalIgnoreCase)) {
      throw "Guardian 운영 데이터는 OneDrive 외부에 있어야 합니다: $data"
    }
  }
  [pscustomobject]@{
    Root = $data
    LicenseEnv = Join-Path $data 'license-api\.env'
    FilterEnv = Join-Path $data 'filter-distribution\.env'
    Secrets = Join-Path $data 'secrets'
    Backups = Join-Path $data 'backups'
    FilterSources = Join-Path $data 'filter-distribution\sources.json'
    FilterDist = Join-Path $data 'filter-distribution\dist'
    FilterLogs = Join-Path $data 'filter-distribution\logs'
  }
}
function Initialize-GuardianDataDirectories {
  param([Parameter(Mandatory)]$Paths)
  foreach ($dir in @((Split-Path $Paths.LicenseEnv -Parent),(Split-Path $Paths.FilterEnv -Parent),$Paths.Secrets,$Paths.Backups,$Paths.FilterDist,$Paths.FilterLogs)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
  }
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
  foreach ($dir in @($Paths.Root,$Paths.Secrets,(Split-Path $Paths.LicenseEnv -Parent),(Split-Path $Paths.FilterEnv -Parent),$Paths.Backups)) {
    & icacls.exe $dir /inheritance:r /grant:r ($identity + ':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Guardian 운영 데이터 ACL 설정 실패: $dir" }
  }
}
function Set-GuardianComposeEnvironment {
  param([Parameter(Mandatory)]$Paths)
  $env:GUARDIAN_FILTER_ENV_FILE = $Paths.FilterEnv
  $env:GUARDIAN_FILTER_DIST_DIR = $Paths.FilterDist
}
