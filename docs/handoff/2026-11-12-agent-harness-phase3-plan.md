# Agent harness Phase 3 — worker integration plan (architecture only)

**Status:** Draft for review — **no implementation**  
**Builds on:** Phase 1 `09bc616`, Phase 2 `c97c7ad` + live validation `735102f` + reliability `89479e6`  
**Evidence:** [`2026-11-12-cursor-sdk-spike-notes.md`](./2026-11-12-cursor-sdk-spike-notes.md)

---

## 1. Goals and non-goals

### Goals

- Wire `AgentHarnessCoordinator` into **existing** portal job workers without rewriting `ProvePhase`, `EmitPhase`, `HealCascade`, or publication gates.
- Run long Cursor tasks **inside** (or beside) a claimed `DurableJobClaim.Lease`, with **renewal** for multi-minute agent work.
- Deterministic **task identity**, **no duplicate `Agent.create`**, safe **recovery** after worker/sidecar crash.
- Feature-flagged rollout; default off preserves today’s Convert / Execute / Hunt / Generate behavior.

### Non-goals (Phase 3)

- Replacing `CursorHealClient` / `heal.mjs` or changing heal bind/solve semantics.
- Unrestricted shell/Maven in agent tools; Hunt remains **read-only** unless an explicit future flag + approval says otherwise.
- Install-wide Cursor quota management (document limits only).
- Cloud Cursor agents (local SDK only, as Phase 2).

---

## 2. Current architecture (facts from code)

| Layer | Location | Relevance |
|-------|----------|-----------|
| Workers | `ConversionWorker`, `ExecuteWorker`, `GenerateBatchWorker`, `HuntWorker`, `CompareGenerateWorker` | All extend or mirror `AbstractJobWorker`: `dispatch` → `beginWork` (lease) → `runOne` |
| Lease | `PortalStore.beginWork`, `LeaseKeeper` (15s heartbeat), `JobLeaseReconciler` | Agent work can exceed default job assumptions; renewal must stay tied to **same job id** |
| Convert pipeline | `ConversionJobRunner` → `ProvePhase` → `EmitPhase` | Compile validation, `CodegenEmitGate`, `PublicationGuard` on emit — **must not be bypassed** |
| Execute | `ExecuteWorker` → prove/execute services | Heal via `HealCascade` + `CursorHealClient` — **separate** from harness sidecar |
| Harness | `AgentHarnessCoordinator`, `agent-runs/{parentJobId}/{taskId}/` | Isolated workspace, `sdk-touch.marker`, Java `AgentStoreLock`, `tools/cursor-agent/agent.mjs` |
| Flags | `application.properties` `delivery.agent-harness.*` | All default **false** today |

Phase 2 leaves **zero** references to harness inside workers (verified: no worker imports `delivery.agent.harness`).

---

## 3. Proposed integration architecture

### 3.1 Facade: `AgentHarnessJobBridge` (new, thin)

Single entry for workers:

```text
AgentHarnessJobBridge
  → resolves projectRoot / parentJobId (= portal jobId) / workspace path
  → checks AgentHarnessProperties + per-task flags (framework-validation, etc.)
  → AgentHarnessCoordinator.createTask / execute / cancel
  → maps AgentRunResult → worker-visible outcome (success, advisory report, hard fail)
```

Workers call the bridge; they do **not** construct `CursorAgentRuntime` directly.

### 3.2 Parent job id and storage layout

- **`parentJobId` = `JobRecord.jobId`** for portal-triggered harness tasks (aligns with existing per-job concurrency in coordinator).
- Store under tenant-scoped project path already used by conversion (`storeRoot` + project id), not an ad-hoc temp dir.
- **`taskId`** = new UUID per harness invocation; persisted under `agent-runs/{jobId}/{taskId}/`.

**Open decision:** Whether multiple harness tasks per convert job (validation + adaptation) share one `parentJobId` with serialized execute (recommended) or separate task types with explicit sequencing in job state machine.

### 3.3 Integration boundaries by workflow

| Workflow | Phase 3 scope | Boundary |
|----------|---------------|----------|
| **Convert** | Optional post-`EmitPhase` hook | After `EmitPhase` returns packaged tree **and** compile gate passed; agent workspace = **copy** of emitted framework dir (never mutate emit tree in place). Does not skip `CodegenEmitGate` / `PublicationGuard`. |
| **Execute** | **No** harness execution in Phase 3 | Failure investigation remains **disabled** (`failure-investigation.enabled=false`) or advisory-only stub that writes report JSON without mutating tests. Heal stays `HealCascade`. |
| **Generate / Compare** | **None** in Phase 3 | Document extension point only; no worker edits unless flag `framework-validation` extended later. |
| **Hunt** | **None** | Read-only agent policy already in router; do not invoke harness from `HuntWorker` until explicit product decision. |

