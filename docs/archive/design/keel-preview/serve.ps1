# Serve the Keel design preview on loopback only.
$ErrorActionPreference = 'Stop'
$port = 8769
$root = $PSScriptRoot

Write-Host "Keel design preview at http://127.0.0.1:$port/"
Write-Host "Press Ctrl+C to stop."

if (Get-Command python -ErrorAction SilentlyContinue) {
    Set-Location $root
    python -m http.server $port --bind 127.0.0.1
    exit $LASTEXITCODE
}

if (Get-Command py -ErrorAction SilentlyContinue) {
    Set-Location $root
    py -m http.server $port --bind 127.0.0.1
    exit $LASTEXITCODE
}

Write-Error 'Python is required. Install Python 3 and rerun serve.ps1, or open index.html directly in a browser.'
exit 1
