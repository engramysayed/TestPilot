# Phase 6.12 — Deferred Cursor reliability requalification

**Date:** 2026-11-15

## Historical diagnosis (Phase 6.8)

Both scenarios showed **INTERRUPTED** with **no `agentId` / `agentRunId`**, **`startedAt: null`**, and task duration **~720s** (12 min) matching harness **`waitBudgetMs`** (`min(maxRuntimeMinutes×60s, gate lease)`).

| Scenario | Task | Events | Launch state at interrupt |
|----------|------|--------|-------------------------|
| `MISSING_PAGE_LEGACY` | `0b0b22d2…` | `TASK_CREATED` → `CANCEL`/`INTERRUPTED` | `CANCEL_REQUESTED`, no sidecar run |
| `MISSING_PAGE_ASSERTIONS` | `264f6d20…` | same pattern | same |

**Classification:** Policy A **wait-budget exhaustion** on `AutomateFrameworkRepairBridge.executeWithJobCancel` → `coordinator.cancel` without a terminal Cursor run — **not** acceptance failure and **not** repair-quality rejection. Distinct from Phase 6.8 compile/revalidation orphan issue (fixed in 6.9).

**Assertions vs legacy:** Same failure *shape*; legacy is a harder repair (full page create) but 6.12 proves both can complete well under 12 min when sidecar launches normally.

## Code change (evidence only)

`FrameworkCompileOnQualificationEvidenceWriter`: optional versioned evidence via  
`-Dkeel.agent.qualification.attempt=…` and `-Dkeel.agent.qualification.evidence.versionedOnly=true`  
(writes `phase6-12-compile-on-<SCENARIO>-<attempt>.json`, enriches task/launch/events; does not change harness budgets).

## Live retries

**Command template:** compile-on flags + versioned evidence props + single `#method`.

**Log:** `docs/handoff/phase6-12-deferred-cursor-live.log`

### `MISSING_PAGE_LEGACY` attempt1

- **Maven exit:** 0 (~106 s)
- **Accepted:** yes — full chain
- **Task / run:** `4af52b68…` / `run-871d5cf3…`, COMPLETED
- **Evidence:** `phase6-12-compile-on-MISSING_PAGE_LEGACY-attempt1.json`

### `MISSING_PAGE_ASSERTIONS` attempt1

- **Maven exit:** 0 (~95 s)
- **Accepted:** yes — full chain
- **Evidence:** `phase6-12-compile-on-MISSING_PAGE_ASSERTIONS-attempt1.json`

## Updated matrix (8 scenarios)

| Status | Count | Scenarios |
|--------|-------|-----------|
| Live ACCEPTED | **8/8** | All qualification fixtures (6.10–6.12 + earlier batch) |
| INTERRUPTED (historical only) | 0 remaining | 6.8 failures superseded by 6.12 attempt1 |

## Production readiness

**NOT READY** — local IT qualification only; harness flags off; intermittent 12 min interrupt class may recur under load without further reliability work.

**No commit. Policy A preserved. No contract weakening.**