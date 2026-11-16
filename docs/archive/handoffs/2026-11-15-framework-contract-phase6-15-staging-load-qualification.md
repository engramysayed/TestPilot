# Phase 6.15 — Bounded staging load and reliability qualification

**Date:** 2026-11-15  
**Status:** **BLOCKED — preflight not satisfied** (not executed; no local substitution for staging)

---

## 1. Preflight gate (must all pass before any run)

| # | Check | Status (this session) |
|---|--------|------------------------|
| 1 | Isolated **staging** host (not developer workstation) identified | **FAIL** — [shared-staging.md](../ops/shared-staging.md) host/DNS **missing** |
| 2 | Staging credentials and Cursor access **approved in writing** | **FAIL** — `KEEL_STAGING_QUALIFICATION_APPROVED` unset; no approval record |
| 3 | Staging tenant/project uses **disposable** test data | **NOT VERIFIED** — no staging tenant |
| 4 | `delivery.agent-harness.max-concurrent-runs=2` on staging | **N/A** — defaults in repo are `2`; staging overlay unconfirmed |
| 5 | Harness + `automate-framework-repair` enabled **only** on approved staging | **FAIL** — workstation `application.properties` keeps both **disabled** |
| 6 | Production flags remain disabled everywhere else | **PASS** (repo defaults) |
| 7 | Operator runbook available | **PASS** — [Phase 6.14 runbook](./2026-11-15-framework-contract-phase6-14-operational-observability.md) |
| 8 | Rollback access (staging config + deploy) | **FAIL** — no staging operator access |

**Abort rule:** Do not run Maven live ITs on a laptop and label the result “staging qualification.”

---

## 2. Execution checklist (when preflight passes)

### A. Baseline capture (before repairs)

Record into `docs/handoff/phase6-15-staging-baseline.json` (or IT-generated equivalent):

- Staging hostname, install mode, git SHA, JDK version, `node` / `agent.mjs` version
- `delivery.agent-harness.*` effective properties (enabled, cursor, repair, max-runtime, max-concurrent-runs)
- Worker concurrency and lease budget from portal/worker config
- `agent-runs/` footprint (task count, stale RUNNING tasks)
- Host CPU count, available memory (operator snapshot)
- Confirm `framework-repair-operational.json` schema writes on a **smoke** gate (optional single no-op or dry contract pass)

### B. Serial soak (3 cycles)

| Cycle | Suggested scenario | Parent job id | Evidence file |
|-------|-------------------|---------------|---------------|
| 1 | `MISSING_TEST` | `staging-serial-1` | `phase6-15-staging-serial-1-MISSING_TEST.json` |
| 2 | `MISSING_PAGE_LOCATORS` | `staging-serial-2` | `phase6-15-staging-serial-2-MISSING_PAGE_LOCATORS.json` |
| 3 | `EMPTY_TEST_STUB` | `staging-serial-3` | `phase6-15-staging-serial-3-EMPTY_TEST_STUB.json` |

**Cooldown:** ≥2 minutes between cycles (operator), watch agent-store for stale `RUNNING`.

**Per cycle capture** (from operational JSON + `task.json` + `events.jsonl`):

- `TASK_CREATED` / `START` instants
- `timeToRunStartedMs` (if null → report **unavailable**)
- `bridgeElapsedMs`, `bridgeBudgetMs`, `bridgeBudgetUtilizationPct`
- `cursorRunMs`, `reconciliationMs` (nullable)
- `sidecarStartupMs` — **unavailable** in 6.14 instrumentation (do not infer from bridge elapsed)
- Gate status, terminal reasons, compile/revalidation, architecture assessor, promotion (`accepted` + digest match)

### C. Two-concurrent repair (1 pair)

| Job | Scenario | Work dir suffix | Parent job id |
|-----|----------|---------------|---------------|
| A | `MISSING_PAGE_ACTIONS` | `staging-concurrent-a` | `staging-conc-a` |
| B | `MIXED_INVENTORY_BAD_IMPORT` | `staging-concurrent-b` | `staging-conc-b` |

- Start both within the same minute; **do not** exceed two active Cursor repairs on the host.
- Verify distinct `taskId`, `agentId`, `agentRunId`; separate `framework-acceptance/` trees; no cross-write.
- Evidence: `phase6-15-staging-concurrent-pair.json` (aggregate + per-job paths).

### D. Lease independence (staging)

On staging worker, confirm `LeaseKeeper` renews conversion leases while repairs run (existing `ConversionWorkerHarnessLeaseIT` pattern on **staging** worker logs—not re-run locally as substitute).

### E. Rollback verification (staging config only)

1. Set `delivery.agent-harness.automate-framework-repair.enabled=false` and `delivery.agent-harness.enabled=false` in **staging** overlay.
2. Restart or reload per staging procedure.
3. Start a new conversion with mandatory contract violations → must **not** enter repair (`REPAIR_NOT_ENABLED`).
4. Confirm in-flight jobs reach documented terminal state; no promotion when `accepted=false`.

---

## 3. Gated automation (staging only)

When preflight passes on the **staging host** (or approved jump box with staging-aligned store):

```powershell
# Load staging secrets — never commit
call api-keys.local.bat

mvn -pl keel-portal test -Dsurefire.forkCount=0 `
  -Dkeel.agent.staging.load.qualification=true `
  -Dkeel.agent.staging.load.approved=true `
  -Dkeel.agent.staging.load.host=staging.example.com `
  -Dkeel.agent.cursor.compile.on=true `
  -Dtest=FrameworkStagingLoadQualificationIT
```

All tests **Skip** unless `qualification`, `approved`, `host`, and `CURSOR_API_KEY` are set.

---

## 4. Measurement and SLO proposal (after execution only)

Report **observed** values first. With 3 serial + 1 concurrent pair:

- Compute min/median/max for `timeToRunStartedMs` and `bridgeBudgetUtilizationPct` only.
- **Do not** claim p95 statistical significance; label as “qualification sample (n=4 repairs).”
- Propose p95 targets only in a staging review meeting with n≥30 future samples.

**Known gap:** `sidecarStartupMs` is not isolated; document as unavailable until non-invasive hooks land (Phase 6.14).

---

## 5. Session blocker summary (2026-11-15)

| Blocker | Detail |
|---------|--------|
| No staging host | Shared staging procedure unfilled |
| No approval token | `KEEL_STAGING_QUALIFICATION_APPROVED` / explicit `-Dkeel.agent.staging.load.approved=true` with ops sign-off missing |
| No Cursor key in agent shell | `CURSOR_API_KEY` unset in execution environment (keys may exist only in local `.bat`, not loaded here) |
| Wrong environment class | Current machine is a **development** workspace |

**Verdict:** Phase 6.15 **not executed**. Qualification remains **BLOCKED** until staging preflight passes.

**This is not production rollout authorization.**