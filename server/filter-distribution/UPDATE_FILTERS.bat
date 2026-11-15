@echo off
setlocal
pushd "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo [Guardian] Node.js 22 or later is required.
  popd
  exit /b 1
)
node -e "const major=Number(process.versions.node.split('.')[0]); if(major<22){console.error('Node.js 22 or later is required.');process.exit(1)}"
if errorlevel 1 (
  popd
  exit /b 1
)

if not defined GUARDIAN_FILTER_SIGNING_PRIVATE_KEY (
  echo [Guardian] GUARDIAN_FILTER_SIGNING_PRIVATE_KEY is not set for this Windows account.
  echo [Guardian] Configure it securely for the scheduled task account. Do not put the PEM in this batch file.
  popd
  exit /b 1
)
if not exist "sources.json" (
  echo [Guardian] sources.json is missing.
  echo [Guardian] Copy sources.example.json to sources.json and configure trusted HTTPS sources.
  popd
  exit /b 1
)

echo [Guardian] Starting validated filter update and signed publication...
call npm run update -- "%~dp0sources.json"
set "RESULT=%ERRORLEVEL%"
if not "%RESULT%"=="0" (
  echo [Guardian] Update failed with exit code %RESULT%.
) else (
  echo [Guardian] Filter update completed. Review dist output before deployment.
)
popd
exit /b %RESULT%
