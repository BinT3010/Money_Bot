$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonExe = Join-Path $projectRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $pythonExe)) {
    $portable = Get-ChildItem (Join-Path $projectRoot '.tools\python') -Filter python.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portable) {
        & $portable.FullName -m venv (Join-Path $projectRoot '.venv')
    } elseif (Get-Command py -ErrorAction SilentlyContinue) {
        & py -3.13 -m venv (Join-Path $projectRoot '.venv')
    } else {
        throw 'Install Python 3.13 x64 (python.org), then run this script again.'
    }
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create Python virtual environment.' }
}
& $pythonExe -m ensurepip --upgrade
& $pythonExe -m pip install -r (Join-Path $projectRoot 'backend\requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
Write-Host 'Ready. Run: .\scripts\START.ps1'
