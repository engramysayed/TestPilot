# Phase 2 — Cursor Agent Runtime Spike (technical plan)

**Status:** Revisions applied (2026-11-12) — **awaiting your re-approval** — **no implementation yet**  
**Builds on:** Phase 1 (`09bc616`), inspection chat 2026-11-12  
**Review packet:** [`../reviews/2026-11-12-agent-harness-phase1-review.md`](../reviews/2026-11-12-agent-harness-phase1-review.md)

---

## Approved product decisions (2026-11-12)

| # | Decision |
|---|----------|
| 1 | **Isolated workspace:** agent operates only under `taskDir/workspace/` (copy), never the original emit tree by default. |
| 2 | **No shell in Phase 2:** SDK `shell` (and Maven) **denied** — no unrestricted shell or `mvn`. |
| 3 | **Concurrency:** enforce **`max-concurrent-runs` per parent job id** only; document that there is **no install-wide limit** in Phase 2. |
| 4 | **Interface:** keep synchronous `AgentRuntime` (`start` → `getResult` blocks). |

Non-goals from the Phase 2 brief are **unchanged** (no workers, no heal changes, no REST/UI, flags default off).

---

## 1. Architecture and integration points

```text
AgentHarnessCoordinator (unchanged entry; surgical edits only)
  → AgentTaskRouter → CURSOR when harness+cursor enabled
  → AgentRunStore (projectRoot from caller; tenant via ScopePaths at Phase 3)
  → AgentRuntime
       FakeAgentRuntime (tests)
       CursorAgentRuntime (new)
            → CursorSidecarClient
            → ProcessSupervisor (deadline + cancel)
            → tools/cursor-agent/agent.mjs
                    → @cursor/sdk 1.0.27 (local agents)
                    → JsonlLocalAgentStore @ taskDir/cursor-sdk-state/
```

**Unchanged:** `tools/cursor-heal/heal.mjs`, `CursorHealClient`, all job workers, `EmitPhase`, `ProvePhase`, `HealCascade`.

---

## 2. Verified SDK surface (`@cursor/sdk` 1.0.27, in-repo types)

Types under `tools/cursor-heal/node_modules/@cursor/sdk/dist/esm/` are the contract until live spike tests run.

| API | Declared behavior | Crash recovery / dedup |
|-----|-------------------|-------------------------|
| `Agent.create` | Creates local agent; `local.cwd`, `tools`, `name`, `local.store` | **Does not** by itself imply idempotent create |
| `Agent.resume` | Resumes by `agentId`; must re-pass `tools` / `disallowedTools` | Depends on store having agent row |
| `Agent.list` | `ListAgentsOptions` with `runtime: "local"`, optional `cwd`, `store` | Lists agents; **filter by `cwd` optional** — not keyed by Keel task id unless we set `name` |
| `Agent.listRuns` | `agentId` + `runtime: "local"`, `store` | Lists run documents from store |
| `Agent.getRun` | `runId` + `runtime: "local"`, `cwd`, `store` | Returns `Run` handle if row exists |
| `Agent.cancelRun` | `runId` + local options | Cancels without holding in-memory `Run` |
| `agent.send` | Returns `Run` | `SendOptions.idempotencyKey` documented on type — **runtime semantics not verified in-repo** (no implementation text for dedup) |
| `run.stream` / `run.wait` / `run.cancel` | Stream, block, cancel | `Run.status` → `running` \| `finished` \| `error` \| `cancelled` |
| `Agent.get` (agent metadata) | Comment: **cloud-only** for full lookup | **Do not rely** for local reconcile |
| `Agent.prompt` | One-shot | **Not used** for harness |

**Local persistence (shared across processes):**

`JsonlLocalAgentStore(rootDir)` writes `agents.ndjson`, `runs.ndjson`, `run_events.ndjson`, `checkpoints.ndjson` under `rootDir`. Same `store` path must be used on `create`, `resume`, `list`, `getRun`, `cancelRun`.

SDK docs on the class say writes are serialized **process-wide** (in-process callbacks only). They do **not** claim multi-process file locking. **Treat concurrent writers from two Node processes as unsafe until the Phase 2 spike proves otherwise** (§3.4).

`LocalAgentDocument` includes: `agentId`, `cwd`, `status`, `activeRunId`, `name`, `sdkMetadata`.

### Phase 2 spike obligation (before calling runtime “proven”)

Run **opt-in live tests** (not default Maven) to record actual behavior for:

1. `SendOptions.idempotencyKey` — duplicate send with same key after crash (does it reject, no-op, or new run?).
2. `Agent.list({ runtime: "local", cwd, store })` — visibility of agents created in prior process.
3. `Agent.listRuns` + `getRun` — reattach to `running` run after JVM restart.
4. `Agent.cancelRun` on run left `running` in store after kill -9 sidecar.
5. `LocalSendOptions.force` — recovery from wedged run (document when safe to use; **not** default for reconcile).
6. **Two Node processes** appending to the same `JsonlLocalAgentStore` directory (execute + cancel) — watch for corruption/interleaved lines.

**Plan rule:** Until spike notes are checked in, production reconcile logic treats API guarantees as **unknown** and prefers **INTERRUPTED** over create.

---

## 3. Sidecar protocol (v1)

**Location:** `tools/cursor-agent/agent.mjs` (+ `package.json`, README). **`heal.mjs` untouched.**

**Process cwd:** Keel **repo root** (resolve `@cursor/sdk`). **Agent `local.cwd`:** canonical `taskDir/workspace` only.

**Transport:** one sidecar OS process per `execute` or per `cancel` / `reconcile` invocation; **stdout = NDJSON**, stderr = logs (no secrets).

### 3.1 Shared state path (critical for multi-process)

```text
agent-runs/{parentJobId}/{taskId}/
  workspace/                 # isolated copy (see §7)
  cursor-sdk-state/          # JsonlLocalAgentStore root — SAME for execute, cancel, reconcile
  launch-state.json
  task.json
  events.jsonl
  result.json
  modified-files.json        # manifest from isolated workspace
  context-manifest.json
```

**`Cursor.configure({ local: { store: new JsonlLocalAgentStore(cursor-sdk-state) } })`** in every sidecar entrypoint before any `Agent.*` call — **only while holding the store lock** (§3.4).

### 3.4 `cursor-sdk-state/` — multi-process safety (mandatory)

**Inspection (SDK 1.0.27 types):** `JsonlLocalAgentStore` serializes writes **within one Node process** only. There is **no documented cross-process lock** on `agents.ndjson` / `runs.ndjson`.

**Requirement:** Phase 2 **must not** rely on two sidecar processes writing the store concurrently without coordination.

**Synchronization strategy (default — implement unless spike proves SDK is safe):**

| Mechanism | Role |
|-----------|------|
| `taskDir/cursor-sdk-state.store.lock` | Exclusive lock (Java `FileLock` preferred; sidecar may use equivalent `proper-lockfile` while lock holder is Node-only). **Hold during any SDK store mutation.** |
| `taskDir/control/cancel.requested` | **Primary cancel while execute is alive:** execute sidecar polls; calls `run.cancel()` **in-process** (no second writer). |
| `op: cancel` sidecar process | **Secondary:** runs only when (a) execute process is **dead**, or (b) Java holds store lock and execute is gone; acquires lock → `cancelRun` / read store → release. |
| `op: reconcile` | Same as cancel: **no concurrent execute**; acquire lock first. |

**Rules:**

1. At most **one** process may hold `store.lock` and mutate `cursor-sdk-state/` at a time.
2. **Never** spawn `cancel` or `reconcile` with live `execute` **unless** cancel is via `control/cancel.requested` only (execute still holds lock).
3. If lock cannot be acquired within deadline → **`INTERRUPTED`** (do not touch store).
4. Spike doc must record result of deliberate two-process append test; if unsafe, lock strategy stays **mandatory**.

### 3.2 Operations

| `op` | When | Process |
|------|------|---------|
| `execute` | Coordinator `execute` | create/resume → send → stream until terminal → `result` line |
| `cancel` | Coordinator `cancel` or timeout handler | read `launch-state` + store → `cancelRun` / `run.cancel` via resume |
| `reconcile` | Retry after crash / `LAUNCHING` without ids | read store + optional `list`/`listRuns`; **no create** |

Request envelope: `{ "protocolVersion": 1, "op": "...", "taskId": "...", "storePath": "...", "workspacePath": "...", ... }`.

Structured errors: `MISSING_API_KEY`, `WORKSPACE_DENIED`, `RECONCILE_FAILED`, `SDK_ERROR`, `SIDECAR_TIMEOUT`, etc.

### 3.3 Tool policy (Phase 2)

Map `AgentPolicy.allowedTools` → SDK **`tools`** allowlist. **Always** `disallowedTools` includes `shell`, `mcp`, `task`, `webFetch`, `webSearch`, `delete` (unless later task types need edit-only repair).

