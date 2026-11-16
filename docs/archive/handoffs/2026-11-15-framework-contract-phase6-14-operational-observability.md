# Phase 6.14 — Operational observability and recovery readiness

**Date:** 2026-11-15  
**Scope:** Diagnosability for Automate framework repair bridge and acceptance gate. No production flag changes, no timeout/budget changes, no acceptance rule changes.

## Existing observability inventory (pre-6.14)

| Signal | Location | Notes |
|--------|-----------|-------|
| Task creation | `agent-runs/<parentJob>/<taskId>/task.json` → `createdAt` | Authoritative |
| Harness events | `events.jsonl` (`TASK_CREATED`, `START`, `TERMINAL`, `CANCEL`) | Authoritative for `run.started` (`START`) |
| Launch state | `launch-state.json` | Cursor sidecar lifecycle |
| Bridge wall clock | Previously only logs / job duration | No structured bridge budget utilization |
| Gate outcome | `AutomateFrameworkAcceptanceOutcome.status` | Acceptance vs harness completion |
| Compile-on evidence | `docs/handoff/phase6-8-compile-on-*.json` | Live qualification; now embeds operational report when present |

## Code changes — terminal reason contract

### Operator terminal reasons (`FrameworkRepairTerminalReason`)

Recorded in `framework-acceptance/framework-repair-operational.json`. They **do not** change `AgentTaskStatus` or Policy A.

| Reason | Typical source |
|--------|----------------|
| `SUCCESS` | Gate `ACCEPTED` / `CONTRACT_PASSED` |
| `BRIDGE_WAIT_TIMEOUT` | `JobCancelSupport` deadline on bridge `coordinator.execute()` |
| `SIDECAR_START_TIMEOUT` | Legacy `SIDECAR_TIMEOUT` / task `TIMED_OUT` |
| `CURSOR_RUN_FAILURE` | Provider/run errors (`cursor start failed`, etc.) |
| `RECONCILE_POLL_EXHAUSTED` | `INTERRUPTED` + summary mentioning reconcile poll budget |
| `EXPLICIT_JOB_CANCEL` | `JobCancelledException` on bridge wait |
| `RECONCILIATION_FAILURE` | Gate `RECONCILIATION_FAILED` |
| `COMPILE_FAILURE` | Gate `COMPILE_FAILED` |
| `REVALIDATION_FAILURE` | Gate `REVALIDATION_FAILED` |
| `REPAIR_INTERRUPTED` | Harness `INTERRUPTED` without reconcile poll signature |
| `REPAIR_FAILED` | Other repair failures |
| `UNSPECIFIED` | Unknown / success path at bridge layer |

Bridge reasons are set in `AutomateFrameworkRepairBridge.executeWithJobCancel`; acceptance reasons in `AutomateFrameworkAcceptanceGate.recordAndReturn` via `FrameworkRepairTerminalReasonClassifier`.

## Timing fields and authoritative sources

| Field | Source |
|-------|--------|
| `bridgeBudgetMs` | `ExecutionRecoveryHarnessBudget.waitBudgetMs(properties, waitBudget)` |
| `bridgeElapsedMs` | Wall clock around bridge `awaitOrCancel` |
| `bridgeBudgetUtilizationPct` | Derived from elapsed / budget |
| `workspacePrepareMs` | Reserved (not wired in 6.14 — reported `null`) |
| `storeLockWaitMs` | Reserved (`null`) |
| `sidecarStartupMs` | Reserved (`null`) |
| `timeToRunStartedMs` | `task.json.createdAt` → `events.jsonl` `START` |
| `cursorRunMs` | `START` → `TERMINAL` events, else `createdAt`→`completedAt` when run started |
| `resultRetrievalMs` | Reserved (`null`) |
| `reconciliationMs` | Gate wall clock around `FrameworkRepairWorkspaceReconciler.reconcile` |
| `agentRunStarted` | `agentId`+`agentRunId` on task **or** `START` event |
| `agentId` / `agentRunId` | `task.json` |
| `taskStatus` | `task.json` |

Unavailable measurements are omitted (`null`), not zero.

## Deterministic tests

```
mvn -pl keel-portal test -Dsurefire.forkCount=0 -Dtest=FrameworkRepairTerminalReasonClassifierTest,FrameworkRepairBridgeOperationalTest,AutomateFrameworkAcceptanceGateTest
```

`FrameworkRepairBridgeOperationalTest` covers: fast start, bridge timeout before/after run IDs, explicit cancel, reconcile poll exhaustion, cursor run failure, compile/revalidation/promotion operational report.

## Operator runbook

