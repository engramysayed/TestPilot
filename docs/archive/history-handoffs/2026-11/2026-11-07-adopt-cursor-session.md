# Adopt A1–A6 + Suites E/F — session handoff (2026-11-07)

**Repo:** `TestPilot`  
**Tests (spot):** `TunnelBrokerApiTest`, `FailurePackApiTest`, `StructuredPlanApiTest`, `RunMemoryApiTest`, `SuiteSliceEApiTest`, `VisionPolicyApiTest` — PASS offline.

## Shipped this session

| ID | Deliverable |
|----|-------------|
| **E** | TC library API + suites picker |
| **F** | Portal schedule worker (`delivery.suites.portal-schedule-worker.enabled=true`) |
| **A1** | Failure pack ZIP + manifest (`usedVision`) |
| **A3** | Structured plans API + UI; suite pin + job snapshot |
| **A2** | `KeelPortalCli`, skill, `-Pkeel-cli` fat jar |
| **A4** | Run memory CRUD + heal injection (Ollama + Cursor JSON) |
| **A5** | Cloud tunnel broker, job `baseUrl` binding, `tunnel start` relay |
| **A6** | Vision policy API + job/failure-pack vision summary |

Deep dive: `docs/history/handoffs/2026-11/2026-11-07-adopt-a5-tunnel-broker.md`.

## Live verification

| Gate | Status |
|------|--------|
| Tunnel broker (embedded Tomcat) | **PASS** — `TunnelBrokerApiTest` |
| Tunnel live (full JVM) | **PASS** (2026-11-07) — `powershell -File scripts/tunnel-live-smoke.ps1` |
| HOME portal UI smoke | **PASS** (2026-11-07) — login, nav links, `/suites` picker + structured plan panel, `/execute` tunnel chip, `/run-memory`, `/structured-plans` on `http://127.0.0.1:8080` |

## Bugs found / fixed

1. **Agent relay 403** — `TunnelAgentExecutor` omitted `X-Keel-Requested-With: Keel` on poll/complete (D11 filter). **Fixed.**
2. **Live smoke script** — `java -jar` with default `product` profile failed without `KEEL_DB_PASSWORD`; double enroll caused 404. Script now uses in-memory H2 + badge token. **Fixed.**
3. **`TunnelLocalProxy` Connection header** — 502 through full relay chain. **Fixed.**

## Open defects

- ~~`tunnel start` always re-enrolls~~ — fixed (credential cache + `reuseToken`; `--re-enroll` to force).
- ~~Stale `tunnel-index/*.json` after re-enroll~~ — fixed (superseded index deleted).
- No mTLS / WebSocket tunnel.
- Token-in-URL security (edge rate limits required in prod).

## No commit / push unless requested

Tree may remain dirty with unrelated local files — stage only adopt paths when committing.
