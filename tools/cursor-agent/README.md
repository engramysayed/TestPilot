# Keel cursor-agent sidecar (Phase 2)

Node sidecar for the unified agent harness. **Does not replace** `tools/cursor-heal/heal.mjs`.

## Protocol

- **stdin:** one JSON request (`protocolVersion: 1`)
- **stdout:** NDJSON events (`ack`, `run.started`, `sdk.event`, `result`, `error`)
- **Workspace:** must be `taskDir/workspace` (isolated copy prepared by Java)
- **SDK store:** `taskDir/cursor-sdk-state/` (`JsonlLocalAgentStore`) — single-writer via Java `cursor-sdk-state.store.lock`; cancel while running uses `taskDir/control/cancel.requested` (in-process poll)

## Crash recovery

Sidecar writes `taskDir/sdk-touch.marker` atomically:

1. `sdkTouched: false` on start  
2. `sdkTouched: true` **before** `Agent.create`  

Java never infers safety from an empty `agents.ndjson`.

## Tools (Phase 2)

`shell` is **denied** (no Maven). Allowlist is mapped from Keel `AgentPolicy` in Java.

## Run manually

```bash
cd tools/cursor-agent && npm install
export CURSOR_API_KEY=...
echo '{"protocolVersion":1,"op":"execute",...}' | node agent.mjs
```

## Tests

Maven uses `src/test/resources/cursor-agent/fake-sidecar.mjs` (no API key).
