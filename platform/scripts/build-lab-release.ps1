param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$StagingDirectory
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
$temporaryRoot = $null
Push-Location $ProjectRoot
try {
    & npm.cmd --prefix frontend run build -- --base=/app/
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    # 只清理本项目生成的静态资源，避免旧 hash 资源混入发行包。
    $staticPath = [IO.Path]::GetFullPath((Join-Path $ProjectRoot 'backend/src/main/resources/static/app'))
    $expectedPath = [IO.Path]::GetFullPath((Join-Path $ProjectRoot 'backend/src/main/resources/static/app'))
    if ($staticPath -ne $expectedPath -or -not $staticPath.StartsWith($ProjectRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Invalid static asset path.' }
    if (Test-Path -LiteralPath $staticPath) { Remove-Item -LiteralPath $staticPath -Recurse -Force }
    New-Item -ItemType Directory -Path $staticPath -Force | Out-Null
    Copy-Item -Path (Join-Path $ProjectRoot 'frontend/dist/*') -Destination $staticPath -Recurse
    Push-Location (Join-Path $ProjectRoot 'backend')
    try { & mvn.cmd -q clean '-DskipTests' package; if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' } } finally { Pop-Location }
    & (Join-Path $ProjectRoot 'scripts/assert-java-release.ps1') -Jar (Join-Path $ProjectRoot 'backend/target/wuxiaozhi-backend-1.0.0.jar') -JavaRelease 17
    if ($StagingDirectory) {
        $release = [IO.Path]::GetFullPath($StagingDirectory)
        if (Test-Path -LiteralPath $release) { throw 'Staging directory must not already exist.' }
    } else {
        $temporaryRoot = Join-Path $ProjectRoot ('.codex-tmp/platform-release-' + [Guid]::NewGuid().ToString('N'))
        $release = Join-Path $temporaryRoot 'platform'
    }
    New-Item -ItemType Directory -Path (Join-Path $release 'config') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'backend/target/wuxiaozhi-backend-1.0.0.jar') -Destination (Join-Path $release 'app.jar')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'config/application.yml.example') -Destination (Join-Path $release 'config/application.yml')
    if (Test-Path -LiteralPath (Join-Path $ProjectRoot 'config/dify.env')) {
        Copy-Item -LiteralPath (Join-Path $ProjectRoot 'config/dify.env') -Destination (Join-Path $release 'config/dify.env')
    }
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'config/lab-schema-migration.sql') -Destination (Join-Path $release 'config/lab-schema-migration.sql')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'scripts/start-lab-release.bat') -Destination (Join-Path $release 'start.bat')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'scripts/start-lab-release.sh') -Destination (Join-Path $release 'start.sh')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'scripts/stop-lab-release.ps1') -Destination (Join-Path $release 'stop.ps1')
    if (-not $StagingDirectory) {
        $outputDirectory = Join-Path $ProjectRoot 'dist'
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
        $releaseTime = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'China Standard Time').ToString('yyyyMMddHHmm')
        $zip = Join-Path $outputDirectory ('wuxiaozhi-' + $releaseTime + '.zip')
        if (Test-Path -LiteralPath $zip) { throw "A package already exists for this minute: $zip. Retry in the next minute." }
        $pendingZip = Join-Path $temporaryRoot 'package.zip'
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [IO.Compression.ZipFile]::CreateFromDirectory($release, $pendingZip)
        [IO.File]::Move($pendingZip, $zip)
        Write-Host "Archive: $zip"
    }
} finally {
    Pop-Location
    if ($temporaryRoot -and (Test-Path -LiteralPath $temporaryRoot)) {
        $allowedParent = [IO.Path]::GetFullPath((Join-Path $ProjectRoot '.codex-tmp')) + [IO.Path]::DirectorySeparatorChar
        $resolvedTemporary = (Resolve-Path -LiteralPath $temporaryRoot).Path
        if (-not $resolvedTemporary.StartsWith($allowedParent, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid temporary build directory.' }
        Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
    }
}
