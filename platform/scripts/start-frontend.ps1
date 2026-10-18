param(
    [ValidateRange(0,65535)][int]$Port = 0,
    [ValidateSet('127.0.0.1','0.0.0.0')][string]$BindAddress = '0.0.0.0',
    [switch]$Follow
)
# Compatibility entry point. Keep the dev server attached to this console.
# Do not create WMI workers or depend on a second process to keep Vite alive.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ($Port -eq 0) {
    $Port = 5174
    foreach ($line in Get-Content -LiteralPath (Join-Path $projectRoot 'config\ports.env')) {
        if ($line -match '^FRONTEND_PORT=(\d+)\s*$') { $Port = [int]$Matches[1] }
    }
}
$checker = Join-Path $PSScriptRoot 'check-service-owner.ps1'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Port $Port -Role frontend
$ownershipResult = $LASTEXITCODE
if ($ownershipResult -eq 0) {
    Write-Output ('Frontend already running at http://localhost:' + $Port + '/')
    exit 0
}
if ($ownershipResult -ne 1) { exit $ownershipResult }
$nodePath = (Get-Command node.exe -ErrorAction Stop).Source
$entry = Join-Path $projectRoot 'frontend\scripts\run-vite.mjs'
$env:CI = 'true'
$nodeMajor = [int](& $nodePath -p "process.versions.node.split('.')[0]")
$stabilityFlags = @()
if ($nodeMajor -ge 22) { $stabilityFlags = @('--no-maglev') }
$stopFile = Join-Path $projectRoot ('logs\.frontend-' + $Port + '.stop')
if (Test-Path -LiteralPath $stopFile) { Remove-Item -LiteralPath $stopFile -Force }
Push-Location (Join-Path $projectRoot 'frontend')
try {
    Write-Output ('Frontend running in this console: http://localhost:' + $Port + '/')
    Write-Output 'Keep this window open (it may be minimized). Closing it stops the frontend.'
    for ($retry=0; $retry -le 3; $retry++) {
        & $nodePath @stabilityFlags $entry --host $BindAddress --port $Port --strictPort
        $result = $LASTEXITCODE
        Add-Content -LiteralPath (Join-Path $projectRoot 'logs\frontend-launcher.log') -Value ((Get-Date).ToString('o') + ' EXIT code=' + $result + ' retry=' + $retry)
        if ((Test-Path -LiteralPath $stopFile) -or $result -notin @(-1073740791,-1073741819) -or $retry -eq 3) { break }
        Write-Output 'Native runtime crash. Restarting frontend in 2 seconds...'
        Start-Sleep -Seconds 2
        if (Test-Path -LiteralPath $stopFile) { break }
    }
} finally { Pop-Location }
exit $result
