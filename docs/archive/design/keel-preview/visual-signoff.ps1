# Capture prod vs mock screenshots (Phase 8.4 automated sign-off helper).
# Prerequisites:
#   - Portal running (default http://127.0.0.1:8080)
#   - Mock: run serve.ps1 in another terminal (http://127.0.0.1:8769)
#   - Node.js + playwright: npm install -D playwright && npx playwright install chromium
#     (from repo root or this folder)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
Set-Location $here

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Error 'Node.js is required for visual-signoff.mjs'
    exit 1
}

node visual-signoff.mjs @args
exit $LASTEXITCODE
