# Dot-source file này: nạp JDK/SDK portable nếu có, không đổi cấu hình Windows toàn cục.
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdk = Get-ChildItem (Join-Path $projectRoot '.tools\jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17, or install Android Studio and select JDK 17.' }
$portableSdk = Join-Path $projectRoot '.tools\android-sdk'
if (Test-Path -LiteralPath $portableSdk) { $env:ANDROID_HOME = $portableSdk }
if (-not $env:ANDROID_HOME) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -LiteralPath $defaultSdk) { $env:ANDROID_HOME = $defaultSdk }
}
if (-not $env:ANDROID_HOME) { throw 'Install Android SDK platform 35 and Build Tools 34.0.0 in Android Studio.' }
$sdkPath = $env:ANDROID_HOME.Replace('\','/')
Set-Content -LiteralPath (Join-Path $projectRoot 'android\local.properties') -Value "sdk.dir=$sdkPath" -Encoding ascii