Primary hook candidate: **`ConversionWorker` / `ConversionJobRunner`** after successful emit (or behind a sub-flag `delivery.agent-harness.framework-validation.enabled`).

### 3.4 Lease renewal during agent tasks

**Problem:** `LeaseKeeper` renews while worker thread is in `runOne`. Phase 2 `AgentRuntime.getResult` **blocks** the worker thread for the full sidecar lifetime (minutes).

**Proposed approach (minimal churn):**

1. Worker already holds lease for entire `runOne`; ensure `LeaseKeeper.track(jobId, lease)` wraps **entire** conversion including harness sub-step (likely already true for convert — verify call site).
2. Pass job `maxRuntime` from `DurableJobClaim` / portal properties into harness `AgentPolicy.maxRuntime` so sidecar deadline **≤** job lease max runtime (avoid lease outliving intentional agent cap).
3. On harness **reconcile** retry (new worker process after crash), **re-acquire** job lease via normal `beginWork` / attempt rules before touching agent store — do not reconcile without portal job ownership.

**Open decision:** Whether agent work should run on a **child thread** with cooperative cancel that calls `coordinator.cancel` while main thread renews lease — only needed if we discover lease heartbeat starvation (today blocking model likely still renews because worker is alive).

### 3.5 Cancellation path

```text
Job cancel (portal) → worker cancel flag (existing)
  → AgentHarnessJobBridge.cancel(projectRoot, jobId, taskId)
  → coordinator.cancel (control/cancel.requested + cancel sidecar when ids known)
```

Cancel sidecar must respect `AgentStoreLock` (Phase 2). Worker must not call `Agent.create` on cancel.

### 3.6 Retries and recovery

| Event | Policy |
|-------|--------|
| Worker crash mid-agent | Job lease lapses → `JobLeaseReconciler` → new attempt may call `execute` with task `RUNNING` + ids → reconcile sidecar (Phase 2 path) |
| `sdk-touch.marker` `sdkTouched: true`, no ids | **`INTERRUPTED`** — manual ops (Phase 2 rule) |
| Duplicate worker dispatch | `beginWork` fails or fence → no harness call |
| Second `execute` same task while `RUNNING` | Coordinator concurrency + SDK “already has active run” — treat as **in-flight**, reconcile not create |

**Preventing duplicate agent execution:**

1. Java: marker + `AgentHarnessReconcile` + `AgentStoreLock`.
2. SDK: never second `create` when marker touched; use `getRun` poll reconcile only.
3. Portal: one active harness task per job stage (bridge enforces at most one `RUNNING` task per `parentJobId` in store).

---

## 4. Recovery policy — analysis (cancel is not the default)

### 4.1 What Phase 2 does today

After sidecar kill, `agent.mjs` reconcile polls `Agent.getRun` for up to `reconcileMaxPollMs` (derived from task budget, capped ~120s in tests). If status remains `running`, it calls **`Agent.cancelRun`** and polls again.

**Risk:** A legitimate long-running validation task can be **cancelled** merely because the **reconcile** waiter gave up, not because the user or job cancelled. That is unsafe as a production default.

### 4.2 Alternatives (recommend evaluation in Phase 3 implement)

| Option | Behavior | Pros | Cons |
|--------|----------|------|------|
| **A. INTERRUPTED + lease handoff (recommended default)** | On reconcile poll budget exhausted while `running`: persist task `INTERRUPTED` (or `RUNNING_AWAITING_RECONCILE`), **do not** cancel SDK run; release store lock; job/ops retries reconcile later with **fresh poll budget** | Preserves in-flight agent work; aligns with “honest INTERRUPTED” Phase 2 philosophy | Job may stay non-terminal until run finishes or ops intervenes |
| **B. Extend poll to full `maxRuntime`** | Reconcile poll until task `budget.maxRuntime` (same as execute), no cancel | Simple; matches user expectation for long tasks | Blocks worker thread unless async reconcile worker; must not block lease heartbeat |
| **C. Background reconcile worker** | Detached scheduled job attempts reconcile with backoff; portal job marks “agent pending” | Decouples from worker lifetime | New component; must coordinate lock + job state |
| **D. Cancel only on explicit cancel** | Remove automatic `cancelRun` from reconcile; cancel only via `coordinator.cancel` / user | Clearest semantics | Zombie `running` runs in store until natural completion — needs monitoring |
| **E. `LocalSendOptions.force` (SDK)** | Spike noted “document when safe”; **not** default | May recover wedged runs | Unverified for production; risk of duplicate effects |

**Recommendation:** Adopt **A** as default product policy for Phase 3 implementation; keep **D** for user-initiated cancel only. Demote current test-only **cancel fallback** to an **opt-in** flag (e.g. `delivery.agent-harness.reconcile.cancel-on-poll-timeout=false` default). Re-run live IT with policy A before enabling framework validation in prod.

### 4.3 Worker-visible outcomes

