# Handoff — Guide + Pricing backend (covers + draft add-on seeds)

**Status:** GREEN  
**Tip SHA:** TIP_SHA_PLACEHOLDER  
**Base:** c93aa6d (descendant)  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\guide-pricing-20261102-2115`  
**Tree:** Keel live tip `D:\Work\projects\Keel\keel\TestPilot` (Keel ≠ Sonic)  
**Constraint note:** Credits only — no USD / dollar / money language. Costs are integer credit units in `service_prices`.

## What changed

### 1) Core covers refreshed (`ServicePriceService`)
Plain-English `detail` / `unitLabel` for GENERATE / EXECUTE / AUTOMATE / HUNT (each ≤240 chars).

| Code | Notes |
|------|--------|
| GENERATE | Folded Generate-from-knowledge into covers. No separate GFK row (no JobKind for GFK; GENERATE_BATCH / GENERATE_COMPARE → GENERATE). |
| EXECUTE | Per-case run credits; evidence in everyday words (failure replay, heal Accept/Reject, flaky tags). |
| AUTOMATE | Per-case build credits; stronger locators that heal + clearer steps (no W1–W3 jargon). |
| HUNT | Step-based wording kept. |

Already-seeded DB rows whose `covers` still equal the old canned detail string are refreshed via `refreshLegacyCovers` (same idea as `renameLegacy`). Owner-customized covers are left alone.

### 2) Draft add-on `ServicePrice` rows (display + admin-editable only)

Seeded by `ensureAddon` inside `ensureSeeded` — insert **only if missing** (re-runs do not reset owner edits).

**Do not** treat these draft credits as final until the user confirms.

| code | label | sortOrder | draft credits | chargedAs | CreditsBillingKind wired? |
|------|-------|-----------|---------------|-----------|---------------------------|
| VISUAL_DIFF | Visual diffs | 5 | **5** | Each review | **No — DRAFT — user must confirm before treating as final** |
| SUITES_MONITOR | Suites & monitor | 6 | **10** | Each scheduled run | **No — DRAFT — user must confirm before treating as final** |
| HEAL_REVIEW | Heal review | 7 | **5** | Each proposal | **No — DRAFT — user must confirm before treating as final** |

- **Skipped GFK row** — folded into GENERATE covers only.
- **No** new enum values on `CreditsBillingKind`.
- **No** wiring into `fromJobKind` / job start debit.

### 3) Tests
- Added `src/test/java/delivery/portal/service/credits/ServicePriceServiceTest.java`
- Covers: detail/unitLabel for core+addons; `list()` seeds core 4 + 3 add-ons; legacy cover refresh; re-run preserves owner edits.

### 4) FE / templates
Backend only — did **not** edit `guide.html` / `pricing.html`.

## Verify

```bat
mvn -q -pl keel-portal -Dtest=ServicePriceServiceTest test
```

After Maestro portal jar refresh (`start-portal.cmd`, serialize Maven `-Dmaven.test.skip=true`):

- `/pricing` — live table should show core rows + draft add-ons once seeded
- `/admin/pricing` — Owner can edit credit costs / covers for all rows including draft add-ons
- `/api/pricing` — same codes

## Out of scope / next
- User confirm draft credit amounts before treating as final
- Wire add-on codes into billing only when a real JobKind / charge path exists
- FE Guide + Pricing copy (separate FE handoff)
- No push / merge

## Paths touched
- `src/main/java/delivery/portal/service/credits/ServicePriceService.java`
- `src/test/java/delivery/portal/service/credits/ServicePriceServiceTest.java`
- `docs/handoff/2026-11-02-guide-pricing-backend.md`
- Backup: `_backup/guide-pricing-20261102-2115/ServicePriceService.java`
