#Requires -Version 7.2
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'

& (Join-Path $PSScriptRoot 'Invoke-GuardianFilterUpdate.ps1')
if ($LASTEXITCODE -ne 0) { throw '필터 다운로드/검증/서명 작업이 실패했습니다. 클라우드에는 게시하지 않았습니다.' }

& (Join-Path $PSScriptRoot 'Publish-GuardianFilterToCloud.ps1')
if ($LASTEXITCODE -ne 0) { throw '클라우드 게시에 실패했습니다. 노트북의 서명된 dist 파일은 유지됩니다.' }

Write-Host 'Guardian 필터 업데이트와 GitHub 정적 클라우드 게시가 완료되었습니다.'