**No Maven / shell** — framework compile validation remains Java (`EmitCompileCheck`) in Phase 3+.

---

## 4. Cancellation across separate sidecar processes

### Problem

`execute` runs a long-lived sidecar process A. **Cancel** or **timeout** may spawn process B while A is still alive or after A died. Both must see the same agent/run.

### Design

1. **Single store directory:** `taskDir/cursor-sdk-state/` — source of truth for `agentId` / `runId` / run status **when rows exist** (never the only signal for “safe to create”).
2. **`launch-state.json`** (Java-owned, atomic writes) mirrors: `launchState`, `agentId`, `runId`, `sidecarPid` (best-effort), `cancelRequestedAt`, `attempt`, `idempotencyKey`, and **Keel-owned SDK touch flags** (§5.3).
3. **Process A (`execute`):**
   - Acquires `cursor-sdk-state.store.lock` before first SDK call; holds until exit.
   - Persists ids to `launch-state` + `task.json` on first `agent.created` / `run.started` events.
   - Streams to `events.jsonl`.
   - Polls `control/cancel.requested`; on timeout or flag → in-process `run.cancel()`; emits lifecycle events.
4. **Cancel while A is alive (primary):**
   - Java writes `control/cancel.requested` + `launchState=CANCEL_REQUESTED`.
   - **Does not** spawn process B touching the store.
   - Waits for A’s NDJSON `cancel.confirmed` or terminal status.
5. **Process B (`cancel` op) — only when A is dead:**
   - Verify A not alive (`ProcessSupervisor` / pid check).
   - Acquire `store.lock` (timeout bounded).
   - Read `launch-state.json`; `cancelRun` / store inspection.
   - Release lock; emit `cancel.confirmed` or `cancel.unconfirmed`.
6. **Java coordinator:**
   - **Does not** set task `CANCELLED` until cancel confirmed per §4 timeout table (never on timer alone).
7. If A wedged and in-process cancel fails: kill A, then **only then** run B with store lock — else `INTERRUPTED`.

### Timeout vs cancel

| Outcome | Condition |
|---------|-----------|
| `TIMED_OUT` | Wall clock exceeded; cancel sent; store/run terminal **or** process tree dead; emit `TIMED_OUT` in `launch-state` and task |
| `CANCELLED` | User cancel; same confirmation rules |
| `INTERRUPTED` | Cancel could not be confirmed; ambiguous store; reconcile failed |

**Never** mark `TIMED_OUT`/`CANCELLED` on timer alone without confirmation step above.

---

## 5. Lifecycle, recovery, and idempotency

### 5.1 Launch state machine (Java `launch-state.json`)

| State | Meaning |
|-------|---------|
| `CREATED` | Task on disk, never launched |
| `LAUNCHING` | Sidecar spawn requested; ids may be missing |
| `RUNNING` | `agentId` + `runId` persisted |
| `CANCEL_REQUESTED` | Cancel/timeout path active |
| `COMPLETED` / `FAILED` / `CANCELLED` / `TIMED_OUT` / `INTERRUPTED` | Terminal |

`AgentTaskStatus` stays aligned; do not replace Phase 1 enum unnecessarily.

### 5.2 Ordering (crash-safe)

1. Materialize `workspace/` copy (§7).
2. Write `launch-state.json` → `LAUNCHING` + `idempotencyKey` = `keel:{parentJobId}:{taskId}:a{attempt}`.
3. Start sidecar `execute`.
4. On `agent.created` / `run.started` NDJSON → atomic update `launch-state` + `task.json` (ids).
5. On terminal NDJSON → `result.json`, terminal launch + task status.

### 5.3 Reconcile rules (no blind create)

**Core rule:** **Never** treat an empty or missing `agents.ndjson` row (or zero `Agent.list` candidates) as proof that the SDK never created an agent. The SDK may have created an agent before persisting a local record.

**Durable SDK touch marker** (sidecar-owned — **not** stdout alone):

File `taskDir/sdk-touch.marker` (atomic write-tmp-rename by sidecar):

1. On sidecar start: write `{"sdkTouched":false,"phase":"sidecar_started"}`.
2. **Immediately before `Agent.create`:** write `{"sdkTouched":true,"phase":"create_requested"}` — **must complete before** any SDK create call.
3. Optional follow-ups (`create_returned`, `send_requested`) update the same file; `sdkTouched` stays `true`.

Java NDJSON `sdk.phase` events are **telemetry only** — recovery **must not** depend on the JVM having persisted stdout before crash.

**Recovery rule (final):**

