param([Parameter(Mandatory=$true)][string]$Jar, [int]$JavaRelease = 17)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$script:checkedClasses = 0
$script:maximumMajor = 0
$maximumAllowed = $JavaRelease + 44
function Test-Archive([IO.Compression.ZipArchive]$Archive, [string]$Prefix) {
    foreach ($entry in $Archive.Entries) {
        if ($entry.FullName.EndsWith('.class')) {
            # The JVM ignores multi-release variants intended for newer Java versions.
            if ($entry.FullName -match '^META-INF/versions/(\d+)/' -and [int]$Matches[1] -gt $JavaRelease) { continue }
            $stream = $entry.Open()
            try {
                $header = New-Object byte[] 8
                $read = 0
                while ($read -lt 8) {
                    $count = $stream.Read($header, $read, 8 - $read)
                    if ($count -eq 0) { throw ('Invalid class file: ' + $Prefix + $entry.FullName) }
                    $read += $count
                }
                $major = [int]$header[6] * 256 + [int]$header[7]
                if ($major -gt $maximumAllowed) { throw ('Java ' + $JavaRelease + ' incompatible class (major ' + $major + '): ' + $Prefix + $entry.FullName) }
                $script:checkedClasses++
                $script:maximumMajor = [Math]::Max($script:maximumMajor, $major)
            } finally { $stream.Dispose() }
        } elseif ($entry.FullName.StartsWith('BOOT-INF/lib/') -and $entry.FullName.EndsWith('.jar')) {
            $stream = $entry.Open()
            $memory = New-Object IO.MemoryStream
            try {
                $stream.CopyTo($memory)
                $memory.Position = 0
                $nested = New-Object IO.Compression.ZipArchive($memory, [IO.Compression.ZipArchiveMode]::Read, $true)
                try { Test-Archive $nested ($entry.FullName + '!/') } finally { $nested.Dispose() }
            } finally { $stream.Dispose(); $memory.Dispose() }
        }
    }
}
$path = (Resolve-Path -LiteralPath $Jar).Path
$archive = [IO.Compression.ZipFile]::OpenRead($path)
try { Test-Archive $archive '' } finally { $archive.Dispose() }
if ($script:checkedClasses -eq 0) { throw 'No Java classes found in release JAR.' }
Write-Output ('Java ' + $JavaRelease + ' compatibility verified: ' + $script:checkedClasses + ' classes; maximum class major version ' + $script:maximumMajor + '.')
