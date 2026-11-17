$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$configFile = Join-Path $root 'config/expert-relay.json'
if (-not (Test-Path -LiteralPath $configFile)) { exit 0 }
$config = Get-Content -LiteralPath $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
$script = Join-Path $PSScriptRoot 'expert-relay.py'
$listener = Get-NetTCPConnection -State Listen -LocalPort $config.localPort -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    $owner = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listener.OwningProcess)
    if ($owner.CommandLine -and $owner.CommandLine.Contains($script)) { Write-Output 'Expert relay already running.'; exit 0 }
    throw 'Expert relay port belongs to another application.'
}
$python = Join-Path $root '.expert-relay-venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $python)) { throw 'Install expert relay dependencies as described in docs/expert-assistant.md.' }
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Path $logs -Force | Out-Null
$worker = Start-Process -FilePath $python -ArgumentList ('-u "' + $script + '"') -WindowStyle Hidden -WorkingDirectory $root -PassThru -RedirectStandardOutput (Join-Path $logs 'expert-relay.log') -RedirectStandardError (Join-Path $logs 'expert-relay.err')
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Milliseconds 500
    $worker.Refresh()
    if ($worker.HasExited) { throw 'Expert relay failed. See logs/expert-relay.err.' }
    $ready = Get-NetTCPConnection -State Listen -LocalPort $config.localPort -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($ready) {
        $readyOwner = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $ready.OwningProcess)
        if (-not $readyOwner.CommandLine.Contains($script)) { $ready = $null }
    }
    if ($ready -and (Test-Path -LiteralPath (Join-Path $root 'config/expert.env'))) { Write-Output 'Expert relay ready.'; exit 0 }
}
Stop-Process -Id $worker.Id -ErrorAction SilentlyContinue
throw 'Timed out starting expert relay.'