| Harness terminal | Convert job effect (proposed) |
|------------------|-------------------------------|
| `COMPLETED` | Continue job success path; attach harness summary to job message / artifact |
| `CANCELLED` | Propagate job cancel if user cancelled; else advisory |
| `INTERRUPTED` | Fail convert stage with recoverable error; allow retry attempt without new agent create |
| `TIMED_OUT` | Same as INTERRUPTED or hard fail per flag |
| `FAILED` | Hard fail stage; no publication |

---

## 5. Feature flags and rollout

| Flag | Phase 3 use |
|------|-------------|
| `delivery.agent-harness.enabled` | Master gate for any bridge call |
| `delivery.agent-harness.cursor.enabled` | CURSOR provider in router |
| `delivery.agent-harness.framework-validation.enabled` | Convert post-emit hook only |
| `delivery.agent-harness.failure-investigation.enabled` | Stay **false** in Phase 3 |
| `delivery.provider.allowlist` | Still required for Cursor (existing privacy layer) |

**Rollout:** dev → single-tenant pilot → enable framework-validation per project.setting (future) before global default true.

**Backward compatibility:** When master flag false, workers execute **identical** code paths to pre-Phase-3 (bridge short-circuits).

---

## 6. Testing strategy (Phase 3)

| Layer | Type |
|-------|------|
| Unit | `AgentHarnessJobBridge` mapping, flag gating, path resolution |
| Fake sidecar | Convert integration test with `fake-sidecar.mjs` (no API key) |
| Failure injection | Kill sidecar mid-task; worker lease reclaim; verify **no second agent** row |
| Recovery policy | Tests for policy **A**: poll timeout → `INTERRUPTED` without `cancelRun` (mock sidecar asserting no cancel op) |
| Live opt-in | Existing `CursorActiveRunRecoveryLiveIT` updated when policy changes |
| Regression | Full `delivery.agent.harness.*` + convert worker tests unchanged when flags off |

---

## 7. Dependencies and risks

| Dependency | Risk |
|------------|------|
| `@cursor/sdk` detached runs | Cannot `wait()`; poll-only reconcile — status may stall “running” indefinitely |
| Job `maxRuntime` vs agent budget | Mismatch causes lease reclaim during legitimate agent work |
| `JsonlLocalAgentStore` | Requires Java store lock for all sidecars (proven cross-process) |
| Compile / publication | Agent must not write outside isolated workspace; promotion of agent changes needs explicit “apply patch” step (Phase 3.5?) |
| Heal confusion | Two Cursor paths (heal client vs harness) — document operational separation |

---

## 8. Open decisions (need product/engineering sign-off)

1. Exact convert hook point: after emit zip only, or also after compile-only dry paths?
2. Whether harness-modified workspace merges back via existing codegen writers or human review gate only (advisory Phase 3).
3. Recovery default: **A (INTERRUPTED)** vs extended poll **B**.
4. Per-tenant concurrent Cursor runs beyond per-job cap.
5. Async reconcile worker (**C**) — Phase 3 or Phase 4?

---

## 9. Recommended implementation sequence

1. **`AgentHarnessJobBridge`** + tests (flags off; no worker call).
2. **Recovery policy A** in `agent.mjs` + coordinator semantics; update live IT; remove implicit cancel-as-default.
3. **Lease/budget alignment** — map job max runtime → `AgentPolicy`; verify `LeaseKeeper` during long fake-sidecar run in convert IT.
4. **Convert hook** behind `framework-validation.enabled` — copy emit output → harness task → record outcome on job; **no** change to `EmitPhase` internals.
5. **Failure-injection tests** (fake sidecar + lease reclaim).
6. **Docs / PRODUCT** + pilot flag checklist.
7. Defer Execute/Hunt/Generate integration to Phase 4+.

---

## 10. Files / components expected to change (Phase 3)

| Area | Files (anticipated) |
|------|---------------------|
| Bridge | `delivery.agent.harness.AgentHarnessJobBridge` (new), maybe `AgentHarnessOutcome` |
| Worker | `ConversionWorker` and/or `ConversionJobRunner` (surgical hook), possibly shared helper in `delivery.job` |
| Config | `AgentHarnessProperties`, `application.properties` (new reconcile flags only; defaults false) |
| Sidecar | `tools/cursor-agent/agent.mjs` (reconcile policy A) |
| Coordinator | `AgentHarnessCoordinator` (INTERRUPTED vs continue polling metadata) |
| Tests | `ConversionWorker*Test`, new `AgentHarnessJobBridgeTest`, failure-injection IT |
| Docs | `PRODUCT.md`, `CHANGELOG.md`, handoff completion |

**Explicitly unchanged in early commits:** `ProvePhase`, `HealCascade`, `EmitPhase` logic, `HuntWorker`, `ExecuteWorker` (until Phase 4).

---

## Approval

| Role | Status | Date |
|------|--------|------|
| Product | Pending | — |
| Engineering | Draft | 2026-11-12 |
