# usage: sh tools/gradle-gate.sh ; blocks until the machine has room for one more gradle job.
# State (lock, cap file) lives in $SF_STATE (default <temp>/shatterfish-ops), see tools/ops-env.sh.
# Wait until the machine has room for one more gradle job: fewer than $(cat $SF_STATE/gw.cap) gradle-wrapper, read on every check so the cap changes without restarting waiters (low-CPU mode, Bash 2026-11-07: CPU is capped by the 4-core pin in throttle.ps1, so 2 jobs share 4 cores; 4 rig jobs always reach 26 java: 22:14 on 2026-10-30; 4 reached 25 java and 82% on 2026-10-30 06:34 under a heavier idle baseline) processes, fewer than 16 java.exe in all,
# at least 8 GB RAM free and commit charge under 72% (idle baseline is 62-64% from non-gradle apps on 2026-10-30; three rig jobs reached 82% on 2026-10-29 21:11; watchdog trips at 85). 26+ java made explorer.exe hang on 2026-10-29; keep well under that.
# One waiter checks at a time, and the lock is held 180 s after the gate opens so the caller's job shows in the next check.
# The cap is 2 when gw.cap is absent; write a number into $SF_STATE/gw.cap to change it (8 on 2026-11-12, while no one else used the machine).
. "$(dirname "$0")/ops-env.sh"
S=$SF_STATE
st(){ powershell -NoProfile -c "\$j=@(Get-CimInstance Win32_Process -Filter \"Name='java.exe'\"); \$w=@(\$j | Where-Object { \$_.CommandLine -match 'gradle-wrapper.jar' }).Count; \$o=Get-CimInstance Win32_OperatingSystem; '{0} {1} {2} {3}' -f \$w, \$j.Count, [int](\$o.FreePhysicalMemory/1MB), [int](100*(\$o.TotalVirtualMemorySize-\$o.FreeVirtualMemory)/\$o.TotalVirtualMemorySize)" | tr -d '\r'; }
until mkdir $S/gw.lock 2>/dev/null; do
  # A lock older than 5 minutes belongs to a killed waiter.
  [ -n "$(find $S/gw.lock -maxdepth 0 -mmin +5 2>/dev/null)" ] && rmdir $S/gw.lock 2>/dev/null
  sleep 15
done
while :; do set -- $(st); [ $# -eq 4 ] && [ "$1" -lt "$(cat $S/gw.cap 2>/dev/null || echo 2)" ] && [ "$2" -lt 16 ] && [ "$3" -ge 8 ] && [ "$4" -lt 72 ] && break; sleep 30; done
( sleep 180; rmdir $S/gw.lock 2>/dev/null ) &
