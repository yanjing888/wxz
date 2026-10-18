$ErrorActionPreference = 'Stop'
$serviceRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$configPath = Join-Path $serviceRoot 'config/service.json'
if (-not (Test-Path -LiteralPath $configPath)) {
    Copy-Item -LiteralPath (Join-Path $serviceRoot 'config/service.json.example') -Destination $configPath
}
$config = [IO.File]::ReadAllText($configPath) | ConvertFrom-Json
$port = if ($config.port) { [int]$config.port } else { 8765 }
$listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    $owner = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listener.OwningProcess)
    if ($owner.Name -match '^python(w)?\.exe$' -and $owner.CommandLine -and $owner.CommandLine.Contains((Join-Path $serviceRoot 'run.py'))) {
        Write-Output ('Acquisition service already running on port ' + $port + ', PID=' + $owner.ProcessId)
        exit 0
    }
    throw ('Port ' + $port + ' belongs to another application. No process was stopped.')
}
$python = Join-Path $serviceRoot '.venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    throw 'Install dependencies in acquisition-service first: python -m venv .venv; .venv\Scripts\python.exe -m pip install -r requirements.txt'
}
Push-Location $serviceRoot
try { & $python (Join-Path $serviceRoot 'run.py') --config $configPath; exit $LASTEXITCODE }
finally { Pop-Location }
