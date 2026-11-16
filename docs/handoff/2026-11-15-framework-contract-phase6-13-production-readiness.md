# Phase 6.13 — Framework repair production readiness assessment

**Date:** 2026-11-15  
**Qualification baseline:** 8/8 live compile-on scenarios ACCEPTED (Phases 6.10–6.12); **not** production authorization.

---

## 1. Historical 12-minute interruption (Phase 6.8)

### Budget chain

| Layer | Source | What it measures |
|-------|--------|------------------|
| **Bridge wait** | `ExecutionRecoveryHarnessBudget.waitBudgetMs(props, leaseCap)` | Wall-clock cap on **entire** `AgentHarnessCoordinator.execute()` in a virtual thread (`AutomateFrameworkRepairBridge.executeWithJobCancel` → `JobCancelSupport.awaitOrCancel`) |
| **IT / live** | `props.maxRuntimeMinutes=12`, gate `waitBudget=15m` | **`min(12m, 15m) = 720_000 ms`** |
| **Production-shaped defaults** | `application.properties` `max-runtime-minutes=15` + `ConversionJobRunner.investigationBudgetCap` | Same formula; actual lease cap depends on worker wiring |
| **Task policy** | `AgentTaskRouter` → `AgentPolicy.maxRuntime` | Copied onto each task (same minutes as props) |
| **Sidecar session** | `CursorSidecarClient` → `CursorSidecarSession` | Separate **in-process** deadline on sidecar stdout drain (`task.budget().maxRuntime`) |
| **Reconcile poll** | `reconcileMaxPollMs` in sidecar request | Only **`reconcile`** op; capped ≤120s, unrelated to fresh `execute` unless reconciling with ids |

**Conclusion:** Phase 6.8 **720s** elapsed is **bridge-level total wait**, not “Cursor repair only.” It includes workspace copy, store lock acquisition (default 30s max per attempt), sidecar process start, and waiting for `run.started`.

### Why Phase 6.8 had no agent run

Evidence (`0b0b22d2…`, `264f6d20…`):

- Events: **`TASK_CREATED` → `CANCEL` / `INTERRUPTED` only** — no `START`, no `TERMINAL` from sidecar.
- `task.startedAt: null`, `agentId` / `agentRunId: null`.
- `launch-state.json`: **`CANCEL_REQUESTED`** without ids.

**Interpretation:** `coordinator.execute()` did not persist a started Cursor run before the bridge timer fired. Plausible causes (not mutually exclusive):

1. Sidecar blocked (store lock contention, slow `agent.mjs` start, host load during **~57 min** serial matrix).
2. Sidecar ran but never emitted `run.started` within the envelope.
3. `Future.cancel(true)` interrupted the virtual thread at bridge timeout before ids were saved.

**Phase 6.12:** Same scenarios **COMPLETED in ~95–106s** with full chain — proves repair feasibility; **does not disprove** repeat interrupt under load.

### `coordinator.cancel()` behavior

From `AgentHarnessCoordinator.cancel`:

- Writes `cancel.requested`, sets launch state **`CANCEL_REQUESTED`**.
- Calls **`runtime.cancel(agentId, runId)`** only when **both ids are present** → terminal **`CANCELLED`** (confirmed remote cancel op).
- If ids **missing** → terminal **`INTERRUPTED`** without sidecar `cancel` op → matches Policy A “no cancelRun without persisted ids.”

Bridge timeout path: `TimeoutException` → `future.cancel(true)` → `abort()` → `requestCancel` + **`coordinator.cancel()`**. This is **harness lease exhaustion**, not reconcile poll exhaustion.

### Policy A reconciliation (no code changes in 6.13)

| Policy A rule | Implementation match |
|---------------|---------------------|
| Poll-budget exhaustion → `INTERRUPTED` | Reconcile path: sidecar returns `INTERRUPTED`, coordinator persists without `cancel` when ids exist but poll ends (`2026-11-12` evidence). |
| Reconciliation must not `cancelRun` merely because polling expired | Reconcile `getResult` does not auto-cancel; documented in phase 3 handoff. |
| Explicit cancellation separate | `JobCancelledException` → `abort()`; user/job cancel is explicit `cancelCheck`. |
| Do not convert `INTERRUPTED` → `COMPLETED` | Gate rejects `REPAIR_INTERRUPTED` / failed repair; promotion only after acceptance. |

**Tension (document as ops risk, not changed here):** Bridge **lease timeout** triggers **`coordinator.cancel()`**, which is stronger than “stop polling” but **degrades to `INTERRUPTED`** when no ids — consistent with 6.8 evidence. When ids **do** exist at timeout, **`runtime.cancel()`** runs — treat as **explicit harness abort**, not Policy A reconcile poll.

**Ambiguous state / retry:** `AgentHarnessReconcile.shouldMarkInterrupted` + sdk-touch marker govern whether a **new** `execute` may run. INTERRUPTED **without** ids on a fresh task is retriable (6.12 attempt1 succeeded). INTERRUPTED **with** ids requires **reconcile** path per Policy A docs.

---

## 2. Operational reliability requirements

### Existing guarantees (code + qualification)

- Original project tree snapshotted; repair only on **`repair-candidate`** (`FrameworkAcceptanceWorkspace`).
- Promotion only after repair success, compile-on, inventory v2 revalidation pass.
- Reconciliation staged with backup/restore; lock on `reconciliation.lock`.
- `FW_ORPHAN_TEST_CLASS` scope: `generated/` + `todo/` only (6.9).
- Harness concurrency cap: `delivery.agent-harness.max-concurrent-runs=2` per parent job id.
- Framework repair gated on **`automate-framework-repair.enabled`** (default **false**).

### Proposed measurable requirements (targets — **not** measured in 6.x)

