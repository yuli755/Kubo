$procs = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -match 'SPMS-Server|cn\.hamm\.spms' }
foreach ($p in $procs) { Write-Output ("KILL " + $p.ProcessId); Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 2
$left = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'java' -and $_.CommandLine -match 'SPMS-Server|cn\.hamm\.spms' })
Write-Output ("REMAIN=" + $left.Count)
