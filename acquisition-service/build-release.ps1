$ErrorActionPreference = 'Stop'
$serviceRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$release = Join-Path $serviceRoot ('dist/acquisition-release-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $release -Force | Out-Null
foreach ($name in @('src','tools')) {
    $source = Join-Path $serviceRoot $name
    foreach ($file in Get-ChildItem -LiteralPath $source -Recurse -File | Where-Object { $_.Extension -eq '.py' }) {
        $relative = $file.FullName.Substring($serviceRoot.Length + 1)
        $destination = Join-Path $release $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $destination
    }
}
New-Item -ItemType Directory -Path (Join-Path $release 'config') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $serviceRoot 'config/service.json.example') -Destination (Join-Path $release 'config/service.json.example')
foreach ($name in @('run.py','requirements.txt','start.bat','start.ps1','start.sh','stop.ps1')) {
    Copy-Item -LiteralPath (Join-Path $serviceRoot $name) -Destination (Join-Path $release $name)
}
Copy-Item -LiteralPath (Join-Path (Split-Path -Parent $serviceRoot) 'contracts/acquisition-v1.openapi.yaml') -Destination (Join-Path $release 'acquisition-v1.openapi.yaml')
$readme = [IO.File]::ReadAllText((Join-Path $serviceRoot 'README.md')).Replace('../contracts/acquisition-v1.openapi.yaml', 'acquisition-v1.openapi.yaml')
[IO.File]::WriteAllText((Join-Path $release 'README.md'), $readme, (New-Object Text.UTF8Encoding($false)))
Compress-Archive -Path (Join-Path $release '*') -DestinationPath ($release + '.zip')
Write-Output ('Release: ' + $release + '.zip')
