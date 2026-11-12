$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonExe = Join-Path $projectRoot '.venv\Scripts\python.exe'
if (-not (Test-Path -LiteralPath $pythonExe)) {
    $portable = Get-ChildItem (Join-Path $projectRoot '.tools\python') -Filter python.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portable) {
        & $portable.FullName -m venv (Join-Path $projectRoot '.venv')
    } elseif ((Get-Command py -ErrorAction SilentlyContinue) -and ((& py -0 2>$null) -match '3\.12')) {
        # rapidocr-onnxruntime 1.4.4 chỉ hỗ trợ Python < 3.13.
        & py -3.12 -m venv (Join-Path $projectRoot '.venv')
    } elseif (Get-Command uv -ErrorAction SilentlyContinue) {
        # uv tự tải Python 3.12 nếu máy chưa có; --seed để venv có pip.
        & uv venv --seed --python 3.12 (Join-Path $projectRoot '.venv')
    } else {
        throw 'Install Python 3.12 x64 (python.org) or uv (https://docs.astral.sh/uv/), then run this script again.'
    }
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create Python virtual environment.' }
}
& $pythonExe -m ensurepip --upgrade
& $pythonExe -m pip install -r (Join-Path $projectRoot 'backend\requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed.' }
Write-Host 'Ready. Run: .\scripts\START.ps1'
