param([Parameter(Mandatory=$true)][ValidateRange(1,65535)][int]$Port,[ValidateSet('backend','frontend')][string]$Role='backend')
$projectRoot = Split-Path -Parent $PSScriptRoot
$listener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $listener) { exit 1 }
$owner = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listener.OwningProcess)
$command = if ($owner) { $owner.CommandLine } else { '' }
if ($Role -eq 'backend' -and $command -and $command.Contains($projectRoot) -and ($command.Contains('com.wuxiaozhi.WuxiaozhiApplication') -or $command.Contains('wuxiaozhi-backend'))) { Write-Output ('Backend already running on port ' + $Port + ', PID=' + $owner.ProcessId); exit 0 }
if ($Role -eq 'frontend' -and $command -and $command.Contains((Join-Path $projectRoot 'frontend'))) { exit 0 }
Write-Error ('Port ' + $Port + ' belongs to another application. No process was stopped.')
exit 2
