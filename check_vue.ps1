Write-Output "=== 测试 / 首页 ==="
$r = Invoke-WebRequest -Uri 'http://localhost:8088/' -UseBasicParsing -MaximumRedirection 0 -TimeoutSec 5 -ErrorAction SilentlyContinue
if ($r) {
    Write-Output ("StatusCode: " + $r.StatusCode)
    Write-Output ("Content-Type: " + $r.Headers['Content-Type'])
    Write-Output ("Location: " + $r.Headers['Location'])
} else { Write-Output "首页无响应" }

Write-Output ""
Write-Output "=== 测试 /index.vue ==="
$r2 = Invoke-WebRequest -Uri 'http://localhost:8088/index.vue' -UseBasicParsing -TimeoutSec 5 -ErrorAction SilentlyContinue
if ($r2) {
    Write-Output ("StatusCode: " + $r2.StatusCode)
    Write-Output ("Content length: " + $r2.Content.Length)
} else { Write-Output "index.vue 无响应" }

Write-Output ""
Write-Output "=== 测试 /index.html (旧版 fallback) ==="
$r3 = Invoke-WebRequest -Uri 'http://localhost:8088/index.html' -UseBasicParsing -TimeoutSec 5 -ErrorAction SilentlyContinue
if ($r3) {
    Write-Output ("StatusCode: " + $r3.StatusCode)
    Write-Output ("Content length: " + $r3.Content.Length)
} else { Write-Output "index.html 无响应" }
