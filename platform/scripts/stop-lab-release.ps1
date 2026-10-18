$ErrorActionPreference = 'Stop'
$jar = Join-Path (Resolve-Path -LiteralPath $PSScriptRoot).Path 'app.jar'
$owned = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'java.exe' -and $_.CommandLine -and $_.CommandLine.Contains($jar)
})
foreach ($process in $owned) { Stop-Process -Id $process.ProcessId -ErrorAction SilentlyContinue }
Write-Output ('Platform stopped: ' + $owned.Count + ' owned process(es).')
