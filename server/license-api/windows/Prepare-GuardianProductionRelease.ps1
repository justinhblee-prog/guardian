#Requires -Version 7.2
[CmdletBinding()]
param([string]$VersionName='1.0.0',[int]$VersionCode=28,[string]$Repository='justinhblee-prog/guardian')
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
if($VersionName -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$' -or $VersionCode -lt 1){throw '정식 버전은 x.y.z 형식이고 versionCode는 양의 정수여야 합니다.'}
foreach($path in @($licensePublicPath,$filterPublicPath)){if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "운영 공개키가 없습니다: $path. Setup-GuardianServer.ps1을 먼저 실행하세요."}}
foreach($name in @('keytool','gh','tailscale','git')){if(-not(Get-Command $name -ErrorAction SilentlyContinue)){throw "$name 명령을 찾을 수 없습니다."}}
$remote=(& git -C $repoRoot remote get-url origin 2>$null | Out-String).Trim()
if($LASTEXITCODE -ne 0 -or $remote -notmatch 'github\.com[:/]justinhblee-prog/guardian(?:\.git)?$'){throw '현재 저장소 origin이 justinhblee-prog/guardian인지 확인할 수 없습니다.'}
& gh auth status --hostname github.com *> $null
if($LASTEXITCODE -ne 0){throw 'GitHub CLI 인증이 필요합니다. gh auth login 후 다시 실행하세요.'}
$tailStatusText=(& tailscale status --json | Out-String)
if($LASTEXITCODE -ne 0){throw 'Tailscale 연결 상태를 확인하지 못했습니다.'}
$tailStatus=$tailStatusText | ConvertFrom-Json
$dnsName=([string]$tailStatus.Self.DNSName).TrimEnd('.')
if([string]::IsNullOrWhiteSpace($dnsName) -or -not $dnsName.EndsWith('.ts.net',[StringComparison]::OrdinalIgnoreCase)){throw 'Tailscale MagicDNS 이름이 없습니다. 노트북의 Tailscale 로그인과 MagicDNS를 확인하세요.'}
$licenseApiUrl='https://'+$dnsName
try{$health=Invoke-WebRequest -Uri ($licenseApiUrl+'/ready') -TimeoutSec 8 -UseBasicParsing;if($health.StatusCode -ne 200){throw 'not ready'}}catch{throw 'Tailscale HTTPS 라이선스 API가 준비되지 않았습니다. Enable-GuardianPrivateAccess.ps1을 먼저 완료하세요.'}
# Require the public filter branch and each configured source manifest before writing production GitHub settings.
$manifestBaseUrl='https://raw.githubusercontent.com/justinhblee-prog/guardian/guardian-filter-data'
if(-not(Test-Path -LiteralPath $paths.FilterSources -PathType Leaf)){throw '운영 sources.json이 없습니다. Setup-GuardianServer.ps1을 먼저 실행하세요.'}
$filterConfig=Get-Content -Raw -LiteralPath $paths.FilterSources | ConvertFrom-Json
if($null -eq $filterConfig.sources -or @($filterConfig.sources).Count -lt 1){throw 'sources.json에 필터 공급원이 없습니다.'}
$publishedBranch=(& git -C $repoRoot ls-remote --heads origin refs/heads/guardian-filter-data 2>$null | Out-String).Trim()
if($LASTEXITCODE -ne 0 -or -not $publishedBranch){throw '공개 guardian-filter-data 브랜치가 없습니다. Update-And-Publish-GuardianFilters.ps1을 먼저 실행하세요.'}
& (Join-Path $PSScriptRoot 'Verify-GuardianPublishedFilters.ps1') -Sources @($filterConfig.sources) -ManifestBaseUrl $manifestBaseUrl -PublicKeyPath $filterPublicPath
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
Set-GitHubVariable 'GUARDIAN_LICENSE_API_BASE_URL' $licenseApiUrl
Set-GitHubVariable 'GUARDIAN_VERSION_NAME' $VersionName
Set-GitHubVariable 'GUARDIAN_VERSION_CODE' ([string]$VersionCode)
& keytool -list -keystore $keystorePath -storepass $storePassword -alias guardian-release *> $null
if($LASTEXITCODE -ne 0){throw 'GitHub 설정 후 로컬 release keystore 재검증 실패'}
Write-Host 'Guardian Android production signing 설정을 GitHub에 등록했습니다.'
Write-Host "버전: $VersionName (versionCode $VersionCode)"
Write-Host "라이선스 API 주소: $licenseApiUrl"
Write-Host "필터 manifest 주소: $manifestUrl"
Write-Host '운영 개인키와 비밀번호는 콘솔·Git 저장소·CI 로그에 출력하지 않았습니다.'
Write-Host '다음 단계: GitHub Actions > Guardian AdBlock Production Release > Run workflow'
