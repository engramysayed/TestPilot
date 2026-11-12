# Cursor SDK live validation (Phase 2 opt-in spike)

**Date:** 2026-11-12  
**SDK:** `@cursor/sdk` 1.0.27  
**Commit under test:** `c97c7ad` (Phase 2) plus post-spike fixes (reconcile first-run, NDJSON noise tolerance, sidecar marker guard) — see local commit after this doc.  
**Machine evidence:** [cursor-sdk-live-spike-output.json](./cursor-sdk-live-spike-output.json) (regenerated 2026-11-12T15:12Z).

Feature flags and workers were **not** changed; validation is opt-in only.

---

## How to reproduce

### Node spike (scenarios 1–7)

From repo root, with a real `CURSOR_API_KEY`:

```bat
call api-keys.local.bat
cd tools\cursor-agent && npm install
cd ..\..
node tools\cursor-agent/live-spike.mjs
```

Exit code `0` and stderr lines `[PASS] …` for every scenario id. JSON written to `docs/handoff/cursor-sdk-live-spike-output.json`.

### Fake sidecar (CI-safe)

```bat
cd keel-portal
mvn test -Dtest=delivery.agent.harness.*
```

### Java coordinator + real sidecar (scenario 8)

```bat
call api-keys.local.bat
cd keel-portal
mvn test -Dkeel.agent.cursor.live=true -Dtest=CursorAgentRuntimeLiveIT
```

Requires `tools/cursor-agent/agent.mjs` on disk (walks up from `keel-portal` cwd). Sets `delivery.provider.allowlist=cursor` in-test only.

### Cross-process store lock (required before push)

```bat
cd keel-portal
mvn test -Dtest=AgentStoreLockCrossProcessIT
```

Spawns separate JVMs (`AgentStoreLockHold`, `AgentStoreLockTryMain`): peer holds `cursor-sdk-state.store.lock` → second process `TIMEOUT`; after kill → `ACQUIRED`. Also verifies `CursorSidecarClient` **cancel/reconcile** ops block with `store lock timeout` while peer holds lock (fake sidecar).

In-process thread test (not sufficient alone):

```bat
mvn test -Dtest=AgentStoreLockTest
```

### Active-run recovery after sidecar kill (opt-in live)

```bat
call api-keys.local.bat
cd keel-portal
mvn test -Dkeel.agent.cursor.live=true -Dtest=CursorActiveRunRecoveryLiveIT
```

Kills real `agent.mjs` after `run.started`, persists `RUNNING` + ids, runs **`CursorActiveRunRecoveryRunner` in a fresh JVM** → coordinator `execute` with `op=reconcile`.

---

## Results summary

| Req | Scenario | Result | Evidence |
|-----|----------|--------|----------|
| 1 | Real agent task via `agent.mjs` | **PASS** | Spike `1.execute_real_sidecar`; Java `CursorAgentRuntimeLiveIT` (~19s) |
| 2 | Persistence / resume across processes | **PASS** with limits | Spike `2a`–`2c`; see limitations |
| 3 | In-process cancel | **PASS** | Spike `3.cancel_in_process` → `CANCELLED` |
| 4 | Cancel after sidecar kill | **PASS** | Spike `4.cancel_after_kill` — separate `cancel` op `code=0` |
| 5 | Reconcile after interrupted execution | **PASS** | Spike `5.marker_blocks_blind_create` → `RECONCILE_FAILED`; Java reconcile allows first `PENDING` launch |
| 6 | Duplicate / idempotency | **Observed, not dedup** | Spike `6` — second `send` errors: *already has active run* |
| 7 | Two-process shared store | **Partial** | Spike `7` — concurrent `Agent.create` without lock: 2 NDJSON lines, valid JSON; **no SDK-level lock** |
| 8 | Java store lock (in-process) | **PASS** | `AgentStoreLockTest.secondAcquireTimesOut` |
| 9 | Cross-process store lock | **PASS** | `AgentStoreLockCrossProcessIT` (2026-11-12) |
| 10 | Active run recovery (live) | **PASS** | `CursorActiveRunRecoveryLiveIT` ~132s (2026-11-12) |

---

