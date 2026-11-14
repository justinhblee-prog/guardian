@echo off
setlocal
cd /d "%~dp0"
if not exist "sources.json" (
  echo sources.json not found. Copy sources.example.json and configure trusted HTTPS sources.
  exit /b 2
)
call npm run update -- .\sources.json
if errorlevel 1 (
  echo Guardian filter update failed.
  exit /b 1
)
echo Guardian filter update completed. Review dist output before deployment.
exit /b 0
