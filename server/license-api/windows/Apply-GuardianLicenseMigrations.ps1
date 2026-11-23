#Requires -Version 7.2
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$paths = Get-GuardianPaths -RepositoryRoot $repoRoot
$serverDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$docker = (Get-Command docker -ErrorAction Stop).Source

if (-not (Test-Path -LiteralPath $paths.LicenseEnv -PathType Leaf)) {
    throw '외부 운영 .env가 없습니다. Setup-GuardianServer.ps1을 먼저 실행하세요.'
}

Push-Location $serverDir
try {
    Write-Host '1/5 PostgreSQL 컨테이너 상태 확인'
    & $docker compose --env-file $paths.LicenseEnv exec -T db pg_isready -U guardian -d guardian
    if ($LASTEXITCODE -ne 0) {
        throw 'PostgreSQL이 준비되지 않았습니다. Start-All-GuardianServices.ps1 실행 후 다시 시도하세요.'
    }

    Write-Host '2/5 마이그레이션 전 데이터베이스 백업'
    & (Join-Path $PSScriptRoot 'Backup-GuardianServer.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw '마이그레이션 전 PostgreSQL 백업에 실패했습니다.'
    }
    $backup = Get-ChildItem -LiteralPath $paths.Backups -Filter 'guardian-license-*.dump' -File |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($null -eq $backup -or $backup.Length -lt 128) {
        throw '유효한 마이그레이션 전 DB 백업 파일을 찾지 못했습니다.'
    }
    Write-Host "백업 확인: $($backup.FullName) ($($backup.Length) bytes)"

    Write-Host '3/5 순서대로 idempotent SQL migration 적용'
    $migrationFiles = @(
        'migrations/002_shared_rate_limit_buckets.sql',
        'migrations/003_support_tickets.sql',
        'migrations/004_customers.sql',
        'migrations/005_shared_guardian_product.sql',
        'migrations/006_permanent_licenses.sql',
        'migrations/007_guardian_updates.sql',
        'migrations/008_admin_credentials.sql'
    )
    $OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    foreach ($relativePath in $migrationFiles) {
        $migrationPath = Join-Path $serverDir $relativePath
        if (-not (Test-Path -LiteralPath $migrationPath -PathType Leaf)) {
            throw "Migration 파일이 없습니다: $migrationPath"
        }
        Write-Host "적용 중: $relativePath"
        $sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $migrationPath
        $sql | & $docker compose --env-file $paths.LicenseEnv exec -T db psql -X -v ON_ERROR_STOP=1 -U guardian -d guardian
        if ($LASTEXITCODE -ne 0) {
            throw "Migration 적용 실패: $relativePath. 백업 파일을 보존하고 오류를 확인하세요."
        }
    }

    Write-Host '4/5 필수 테이블 존재 검증'
    $verificationSql = "SELECT COALESCE(to_regclass('public.api_rate_limit_buckets')::text, ''), COALESCE(to_regclass('public.support_tickets')::text, ''), COALESCE(to_regclass('public.customers')::text, ''), COALESCE(to_regclass('public.guardian_updates')::text, ''), COALESCE(to_regclass('public.admin_credentials')::text, ''), (SELECT code FROM products WHERE code='guardian-pro' AND active=true LIMIT 1);"
    $verification = & $docker compose --env-file $paths.LicenseEnv exec -T db psql -X -A -t -U guardian -d guardian -c $verificationSql
    if ($LASTEXITCODE -ne 0) {
        throw 'Migration 결과 조회 실패'
    }
    $verified = ($verification -join '').Trim()
    if ($verified -ne 'api_rate_limit_buckets|support_tickets|customers|guardian_updates|admin_credentials|guardian-pro') {
        throw "필수 테이블 검증 실패: $verified"
    }

    Write-Host '5/5 최신 License API 이미지 빌드 및 준비 상태 확인'
    & $docker compose --env-file $paths.LicenseEnv up -d --build api
    if ($LASTEXITCODE -ne 0) {
        throw 'License API 이미지 빌드/재시작 실패'
    }
    $ready = $false
    for ($attempt = 1; $attempt -le 12; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri 'http://127.0.0.1:8787/ready' -TimeoutSec 5 -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {}
        Start-Sleep -Seconds 5
    }
    if (-not $ready) {
        throw 'Migration은 적용됐지만 License API /ready가 통과하지 않았습니다. docker compose logs api db를 확인하세요.'
    }
    Write-Host 'Guardian License API migration 및 /ready 검증 완료.'
    Write-Host "복구용 백업: $($backup.FullName)"
} finally {
    Pop-Location
}
