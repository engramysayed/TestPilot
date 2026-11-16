# Kickoff — Slice A: wire add-on job debit (2026-11-02)

**Status:** ASSIGNED Backend  
**Base tip:** `0a5a389`  
**Portal LIVE:** PID `4032` on `:8080` (verify before rebuild)  
**Specialist:** Keel Backend (primary). FE only if refuse UX copy needs a pass.  
**Queued after GREEN:** Slice B prompts Owner UI + VERSION (do not start).  
**No push** unless user asks. No secrets. Keel only. Serialize Maven (one reactor).

## Goal

Charge **VISUAL_DIFF** / **SUITES_MONITOR** / **HEAL_REVIEW** when those features are actually used — same credit path as GENERATE/EXECUTE/AUTOMATE/HUNT (`CreditService` / ledger / `InsufficientCreditsException`). Amounts stay admin-editable `ServicePrice` seeds (5 / 10 / 5). Display-only → real debit.

## Current shape (read first)

- `CreditsBillingKind` today: GENERATE | EXECUTE | AUTOMATE | HUNT only (`fromJobKind`).
- Job start debit: `PortalStore` → `CreditService.tryDebitForJob(job)` — idempotent key `debit:job:{jobId}`.
- Add-on rows already in `ServicePriceService.ensureAddon` (VISUAL_DIFF=5, SUITES_MONITOR=10, HEAL_REVIEW=5) but **not** in `CreditsBillingKind` / not debited.
- Likely hook surfaces (confirm & refine):
  - **VISUAL_DIFF:** visual compare/review path — `VisualDiffService` / `VisualDiffController` / prove visual session (seed unit: each review).
  - **HEAL_REVIEW:** accept/reject heal proposal — `HealProposalService` / `HealProposalController` (each proposal reviewed).
  - **SUITES_MONITOR:** scheduled suite run / monitor claim — `ScheduleClaimPoller` / suite schedule execute path (each scheduled run).

## DoD

1. When a run/action uses visual diff review, suites/monitor schedule run, or heal review (accept/reject), balance is checked and credits debited at admin `/admin/pricing` amounts.
2. Short balance → clear refuse via existing `InsufficientCreditsException` (or same HTTP status family as job debit); **no silent free run**.
3. No double-debit (idempotent keys, e.g. `debit:addon:{code}:{stableId}`).
4. No charge when that feature is not used on the action.
5. Core GENERATE/EXECUTE/AUTOMATE/HUNT debit unchanged.
6. Unit/integration tests: debit success + refuse short balance for each add-on code (or shared helper covering all three).
7. Portal rebuild/restart on Ramy if classes change; smoke `/pricing` still 302→login (or 200). Keep Maven serialized — stop jar before package if locked.
8. Handoff `docs/history/handoffs/2026-11/2026-11-02-addon-job-debit-backend.md` with paths, verify cmd, tip SHA, PID if jar refreshed.
9. Backup before edits under `_backup/`.

## Design notes (non-binding — verify)

- Prefer extending billing to resolve cost by **ServicePrice code** (not only JobKind), so add-ons don't fake JobKinds.
- Reuse ledger + idempotency pattern from `tryDebitForJob`.
- Debit **at the moment of use** (review accept, scheduled claim start, visual review), not speculative on unrelated EXECUTE.
- If FE already maps insufficient-credits JSON to a toast, no FE work; else ping Maestro for a one-line FE assign.

## Out of scope

- Slice B prompts Owner UI / VERSION
- Postman/API packs
- Push / force-push
- Changing seed amounts (admin edits prices)

## Report

Ping Maestro GREEN + handoff path + tip + PID. Maestro gates portal refresh and reports Leader.