$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root
python -m pip install -r requirements-dev.txt
python -m pytest -q dns_engine windows_engine
python -m compileall -q dns_engine windows_engine
Write-Host 'Python checks passed. This script does not modify Windows DNS settings.'