### Task lifecycle (framework repair)

1. Contract validation fails → repair candidate snapshot (original untouched).
2. `AutomateFrameworkRepairBridge.attemptRepair` creates harness task, runs bridge-bounded `coordinator.execute`.
3. Cursor path: workspace reconciliation → emit compile check → contract revalidation → **promotion only if all pass**.

### `INTERRUPTED` vs `CANCELLED`

- **`CANCELLED`:** Remote/runtime cancel confirmed (`agentId` + `agentRunId` present and provider cancel succeeded).
- **`INTERRUPTED`:** Local interruption (bridge timeout, unconfirmed cancel, reconcile ambiguity). **Not** upgraded to `CANCELLED` (Policy A).
- An **`INTERRUPTED`** task **without** `agentId`/`agentRunId` is **not** proof Cursor attempted repair—only that the bridge/harness stopped waiting.

### Bridge timeout vs reconcile poll exhaustion

- **`BRIDGE_WAIT_TIMEOUT`:** Bridge elapsed ≈ configured budget; check `framework-repair-operational.json` `bridgeTerminalReason`.
- **`RECONCILE_POLL_EXHAUSTED`:** Task `INTERRUPTED` with reconcile poll wording in `TERMINAL` event / result summary—not the same as bridge wait exhaustion.

### Inspecting state

Under conversion work dir:

- `framework-acceptance/framework-repair-operational.json` — operator summary.
- `framework-acceptance/repair-candidate/` — isolated repair tree (not promoted until acceptance).
- `framework-acceptance/original-snapshot/` — pre-repair snapshot.
- `agent-runs/<parentJob>/<taskId>/` — `task.json`, `events.jsonl`, `launch-state.json`, `result.json` (if present).

### When to reconcile vs retry

- **Reconcile:** `INTERRUPTED` with **both** `agentId` and `agentRunId`, events show `START`, launch state not terminal—follow existing Cursor reconcile rules; do not spawn a second run blindly.
- **New execution:** Only when persisted state shows no active run, cancel completed, or explicit operator decision after reading artifacts.
- **Stop and investigate:** `RECONCILIATION_FAILURE`, ambiguous dual-run signals, promotion concerns, or repeated `BRIDGE_WAIT_TIMEOUT` under normal load.

### Compile / validation / promotion failures

- **`COMPILE_FAILURE`:** `emit-compile-check` failed; repair candidate not promoted; original snapshot unchanged.
- **`REVALIDATION_FAILURE`:** Mandatory contract still failing on candidate; no promotion.
- **Verify no partial publish:** Published tree is `project/`; compare to `original-snapshot` and absence of promotion when `accepted=false` in operational report.

### Rollback flags (defaults remain off)

```properties
delivery.agent-harness.enabled=false
delivery.automate-framework-repair.enabled=false
delivery.agent-harness.cursor.enabled=false
```

Do not enable for production rollback without staging canary approval.

## Bounded load qualification plan (staging only — not executed in 6.14)

**Prerequisites:** Staging credentials, Cursor sidecar approved, harness enabled **only** on staging, `max-concurrent-runs=2`, production flags stay off elsewhere.

| Step | Action |
|------|--------|
| 1 | Three **serial** soak cycles: one compile-on scenario each, cooldown between runs |
| 2 | One **two-concurrent-repair** test (two jobs, respect `max-concurrent-runs=2`) |
| 3 | Capture per run: TASK_CREATED→START (`timeToRunStartedMs`), `bridgeElapsedMs`, `bridgeBudgetUtilizationPct`, sidecar/run durations from operational JSON |
| 4 | Record all `bridgeTerminalReason` / `acceptanceTerminalReason` |
| 5 | Confirm worker **lease renewal** independent of repair (existing lease IT pattern) |
| 6 | Assert `accepted=false` → no promotion (`repair-candidate` only) |

**Slow-start test:** Optional isolated scenario with elongated sidecar start—requires explicit approval before run.

**SLOs:** None fixed in 6.14—collect measurements first, propose thresholds in staging review.

## Remaining risks

- Long-running sidecar startup under concurrent load still **unqualified**.
- Several phase timings (`workspacePrepareMs`, `sidecarStartupMs`, `resultRetrievalMs`) remain unset until non-invasive hooks exist.
- `interactive-feedback-mcp` not available in agent environment for automated user feedback.

## Readiness verdict

**Instrumentation and operator documentation: READY.**  
**Staging canary / production: NOT READY** — bounded load soak, staging canary, and approved SLOs still required (per Phase 6.13). Do not enable production harness flags based on 6.14 alone.