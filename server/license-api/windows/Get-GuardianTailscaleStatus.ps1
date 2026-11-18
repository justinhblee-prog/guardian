function Get-GuardianTailscaleStatus {
  [CmdletBinding()]
  param([Parameter(Mandatory)][string]$Executable)
  $psi = [Diagnostics.ProcessStartInfo]::new()
  $psi.FileName = $Executable
  [void]$psi.ArgumentList.Add('status')
  [void]$psi.ArgumentList.Add('--json')
  $psi.UseShellExecute = $false
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true
  $process = [Diagnostics.Process]::new()
  $process.StartInfo = $psi
  try {
    if (-not $process.Start()) { throw 'Tailscale status 프로세스를 시작하지 못했습니다.' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) {
      $detail = if ([string]::IsNullOrWhiteSpace($stderr)) { $stdout.Trim() } else { $stderr.Trim() }
      throw "Tailscale status 실행 실패 (exit $($process.ExitCode)): $detail"
    }

    # Windows CLI builds may prepend or append human-readable notices. Do not
    # assume the first opening brace belongs to JSON: notices can contain braces.
    # Try each object boundary and accept only a complete JSON object with Self.
    $lastParseError = $null
    for ($start = 0; $start -lt $stdout.Length; $start++) {
      if ([int][char]$stdout[$start] -ne 123) { continue } # {
      $depth = 0
      $inString = $false
      $escaped = $false
      for ($i = $start; $i -lt $stdout.Length; $i++) {
        $code = [int][char]$stdout[$i]
        if ($inString) {
          if ($escaped) { $escaped = $false; continue }
          if ($code -eq 92) { $escaped = $true; continue } # backslash
          if ($code -eq 34) { $inString = $false } # quote
          continue
        }
        if ($code -eq 34) { $inString = $true; continue }
        if ($code -eq 123) { $depth++; continue } # {
        if ($code -eq 125) { # }
          $depth--
          if ($depth -eq 0) {
            $json = $stdout.Substring($start, $i - $start + 1)
            try {
              $candidate = ConvertFrom-Json -InputObject $json -ErrorAction Stop
              if ($null -ne $candidate -and $null -ne $candidate.Self) {
                return $candidate
              }
            } catch {
              $lastParseError = $_.Exception.Message
            }
            break
          }
        }
      }
    }

    if ($null -ne $lastParseError) {
      throw "Tailscale status JSON 객체를 찾았지만 해석하지 못했습니다: $lastParseError"
    }
    $tail = $stdout
    if ($tail.Length -gt 240) { $tail = $tail.Substring($tail.Length - 240) }
    $tail = ($tail -replace '[\r\n\t]+', ' ').Trim()
    throw "Tailscale status 출력에서 Self를 포함하는 완전한 JSON 객체를 찾지 못했습니다. stdout 길이: $($stdout.Length), stderr 길이: $($stderr.Length), 출력 끝부분: $tail"
  } finally { $process.Dispose() }
}
