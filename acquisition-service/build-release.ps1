param([string]$StagingDirectory)
$ErrorActionPreference = 'Stop'
$serviceRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$temporaryRoot = $null
if ($StagingDirectory) {
    $release = [IO.Path]::GetFullPath($StagingDirectory)
    if (Test-Path -LiteralPath $release) { throw 'Staging directory must not already exist.' }
} else {
    $temporaryRoot = Join-Path $serviceRoot ('.codex-tmp/acquisition-release-' + [Guid]::NewGuid().ToString('N'))
    $release = Join-Path $temporaryRoot 'acquisition-service'
}
try {
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
if (-not $StagingDirectory) {
    $outputDirectory = Join-Path (Split-Path -Parent $serviceRoot) 'dist'
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    & (Join-Path (Split-Path -Parent $serviceRoot) 'platform/scripts/filter-release-config.ps1') -ReleaseDirectory $release -ArchiveDirectory $outputDirectory -ServiceName 'acquisition-service'
    $releaseTime = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'China Standard Time').ToString('yyyyMMddHHmm')
    $zip = Join-Path $outputDirectory ('wuxiaozhi-' + $releaseTime + '.zip')
    if (Test-Path -LiteralPath $zip) { throw "A package already exists for this minute: $zip. Retry in the next minute." }
    $pendingZip = Join-Path $temporaryRoot 'package.zip'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($release, $pendingZip, [IO.Compression.CompressionLevel]::Optimal, $true)
    [IO.File]::Move($pendingZip, $zip)
    Write-Output ('Release: ' + $zip)
}
} finally {
    if ($temporaryRoot -and (Test-Path -LiteralPath $temporaryRoot)) {
        $allowedParent = [IO.Path]::GetFullPath((Join-Path $serviceRoot '.codex-tmp')) + [IO.Path]::DirectorySeparatorChar
        $resolvedTemporary = (Resolve-Path -LiteralPath $temporaryRoot).Path
        if (-not $resolvedTemporary.StartsWith($allowedParent, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid temporary build directory.' }
        Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
    }
}
