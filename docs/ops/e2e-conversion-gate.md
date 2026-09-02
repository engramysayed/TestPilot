# E2E conversion gate (NEW + UPDATE) — site-agnostic

**Purpose**: Release criteria T041 / T049 — prove two-phase conversion with a real browser + Ollama against **whatever application you choose**. No product code is tied to a specific demo site.

## Prerequisites

- JDK 21, Maven, browser driver as used by TAF  
- Ollama running with model from `application.properties`  
- `delivery.dry-run=false`  
- Portal started (`start-portal.bat` or `mvn spring-boot:run`)  
- Excel filled with **our template** for the app under test  

## Gate A — NEW (T041)

1. Sign in → create/select project.  
2. Upload Excel; enter **that app’s** base URL + authoring credentials; mode **NEW**.  
3. Watch `/status`: `Phase1 prove … step …` and optional `retry`.  
4. When COMPLETED:
   - Download ZIP  
   - Confirm `delivery-work/<folder>/ir/*.json` exists (one per TC)  
   - Confirm ZIP has `docs/locator-map.json`, `docs/AUTOMATION_SCORE.md`  
   - Page classes should cluster by URL path (not one god `Page` when screens differ)  
   - Partial failures keep proven steps in Todo tests with `STOPPED HERE`  
5. **Pass**: job completes; every TC is PASSED / PARTIAL / TODO (no silent drop).

## Gate B — UPDATE (T049)

1. Same project; Excel with one new/changed `TC_ID`; mode **UPDATE**.  
2. Unchanged TCs stay `REUSED`; only changed TCs re-prove.  
3. Download versioned ZIP; prior passed tests remain.

## Helper script

```powershell
pwsh -File scripts/run-conversion-gate.ps1 -SkipLive
# Optional: -ExcelPath path\to\your.xlsx
```

The script only prints the checklist / points at unit tests. It does **not** hard-code a website or credentials.
