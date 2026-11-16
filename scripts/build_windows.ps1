$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
python -m pip install -r requirements-dev.txt -r requirements-windows-build.txt
python -m pytest -q dns_engine windows_engine
python -m compileall -q dns_engine windows_engine
python -m PyInstaller --noconfirm --clean --windowed --name GuardianAdBlock windows_engine/desktop.py
python -m PyInstaller --noconfirm --clean --windowed --name GuardianDnsProxy --distpath dist --workpath build/daemon windows_engine/proxy_daemon.py
$ProgramFilesX86 = [Environment]::GetFolderPath('ProgramFilesX86')
$Iscc = Join-Path $ProgramFilesX86 'Inno Setup 6\ISCC.exe'
if (-not (Test-Path $Iscc)) { throw 'Inno Setup 6 is required to build the Windows installer.' }
& $Iscc installer\guardian.iss
if ($LASTEXITCODE -ne 0) { throw 'Inno Setup compilation failed.' }
