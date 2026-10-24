param (
    [string]$Version = "1.0.0",
    [string]$GithubRepo = "cleanpilot/cleanpilot",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " CleanPilot Automated Multi-Platform Release Engine" -ForegroundColor Cyan
Write-Host " Version: $Version | Repo: $GithubRepo" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Build via Gradle
Write-Host "`n[1/3] Packaging via Gradle..." -ForegroundColor Yellow
$hasWix = (Get-Command candle.exe -ErrorAction SilentlyContinue) -ne $null

$gradleArgs = @()
if (-not $SkipTests) {
    $gradleArgs += "test"
}
$gradleArgs += "bootJar"
$gradleArgs += "packageAppImage"

if ($hasWix) {
    Write-Host "  [+] WiX Toolset detected. Native MSI installer will be packaged." -ForegroundColor Green
    $gradleArgs += "packageMsi"
} else {
    Write-Warning "WiX Toolset (candle.exe) not found on local PATH."
    Write-Host "  [*] Building portable standalone application image and zip archive." -ForegroundColor DarkCyan
    Write-Host "  [*] (Note: WiX is pre-installed on GitHub Actions windows-latest runners for cloud CI/CD releases)." -ForegroundColor DarkGray
}

./gradlew @gradleArgs

if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle build failed."
    exit 1
}

# 2. Package Portable Zip
Write-Host "`n[2/3] Zipping Portable Distribution..." -ForegroundColor Yellow
$distDir = "build/dist"
$zipPath = "$distDir/CleanPilot-$Version-windows-x64-portable.zip"
$appImageDir = "$distDir/CleanPilot"

if (Test-Path $appImageDir) {
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Compress-Archive -Path $appImageDir -DestinationPath $zipPath -Force
    Write-Host "Created: $zipPath" -ForegroundColor Green
}

# 3. Synchronize Manifests via release.py
Write-Host "`n[3/3] Synchronizing Manifests with Hashes..." -ForegroundColor Yellow
python scripts/release.py --version $Version --repo $GithubRepo --dist $distDir

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " Multi-Platform Release v$Version Ready!" -ForegroundColor Green
Write-Host " To deploy automatically via GitHub Actions:" -ForegroundColor Cyan
Write-Host "   git add ." -ForegroundColor White
Write-Host "   git commit -m 'chore(release): v$Version'" -ForegroundColor White
Write-Host "   git tag v$Version" -ForegroundColor White
Write-Host "   git push origin v$Version" -ForegroundColor White
Write-Host "==========================================================" -ForegroundColor Green
