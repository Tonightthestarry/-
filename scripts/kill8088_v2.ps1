$conn = Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue
foreach ($c in $conn) {
  $pid_ = $c.OwningProcess
  Write-Output "killing $pid_"
  Stop-Process -Id $pid_ -Force -ErrorAction SilentlyContinue
}
Start-Sleep 3
$after = (Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue | Measure-Object).Count
Write-Output "after_kill_connections=$after"
