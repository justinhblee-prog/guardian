$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
python -m pip install -r requirements-dev.txt -r requirements-windows-build.txt
if ($LASTEXITCODE -ne 0) { throw "Python dependency installation failed with exit code $LASTEXITCODE." }
python -m pytest -q dns_engine windows_engine
if ($LASTEXITCODE -ne 0) { throw "DNS and Windows test suite failed with exit code $LASTEXITCODE." }
python -m compileall -q dns_engine windows_engine
if ($LASTEXITCODE -ne 0) { throw "Python compilation failed with exit code $LASTEXITCODE." }
python -m PyInstaller --noconfirm --clean --windowed --name GuardianAdBlock windows_engine/desktop.py
if ($LASTEXITCODE -ne 0) { throw "Guardian desktop packaging failed with exit code $LASTEXITCODE." }
python -m PyInstaller --noconfirm --clean --windowed --name GuardianDnsProxy --distpath dist --workpath build/daemon windows_engine/proxy_daemon.py
if ($LASTEXITCODE -ne 0) { throw "Guardian DNS daemon packaging failed with exit code $LASTEXITCODE." }
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

