$ErrorActionPreference = 'Stop'
$serviceRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$entry = Join-Path $serviceRoot 'run.py'
$owned = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^python(w)?\.exe$' -and $_.CommandLine -and $_.CommandLine.Contains($entry)
})
foreach ($process in $owned) { Stop-Process -Id $process.ProcessId -ErrorAction SilentlyContinue }
Write-Output ('Acquisition service stopped: ' + $owned.Count + ' owned process(es).')
