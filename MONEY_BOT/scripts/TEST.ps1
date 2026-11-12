param([switch]$IncludeAndroid)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonExe = Join-Path $projectRoot '.venv\Scripts\python.exe'
Push-Location (Join-Path $projectRoot 'backend')
try {
    & $pythonExe -m pytest -q
    if ($LASTEXITCODE -ne 0) { throw 'Python tests failed.' }
} finally { Pop-Location }
if ($IncludeAndroid) {
    . (Join-Path $PSScriptRoot 'ANDROID_ENV.ps1')
    $testRoot = Join-Path $env:TEMP ('moneybot-tests-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force $testRoot | Out-Null
    $oldData = $env:MONEYBOT_DATA
    $env:MONEYBOT_DATA = $testRoot
    $server = $null
    try {
        # Cổng riêng; không kiểm thử trên database đang dùng thật.
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 18080)
        try { $listener.Start() } finally { $listener.Stop() }
        $server = Start-Process -FilePath $pythonExe -ArgumentList '-m','uvicorn','app:app','--host','127.0.0.1','--port','18080' -WorkingDirectory (Join-Path $projectRoot 'backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $testRoot 'server.log') -RedirectStandardError (Join-Path $testRoot 'server-error.log')
        $ready = $false
        for ($i=0; $i -lt 30; $i++) {
            try { $health = Invoke-RestMethod 'http://127.0.0.1:18080/api/health' -TimeoutSec 1; if ($health.status -eq 'ok') { $ready=$true; break } } catch { Start-Sleep -Milliseconds 500 }
        }
        if (-not $ready) { throw "Test server failed. See $testRoot" }
        Push-Location (Join-Path $projectRoot 'android')
        try {
            & .\gradlew.bat testDebugUnitTest --rerun-tasks --console=plain
            if ($LASTEXITCODE -ne 0) { throw 'Java integration tests failed.' }
        } finally { Pop-Location }
    } finally {
        if ($server -and -not $server.HasExited) { Stop-Process -Id $server.Id }
        $env:MONEYBOT_DATA = $oldData
    }
}