| Marker state | Action on retry |
|--------------|-----------------|
| Definitely `sdkTouched: false` and phase proves no SDK op started | May `Agent.create` |
| `sdkTouched: true` | **Never create again** — reconcile or `INTERRUPTED` |
| Missing, unreadable, parse error, or ambiguous | **`INTERRUPTED`** |

**Decision table** when coordinator retries `execute`:

| Prior state | Action |
|-------------|--------|
| `COMPLETED` + `result.json` | Return cached result |
| `RUNNING` + ids in `launch-state` | `reconcile` with store lock, execute dead → resume / `getRun`+wait; **no** `Agent.create` |
| `LAUNCHING`, ids present | Same as `RUNNING` — reconcile only |
| `LAUNCHING`, no ids, **`sdkTouched === true`** OR any `sdk.phase` event in `events.jsonl` | **`INTERRUPTED`** — creation may have started; outcome unknown (**no create**) |
| `LAUNCHING`, no ids, **`sdk-touch.marker` explicitly `sdkTouched: false`** and sidecar never reached create | May start fresh `execute` |
| `LAUNCHING`, no ids, marker **`sdkTouched: false`**, sidecar exited with only `sidecar_started` phase | **Creation proven not started** → **one** new `Agent.create` allowed |
| `LAUNCHING`, no ids, marker **missing / unreadable / ambiguous** | **`INTERRUPTED`** |
| `LAUNCHING`, no ids, marker **`sdkTouched: true`** | **`INTERRUPTED`** or reconcile only — **never create** |
| Store/list: exactly **one** candidate agent/run | Resume; **no** `Agent.create` |
| Store/list: **>1** or ambiguous | **`INTERRUPTED`** + `RECONCILE_FAILED` |
| Spike proves list/idempotency unreliable | Tighten default: any `LAUNCHING` without ids → **`INTERRUPTED`** unless `sdkTouched === false` with **strong** exit proof |

**Agent name:** `name: "keel-harness:{taskId}"` at `create` for human/list debugging only — **not** used to override the rules above.

**Forbidden:** “No row in `agents.ndjson` → safe to create.”

### 5.4 Idempotency (honest)

- Type surface exposes `idempotencyKey` on `AgentOptions` / `SendOptions`.
- **No in-repo proof** of duplicate-send prevention.
- Plan: set key on first `send`; log outcome in spike doc; **if behavior is not strict dedup**, rely on reconcile rules + **INTERRUPTED** rather than second `create`.

---

## 6. Timeout — full lifecycle

1. **Deadline:** `task.budget().maxRuntime()` from `delivery.agent-harness.max-runtime-minutes`.
2. **Java:** `ProcessSupervisor` deadline covers **entire** sidecar process for `execute` (from spawn until NDJSON terminal or kill).
3. **Sidecar:** watches wall clock; at soft limit → `run.cancel()` → continue reading until `cancelled`/`finished`/`error` event or short grace (e.g. 30s).
4. **On supervisor timeout:** Java sets `CANCEL_REQUESTED`, spawns `cancel` op (§4), then evaluates confirmation before `TIMED_OUT`.
5. **Events:** `TIMEOUT_ARMED`, `CANCEL_SENT`, `CANCEL_CONFIRMED`, `TERMINAL_TIMED_OUT` in `events.jsonl`.

---

## 7. Isolated workspace copy and change collection

### 7.1 Copy (before sidecar)

On first `execute` (or at `createTask` if workspace path empty):

1. **Source:** caller-supplied original path (e.g. generated framework dir) — **read-only** for agent; recorded in `context-manifest.json` as `sourceWorkspace`.
2. **Target:** `taskDir/workspace/` — recursive copy using existing safe copy patterns (no symlinks followed out of source; reject if source contains symlink escape).
3. **AgentTask.workspacePath** persisted as **absolute path to `taskDir/workspace`** (not original).

Re-runs reuse existing copy unless `attempt` increments and policy requests refresh (Phase 2: reuse copy).

### 7.2 Collecting changes (original untouched)

- Cursor/SDK edits only under `taskDir/workspace/`.
- Sidecar at end of `execute` walks workspace (respecting ignore list: `.git`, `node_modules`, large binaries) and emits **`modified-files.json`:**

```json
{
  "schemaVersion": 1,
  "sourceWorkspace": "...",
  "isolatedWorkspace": "...",
  "files": [
    { "relativePath": "src/...", "sha256Before": "...", "sha256After": "...", "action": "modified" }
  ]
}
```

