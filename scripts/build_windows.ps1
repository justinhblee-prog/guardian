$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
$VersionLines = Get-Content (Join-Path $Root 'VERSION')
if ($VersionLines.Count -lt 2) { throw 'VERSION must contain product version and monotonically increasing build number.' }
$ProductVersion = $VersionLines[0].Trim()
$BuildNumber = [int]$VersionLines[1].Trim()
if ($ProductVersion -notmatch '^\\d+\\.\\d+\\.\\d+(-[A-Za-z0-9.-]+)?python -m pytest -q dns_engine windows_engine
python -m compileall -q dns_engine windows_engine
python -m PyInstaller --noconfirm --clean --windowed --name GuardianAdBlock --add-data "VERSION;." windows_engine/desktop.py
python -m PyInstaller --noconfirm --clean --windowed --name GuardianDnsProxy --distpath dist --workpath build/daemon windows_engine/proxy_daemon.py
python -m PyInstaller --noconfirm --clean --console --name GuardianSelfTest --distpath dist --workpath build/selftest windows_engine/self_test.py
python -m PyInstaller --noconfirm --clean --console --name GuardianMaintenance --distpath dist --workpath build/maintenance windows_engine/maintenance.py
$SigningCertificate = $null
$SigningCertificatePath = Join-Path $env:RUNNER_TEMP 'guardian-code-signing.pfx'
if (-not [string]::IsNullOrWhiteSpace($env:GUARDIAN_SIGNING_CERT_BASE64)) {
    if ([string]::IsNullOrWhiteSpace($env:GUARDIAN_SIGNING_CERT_PASSWORD)) {
        throw 'GUARDIAN_SIGNING_CERT_PASSWORD is required when a signing certificate is configured.'
    }
    [IO.File]::WriteAllBytes($SigningCertificatePath, [Convert]::FromBase64String($env:GUARDIAN_SIGNING_CERT_BASE64))
    $SecurePassword = ConvertTo-SecureString $env:GUARDIAN_SIGNING_CERT_PASSWORD -AsPlainText -Force
    $ImportedCertificates = Import-PfxCertificate -FilePath $SigningCertificatePath -CertStoreLocation Cert:\CurrentUser\My -Password $SecurePassword
    $SigningCertificate = $ImportedCertificates | Where-Object { $_.HasPrivateKey -and $_.NotAfter -gt (Get-Date) } | Select-Object -First 1
    if (-not $SigningCertificate) { throw 'The supplied PFX has no usable, unexpired private-key certificate.' }
    $CodeSigningEku = $SigningCertificate.EnhancedKeyUsageList | Where-Object { $_.ObjectId.Value -eq '1.3.6.1.5.5.7.3.3' }
    if (-not $CodeSigningEku) { throw 'The supplied certificate is not valid for code signing.' }
    function Sign-GuardianBinary([string]$Path) {
        $Signature = Set-AuthenticodeSignature -FilePath $Path -Certificate $SigningCertificate -HashAlgorithm SHA256 -TimestampServer 'http://timestamp.digicert.com'
        if ($Signature.Status -ne 'Valid') { throw "Authenticode signing failed for ${Path}: $($Signature.StatusMessage)" }
    }
    Sign-GuardianBinary (Join-Path $Root 'dist/GuardianAdBlock/GuardianAdBlock.exe')
    Sign-GuardianBinary (Join-Path $Root 'dist/GuardianDnsProxy/GuardianDnsProxy.exe')
    Sign-GuardianBinary (Join-Path $Root 'dist/GuardianSelfTest/GuardianSelfTest.exe')
    Sign-GuardianBinary (Join-Path $Root 'dist/GuardianMaintenance/GuardianMaintenance.exe')
    Write-Host "Guardian binaries signed with: $($SigningCertificate.Subject)"
} else {
    Write-Warning 'No trusted code-signing certificate configured. This development installer will be unsigned and may trigger SmartScreen/Defender reputation warnings.'
}
$ProgramFilesX86 = [Environment]::GetFolderPath('ProgramFilesX86')
$Iscc = Join-Path $ProgramFilesX86 'Inno Setup 6\ISCC.exe'
if (-not (Test-Path $Iscc)) { throw 'Inno Setup 6 is required to build the Windows installer.' }
& $Iscc installer\guardian.iss
if ($LASTEXITCODE -ne 0) { throw 'Inno Setup compilation failed.' }

$InstallerPath = Get-ChildItem (Join-Path $Root 'dist/installer/Guardian-AdBlock-Windows-Setup-*.exe') | Select-Object -First 1
if (-not $InstallerPath) { throw 'Compiled Windows installer was not found.' }
if ($SigningCertificate) {
    Sign-GuardianBinary $InstallerPath.FullName
    $InstallerSignature = Get-AuthenticodeSignature -FilePath $InstallerPath.FullName
    if ($InstallerSignature.Status -ne 'Valid') { throw "Signed installer verification failed: $($InstallerSignature.StatusMessage)" }
    Write-Host "Verified signed installer: $($InstallerSignature.SignerCertificate.Subject)"
} else {
    Write-Warning 'Installer remains unsigned; configure repository Actions secrets GUARDIAN_SIGNING_CERT_BASE64 and GUARDIAN_SIGNING_CERT_PASSWORD to enable trusted signing.'
}

 -or $BuildNumber -lt 1) { throw 'VERSION metadata is invalid.' }
