# Handoff — Slice A: add-on job debit (Backend) — 2026-11-02

**Status:** GREEN  
**Tip:** verify with `git rev-parse --short HEAD` (base was `0a5a389`; commit subject: feat(portal): debit VISUAL_DIFF SUITES_MONITOR HEAL_REVIEW addons)
**Portal:** rebuilt + restarted on Ramy; LIVE PID **17288** on `:8080` (was 4032)  
**Backup:** `_backup/addon-job-debit-20261102-2228/`  
**No push.** Slice B (prompts Owner UI / VERSION) not started.

## What changed

Debit **VISUAL_DIFF** / **SUITES_MONITOR** / **HEAL_REVIEW** at moment of use via `CreditService.tryDebitForAddon`, same ledger + `InsufficientCreditsException` (HTTP 402) path as job debit. Amounts from admin `ServicePrice` (`costByCode`), seeds unchanged (5 / 10 / 5).

### Core credits
- `CreditService.java` — `tryDebitForAddon(userId, code, stableId)`; idempotent key `debit:addon:{code}:{stableId}`; reason `ADDON_DEBIT`; constants `ADDON_VISUAL_DIFF` / `ADDON_SUITES_MONITOR` / `ADDON_HEAL_REVIEW`
- `ServicePriceService.java` — `costByCode(String)`; comment that add-ons are wired to debit
- `CreditCostTable.java` — `costByCode(String)`
- Core `tryDebitForJob` / `CreditsBillingKind` **unchanged** (still GENERATE|EXECUTE|AUTOMATE|HUNT only)

### Hooks
- **VISUAL_DIFF:** `VisualDiffService.compareActual` + `approveFromRun` (shared stable id `projectId:runId:caseKey:viewport` so compare+approve does not double-charge). Controllers pass `ownerUserId`; rethrow ICE → 402.
- **HEAL_REVIEW:** `HealProposalService.accept` / `reject` after pending check; stable `projectId:proposalId` (one charge per proposal either decision). Controllers rethrow ICE.
- **SUITES_MONITOR:** `SuiteController.startRun` when `trigger` is `schedule` or `monitor` only (not manual); debit **before** `saveJob`; stable `projectId:suiteId:jobId`. Manual suite runs unchanged.

### Tests
- `AddonDebitTest.java` — seed costs 5/10/5; debit success + idempotent for each code; short-balance refuse for each; disabled flag no-op. Uses `jdbc:h2:mem:addon-debit`.

## Verify

```bat
cd /d D:\Work\projects\Keel\keel\TestPilot
mvn -pl keel-portal -Dtest=AddonDebitTest,ServicePriceServiceTest test
```

Observed: **Tests run: 7, Failures: 0** (AddonDebitTest 4 + ServicePriceServiceTest 3).

Smoke (after restart): `GET http://127.0.0.1:8080/pricing` → **200** (allowed by DoD; or 302→login).

## FE refuse UX

Same `INSUFFICIENT_CREDITS` JSON + HTTP 402 as job debit via `PortalExceptionHandler`. **No FE assign required** if existing job-debit toast already maps that code; Maestro may spot-check visual/heal/suite schedule refuse once. **FE refuse UX ping: not required** unless toast missing on those surfaces.

## Out of scope / notes

- Slice B held.
- Did not change seed amounts, push, or portal DB / `api-keys.local.bat`.
- `CreditsGateLockTest` still assumes HUNT unit=1 against empty temp workbook (pre-existing quantity×15); not modified for this slice.

## Next

Maestro: gate portal refresh note (PID 17288), report Leader; queue Slice B when ready.
