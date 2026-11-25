param(
    [string]$Serial = "",
    [string]$BlockedDomain = "bi-tracker-global.rivergame.net",
    [string]$AllowedDomain = "example.com",
    [string]$Ipv6Domain = "ipv6.google.com"
)
$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA "Android/platform-tools/adb.exe"
if (!(Test-Path -LiteralPath $adb -PathType Leaf)) { throw "ADB not found at '$adb'." }
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$resultsDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $resultsDir "guardian-device-validation-$stamp.txt"
$bundlePath = Join-Path $resultsDir "guardian-device-validation-$stamp.zip"
$script:lines = [System.Collections.Generic.List[string]]::new()
$script:results = [ordered]@{}
function Add-ReportLine([string]$Message) {
    $script:lines.Add($Message)
    Write-Host $Message
}
function Invoke-Adb([string[]]$Arguments) {
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $adb @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    return [pscustomobject]@{ ExitCode = [int]$code; Output = ($output -join [Environment]::NewLine) }
}
Add-ReportLine "GUARDIAN ANDROID DEVICE VALIDATION"
Add-ReportLine ("Timestamp: " + (Get-Date -Format o))
Add-ReportLine ("Repository: " + $repoRoot)
Add-ReportLine ("ADB: " + $adb)
$devices = Invoke-Adb @("devices")
if ($devices.ExitCode -ne 0) { throw "ADB devices command failed: $($devices.Output)" }
$connected = @($devices.Output -split "`r?`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match "^\S+\s+device(?:\s|$)" } | ForEach-Object { ($_ -split "\s+")[0] })
if ($Serial) {
    if ($connected -notcontains $Serial) { throw "Requested device '$Serial' is not connected and authorized. Connected: $($connected -join ', ')" }
} elseif ($connected.Count -eq 1) { $Serial = $connected[0] }
elseif ($connected.Count -eq 0) { throw "No authorized Android device found. Connect and unlock the phone, then accept USB debugging." }
else { throw "Multiple devices connected. Re-run with -Serial <ADB serial>: $($connected -join ', ')" }
Add-ReportLine ("Device serial: " + $Serial)
function Test-DeviceCommand([string]$Name, [string[]]$Arguments) {
    $r = Invoke-Adb (@("-s", $Serial, "shell") + $Arguments)
    Add-ReportLine ""
    Add-ReportLine ("--- " + $Name + " (exit " + $r.ExitCode + ") ---")
    Add-ReportLine $r.Output
    return $r
}
# 1. Installation and VPN state
$pkg = Test-DeviceCommand "Guardian installation" @("pm", "path", "com.guardian.adblock")
$connectivity = Test-DeviceCommand "VPN connectivity and assigned DNS/routes" @("dumpsys", "connectivity")
$vpnConnected = $connectivity.Output -match "VPN CONNECTED extra: VPN:com\.guardian\.adblock"
$tunPresent = $connectivity.Output -match "InterfaceName:\s*tun0"
$vpnDnsV6 = $connectivity.Output -match "fd13:37::2"
$script:results["Guardian app installed"] = ($pkg.Output -match "package:")
$script:results["Guardian VPN connected"] = $vpnConnected
$script:results["VPN tun0 interface"] = $tunPresent
$script:results["VPN IPv6 virtual DNS assigned"] = $vpnDnsV6
# 2. DNS behavior
$allowed = Test-DeviceCommand "Allowed-domain DNS + IPv4 connectivity" @("ping", "-c", "1", "-W", "5", $AllowedDomain)
$blocked = Test-DeviceCommand "Blocked-domain DNS probe" @("ping", "-c", "1", "-W", "5", $BlockedDomain)
$ipv6 = Test-DeviceCommand "IPv6-domain resolution probe (ping6)" @("ping6", "-c", "1", "-W", "5", $Ipv6Domain)
$allowedResolved = $allowed.Output -match "(?im)^PING\s+$([regex]::Escape($AllowedDomain))\s+\("
$allowedReachable = $allowed.Output -match "1 received|1 packets transmitted, 1 received|0% packet loss"
$blockedNameFailure = $blocked.Output -match "(?i)unknown host|temporary failure in name resolution|Name or service not known"
$blockedResolved = $blocked.Output -match "(?im)^PING\s+$([regex]::Escape($BlockedDomain))\s+\("
# ping6 emits "connect: Network is unreachable" only after resolving the hostname
# to an IPv6 address; it indicates a routing limitation, not a DNS lookup failure.
$ipv6Resolved = ($ipv6.Output -match "(?im)^PING\s+$([regex]::Escape($Ipv6Domain))\s*\(") -or
    ($ipv6.Output -match "(?i)connect:\s*Network is unreachable")
$script:results["Allowed domain resolved"] = $allowedResolved
$script:results["Allowed domain ICMP reachable"] = $allowedReachable
$script:results["Blocked domain DNS blocked"] = ($blockedNameFailure -and -not $blockedResolved)
$script:results["IPv6 domain AAAA lookup reached socket stage"] = $ipv6Resolved
# 3. IPv6 policy route (not general IPv6 Internet reachability)
$route = Test-DeviceCommand "IPv6 policy route to Cloudflare resolver" @("ip", "-6", "route", "get", "2606:4700:4700::1111")
$script:results["IPv6 policy route uses tun0"] = ($route.Output -match "dev tun0")
Add-ReportLine ""
Add-ReportLine "=== FUNCTION-AREA SUMMARY ==="
foreach ($entry in $script:results.GetEnumerator()) {
    $state = if ($entry.Value) { "PASS" } else { "NOT PASS / NEEDS REVIEW" }
    Add-ReportLine ("{0}: {1}" -f $state, $entry.Key)
}
Add-ReportLine ""
Add-ReportLine "Interpretation:"
Add-ReportLine "- Allowed-domain ICMP reply confirms DNS resolution plus IPv4 connectivity."
Add-ReportLine "- Unknown-host for the blocked domain is consistent with DNS blocking."
Add-ReportLine "- IPv6 AAAA resolution is separate from IPv6 packet reachability."
Add-ReportLine "- IPv6 route through tun0 does not prove Guardian relays general IPv6 traffic."
Add-ReportLine "- No settings are changed; the script does not install, uninstall, or toggle protection."
$script:lines | Set-Content -LiteralPath $logPath -Encoding UTF8
Compress-Archive -LiteralPath $logPath -DestinationPath $bundlePath -Force
Add-ReportLine ""
Add-ReportLine ("Report: " + $logPath)
Add-ReportLine ("ZIP: " + $bundlePath)
$failed = @($script:results.GetEnumerator() | Where-Object { -not $_.Value })
if ($failed.Count -gt 0) {
    Write-Warning ("Some checks need review: " + (($failed | ForEach-Object { $_.Key }) -join "; "))
    exit 2
}
exit 0
