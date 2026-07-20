Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
  $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)" -ErrorAction SilentlyContinue).CommandLine
  if ($cmd -and $cmd -like '*banaly2*') {
    Write-Output "killing PID=$($_.Id) $cmd"
    Stop-Process -Id $_.Id -Force
  }
}
Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue | ForEach-Object {
  Write-Output "still on 8088: PID=$($_.OwningProcess)"
}
