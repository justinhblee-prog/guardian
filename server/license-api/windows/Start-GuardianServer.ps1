$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$paths=Get-GuardianPaths -RepositoryRoot $repoRoot
if (-not (Test-Path -LiteralPath $paths.LicenseEnv -PathType Leaf)) { throw '외부 운영 .env가 없습니다. Setup-GuardianServer.ps1을 먼저 실행하세요.' }
$ready=$false
for ($attempt=1; $attempt -le 24; $attempt++) {
  docker info *> $null
  if ($LASTEXITCODE -eq 0) {
    docker compose --env-file $paths.LicenseEnv up -d --build
    if ($LASTEXITCODE -eq 0) {
      try {$response=Invoke-WebRequest -Uri 'http://127.0.0.1:8787/ready' -TimeoutSec 5 -UseBasicParsing;if($response.StatusCode -eq 200){$ready=$true;break}}catch{}
    }
  }
  Start-Sleep -Seconds 5
}
if (-not $ready) { throw 'Guardian 서버가 120초 안에 준비되지 않았습니다. docker compose ps와 docker compose logs를 확인하세요.' }
Write-Host 'Guardian License API 준비 완료: http://127.0.0.1:8787/ready'
