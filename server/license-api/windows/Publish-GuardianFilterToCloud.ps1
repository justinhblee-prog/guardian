[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'

$repoRoot = (& git -C $PSScriptRoot rev-parse --show-toplevel 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or -not $repoRoot) { throw 'Guardian Git 저장소 안에서 실행해야 합니다.' }
$remote = (& git -C $repoRoot remote get-url origin 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $remote -notmatch 'github\.com[:/]justinhblee-prog/guardian(?:\.git)?$') {
  throw 'origin이 justinhblee-prog/guardian 저장소인지 확인할 수 없습니다.'
}
$filterRoot = Join-Path $repoRoot 'server\filter-distribution'
$dist = Join-Path $filterRoot 'dist'
$secretPath = Join-Path $repoRoot 'server\license-api\secrets\guardian-filter-private.dpapi'
if (-not (Test-Path -LiteralPath $dist -PathType Container)) { throw '서명된 dist 폴더가 없습니다. 필터 업데이트를 먼저 실행하세요.' }
if (-not (Test-Path -LiteralPath $secretPath -PathType Leaf)) { throw 'DPAPI 보호 필터 서명키가 없습니다.' }

# Verify all public artifacts locally before pushing them to the public static origin.
$protectedText = Get-Content -Raw -LiteralPath $secretPath
$secureKey = ConvertTo-SecureString -String $protectedText
$pointer = [IntPtr]::Zero
$rsa = [System.Security.Cryptography.RSA]::Create()
try {
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
  $plainKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  $rsa.ImportFromPem($plainKey)
  $publicKeyBase64 = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo())
  [IO.File]::WriteAllText((Join-Path $dist 'guardian-filter-public-key.x509.b64'), $publicKeyBase64 + [Environment]::NewLine, [Text.Encoding]::ASCII)
  $files = @(Get-ChildItem -LiteralPath $dist -File -Recurse -Force)
  if ($files.Count -lt 3) { throw '배포할 필터 산출물이 너무 적습니다.' }
  foreach ($file in $files) {
    if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "심볼릭 링크/재분석 지점은 배포할 수 없습니다: $($file.FullName)" }
    $relative = $file.FullName.Substring($dist.Length).TrimStart('\','/').Replace('\','/')
    if ($relative -match '(^|/)(\.env|.*\.pem|.*\.dpapi|.*\.log|.*\.lock)$' -or $relative -match '(^|/)(sources\.json|.*\.bak)$') {
      throw "비밀값 또는 운영 파일이 dist에 포함되어 있습니다: $relative"
    }
    if ($file.Length -gt 10MB) { throw "파일 크기 제한 초과: $relative" }
    if ($relative -ne 'guardian-filter-public-key.x509.b64' -and $relative -notmatch '^[A-Za-z0-9_.+-]+\.manifest$' -and
        $relative -notmatch '^[A-Za-z0-9_.+-]+\.[A-Za-z0-9_.+-]+\.[0-9]+\.sig$' -and
        $relative -notmatch '^filters/[A-Za-z0-9_-]{1,64}/[A-Za-z0-9_.+-]{1,64}\.txt$') {
      throw "허용되지 않은 배포 파일 경로: $relative"
    }
  }
  $manifestFiles = @(Get-ChildItem -LiteralPath $dist -File -Filter '*.manifest')
  if ($manifestFiles.Count -lt 1) { throw '서명 manifest가 없습니다.' }
  $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
  foreach ($manifestFile in $manifestFiles) {
    $bytes = [IO.File]::ReadAllBytes($manifestFile.FullName)
    $text = $utf8.GetString($bytes)
    $values = @{}
    foreach ($line in ($text -split '\r?\n')) {
      if (-not $line) { continue }
      $parts = $line -split '=', 2
      if ($parts.Count -ne 2 -or $values.ContainsKey($parts[0])) { throw "Manifest 형식 오류: $($manifestFile.Name)" }
      $values[$parts[0]] = $parts[1]
    }
    $sourceId = [IO.Path]::GetFileNameWithoutExtension($manifestFile.Name)
    foreach ($key in @('manifestVersion','generatedAt','sourceId','sourceVersion','url','sha256','minimumRules')) {
      if (-not $values.ContainsKey($key)) { throw "Manifest 필수 항목 누락: $($manifestFile.Name) / $key" }
    }
    if ($values.sourceId -ne $sourceId -or $values.manifestVersion -ne '1' -or
        $values.sourceVersion -notmatch '^[A-Za-z0-9._+-]{1,64}$' -or
        $values.generatedAt -notmatch '^[1-9][0-9]{0,15}$' -or
        $values.sha256 -notmatch '^[a-f0-9]{64}$' -or
        $values.url -notmatch '^https://raw\.githubusercontent\.com/justinhblee-prog/guardian/guardian-filter-data/filters/') {
      throw "Manifest 필드 검증 실패: $($manifestFile.Name)"
    }
    $sigPath = Join-Path $dist "$sourceId.$($values.sourceVersion).$($values.generatedAt).sig"
    $filterPath = Join-Path $dist ("filters\$sourceId\$($values.sourceVersion).txt")
    if (-not (Test-Path -LiteralPath $sigPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $filterPath -PathType Leaf)) {
      throw "Manifest가 참조하는 서명 또는 필터 파일이 없습니다: $sourceId"
    }
    $signature = [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $sigPath).Trim())
    if (-not $rsa.VerifyData($bytes, $signature, [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)) {
      throw "RSA-SHA256 서명 검증 실패: $($manifestFile.Name)"
    }
    $actualHash = (Get-FileHash -LiteralPath $filterPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $values.sha256) { throw "필터 SHA-256 검증 실패: $sourceId" }
  }
} finally {
  $rsa.Dispose()
  $plainKey = $null
  if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

$worktree = Join-Path ([IO.Path]::GetTempPath()) ("guardian-filter-cloud-" + [guid]::NewGuid().ToString('N'))
$branchName = "guardian-filter-cloud-publish-" + [guid]::NewGuid().ToString('N')
try {
  & git -C $repoRoot worktree add --detach $worktree HEAD
  if ($LASTEXITCODE -ne 0) { throw '임시 Git worktree 생성 실패' }
  & git -C $worktree fetch --no-tags origin refs/heads/guardian-filter-data 2>$null
  $remoteBranchExists = ($LASTEXITCODE -eq 0)
  if ($remoteBranchExists) {
    & git -C $worktree checkout --detach FETCH_HEAD
    if ($LASTEXITCODE -ne 0) { throw '기존 클라우드 배포 브랜치 체크아웃 실패' }
    & git -C $worktree switch -c $branchName
  } else {
    & git -C $worktree switch --orphan $branchName
  }
  if ($LASTEXITCODE -ne 0) { throw '클라우드 배포용 임시 브랜치 생성 실패' }

  Get-ChildItem -LiteralPath $worktree -Force | Where-Object { $_.Name -ne '.git' } |
    Remove-Item -Recurse -Force
  Get-ChildItem -LiteralPath $dist -Force | Copy-Item -Destination $worktree -Recurse -Force
  & git -C $worktree add --all
  if ($LASTEXITCODE -ne 0) { throw '클라우드 배포 파일 stage 실패' }
  & git -C $worktree -c user.name='Guardian Filter Publisher' -c user.email='guardian-filter-publisher@users.noreply.github.com' commit -m ("Publish signed Guardian filters " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
  if ($LASTEXITCODE -ne 0) { throw '클라우드 배포 커밋 생성 실패' }
  & git -C $worktree push origin 'HEAD:refs/heads/guardian-filter-data'
  if ($LASTEXITCODE -ne 0) { throw 'GitHub 클라우드 필터 브랜치 push 실패. GitHub 로그인/쓰기 권한과 브랜치 보호 규칙을 확인하세요.' }
  Write-Host 'Guardian 서명 필터를 GitHub 정적 배포 원본에 게시했습니다.'
  Write-Host 'Manifest base URL: https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data'
  Write-Host '개인키/라이선스 DB/환경 파일은 업로드하지 않았으며, manifest RSA 서명과 필터 SHA-256을 push 전에 검증했습니다.'
} finally {
  & git -C $repoRoot worktree remove --force $worktree 2>$null | Out-Null
  & git -C $repoRoot worktree prune 2>$null | Out-Null
}
 -and
        $relative -notmatch '^[A-Za-z0-9_.+-]+\.[A-Za-z0-9_.+-]+\.[0-9]+\.sig$' -and
        $relative -notmatch '^filters/[A-Za-z0-9_-]{1,64}/[A-Za-z0-9_.+-]{1,64}\.txt$') {
      throw "허용되지 않은 배포 파일 경로: $relative"
    }
  }
  $manifestFiles = @(Get-ChildItem -LiteralPath $dist -File -Filter '*.manifest')
  if ($manifestFiles.Count -lt 1) { throw '서명 manifest가 없습니다.' }
  $utf8 = [System.Text.UTF8Encoding]::new($false, $true)
  foreach ($manifestFile in $manifestFiles) {
    $bytes = [IO.File]::ReadAllBytes($manifestFile.FullName)
    $text = $utf8.GetString($bytes)
    $values = @{}
    foreach ($line in ($text -split '\r?\n')) {
      if (-not $line) { continue }
      $parts = $line -split '=', 2
      if ($parts.Count -ne 2 -or $values.ContainsKey($parts[0])) { throw "Manifest 형식 오류: $($manifestFile.Name)" }
      $values[$parts[0]] = $parts[1]
    }
    $sourceId = [IO.Path]::GetFileNameWithoutExtension($manifestFile.Name)
    foreach ($key in @('manifestVersion','generatedAt','sourceId','sourceVersion','url','sha256','minimumRules')) {
      if (-not $values.ContainsKey($key)) { throw "Manifest 필수 항목 누락: $($manifestFile.Name) / $key" }
    }
    if ($values.sourceId -ne $sourceId -or $values.manifestVersion -ne '1' -or
        $values.sourceVersion -notmatch '^[A-Za-z0-9._+-]{1,64}$' -or
        $values.generatedAt -notmatch '^[1-9][0-9]{0,15}$' -or
        $values.sha256 -notmatch '^[a-f0-9]{64}$' -or
        $values.url -notmatch '^https://raw\.githubusercontent\.com/justinhblee-prog/guardian/guardian-filter-data/filters/') {
      throw "Manifest 필드 검증 실패: $($manifestFile.Name)"
    }
    $sigPath = Join-Path $dist "$sourceId.$($values.sourceVersion).$($values.generatedAt).sig"
    $filterPath = Join-Path $dist ("filters\$sourceId\$($values.sourceVersion).txt")
    if (-not (Test-Path -LiteralPath $sigPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $filterPath -PathType Leaf)) {
      throw "Manifest가 참조하는 서명 또는 필터 파일이 없습니다: $sourceId"
    }
    $signature = [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $sigPath).Trim())
    if (-not $rsa.VerifyData($bytes, $signature, [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)) {
      throw "RSA-SHA256 서명 검증 실패: $($manifestFile.Name)"
    }
    $actualHash = (Get-FileHash -LiteralPath $filterPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $values.sha256) { throw "필터 SHA-256 검증 실패: $sourceId" }
  }
} finally {
  $rsa.Dispose()
  $plainKey = $null
  if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

$worktree = Join-Path ([IO.Path]::GetTempPath()) ("guardian-filter-cloud-" + [guid]::NewGuid().ToString('N'))
$branchName = "guardian-filter-cloud-publish-" + [guid]::NewGuid().ToString('N')
try {
  & git -C $repoRoot worktree add --detach $worktree HEAD
  if ($LASTEXITCODE -ne 0) { throw '임시 Git worktree 생성 실패' }
  & git -C $worktree fetch --no-tags origin refs/heads/guardian-filter-data 2>$null
  $remoteBranchExists = ($LASTEXITCODE -eq 0)
  if ($remoteBranchExists) {
    & git -C $worktree checkout --detach FETCH_HEAD
    if ($LASTEXITCODE -ne 0) { throw '기존 클라우드 배포 브랜치 체크아웃 실패' }
    & git -C $worktree switch -c $branchName
  } else {
    & git -C $worktree switch --orphan $branchName
  }
  if ($LASTEXITCODE -ne 0) { throw '클라우드 배포용 임시 브랜치 생성 실패' }

  Get-ChildItem -LiteralPath $worktree -Force | Where-Object { $_.Name -ne '.git' } |
    Remove-Item -Recurse -Force
  Get-ChildItem -LiteralPath $dist -Force | Copy-Item -Destination $worktree -Recurse -Force
  & git -C $worktree add --all
  if ($LASTEXITCODE -ne 0) { throw '클라우드 배포 파일 stage 실패' }
  & git -C $worktree -c user.name='Guardian Filter Publisher' -c user.email='guardian-filter-publisher@users.noreply.github.com' commit -m ("Publish signed Guardian filters " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
  if ($LASTEXITCODE -ne 0) { throw '클라우드 배포 커밋 생성 실패' }
  & git -C $worktree push origin 'HEAD:refs/heads/guardian-filter-data'
  if ($LASTEXITCODE -ne 0) { throw 'GitHub 클라우드 필터 브랜치 push 실패. GitHub 로그인/쓰기 권한과 브랜치 보호 규칙을 확인하세요.' }
  Write-Host 'Guardian 서명 필터를 GitHub 정적 배포 원본에 게시했습니다.'
  Write-Host 'Manifest base URL: https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data'
  Write-Host '개인키/라이선스 DB/환경 파일은 업로드하지 않았으며, manifest RSA 서명과 필터 SHA-256을 push 전에 검증했습니다.'
} finally {
  & git -C $repoRoot worktree remove --force $worktree 2>$null | Out-Null
  & git -C $repoRoot worktree prune 2>$null | Out-Null
}
