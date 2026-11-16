# Local harness enabled — run evidence (2026-11-15)

**Scope:** Developer workstation only. `application.properties` in git **unchanged** (harness flags still false in repo). Local overrides via gitignored `keel-env.local.bat`.

## Configuration source

| Item | Location |
|------|----------|
| Harness flags (local only) | `KEEL_EXTRA_ARGS` in `keel-env.local.bat` |
| API key | `api-keys.local.bat` (`CURSOR_API_KEY` — not logged) |
| Provider allowlist | `application.properties` → `ollama,cursor,agentrouter,vision` |
| Portal start | `start-portal.cmd` (loads both `.bat` files) |

**Local flags added:**

`--delivery.agent-harness.enabled=true --delivery.agent-harness.cursor.enabled=true --delivery.agent-harness.automate-framework-repair.enabled=true`

## Blocker fixed

`keel-env.local.bat` had **LF-only** line endings; `cmd.exe` failed to load `KEEL_DB_PASSWORD` and broke startup. Converted to **CRLF** (file remains gitignored).

## Portal verification

| Check | Result |
|-------|--------|
| Build | `mvn -pl keel-portal -am -DskipTests package` OK |
| Start | `start-portal.cmd` → **Started PortalApplication** (~10s) |
| `GET /api/health` | `status=UP`, `installMode=shared`, `production=false` |
| `GET /api/ready` | `storeWritable=true`, `status=READY` |
| `CURSOR_API_KEY` | Present after `api-keys.local.bat` |
| Node / sidecar script | `node` v24.x; `tools/cursor-agent/agent.mjs` present |

Harness flags are passed via JVM args from `KEEL_EXTRA_ARGS` (not exposed on `/api/health`).

## Controlled Automate workflow

**Method:** `FrameworkAdaptationCursorCompileOnLiveIT#missingExpectedTest_compileOnQualification` — same `AutomateFrameworkAcceptanceGate.forConversionJob` path as `ConversionWorker`, disposable temp project (not customer store).

**Log:** `docs/handoff/phase6-17-local-e2e-harness.log`  
**Evidence:** `docs/handoff/phase6-12-compile-on-MISSING_TEST-local-e2e.json`

| Step | Outcome |
|------|---------|
| Contract pre-check (mandatory violation) | `FW_EXPECTED_TEST_PRESENT` |
| Cursor repair | Task `COMPLETED`, agent/run ids persisted |
| Reconciliation | 202 ms; copied `Login_Test.java` |
| Emit compile on repair candidate | OK |
| Independent revalidation | Passed |
| Promotion / publication (gate) | `ACCEPTED`; digest match repair-candidate → project |
| Operational JSON | `acceptanceTerminalReason=SUCCESS`, `agentRunStarted=true` |

**Not run:** Full portal UI CONVERT job with ZIP + `saveVersion` (would require logged-in project + Excel job). Acceptance + promotion semantics verified via compile-on qualification chain.

## Duration notes

- `timeToRunStartedMs` ~99s (operational JSON); `sidecarStartupMs` **unavailable** (6.14 instrumentation).