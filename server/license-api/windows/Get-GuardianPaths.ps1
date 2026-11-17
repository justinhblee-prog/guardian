# Shared path and ACL policy for Windows-hosted Guardian operations.
# All private material and operational state are kept outside the Git/OneDrive checkout.
function Get-GuardianPaths {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$ScriptRoot)

  $windowsDir = (Resolve-Path -LiteralPath $ScriptRoot).Path
  $repoRoot = (Resolve-Path -LiteralPath (Join-Path $windowsDir '..\..\..')).Path
  $localRoot = [Environment]::GetFolderPath('LocalApplicationData')
  if ([string]::IsNullOrWhiteSpace($localRoot)) { throw 'Windows LocalAppData 경로를 확인할 수 없습니다.' }
  $dataRoot = [IO.Path]::GetFullPath((Join-Path $localRoot 'Guardian')).TrimEnd('\')
  $repoFull = [IO.Path]::GetFullPath($repoRoot).TrimEnd('\')
  $oneDriveRoots = @($env:OneDrive, $env:OneDriveCommercial, $env:OneDriveConsumer) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
  foreach ($root in $oneDriveRoots) {
    $oneDriveFull = [IO.Path]::GetFullPath($root).TrimEnd('\')
    if ($dataRoot.Equals($oneDriveFull, [StringComparison]::OrdinalIgnoreCase) -or
        $dataRoot.StartsWith($oneDriveFull + '\', [StringComparison]::OrdinalIgnoreCase)) {
      throw "Guardian 데이터 경로가 OneDrive 내부입니다. 안전하지 않아 중단합니다: $dataRoot"
    }
  }
  if ($dataRoot.Equals($repoFull, [StringComparison]::OrdinalIgnoreCase) -or
      $dataRoot.StartsWith($repoFull + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Guardian 데이터 경로가 Git 저장소 내부입니다. 안전하지 않아 중단합니다: $dataRoot"
  }

  $configDir = Join-Path $dataRoot 'config'
  $secretsDir = Join-Path $dataRoot 'secrets'
  $backupDir = Join-Path $dataRoot 'backups'
  $licenseEnv = Join-Path $configDir 'license-api.env'
  $filterEnv = Join-Path $configDir 'filter-distribution.env'
  [pscustomobject]@{
    RepoRoot = $repoRoot
    ServerDir = (Resolve-Path -LiteralPath (Join-Path $repoRoot 'server\license-api')).Path
    FilterDir = (Resolve-Path -LiteralPath (Join-Path $repoRoot 'server\filter-distribution')).Path
    DataRoot = $dataRoot
    ConfigDir = $configDir
    SecretsDir = $secretsDir
    BackupDir = $backupDir
    LicenseEnv = $licenseEnv
    FilterEnv = $filterEnv
  }
}

function Initialize-GuardianPrivateDirectories {
  [CmdletBinding()]
  param([Parameter(Mandatory)]$Paths)
  $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
  foreach ($directory in @($Paths.DataRoot, $Paths.ConfigDir, $Paths.SecretsDir, $Paths.BackupDir)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    & icacls.exe $directory /inheritance:r /grant:r ($identity + ':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Guardian 로컬 데이터 폴더 ACL 설정 실패: $directory" }
  }
}
