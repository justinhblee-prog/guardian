[CmdletBinding()]
param(
  [Parameter(Mandatory)][object[]]$Sources,
  [Parameter(Mandatory)][string]$ManifestBaseUrl,
  [Parameter(Mandatory)][string]$PublicKeyPath
)
$ErrorActionPreference = 'Stop'
$rsa = [Security.Cryptography.RSA]::Create()
try {
  $publicBytes = [Convert]::FromBase64String((Get-Content -Raw -LiteralPath $PublicKeyPath).Trim())
  $bytesRead = 0
  $rsa.ImportSubjectPublicKeyInfo($publicBytes, [ref]$bytesRead)
  if ($bytesRead -ne $publicBytes.Length -or $rsa.KeySize -lt 2048) {
    throw '필터 공개키 형식 또는 RSA 키 크기가 올바르지 않습니다.'
  }
  foreach ($source in $Sources) {
    $sourceId = [string]$source.id
    if ($sourceId -notmatch '^[A-Za-z0-9_-]{1,64}$') {
      throw 'sources.json source id 형식이 잘못되었습니다.'
    }
    $manifestTemp = Join-Path ([IO.Path]::GetTempPath()) ('guardian-preflight-' + [guid]::NewGuid().ToString('N') + '.manifest')
    $signatureTemp = Join-Path ([IO.Path]::GetTempPath()) ('guardian-preflight-' + [guid]::NewGuid().ToString('N') + '.sig')
    $filterTemp = Join-Path ([IO.Path]::GetTempPath()) ('guardian-preflight-' + [guid]::NewGuid().ToString('N') + '.txt')
    try {
      $manifestUri = '{0}/{1}.manifest' -f $ManifestBaseUrl, $sourceId
      Invoke-WebRequest -Uri $manifestUri -OutFile $manifestTemp -TimeoutSec 20 -UseBasicParsing
      $manifestBytes = [IO.File]::ReadAllBytes($manifestTemp)
      if ($manifestBytes.Length -lt 1 -or $manifestBytes.Length -gt 16KB) {
        throw 'manifest 크기가 허용 범위를 벗어났습니다.'
      }
      $utf8 = [Text.UTF8Encoding]::new($false, $true)
      $manifestText = $utf8.GetString($manifestBytes)
      $values = @{}
      foreach ($line in ($manifestText -split '\r?\n')) {
        if (-not $line) { continue }
        $parts = $line -split '=', 2
        if ($parts.Count -ne 2 -or $values.ContainsKey($parts[0])) {
          throw 'manifest 필드 형식 또는 중복이 잘못되었습니다.'
        }
        $values[$parts[0]] = $parts[1]
      }
      $required = @('manifestVersion','generatedAt','sourceId','sourceVersion','url','sha256','minimumRules')
      if ($values.Count -ne $required.Count -or @($required | Where-Object { -not $values.ContainsKey($_) }).Count -gt 0) {
        throw 'manifest 필수 필드가 누락되었거나 알 수 없는 필드가 있습니다.'
      }
      $version = [string]$values.sourceVersion
      $generatedAt = [string]$values.generatedAt
      $expectedUrl = '{0}/filters/{1}/{2}.txt' -f $ManifestBaseUrl, $sourceId, $version
      if ($values.manifestVersion -ne '1' -or $values.sourceId -ne $sourceId -or
          $version -notmatch '^[A-Za-z0-9._+-]{1,64}$' -or
          $generatedAt -notmatch '^[1-9][0-9]{0,15}$' -or
          $values.sha256 -notmatch '^[a-f0-9]{64}$' -or
          $values.minimumRules -notmatch '^[1-9][0-9]*$' -or
          $values.url -cne $expectedUrl) {
        throw 'manifest 필드 또는 필터 URL 검증에 실패했습니다.'
      }
      $signatureUri = '{0}/{1}.{2}.{3}.sig' -f $ManifestBaseUrl, $sourceId, $version, $generatedAt
      Invoke-WebRequest -Uri $signatureUri -OutFile $signatureTemp -TimeoutSec 20 -UseBasicParsing
      $signatureText = [IO.File]::ReadAllText($signatureTemp, [Text.Encoding]::ASCII).Trim()
      $signature = [Convert]::FromBase64String($signatureText)
      if ([Convert]::ToBase64String($signature) -cne $signatureText -or
          $signature.Length -ne ($rsa.KeySize / 8) -or
          -not ($rsa.VerifyData($manifestBytes, $signature, [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1))) {
        throw 'manifest RSA-SHA256 detached signature 검증에 실패했습니다.'
      }
      Invoke-WebRequest -Uri $values.url -OutFile $filterTemp -TimeoutSec 30 -UseBasicParsing
      $filterBytes = [IO.File]::ReadAllBytes($filterTemp)
      if ($filterBytes.Length -lt 1 -or $filterBytes.Length -gt 10MB) {
        throw '필터 파일 크기가 허용 범위를 벗어났습니다.'
      }
      $actualHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($filterBytes)).ToLowerInvariant()
      if ($actualHash -cne [string]$values.sha256) {
        throw '필터 payload SHA-256 검증에 실패했습니다.'
      }
    } catch {
      throw ('공개 서명 필터 검증 실패: ' + $sourceId + '. manifest, detached signature, payload를 확인하세요. ' + $_.Exception.Message)
    } finally {
      Remove-Item -LiteralPath $manifestTemp, $signatureTemp, $filterTemp -Force -ErrorAction SilentlyContinue
    }
  }
  Write-Host ('모든 {0}개 운영 필터 manifest의 RSA 서명과 payload SHA-256 검증을 통과했습니다.' -f $Sources.Count)
} finally {
  $rsa.Dispose()
}
