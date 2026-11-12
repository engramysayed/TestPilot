# Agent harness — Phase 1 handoff (2026-11-12)

## Shipped

- Java package `delivery.agent.harness` with bounded task model, policy/tool registry, context builder, router (always `FAKE` in Phase 1), disk store under `agent-runs/`, and `AgentHarnessCoordinator`.
- `FakeAgentRuntime` only — **no** `tools/cursor-agent`, no subprocess, no worker integration.
- Properties: `delivery.agent-harness.*` via `AgentHarnessProperties` (defaults false).

## How to test

```text
cd keel-portal
mvn test -Dtest=delivery.agent.harness.*
```

## Known gaps (by design)

- `AgentTaskRouter` hard-selects `FAKE` until Phase 2 SDK contract is finalized.
- No `EmitPhase` / `ExecuteWorker` hooks; harness disabled in production config.
- Concurrent run cap is counted per parent job directory only (no cluster-wide limit).

## Phase 2 next

- Inspect real `@cursor/sdk` streaming/cancel/resume in a sidecar prototype.
- Add `CursorAgentRuntime` behind `delivery.agent-harness.cursor.enabled` without changing worker behavior until Phase 3.
