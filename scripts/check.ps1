try {
    $r = Invoke-WebRequest -Uri 'http://localhost:8088' -UseBasicParsing -TimeoutSec 5
    Write-Output ("OK status=" + $r.StatusCode)
} catch {
    Write-Output ("FAIL: " + $_.Exception.Message)
}

try {
    $m = Invoke-WebRequest -Uri 'http://localhost:27017' -UseBasicParsing -TimeoutSec 5
    Write-Output ("MongoDB HTTP status=" + $m.StatusCode)
} catch {
    Write-Output ("MongoDB FAIL: " + $_.Exception.Message)
}