| ID | Requirement | Notes |
|----|-------------|--------|
| R1 | p95 sidecar `run.started` latency < 60s under nominal single-repair load | No production metrics yet |
| R2 | p95 framework repair wall-clock < 10m for single-violation fixtures | 6.10–6.12: ~1.5–2m typical |
| R3 | Bridge wait budget ≥ p99 repair duration + 2m startup slack | Today: single `min(runtime, lease)` envelope |
| R4 | INTERRUPTED-without-ids rate < 1% per repair attempt in canary | 6.8: 2/8 in one long batch |
| R5 | Zero promotion without `ACCEPTED` gate outcome | Enforced in tests |
| R6 | Operator can classify terminal status within 1 log line | Partial — see observability |

---

## 3. Observability

### Available today

| Signal | Where |
|--------|--------|
| Task/run ids, cursor status | `task.json`, qualification JSON (`phase6-8-compile-on-*.json`, `phase6-12-*-attempt1.json`) |
| Event timeline | `events.jsonl` (sparse for 6.8 failures) |
| Launch state | `launch-state.json` |
| Bridge outcome | Gate logs, `gateStatus`, `failureClass` in evidence |
| Reconciliation | `reconciliation-manifest.json` |
| Compile | `emit-compile-check.ok`, `EMIT_COMPILE_FAIL.log` |
| Revalidation | `…_acceptance-revalidate/contract-validation-report.json` |
| Sidecar NDJSON | Task dir (not centralized) |

### Gaps

- No unified **“time-to-run.started”** metric in logs.
- Bridge **`TimeoutException`** vs sidecar **`SIDECAR_TIMEOUT`** vs **`INTERRUPTED`** not surfaced distinctly in `AutomateFrameworkAcceptanceOutcome` summary strings for operators.
- No aggregate dashboard for INTERRUPTED rate.
- **Budget consumption %** (elapsed / waitBudgetMs) not recorded in evidence writer (only max runtime seconds in 6.12 enrichments).

---

## 4. Concurrency and load risk

**Unproven:** Phase 6.8 serial marathon contributed to delays (hypothesis only).

**Inspect findings:**

- **Store lock:** per-task `AgentStoreLock` (30s default); serial contention if same task dir retried concurrently (unlikely).
- **Sidecar:** one process per `execute`; no global pool limit beyond `max-concurrent-runs` per parent job.
- **Shared `cursor-sdk-state`:** per task dir — isolated.
- **Worker lease:** `investigationBudgetCap` passed from `ConversionJobRunner` — independent of single IT `waitBudget`.

**Bounded load qualification plan (recommended, not executed):**

1. **Two concurrent** framework repair tasks (different work dirs, different parent jobs) — max 2 per config.
2. **Repeated single-scenario** repair (3×) after forced sidecar slow-start (artificial delay in staging only — needs approval).
3. **Soak:** 8 scenarios serial with evidence capture of `createdAt` → `START` delta (regression guard).

Do not run against production credentials.

---

## 5. Deployment safety (verified)

| Check | Status |
|-------|--------|
| `delivery.agent-harness.enabled=false` | Default in `application.properties` |
| `delivery.agent-harness.automate-framework-repair.enabled=false` | Default |
| `delivery.agent-harness.cursor.enabled=false` | Default |
| Repair only via Automate acceptance gate | `AutomateFrameworkAcceptanceGate`, `repairEnabled()` |
| Convert advisory validation separate | `AgentHarnessJobBridge` / Phase 3 docs |
| Original protected until accept | Snapshot + repair-candidate + promote only on pass |
| `heal.mjs` | Unchanged |
| Execute recovery Policy A | Unchanged in 6.13 |

---

## 6. Rollout recommendation

### Production readiness checklist

- [x] 8/8 compile-on live qualification (inventory v2 + compile-on + promotion)
- [x] Deterministic contract/regression suites green (6.9–6.11)
- [x] Production flags default off
- [ ] Measured SLOs for sidecar startup and repair duration
- [ ] INTERRUPTED rate monitoring in staging
- [ ] Bounded concurrent repair qualification
- [ ] Operator runbook for INTERRUPTED vs CANCELLED vs REPAIR_FAILED
- [ ] Canary tenant + rollback tested on real Conversion jobs (not only IT)

### Go / no-go

## **Verdict: READY AFTER SPECIFIED BLOCKERS**

**Blockers before controlled canary:**

1. **Observability:** Log/evidence fields for bridge elapsed ms, terminal reason enum (`BRIDGE_WAIT_TIMEOUT`, `SIDECAR_TIMEOUT`, `RECONCILE_POLL_EXHAUSTED`, `EXPLICIT_JOB_CANCEL`).
2. **Load qualification:** Bounded concurrent + serial soak with timing metrics (Section 4).
3. **Ops runbook:** Policy A distinctions and safe retry rules (reconcile vs new execute).
4. **Canary scope:** Enable **only** `automate-framework-repair` + harness on staging; single tenant; manual rollback = disable flags + discard repair-candidate.

**Risks (severity):**

| Risk | Sev | Mitigation |
|------|-----|------------|
| Bridge 12–15m timeout → INTERRUPTED without repair | **High** | Metrics; optional lease sizing per job (product decision, not silent change) |
| Orphan sidecar if cancel mid-start | **Medium** | Process audit in staging soak |
| Long serial batches slow sidecar | **Medium** | Concurrency limits + soak |
| Operator mis-reads INTERRUPTED as quality fail | **Low** | Runbook + outcome enum |

**Rollback:** Disable `automate-framework-repair.enabled`; failed acceptance never promotes; `framework-acceptance/` artifacts disposable per job work dir.

---

**No production flags enabled. No implementation changes in 6.13. No commit.**