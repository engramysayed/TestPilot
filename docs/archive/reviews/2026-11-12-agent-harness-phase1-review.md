# Unified Agent Harness — Phase 1 review packet

**Date:** 2026-11-12  
**Scope:** Repo sync to `main`, Phase 1 harness foundation only (no Cursor SDK, no worker hooks)  
**Git tip (at review time):** `09bc616` on `main` (`keel/main`, `origin/main`)  
**Implementation handoff:** [`../handoff/2026-11-12-agent-harness-phase1.md`](../handoff/2026-11-12-agent-harness-phase1.md)  
**Changelog:** [`../../CHANGELOG.md`](../../CHANGELOG.md) (section 2026-11-12)

---

## 1. Objectives and success criteria

| Goal | Met? |
|------|------|
| Bring `main` up to date (commit, merge, push) | Yes |
| Merge/accept open PRs | N/A — no open PRs |
| Phase 1: task model, runtime interface, router, context, policy, tools, disk store, lifecycle | Yes |
| Fake/mock runtime tests before Cursor | Yes (`FakeAgentRuntime`) |
| Do **not** integrate Cursor SDK or job workers | Yes |
| Default flags off — existing workflows unchanged | Yes |

---

## 2. Git and remote state

### 2.1 Pre-harness sync

- Working branch before merge: `alerts-members-and-cleanup` (many commits ahead of old `main` at `43922ae`).
- Uncommitted portal work staged and committed as **`c265e5b`**: login/dashboard/refunds/suites/GFK templates and CSS, `start-portal.cmd`, `keel-env.local.bat.example`.
- Merged `alerts-members-and-cleanup` → **`main`**, pushed to **keel** and **origin**.
- **Not committed:** `ex-test/p1/heal-proposals/*.json` (local artifacts).

### 2.2 Phase 1 commit

- **`09bc616`** — `feat(agent): Phase 1 unified agent harness foundation (fake runtime only)`  
  32 files, +1106 lines (harness + docs).

### 2.3 Review metadata (process)

- Commits `c265e5b` and `09bc616` include `Co-authored-by: Cursor` in git metadata. Workspace policy prefers `rsayed <rsayed@axisapp.com>` without that trailer; adjust on future commits if required (`git commit-tree`).

---

## 3. Phase 0 context (discovery — no code)

Phase 0 (prior session) verified in source:

- All five job workers use `AbstractJobWorker.dispatch`.
- `EmitPhase` already runs `EmitCompileCheck` (`mvn test-compile`) before ZIP — Framework Validator must **add** review/repair, not replace Java compile gates.
- Cursor today: `tools/cursor-heal/heal.mjs` + `CursorHealClient` (one-shot JSON), not a multi-step harness.
- No pre-existing `AgentTask` / `agent-harness` types.

Phase 2 recommendation: extend or sibling Node sidecar for SDK streaming; Java adapter similar to `CursorHealClient`.

---

## 4. Phase 1 deliverables — component inventory

### 4.1 Package `delivery.agent.harness` (19 types)

| Component | Role |
|-----------|------|
| `AgentTaskType` | Versioned task kinds (`*_V1`) |
| `AgentTaskStatus` | Lifecycle enum including `INTERRUPTED` |
| `AgentTask` | Durable task record + `with*` helpers |
| `NewAgentTaskRequest` | Create input |
| `AgentProviderId` | `FAKE` (active), `CURSOR` (reserved) |
| `AgentRuntime` | Provider interface |
| `FakeAgentRuntime` | In-process stub; sync complete; resume materializes result if missing |
| `AgentRunHandle` / `AgentRunResult` | Provider identity and envelope |
| `AgentToolId` / `AgentToolRegistry` | Tool permissions by task type |
| `AgentPolicy` | Runtime limits + workspace roots |
| `AgentTaskRouter` / `AgentTaskPlan` | Type → provider + policy (**always FAKE in Phase 1**) |
| `AgentContextReference` / `AgentContextBuilder` / `AgentContextManifest` | Context package for disk |
| `AgentRunStore` | `agent-runs/{parentJobId}/{taskId}/` + atomic JSON writes |
| `AgentHarnessCoordinator` | `createTask`, `execute`, `cancel` |
| `AgentEvent` | `events.jsonl` lines |
| `AgentHarnessException` | Harness errors |

### 4.2 Configuration

- **`delivery.portal.AgentHarnessProperties`** — `@ConfigurationProperties(prefix = "delivery.agent-harness")`.
- Registered in `PortalApplication` `@EnableConfigurationProperties`.
- **`application.properties`** — all harness flags default **false/off** (see §5).

### 4.3 Disk layout

```text
{projectRoot}/agent-runs/{parentJobId}/{taskId}/
  task.json
  context-manifest.json
  events.jsonl
  result.json
```

Writes use `delivery.store.Atomic` (same pattern as `heal-proposals/`, run memory, etc.).

### 4.4 Orchestration behavior (review focus)

1. **`createTask`** — Always allowed (even when harness disabled). Router plans policy; workspace must match allowed roots derived from requested workspace path.
2. **`execute`** — Requires `delivery.agent-harness.enabled=true`. Re-loads result if already `COMPLETED`. Supports resume when task on disk is `RUNNING` with `agentId`/`agentRunId`. Re-checks workspace against stored policy (tamper-resistant).
3. **`cancel`** — Propagates to runtime when handle present; sets `CANCELLED`.
4. **Concurrency** — `max-concurrent-runs` counted per **parent job id** under `agent-runs/` (not install-wide).

