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
    # Some Windows CLI builds append a human-readable notice to stdout. Extract
    # exactly the first balanced JSON object instead of feeding trailing text to ConvertFrom-Json.
    $start = $stdout.IndexOf('{')
    if ($start -lt 0) { throw 'Tailscale status 출력에서 JSON 객체를 찾을 수 없습니다.' }
    $depth = 0; $inString = $false; $escaped = $false; $end = -1
    for ($i = $start; $i -lt $stdout.Length; $i++) {
      $ch = $stdout[$i]
      if ($inString) {
        if ($escaped) { $escaped = $false; continue }
        if ($ch -eq '\\') { $escaped = $true; continue }
        if ($ch -eq '"') { $inString = $false }
        continue
      }
      if ($ch -eq '"') { $inString = $true; continue }
      if ($ch -eq '{') { $depth++ }
      elseif ($ch -eq '}') {
        $depth--
        if ($depth -eq 0) { $end = $i; break }
      }
    }
    if ($end -lt $start) { throw 'Tailscale status JSON 객체가 끝나기 전에 출력이 종료되었습니다.' }
    $json = $stdout.Substring($start, $end - $start + 1)
    try { $status = $json | ConvertFrom-Json -ErrorAction Stop }
    catch { throw "Tailscale status JSON을 해석하지 못했습니다: $($_.Exception.Message)" }
    if ($null -eq $status.Self) { throw 'Tailscale status JSON에 Self 정보가 없습니다.' }
    return $status
  } finally { $process.Dispose() }
}