$env:GUARDIAN_VERSION_NAME = $ProductVersion
$env:GUARDIAN_BUILD_NUMBER = [string]$BuildNumber
Write-Host "Building Guardian $ProductVersion (Build $BuildNumber)"
python -m pip install -r requirements-dev.txt -r requirements-windows-build.txt
python -m pytest -q dns_engine windows_engine
python -m compileall -q dns_engine windows_engine
python -m PyInstaller --noconfirm --clean --windowed --name GuardianAdBlock windows_engine/desktop.py
python -m PyInstaller --noconfirm --clean --windowed --name GuardianDnsProxy --distpath dist --workpath build/daemon windows_engine/proxy_daemon.py
python -m PyInstaller --noconfirm --clean --console --name GuardianSelfTest --distpath dist --workpath build/selftest windows_engine/self_test.py
python -m PyInstaller --noconfirm --clean --console --name GuardianMaintenance --distpath dist --workpath build/maintenance windows_engine/maintenance.py
$SigningCertificate = $null
$SigningCertificatePath = Join-Path $env:RUNNER_TEMP 'guardian-code-signing.pfx'
if (-not [string]::IsNullOrWhiteSpace($env:GUARDIAN_SIGNING_CERT_BASE64)) {
    if ([string]::IsNullOrWhiteSpace($env:GUARDIAN_SIGNING_CERT_PASSWORD)) {
        throw 'GUARDIAN_SIGNING_CERT_PASSWORD is required when a signing certificate is configured.'
    }
    [IO.File]::WriteAllBytes($SigningCertificatePath, [Convert]::FromBase64String($env:GUARDIAN_SIGNING_CERT_BASE64))
    $SecurePassword = ConvertTo-SecureString $env:GUARDIAN_SIGNING_CERT_PASSWORD -AsPlainText -Force
    $ImportedCertificates = Import-PfxCertificate -FilePath $SigningCertificatePath -CertStoreLocation Cert:\CurrentUser\My -Password $SecurePassword
    $SigningCertificate = $ImportedCertificates | Where-Object { $_.HasPrivateKey -and $_.NotAfter -gt (Get-Date) } | Select-Object -First 1
    if (-not $SigningCertificate) { throw 'The supplied PFX has no usable, unexpired private-key certificate.' }
    $CodeSigningEku = $SigningCertificate.EnhancedKeyUsageList | Where-Object { $_.ObjectId.Value -eq '1.3.6.1.5.5.7.3.3' }
    if (-not $CodeSigningEku) { throw 'The supplied certificate is not valid for code signing.' }
    function Sign-GuardianBinary([string]$Path) {
        $Signature = Set-AuthenticodeSignature -FilePath $Path -Certificate $SigningCertificate -HashAlgorithm SHA256 -TimestampServer 'http://timestamp.digicert.com'
        if ($Signature.Status -ne 'Valid') { throw "Authenticode signing failed for ${Path}: $($Signature.StatusMessage)" }
    }
    Sign-GuardianBinary (Join-Path $Root 'dist/GuardianAdBlock/GuardianAdBlock.exe')
    Sign-GuardianBinary (Join-Path $Root 'dist/GuardianDnsProxy/GuardianDnsProxy.exe')
    Sign-GuardianBinary (Join-Path $Root 'dist/GuardianSelfTest/GuardianSelfTest.exe')
    Sign-GuardianBinary (Join-Path $Root 'dist/GuardianMaintenance/GuardianMaintenance.exe')
    Write-Host "Guardian binaries signed with: $($SigningCertificate.Subject)"
} else {
    Write-Warning 'No trusted code-signing certificate configured. This development installer will be unsigned and may trigger SmartScreen/Defender reputation warnings.'
}
$ProgramFilesX86 = [Environment]::GetFolderPath('ProgramFilesX86')
$Iscc = Join-Path $ProgramFilesX86 'Inno Setup 6\ISCC.exe'
if (-not (Test-Path $Iscc)) { throw 'Inno Setup 6 is required to build the Windows installer.' }
& $Iscc installer\guardian.iss
if ($LASTEXITCODE -ne 0) { throw 'Inno Setup compilation failed.' }

$InstallerPath = Get-ChildItem (Join-Path $Root 'dist/installer/Guardian-AdBlock-Windows-Setup-*.exe') | Select-Object -First 1
if (-not $InstallerPath) { throw 'Compiled Windows installer was not found.' }
if ($SigningCertificate) {
    Sign-GuardianBinary $InstallerPath.FullName
    $InstallerSignature = Get-AuthenticodeSignature -FilePath $InstallerPath.FullName
    if ($InstallerSignature.Status -ne 'Valid') { throw "Signed installer verification failed: $($InstallerSignature.StatusMessage)" }
    Write-Host "Verified signed installer: $($InstallerSignature.SignerCertificate.Subject)"
} else {
    Write-Warning 'Installer remains unsigned; configure repository Actions secrets GUARDIAN_SIGNING_CERT_BASE64 and GUARDIAN_SIGNING_CERT_PASSWORD to enable trusted signing.'
}

