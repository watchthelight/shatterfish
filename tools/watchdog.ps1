# Watchdog for Shatterfish rig work: stops every java.exe when the machine is in danger.
# Trips at more than 32 java processes, commit charge over 85%, or less than 8 GB free on C:.
# 26+ java made explorer.exe hang on 2026-10-29; rig output filled C: on 2026-11-09.
# The log goes to $env:SF_STATE (default <temp>\shatterfish-ops) unless -Log is given.
param([string]$Log)
if (-not $Log) {
  $dir = if ($env:SF_STATE) { $env:SF_STATE } else { Join-Path $env:TEMP 'shatterfish-ops' }
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $Log = Join-Path $dir 'watchdog.log'
}
while ($true) {
  $j = @(Get-Process java -ErrorAction SilentlyContinue)
  $o = Get-CimInstance Win32_OperatingSystem
  $commit = [int](100 * ($o.TotalVirtualMemorySize - $o.FreeVirtualMemory) / $o.TotalVirtualMemorySize)
  $free = [int]((Get-PSDrive C).Free / 1GB)
  $why = $null
  if ($j.Count -gt 32) { $why = "java=$($j.Count)" }
  elseif ($commit -gt 85) { $why = "commit=$commit%" }
  elseif ($free -lt 8 -and $j.Count -gt 0) { $why = "disk=${free}GB" }
  if ($why) {
    $line = "{0} TRIP {1}: stopping {2} java" -f (Get-Date -Format s), $why, $j.Count
    Add-Content -Path $Log -Value $line; Write-Output $line
    $j | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 120
  }
  Start-Sleep -Seconds 20
}
