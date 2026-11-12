param([int]$Port = 8080, [string]$BindAddress = '0.0.0.0')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonExe = Join-Path $projectRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $pythonExe)) { throw 'Run scripts\SETUP.ps1 first.' }
Push-Location (Join-Path $projectRoot 'backend')
try {
    Write-Host "API: http://127.0.0.1:$Port/api/health"
    Write-Host "Interactive API: http://127.0.0.1:$Port/docs"
    & $pythonExe -m uvicorn app:app --host $BindAddress --port $Port
} finally { Pop-Location }