- **Phase 2 does not apply** changes back to source tree or parent job artifacts.
- **`AgentRunResult.modifiedFiles`** = relative paths from manifest (for coordinator persistence).
- **Validation (Java, Phase 2):** manifest paths must stay under `workspace/` root; reject `..` paths; optional max file count/size.

Phase 3 may add “promote permitted repairs” — out of scope here.

---

## 8. Concurrency (documented limitation)

- **Enforced:** `delivery.agent-harness.max-concurrent-runs` counts `STARTING` \| `RUNNING` tasks under `agent-runs/{parentJobId}/` **excluding current task id**.
- **Not enforced:** cross-job or install-wide totals.
- **Docs must state:** two parent jobs can each run `max-concurrent-runs` Cursor agents simultaneously.

---

## 9. Synchronous `AgentRuntime` mapping

| Method | `CursorAgentRuntime` behavior |
|--------|--------------------------------|
| `start` | Write `LAUNCHING`; spawn sidecar; block until NDJSON carries `run.started` (or fail); return `AgentRunHandle` |
| `getResult` | If execute sidecar still running, continue draining NDJSON (or share single execute invocation — implementation detail **internal** to runtime); block until `result` line; persist |
| `resume` | Spawn `reconcile`/`execute` continuation with ids from disk; same store path |
| `cancel` | Spawn `cancel` op; block until confirmed or give up → coordinator sets terminal |

Coordinator can keep `start` then `getResult` sequential call pattern from Phase 1.

---

## 10. Configuration and persistence

- All harness + `cursor.enabled` **false** by default.
- New: `delivery.agent-harness.cursor.command` (default `node tools/cursor-agent/agent.mjs`).
- `AgentTask.schemaVersion` = `2` for new tasks; loader treats missing as `1`.
- Tenant-aware `projectRoot`: unchanged (callers pass `ScopePaths.projectRoot` in later phases).
- Atomic writes: `delivery.store.Atomic` for all new JSON.

---

## 11. Files to create / modify (implementation checklist)

Same as prior plan, plus:

| New concern | Files |
|-------------|--------|
| Workspace copy | `AgentWorkspacePreparer.java` (or method on coordinator) |
| Launch state | `AgentLaunchState.java`, `AgentRunStore.saveLaunchState` (`sdkTouched`, phases) |
| Store lock | Java `FileLock` on `cursor-sdk-state.store.lock`; sidecar respects lock |
| In-process cancel | `control/cancel.requested`, execute poll loop |
| Cancel op | `agent.mjs` `op: cancel` (post-mortem + lock only) |
| Modified manifest | `ModifiedFilesManifest.java`, sidecar walker |
| Spike results | `docs/handoff/2026-11-XX-cursor-sdk-spike-notes.md` (from live IT) |

---

## 12. Tests (unchanged intent + additions)

**Default Maven:** fake sidecar NDJSON; in-process cancel via `cancel.requested`; cancel process B only after mock execute death + lock; store lock contention → `INTERRUPTED`; `sdkTouched` true + no ids → `INTERRUPTED`; proven pre-create exit → create allowed; **never** “empty agents.ndjson → create” test; timeout confirmation; workspace copy + manifest; traversal/symlink; schema compat; per-job concurrency; `FakeAgentRuntime` regression.

**Opt-in:** `CursorAgentRuntimeLiveIT` — records §2 spike answers; **excluded** from CI without `CURSOR_API_KEY`.

---

## 13. Documentation updates (on implementation complete)

- `docs/knowledge/CURRENT.md`
- `docs/ENGINEERING_HANDOFF.md`
- `tools/cursor-agent/README.md` (protocol + shared store + cancel process)
- Phase 2 implementation handoff + SDK spike notes
- Concurrency limitation in plan/README

---

## 14. Phase 3 readiness gate (preview)

Runtime ready for workflow integration only if: fake-sidecar suite green; spike notes answer §2; at least one manual live execute+cancel+reconcile documented; **zero** blind-create on ambiguous reconcile.

---

## Approval

| Reviewer | Approved to implement? | Date | Notes |
|----------|------------------------|------|-------|
| Owner | ☑ Approved (incl. durable `sdk-touch.marker` + store lock) | 2026-11-12 | Implementation in progress |

**Revisions in this revision (2026-11-12):**

- §5.3: **No create** from absent store rows; **`sdkTouched` / phase events** gate create vs **`INTERRUPTED`**.
- §3.4: **Store lock** + **in-process cancel**; secondary `cancel` op only when execute dead.

**Next step after approval:** implement Phase 2 per this plan only (no worker integration).
