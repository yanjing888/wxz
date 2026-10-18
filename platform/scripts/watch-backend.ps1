param([ValidateRange(1,65535)][int]$Port=8082)
Write-Output ('Backend already running: http://127.0.0.1:' + $Port + '/')
Write-Output 'This is a status window. Closing it does not stop the backend. Use scripts\stop.bat to stop services.'
while ($true) {
    try { $response=Invoke-WebRequest -UseBasicParsing -Uri ('http://127.0.0.1:' + $Port + '/api/system/bench-camera') -TimeoutSec 3; Write-Output ((Get-Date).ToString('HH:mm:ss') + ' Backend HTTP ' + $response.StatusCode) }
    catch { Write-Output ((Get-Date).ToString('HH:mm:ss') + ' Backend unavailable: ' + $_.Exception.Message) }
    Start-Sleep -Seconds 10
}
