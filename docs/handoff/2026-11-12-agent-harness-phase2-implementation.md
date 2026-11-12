# Agent harness Phase 2 — implementation handoff

## Shipped

- Sidecar `tools/cursor-agent/agent.mjs` (local SDK agent, NDJSON).
- Java `delivery.agent.harness.cursor.*` + workspace copy, `SdkTouchMarker`, `AgentStoreLock`, reconcile rules.
- Durable `sdk-touch.marker` written by sidecar **before** `Agent.create`.
- `shell` denied; per-parent-job concurrency only (documented in phase-2 plan).

## Test

```text
cd keel-portal && mvn test -Dtest=delivery.agent.harness.*
```

## Manual live (opt-in)

1. `cd tools/cursor-agent && npm install`
2. Set `CURSOR_API_KEY`, `delivery.provider.allowlist` includes `cursor`
3. Enable `delivery.agent-harness.enabled=true` and `cursor.enabled=true` in a test JVM only
4. Live: `CursorAgentRuntimeLiveIT` with `-Dkeel.agent.cursor.live=true` (see spike notes)
5. Node spike: `node tools/cursor-agent/live-spike.mjs` → `docs/handoff/cursor-sdk-live-spike-output.json`

## Live validation (2026-11-12)

Recorded in **`docs/handoff/2026-11-12-cursor-sdk-spike-notes.md`**. All opt-in scenarios passed on live API key; post-`c97c7ad` fixes: first-run reconcile, NDJSON line filtering, marker guard in sidecar.

## Phase 3 readiness

**No** — workflow integration (EmitPhase) not wired; harness still default off.

## Known limitations

- Install-wide concurrent Cursor runs not capped.
- Cancel confirmation path simplified for FAKE/Cursor cancel op when execute not in same JVM.
- `Agent.get` local lookup not used; reconcile uses marker + store + optional list (live behavior in spike notes TBD).