### 4.5 Explicit non-deliverables

- No `CursorAgentRuntime`, no `tools/cursor-agent/`, no changes to `heal.mjs`.
- No `ConversionWorker` / `EmitPhase` / `ExecuteWorker` hooks.
- No REST API, no JPA entities for tasks.
- `framework-validation.enabled` and `failure-investigation.enabled` bound but **unused** until Phase 3+.
- Router ignores `cursor.enabled` for provider selection (hard-coded `FAKE` until Phase 2).

---

## 5. Configuration reference

| Property | Default | Used in Phase 1 |
|----------|---------|-----------------|
| `delivery.agent-harness.enabled` | `false` | Yes — gates `execute` |
| `delivery.agent-harness.cursor.enabled` | `false` | Bound only |
| `delivery.agent-harness.max-concurrent-runs` | `2` | Yes |
| `delivery.agent-harness.max-runtime-minutes` | `15` | Yes (policy duration) |
| `delivery.agent-harness.max-retries` | `1` | Yes (`maxAttempts = retries + 1`) |
| `delivery.agent-harness.framework-validation.enabled` | `false` | No |
| `delivery.agent-harness.failure-investigation.enabled` | `false` | No |

---

## 6. Tests and verification

### 6.1 Automated tests

```text
cd keel-portal
mvn test -Dtest=delivery.agent.harness.*
```

**Recorded result (2026-11-12):** PASS (exit 0).

| Class | Coverage |
|-------|----------|
| `AgentHarnessCoordinatorTest` | Fake E2E, disabled harness, workspace tamper, resume from `RUNNING` |
| `AgentTaskRouterTest` | FAKE provider, tool sets per type |
| `AgentToolRegistryTest` | Policy enforcement |

### 6.2 Manual verification checklist

- [ ] `git log -1 --oneline` → `09bc616` on `main`
- [ ] `grep agent-harness src/main/resources/application.properties` → all defaults false
- [ ] No references to `CursorAgentRuntime` in `src/main/java`
- [ ] Existing portal start / Convert / Execute unchanged with defaults

### 6.3 Not covered by tests

- Wall-clock `TIMED_OUT` enforcement on long runs
- Worker lease interrupt + cross-process resume
- Install-wide concurrent run limits
- Invalid/malicious agent JSON from a real provider
- Spring `@Bean` wiring of coordinator in the portal

---

## 7. Documentation touched

| File | Purpose |
|------|---------|
| `CHANGELOG.md` | 2026-11-12 harness + sync note |
| `docs/knowledge/CURRENT.md` | Architecture table |
| `docs/ENGINEERING_HANDOFF.md` | Engineer pointer row |
| `docs/PRODUCT.md` | User-visible impact (none with flags off) |
| `docs/handoff/2026-11-12-agent-harness-phase1.md` | Implementation handoff |
| **This file** | Formal review packet |

Private path `D:\priv\testpilot\handoffs\RESUME-2026-10-15.md` was not present on the build machine.

---

## 8. Architecture decisions for reviewer sign-off

| # | Decision | Rationale | Question for reviewer |
|---|----------|-----------|------------------------|
| D1 | Files under `{projectRoot}/agent-runs/` | Aligns with `automate-runs/`, `heal-proposals/` | OK vs job work dir under `delivery-work`? |
| D2 | `delivery.agent.harness` outside Spring component scan | Phase 1 is library + tests only | Add `delivery.portal.service.agent` in Phase 3? |
| D3 | Router forces `FAKE` | Prevents accidental Cursor before Phase 2 | Remove override when sidecar contract is fixed? |
| D4 | `createTask` allowed when harness disabled | Simplifies unit tests | Should create also require `enabled=true` in prod? |
| D5 | Separate `AgentHarnessProperties` class | Matches `DeliveryCreditsProperties` pattern | Nest under `DeliveryPortalProperties` instead? |
| D6 | Jackson records for `task.json` | Consistent with other disk stores | Stable schema versioning field on task? |

---

## 9. Risks and open items

| Risk | Severity | Mitigation / next step |
|------|----------|------------------------|
| `cursor.enabled` implies Cursor but router uses FAKE | Low | Document + remove override in Phase 2 |
| No runtime timeout enforcement | Medium | Coordinator watchdog in Phase 2 with real runs |
| Per-job concurrency only | Medium | Install-level semaphore if needed |
| Commit author/co-author metadata | Process | Follow commit policy on next ship |
| Phase 3 duplicate validation vs `EmitCompileCheck` | Medium | Design: Cursor inspect → Java re-run compile always |

---

## 10. Phase 2 entry criteria (not started)

**Technical plan (awaiting approval, no code yet):** [`../handoff/2026-11-12-agent-harness-phase2-plan.md`](../handoff/2026-11-12-agent-harness-phase2-plan.md)

1. Spike real `@cursor/sdk` behavior: create agent, stream events, cancel, resume, usage.
2. Choose sidecar shape (`tools/cursor-agent/` vs extend `heal.mjs` with isolation).
3. Implement `CursorAgentRuntime` + subprocess protocol; keep workers unwired until Phase 3 acceptance tests exist.

---

## 11. Approval line

| Role | Name | Date | Approve Phase 1 foundation? | Notes |
|------|------|------|-----------------------------|-------|
| Owner / reviewer | | | ☐ Yes ☐ No | |
| Engineering | | | ☐ Proceed to Phase 2 spike | |

---

*Generated for human review of commit `09bc616`. Update this packet if follow-up fixes land on `main`.*
