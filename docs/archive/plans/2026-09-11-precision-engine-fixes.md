# Precision Engine — Review Fix Plan

**Goal:** Close all defects from the 2026-09-11 review without changing Keel default behavior.

**Status:** Implemented 2026-09-11. All precision-related unit/API tests pass.

## Tasks

1. [x] **Budget + provider gates** — Charge cap only before real `groundRank`/`solve`; `isRuntimeReady()` checks API key; no double-call on unavailable provider.
2. [x] **Solve step parsing** — `PrecisionBindService.solveOnce` accepts Cursor `steps[]` via `FreeInventHealer.parseInventResponse`.
3. [x] **Job-scoped config** — `PrecisionJobConfig` on `ConversionJobRequest`; remove `System.setProperty` from workers.
4. [x] **Shared heal budget** — `HealCascade.attachPrecisionBudget`; cursor pick/solve consume same per-job budget on Precision jobs.
5. [x] **Liveness filter** — Apply `CandidateLivenessProbe` to Precision shortlist (same as Keel heal).
6. [x] **Per-TC telemetry** — Per-TC fallback flag + call delta; precision tier (`groundRank`/`solve`) in `healTier` merge ranks.
7. [x] **Portal polish** — `authoringEngine` on job API; UI warn when Precision disabled server-side.
8. [x] **CLI** — `--authoring-engine` flag.
9. [x] **Tests** — `PrecisionBindServiceTest`, `PrecisionJobConfigTest`, `AuthoringEngineTest`, `AuthoringEngineApiTest` (budget preflight, solve steps, cap exceeded, API field).
10. [x] **Pipeline All-in-one** — `authoringEngine` on pipeline API + Generate page toggle; `PortalJobStarter` stamps engine on jobs.
