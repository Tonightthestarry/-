try {
  $r = Invoke-WebRequest 'http://localhost:8088/api/streaming/recent' -UseBasicParsing -TimeoutSec 3
  Write-Output ('recent HTTP=' + $r.StatusCode)
} catch {
  Write-Output ('recent ERR=' + $_.Exception.Message)
}
try {
  $r = Invoke-WebRequest 'http://localhost:8088/api/streaming/sse' -UseBasicParsing -TimeoutSec 3 -Headers @{Accept='text/event-stream'}
  Write-Output ('sse HTTP=' + $r.StatusCode)
} catch {
  Write-Output ('sse ERR=' + $_.Exception.Message)
}
