$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ANDROID_ENV.ps1')
Push-Location (Join-Path $projectRoot 'android')
try {
    & .\gradlew.bat assembleDebug lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    $dist = Join-Path $projectRoot 'dist'
    New-Item -ItemType Directory -Force $dist | Out-Null
    Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination (Join-Path $dist 'MoneyBot-debug.apk') -Force
    Write-Host "APK: $dist\MoneyBot-debug.apk"
} finally { Pop-Location }
