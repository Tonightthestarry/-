$conn = Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue
$ids = $conn | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($id in $ids) {
  Write-Output "killing PID=$id"
  Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
}
Start-Sleep 2
Write-Output "done"
