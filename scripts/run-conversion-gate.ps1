# Generic conversion release-gate helper (T041 / T049).
# Site-agnostic: you pass YOUR Excel (and use YOUR app URL/creds in the portal).
#
# Usage:
#   pwsh -File scripts/run-conversion-gate.ps1 -SkipLive
#   pwsh -File scripts/run-conversion-gate.ps1 -ExcelPath path\to\your.xlsx -SkipLive
#
# This script does NOT call any website and does NOT embed product/site logic.
# Live NEW/UPDATE is started from the portal UI with the customer's base URL + login.

param(
    [switch]$SkipLive,
    [string]$ExcelPath = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ([string]::IsNullOrWhiteSpace($ExcelPath)) {
    $candidates = @(
        (Join-Path $root "src\test\resources\delivery\sample-checkout-e2e.xlsx"),
        (Join-Path $root "src\test\resources\delivery\sample-smoke.xlsx")
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $ExcelPath = $c; break }
    }
}

Write-Host "=== TestPilot conversion gate (site-agnostic) ===" -ForegroundColor Cyan
Write-Host "Repo: $root"
if ($ExcelPath -and (Test-Path $ExcelPath)) {
    Write-Host "Excel sample (optional fixture): $ExcelPath"
} else {
    Write-Host "Excel: (provide your own template-filled .xlsx in the portal)"
}
Write-Host ""
Write-Host "Checklist (any reachable app — no hard-coded host):"
Write-Host "  [ ] Ollama up; delivery.dry-run=false"
Write-Host "  [ ] Portal on :8080"
Write-Host "  [ ] NEW upload: your Excel + your base URL + your login"
Write-Host "  [ ] Status shows Phase1 step/retry messages"
Write-Host "  [ ] ir/*.json under delivery-work/<job>/"
Write-Host "  [ ] ZIP has docs/locator-map.json + AUTOMATION_SCORE.md"
Write-Host "  [ ] UPDATE reuses unchanged TCs"
Write-Host ""
Write-Host "Full procedure: docs/ops/e2e-conversion-gate.md"

if ($SkipLive) {
    Write-Host ""
    Write-Host "SkipLive set — not starting a browser job. Suggested unit tests:" -ForegroundColor Yellow
    Write-Host "  mvn -Dmaven.compiler.release=21 -Dtest=TcDraftStoreTest,PageClustererTest,LocatorMapBuilderTest,EmitPhaseMappingTest,StepIntentBinderTest,RequiredControlFillerTest,CodeWriterTest,TwoPhaseConversionGateTest test"
    exit 0
}

Write-Host ""
Write-Host "Live mode: run the checklist in the portal against YOUR application." -ForegroundColor Green
exit 0