## Verified behaviors (live API / processes)

1. **Execute path** — `agent.mjs` `op=execute` runs a real local SDK agent against an isolated `taskDir/workspace`, emits `run.started`, terminal `result` (`COMPLETED` in spike), and sets `sdk-touch.marker` with `sdkTouched: true` before `Agent.create` returns.
2. **Cross-process store** — After sidecar exit, a new Node process with the same `cursor-sdk-state` path lists the agent (`Agent.list`), `Agent.resume` succeeds, `Agent.getRun` reports `finished`.
3. **Cancel** — `control/cancel.requested` during execute → `CANCELLED`. After SIGKILL once `run.started` was observed, a fresh sidecar `cancel` operation completes with exit `0`.
4. **Marker reconcile** — Pre-existing `sdk-touch.marker` with `sdkTouched: true` causes sidecar to refuse blind `create` (`RECONCILE_FAILED`). Sidecar no longer overwrites an existing touched marker with `sidecar_started`.
5. **Java E2E** — `AgentHarnessCoordinator` + real `agent.mjs`: workspace copy, marker file, `COMPLETED` result (live IT).

---

## Failures found and fixes during validation

| Issue | Symptom | Fix |
|-------|---------|-----|
| First `execute` always `INTERRUPTED` | Missing marker treated like ambiguous crash on `PENDING` | `AgentHarnessReconcile`: allow create when marker file absent and task `PENDING`; do not mark interrupted |
| Live IT stdout parse error | Non-JSON line on sidecar stdout (column 3 `:`) | `CursorSidecarSession`: skip lines not starting with `{` |
| Blind create after SDK touch | Marker reset on sidecar start | `agent.mjs`: refuse create when `sdkTouched: true`; do not clobber existing marker |

---

## Active-run recovery path (verified live, 2026-11-12)

After execute sidecar death while the SDK run is still **detached/running**:

1. **Do not** call `Agent.create` again (coordinator uses `RUNNING` + persisted `agentId`/`runId`).
2. **Do not** rely on `Agent.listRuns` (still fails after resume in spike 2b).
3. **Do not** call `Run.wait()` on a detached handle from `Agent.getRun` — SDK error: `Cannot wait on a detached running run`.
4. **Working path:** sidecar `op=reconcile` with `runId` → `Agent.getRun` **poll** until terminal (bounded by `reconcileMaxPollMs`) → if still running, `Agent.cancelRun` → poll again → emit terminal `result`. Coordinator fix: `RUNNING` retry uses reconcile sidecar (`runtime.start`), not `resume()` only.
5. Java holds `AgentStoreLock` for entire sidecar session (`CursorSidecarClient`).

Live IT outcome: same `agentId`/`runId`, no extra `agents.ndjson` row, `result.json` persisted; terminal **COMPLETED** or **CANCELLED** (after poll timeout + cancel fallback) in ~132s.

## Limitations (not claimed as verified)

- **`Agent.listRuns` after resume** — Spike `2b`: `resume=true` but `Agent.listRuns` throws `AgentNotFoundError` for the same agent id. Resume path must not rely on `listRuns` alone.
- **Idempotency** — SDK rejects a second in-flight `send` with *already has active run*; **not** silent deduplication. Keel must use reconcile + marker rules, not assume strict idempotency keys.
- **Concurrent store writes** — Two unsynchronized `Agent.create` on one `JsonlLocalAgentStore` produced two lines without parse corruption in one run; **no cross-process lock in the SDK**. Production path must use Java `AgentStoreLock` around store mutations (verified separately).
- **Install-wide concurrency** — Still only per-parent-job cap in coordinator; not validated against Cursor cloud quotas.
- **Phase 3** — No `EmitPhase` / worker wiring; harness flags remain default **off**.

---

## Spike scenario detail (from JSON)

- **2b** — `listRunsOk: false`, error `Agent … not found` while `resume: true`.
- **6** — `r2status: not_attempted`, `r2error: … already has active run`.
- **7** — `verdict: no_line_corruption_observed`, `lines: 2`.

---

## Phase 3 readiness

**No** — Live SDK and Java coordinator paths validated opt-in; worker integration and production flag rollout remain Phase 3.
