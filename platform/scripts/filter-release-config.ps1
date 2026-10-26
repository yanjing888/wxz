param(
    [Parameter(Mandatory=$true)][string]$ReleaseDirectory,
    [Parameter(Mandatory=$true)][string]$ArchiveDirectory,
    [Parameter(Mandatory=$true)][string]$ServiceName
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$releaseRoot = (Resolve-Path -LiteralPath $ReleaseDirectory).Path
$configRoot = Join-Path $releaseRoot 'config'
if (-not (Test-Path -LiteralPath $configRoot)) { return }
$archives = @(Get-ChildItem -LiteralPath $ArchiveDirectory -Filter '*.zip' -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)
foreach ($file in Get-ChildItem -LiteralPath $configRoot -File -Recurse) {
    $relative = $file.FullName.Substring($releaseRoot.Length + 1).Replace('\', '/')
    $entryName = $ServiceName + '/' + $relative
    foreach ($archive in $archives) {
        $zip = [IO.Compression.ZipFile]::OpenRead($archive.FullName)
        $found = $false
        try {
            $entry = $zip.Entries | Where-Object { $_.FullName.Replace('\', '/') -eq $entryName } | Select-Object -First 1
            if (-not $entry) { continue }
            $found = $true
            $stream = $entry.Open()
            $hash = [Security.Cryptography.SHA256]::Create()
            try { $previous = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '') }
            finally { $stream.Dispose(); $hash.Dispose() }
            if ($previous -eq (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash) {
                Remove-Item -LiteralPath $file.FullName
                Write-Output ('Unchanged config omitted: ' + $entryName)
            }
        } finally { $zip.Dispose() }
        # A code-only update may omit config; use the most recent archive containing this file.
        if ($found) { break }
    }
}
if (-not (Get-ChildItem -LiteralPath $configRoot -Force)) { Remove-Item -LiteralPath $configRoot }
