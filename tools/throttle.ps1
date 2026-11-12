# Keep every java.exe at Idle priority (so the desktop stays responsive) on all logical cores (Bash: max CPU, 2026-11-09).
# Run it hidden in the background:
#   Start-Process pwsh -WindowStyle Hidden -ArgumentList '-NoProfile','-File','tools\throttle.ps1'
$n = [Environment]::ProcessorCount
$mask = [IntPtr]([Math]::Pow(2,$n)-1)
while ($true) {
  Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    try { if ($_.PriorityClass -ne 'Idle') { $_.PriorityClass = 'Idle' }; if ($_.ProcessorAffinity -ne $mask) { $_.ProcessorAffinity = $mask } } catch {}
  }
  Start-Sleep -Seconds 10
}
