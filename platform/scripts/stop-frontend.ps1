param([ValidateRange(0,65535)][int]$Port = 0)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($Port -eq 0) {
    $portsJson = & node.exe (Join-Path $projectRoot 'config/loadPorts.mjs') --json
    if ($LASTEXITCODE -ne 0) { throw 'Could not read config/application.yml.' }
    $Port = [int](($portsJson | ConvertFrom-Json).FRONTEND_PORT)
}
$logRoot = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$stopFile = Join-Path $logRoot ('.frontend-' + $Port + '.stop')
Set-Content -LiteralPath $stopFile -Value (Get-Date).ToString('o') -Encoding UTF8
$stateFile = Join-Path $logRoot ('frontend-service-' + $Port + '.json')
$state = $null
if (Test-Path -LiteralPath $stateFile) { $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json }
function Stop-OwnedProcess([int]$ProcessId,[string]$ExpectedPath,[switch]$Supervisor) {
    if ($ProcessId -le 0) { return }
    $candidate = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $ProcessId)
    if ($candidate -and $candidate.CommandLine -and $candidate.CommandLine.Contains($ExpectedPath)) {
        if ($Supervisor -and $candidate.CommandLine -notmatch ' -Worker ') { return }
        Stop-Process -Id $ProcessId -ErrorAction SilentlyContinue
    }
}
if ($state) {
    Stop-OwnedProcess -ProcessId $state.supervisorProcessId -ExpectedPath (Join-Path $PSScriptRoot 'start-frontend.ps1') -Supervisor
    Stop-OwnedProcess -ProcessId $state.processId -ExpectedPath (Join-Path $projectRoot 'frontend\scripts\run-vite.mjs')
}
$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    Stop-OwnedProcess -ProcessId $listener.OwningProcess -ExpectedPath (Join-Path $projectRoot 'frontend\scripts\run-vite.mjs')
    Stop-OwnedProcess -ProcessId $listener.OwningProcess -ExpectedPath (Join-Path $projectRoot 'frontend\node_modules\vite\bin\vite.js')
}
Add-Content -LiteralPath (Join-Path $logRoot ('frontend-supervisor-' + $Port + '.log')) -Encoding UTF8 -Value ((Get-Date).ToString('o') + ' INTENTIONAL_STOP requested; automatic restart disabled')
Write-Output ('Frontend stopped on port ' + $Port + '. Run scripts\start.bat to start it again.')
