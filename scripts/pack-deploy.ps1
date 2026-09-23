param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"
Set-Location $Root

Write-Host "==> Root: $Root"
Write-Host "==> Building backend jar ..."

Push-Location (Join-Path $Root "backend")
& mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    throw "Maven package failed."
}
Pop-Location

$jar = Join-Path $Root "backend\target\wuxiaozhi-backend-1.0.0.jar"
if (-not (Test-Path $jar)) {
    throw "Jar not found: $jar"
}

$distDir = Join-Path $Root "dist"
New-Item -ItemType Directory -Force -Path $distDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$zipName = "wuxiaozhi-deploy-$stamp.zip"
$zipPath = Join-Path $distDir $zipName

$stage = Join-Path $env:TEMP "wuxiaozhi-deploy-$stamp"
if (Test-Path $stage) {
    Remove-Item $stage -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $stage | Out-Null

function Copy-Tree {
    param([string]$Src, [string]$Dest)
    if (-not (Test-Path $Src)) { return }
    New-Item -ItemType Directory -Force -Path $Dest | Out-Null
    robocopy $Src $Dest /E /NFL /NDL /NJH /NJS /NC /NS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) {
        throw "Copy failed: $Src"
    }
}

Write-Host "==> Staging files ..."

Copy-Tree (Join-Path $Root "config") (Join-Path $stage "config")
Copy-Tree (Join-Path $Root "scripts") (Join-Path $stage "scripts")

Copy-Tree (Join-Path $Root "frontend\src") (Join-Path $stage "frontend\src")
Copy-Tree (Join-Path $Root "frontend\public") (Join-Path $stage "frontend\public")

$frontendFiles = @(
    "index.html",
    "package.json",
    "package-lock.json",
    "vite.config.js",
    "tailwind.config.js",
    "postcss.config.js",
    "clean_build.js"
)
foreach ($name in $frontendFiles) {
    $src = Join-Path $Root "frontend\$name"
    if (Test-Path $src) {
        $destDir = Join-Path $stage "frontend"
        New-Item -ItemType Directory -Force -Path $destDir | Out-Null
        Copy-Item $src (Join-Path $destDir $name) -Force
    }
}

$targetDir = Join-Path $stage "backend\target"
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
Copy-Item $jar (Join-Path $targetDir "wuxiaozhi-backend-1.0.0.jar") -Force
Copy-Tree (Join-Path $Root "backend\scripts") (Join-Path $stage "backend\scripts")

if (Test-Path (Join-Path $Root "README.md")) {
    Copy-Item (Join-Path $Root "README.md") (Join-Path $stage "README.md") -Force
}

$deployTemplate = Join-Path $PSScriptRoot "DEPLOY.template.txt"
if (Test-Path $deployTemplate) {
    Copy-Item $deployTemplate (Join-Path $stage "DEPLOY.txt") -Force
}

if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

Write-Host "==> Creating zip ..."
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $zipPath -Force
Remove-Item $stage -Recurse -Force

$sizeMb = [math]::Round((Get-Item $zipPath).Length / 1MB, 1)
Write-Host ""
Write-Host "Done: $zipPath (${sizeMb} MB)" -ForegroundColor Green
