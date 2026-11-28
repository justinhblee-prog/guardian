#Requires -Version 7.2
[CmdletBinding()]
param([string]$VersionName='1.0.0',[int]$VersionCode=0,[string]$Repository='justinhblee-prog/guardian',[ValidateSet('Neon','Tailscale')][string]$LicenseBackend='Neon',[string]$NeonInvocationUrl='https://br-shiny-mountain-bi4ia7g2-guardianapi.compute.c-5.ap-southeast-1.aws.neon.tech',[switch]$SigningOnly)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
. (Join-Path $PSScriptRoot 'Guardian-Paths.ps1')
$paths=Get-GuardianPaths -RepositoryRoot $repoRoot
$secretDir=$paths.Secrets
$licensePublicPath=Join-Path $secretDir 'guardian-license-public.x509.b64'
$filterPublicPath=Join-Path $secretDir 'guardian-filter-public.x509.b64'
$keystorePath=Join-Path $secretDir 'guardian-release.jks'
$storePasswordPath=Join-Path $secretDir 'guardian-release-store-password.dpapi'
$keyPasswordPath=Join-Path $secretDir 'guardian-release-key-password.dpapi'
if($VersionName -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$' -or $VersionCode -lt 0){throw '정식 버전은 x.y.z 형식이고 versionCode는 0(자동 산정) 또는 양의 정수여야 합니다.'}
$androidGradleConfig = Get-Content -Raw -LiteralPath (Join-Path $repoRoot 'android\app\build.gradle.kts')
# Production uses a reserved versionCode range above all development releases.
$minimumProductionVersionCode = 2000000
if($VersionCode -eq 0){$VersionCode=$minimumProductionVersionCode}
if($VersionCode -lt $minimumProductionVersionCode){throw "정식 versionCode는 현재 개발 빌드보다 커야 합니다. 현재 개발 versionCode: $($minimumProductionVersionCode - 1), 최소 정식 versionCode: $minimumProductionVersionCode"}
foreach($path in @($licensePublicPath,$filterPublicPath)){if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "운영 공개키가 없습니다: $path. Setup-GuardianServer.ps1을 먼저 실행하세요."}}
$requiredCommands = @('keytool','gh','git')
if(-not $SigningOnly -and $LicenseBackend -eq 'Tailscale'){ $requiredCommands += 'tailscale' }
foreach($name in $requiredCommands){if(-not(Get-Command $name -ErrorAction SilentlyContinue)){throw "$name 명령을 찾을 수 없습니다."}}
$remote=(& git -C $repoRoot remote get-url origin 2>$null | Out-String).Trim()
if($LASTEXITCODE -ne 0 -or $remote -notmatch 'github\.com[:/]justinhblee-prog/guardian(?:\.git)?$'){throw '현재 저장소 origin이 justinhblee-prog/guardian인지 확인할 수 없습니다.'}
& gh auth status --hostname github.com *> $null
if($LASTEXITCODE -ne 0){throw 'GitHub CLI 인증이 필요합니다. gh auth login 후 다시 실행하세요.'}
$licenseApiUrl = $null
if(-not $SigningOnly){
  if($LicenseBackend -eq 'Neon'){
    $parsedNeonUrl = $null
    if(-not [Uri]::TryCreate($NeonInvocationUrl,[UriKind]::Absolute,[ref]$parsedNeonUrl) -or
      $parsedNeonUrl.Scheme -ne 'https' -or
      $parsedNeonUrl.Host -notlike '*.compute.c-5.ap-southeast-1.aws.neon.tech' -or
      $parsedNeonUrl.AbsolutePath -ne '/' -or $parsedNeonUrl.Query -or $parsedNeonUrl.Fragment){
      throw 'NeonInvocationUrl은 지정된 Neon Function의 HTTPS origin이어야 합니다.'
    }
    $licenseApiUrl=$NeonInvocationUrl.TrimEnd('/')
  } else {
    # Explicit legacy/local mode. Neon is the default so laptop uptime is not
    # a production dependency unless the operator deliberately selects it.
    $tailscaleCommand=Get-Command 'tailscale' -ErrorAction Stop
    $tailscaleExecutable=$tailscaleCommand.Source
    if(-not $tailscaleExecutable){$tailscaleExecutable=$tailscaleCommand.Name}
    . (Join-Path $PSScriptRoot 'Get-GuardianTailscaleStatus.ps1')
    $tailStatus=Get-GuardianTailscaleStatus -Executable $tailscaleExecutable
    $selfProperty=$tailStatus.PSObject.Properties['Self']
    if($null -eq $selfProperty -or $null -eq $selfProperty.Value){
      $backendProperty=$tailStatus.PSObject.Properties['BackendState']
      $backendState=if($null -ne $backendProperty){[string]$backendProperty.Value}else{'unknown'}
      throw "Tailscale CLI가 정상 응답했지만 현재 노드 정보(Self)가 없습니다. 상태: $backendState. 노트북 Tailscale 로그인을 완료하고 연결 상태를 확인하세요."
    }
    $dnsName=([string]$selfProperty.Value.DNSName).TrimEnd('.')
    if([string]::IsNullOrWhiteSpace($dnsName) -or -not $dnsName.EndsWith('.ts.net',[StringComparison]::OrdinalIgnoreCase)){throw 'Tailscale MagicDNS 이름이 없습니다. Tailscale 로그인을 확인하세요.'}
    $licenseApiUrl='https://'+$dnsName
    try{$health=Invoke-WebRequest -Uri ($licenseApiUrl+'/ready') -TimeoutSec 8 -UseBasicParsing;if($health.StatusCode -ne 200){throw 'not ready'}}catch{throw 'Tailscale HTTPS 라이선스 API가 준비되지 않았습니다. Enable-GuardianPrivateApiOnly.ps1을 먼저 완료하세요.'}
  }
}
if(-not $SigningOnly){
  # Full release setup requires reviewed filter rights and a published signed manifest.
  $manifestBaseUrl='https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data'
  if(-not(Test-Path -LiteralPath $paths.FilterSources -PathType Leaf)){throw '운영 sources.json이 없습니다. Setup-GuardianServer.ps1을 먼저 실행하세요.'}
  $filterConfig=Get-Content -Raw -LiteralPath $paths.FilterSources | ConvertFrom-Json
  if($null -eq $filterConfig.sources -or @($filterConfig.sources).Count -lt 1){throw 'sources.json에 필터 공급원이 없습니다.'}
  $unapprovedSources = @($filterConfig.sources | Where-Object {
    $license = $_.PSObject.Properties['licenseId']
    $approved = $_.PSObject.Properties['redistributionApproved']
    $approvalRef = $_.PSObject.Properties['redistributionApprovalRef']
    $null -eq $license -or [string]::IsNullOrWhiteSpace([string]$license.Value) -or
      $null -eq $approved -or $approved.Value -ne $true -or
      $null -eq $approvalRef -or [string]::IsNullOrWhiteSpace([string]$approvalRef.Value)
  })
  if($unapprovedSources.Count -gt 0){
    $blockedIds = ($unapprovedSources | ForEach-Object { [string]$_.id }) -join ', '
    throw "운영 필터 재배포 승인 증빙이 없어 Release 설정을 중단합니다: $blockedIds. 권리 검토 후 licenseId, redistributionApproved=true, redistributionApprovalRef를 sources.json에 기록하세요."
  }
  $publishedBranch=(& git -C $repoRoot ls-remote --heads origin refs/heads/guardian-filter-data 2>$null | Out-String).Trim()
  if($LASTEXITCODE -ne 0 -or -not $publishedBranch){throw '공개 guardian-filter-data 브랜치가 없습니다. Update-And-Publish-GuardianFilters.ps1을 먼저 실행하세요.'}
  & (Join-Path $PSScriptRoot 'Verify-GuardianPublishedFilters.ps1') -Sources @($filterConfig.sources) -ManifestBaseUrl $manifestBaseUrl -PublicKeyPath $filterPublicPath
} else {
  $manifestBaseUrl='https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data'
  Write-Host 'Signing-only mode: filter source approvals and public filter branch are not asserted.'
}
New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
$currentIdentity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $secretDir /inheritance:r /grant:r ($currentIdentity + ':(OI)(CI)F') '*S-1-5-18:(OI)(CI)F' | Out-Null
if($LASTEXITCODE -ne 0){throw '운영 비밀 폴더의 Windows ACL 설정에 실패했습니다.'}
function New-RandomSecret([int]$Length=48){$bytes=[byte[]]::new($Length);$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes)}finally{$rng.Dispose()};return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','A').Replace('/','B')}
function Set-GitHubSecret([string]$Name,[string]$Value){
 $psi=[Diagnostics.ProcessStartInfo]::new();$psi.FileName=(Get-Command gh).Source
 foreach($arg in @('secret','set',$Name,'--repo',$Repository)){[void]$psi.ArgumentList.Add($arg)}
 $psi.UseShellExecute=$false;$psi.RedirectStandardInput=$true;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
 $process=[Diagnostics.Process]::Start($psi)
 try{$process.StandardInput.Write($Value);$process.StandardInput.Close();$stdout=$process.StandardOutput.ReadToEnd();$stderr=$process.StandardError.ReadToEnd();$process.WaitForExit();if($process.ExitCode -ne 0){throw "GitHub Secret $Name 등록 실패: $stderr $stdout"}}finally{$process.Dispose()}
}
function Set-GitHubVariable([string]$Name,[string]$Value){& gh variable set $Name --body $Value --repo $Repository;if($LASTEXITCODE -ne 0){throw "GitHub Variable $Name 등록 실패"}}
if((Test-Path $keystorePath) -or (Test-Path $storePasswordPath) -or (Test-Path $keyPasswordPath)){
 if(-not((Test-Path $keystorePath) -and (Test-Path $storePasswordPath) -and (Test-Path $keyPasswordPath))){throw '기존 Android release keystore 구성이 일부만 존재합니다. 덮어쓰지 않고 중단합니다.'}
 $storeSecure=Get-Content -Raw $storePasswordPath | ConvertTo-SecureString;$keySecure=Get-Content -Raw $keyPasswordPath | ConvertTo-SecureString
 $storePtr=[IntPtr]::Zero;$keyPtr=[IntPtr]::Zero
 try{$storePtr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($storeSecure);$keyPtr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($keySecure);$storePassword=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($storePtr);$keyPassword=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPtr)}
 finally{if($storePtr -ne [IntPtr]::Zero){[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($storePtr)};if($keyPtr -ne [IntPtr]::Zero){[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPtr)}}
}else{
 $storePassword=New-RandomSecret;$keyPassword=New-RandomSecret
 $env:GUARDIAN_NEW_STORE_PASSWORD=$storePassword;$env:GUARDIAN_NEW_KEY_PASSWORD=$keyPassword
 try{& keytool -genkeypair -noprompt -v -keystore $keystorePath -storetype JKS -storepass:env GUARDIAN_NEW_STORE_PASSWORD -keypass:env GUARDIAN_NEW_KEY_PASSWORD -alias guardian-release -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Guardian AdBlock Production, OU=Android, O=Guardian, C=KR';if($LASTEXITCODE -ne 0 -or -not(Test-Path $keystorePath)){throw 'Android production keystore 생성 실패'}}
 finally{Remove-Item Env:GUARDIAN_NEW_STORE_PASSWORD,Env:GUARDIAN_NEW_KEY_PASSWORD -ErrorAction SilentlyContinue}
 ConvertTo-SecureString -String $storePassword -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -NoNewline -Encoding ascii $storePasswordPath
 ConvertTo-SecureString -String $keyPassword -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -NoNewline -Encoding ascii $keyPasswordPath
}
& icacls.exe $keystorePath /inheritance:r /grant:r ($currentIdentity + ':F') '*S-1-5-18:F' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Android keystore 파일 ACL 설정 실패'}
# Validate the local key material before changing any GitHub repository secret or variable.
$env:GUARDIAN_RELEASE_STORE_PASSWORD=$storePassword
try {
  & keytool -list -keystore $keystorePath -storepass:env GUARDIAN_RELEASE_STORE_PASSWORD -alias guardian-release *> $null
  if($LASTEXITCODE -ne 0){throw '로컬 Android release keystore 검증 실패. GitHub 설정은 변경하지 않았습니다.'}
} finally {
  Remove-Item Env:GUARDIAN_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
}
$keystoreBase64=[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
$filterPublic=(Get-Content -Raw $filterPublicPath).Trim();$licensePublic=(Get-Content -Raw $licensePublicPath).Trim()
$manifestUrl='https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data'
Set-GitHubSecret 'GUARDIAN_RELEASE_KEYSTORE_BASE64' $keystoreBase64
Set-GitHubSecret 'GUARDIAN_RELEASE_STORE_PASSWORD' $storePassword
Set-GitHubSecret 'GUARDIAN_RELEASE_KEY_ALIAS' 'guardian-release'
Set-GitHubSecret 'GUARDIAN_RELEASE_KEY_PASSWORD' $keyPassword
Set-GitHubVariable 'GUARDIAN_MANIFEST_BASE_URL' $manifestUrl
Set-GitHubVariable 'GUARDIAN_MANIFEST_PUBLIC_KEY' $filterPublic
Set-GitHubVariable 'GUARDIAN_LICENSE_PUBLIC_KEY' $licensePublic
if(-not $SigningOnly){ Set-GitHubVariable 'GUARDIAN_LICENSE_API_BASE_URL' $licenseApiUrl }
else { Write-Host 'Signing-only mode: GUARDIAN_LICENSE_API_BASE_URL is left unchanged.' }
if($SigningOnly){
  # Explicitly reset the policy to deny all configured example sources so a stale approval variable cannot unlock release.
  $exampleSourcesPath=Join-Path $repoRoot 'server\filter-distribution\sources.example.json'
  if(-not(Test-Path -LiteralPath $exampleSourcesPath -PathType Leaf)){throw '필터 승인 기본 정책 파일이 없습니다.'}
  $exampleConfig=Get-Content -Raw -LiteralPath $exampleSourcesPath | ConvertFrom-Json
  $approvalSources = @($exampleConfig.sources | ForEach-Object {
    [ordered]@{ id=[string]$_.id; licenseId=[string]$_.licenseId; redistributionApproved=$false; redistributionApprovalRef='' }
  })
  $approvalPolicyJson = ConvertTo-Json -InputObject ([ordered]@{ sources = $approvalSources }) -Depth 5 -Compress
  Set-GitHubVariable 'GUARDIAN_FILTER_APPROVALS_JSON' $approvalPolicyJson
  Write-Host '필터 재배포 승인 정책을 모두 거부(false)로 설정했습니다. 권리 검토 전 정식 Release는 차단됩니다.'
} else {
  $approvalSources = @($filterConfig.sources | ForEach-Object {
    [ordered]@{
      id = [string]$_.id
      licenseId = [string]$_.licenseId
      redistributionApproved = ($_.redistributionApproved -eq $true)
      redistributionApprovalRef = [string]$_.redistributionApprovalRef
    }
  })
  $approvalPolicyJson = ConvertTo-Json -InputObject ([ordered]@{ sources = $approvalSources }) -Depth 5 -Compress
  Set-GitHubVariable 'GUARDIAN_FILTER_APPROVALS_JSON' $approvalPolicyJson
}
Set-GitHubVariable 'GUARDIAN_VERSION_NAME' $VersionName
Set-GitHubVariable 'GUARDIAN_VERSION_CODE' ([string]$VersionCode)
Write-Host 'Guardian Android production signing 설정을 GitHub에 등록했습니다.'
Write-Host "버전: $VersionName (versionCode $VersionCode)"
Write-Host "라이선스 API 주소: $licenseApiUrl"
Write-Host "필터 manifest 주소: $manifestUrl"
Write-Host '운영 개인키와 비밀번호는 콘솔·Git 저장소·CI 로그에 출력하지 않았습니다.'
Write-Host '다음 단계: GitHub Actions > Guardian AdBlock Production Release > Run workflow'