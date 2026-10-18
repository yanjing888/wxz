param([string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path)
$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
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
    try { & mvn.cmd -q '-DskipTests' package; if ($LASTEXITCODE -ne 0) { throw 'Backend package failed.' } } finally { Pop-Location }
    $release = Join-Path $ProjectRoot ('dist/lab-release-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path (Join-Path $release 'config') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'backend/target/wuxiaozhi-backend-1.0.0.jar') -Destination (Join-Path $release 'app.jar')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'config/lab.properties.example') -Destination (Join-Path $release 'config/lab.properties')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'docs/lab-deployment.md') -Destination (Join-Path $release 'DEPLOY.md')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'docs/learning.md') -Destination (Join-Path $release 'LEARNING.md')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'config/lab-schema-migration.sql') -Destination (Join-Path $release 'config/lab-schema-migration.sql')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'scripts/start-lab-release.bat') -Destination (Join-Path $release 'start.bat')
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'scripts/start-lab-release.sh') -Destination (Join-Path $release 'start.sh')
    New-Item -ItemType Directory -Path (Join-Path $release 'backend') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $ProjectRoot 'backend/scripts') -Destination (Join-Path $release 'backend/scripts') -Recurse
    $zip = $release + '.zip'
    Compress-Archive -Path (Join-Path $release '*') -DestinationPath $zip
    Write-Host "Release: $release"
    Write-Host "Archive: $zip"
} finally { Pop-Location }
