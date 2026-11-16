# Phase 6.5 — Cursor repair quality and lifecycle qualification

**Date:** 2026-11-15  
**Scope:** Qualification only — no production flag changes, no `heal.mjs`, Policy A unchanged.

## Commands

**Deterministic qualification suite:**

```text
mvn -pl keel-portal "-Dtest=FrameworkRepairWorkspaceReconcilerTest,FrameworkRepairReconcilerResilienceTest,FrameworkRepairAcceptanceSafetyTest,FrameworkRepairArchitectureQualityTest,FrameworkAdaptationCursorReconcileIT,AutomateFrameworkAcceptanceGateTest" test
```

**Live matrix (opt-in, real Cursor):**

```text
call api-keys.local.bat
mvn -pl keel-portal test -Dkeel.agent.cursor.live.framework=true -Ddelivery.emit-compile-check=false -Dtest=FrameworkAdaptationCursorLiveIT
```

**Evidence JSON per scenario:** `docs/handoff/phase6-5-live-evidence-<SCENARIO>.json`  
**Run log:** `docs/handoff/phase6-5-live-run.log`

## Live repair matrix (fixtures)

| # | Scenario enum | Violation |
|---|---------------|-----------|
| 1 | `MISSING_TEST` | Delete generated test class |
| 2 | `MULTI_PAGE_BAD_IMPORT` | Strip `Cart_Page` import from TODO test |
| 3 | `MISSING_PAGE_LOCATORS` | Remove `By` fields from `Login_Page` |
| 4 | `MISSING_PAGE_ACTIONS` | Strip action methods |
| 5 | `MISSING_PAGE_ASSERTIONS` | Strip assert methods |
| 6 | `EMPTY_TEST_STUB` | Empty `@Test` body |
| 7 | `MIXED_INVENTORY_BAD_IMPORT` | Login PASSED + multi-page TODO + demoted dump + bad import |

Legacy control: `MISSING_PAGE` (Phase 6.4 regression).

Fixtures: `FrameworkRepairQualificationFixtures`. Repair objective lists mandatory rule IDs; `framework-repair-context.json` holds full inventory v2.

## Observability (qualification)

- `framework-acceptance/reconciliation-manifest.json` written after successful Cursor reconcile (`copied`, `deleted`, `excluded`, `unchanged`).
- Live evidence records: initial rule IDs, task/agent/run IDs, reconciliation JSON, revalidation on disk, permitted file lists.

## Reconciliation resilience (deterministic)

| Case | Result |
|------|--------|
| Symlink / path traversal / identity mismatch | Fail closed (existing + resilience tests) |
| Repeated reconcile | Idempotent (`unchanged` on second pass) |
| Concurrent reconcile | **Unsupported** — races possible; not used in production path |
| Partial I/O failure | **Not atomic** — no rollback; earlier permitted files may remain copied before exception |
| Interruption mid-reconcile | Same as partial failure — gate returns `RECONCILIATION_FAILED`; repair-candidate may be partially updated |
| Stale / wrong workspace | `RECONCILIATION_FAILED` |
| FAKE provider | Reconciler rejects; gate never invokes for FAKE |

**Proposed safe improvement (not implemented — unapproved redesign):** copy permitted files to a temp staging dir under `framework-acceptance/`, validate manifest, then atomic directory swap into `repair-candidate`. Would address partial-failure corruption.

## Acceptance safety (deterministic)

- `RECONCILIATION_FAILED` / `REVALIDATION_FAILED` / `REPAIR_FAILED` → no promotion; `framework-acceptance/original` preserved.
- Cursor complete with no useful edits → reconcile may no-op → `REVALIDATION_FAILED`.
- FAKE: no `reconciliation-manifest.json`; writes directly to repair-candidate.

## Feature flags

Unchanged: `delivery.agent-harness.enabled=false`, `automate-framework-repair.enabled=false` in `application.properties`.

## Live results (2026-11-15 matrix)

Compile check **off** in these ITs. Evidence: `docs/handoff/phase6-5-live-evidence-*.json`. Log: `phase6-5-live-run.log` (7/8 in first batch; assertions re-run after fixture fix).

| Scenario | Accepted | Initial mandatory rules | Reconciliation (copied) | Promotion |
|----------|----------|-------------------------|-------------------------|-----------|
| `MISSING_TEST` | Yes | `FW_EXPECTED_TEST_PRESENT` | `Login_Test.java` | Yes |
| `MULTI_PAGE_BAD_IMPORT` | Yes | `FW_TEST_USES_PAGE` | `CheckoutStepOne_TestTodo.java` | Yes |
| `MISSING_PAGE_LOCATORS` | Yes | `FW_PAGE_MISSING_LOCATORS` | `Login_Page.java` | Yes |
| `MISSING_PAGE_ACTIONS` | Yes | `FW_PAGE_MISSING_ACTIONS` | `Login_Page.java` | Yes |
| `MISSING_PAGE_ASSERTIONS` | Yes | `FW_PAGE_MISSING_ASSERTIONS` | `Login_Page.java` | Yes |
| `EMPTY_TEST_STUB` | Yes | `FW_TEST_STUB_EMPTY`, `FW_TEST_PAGE_UNUSED` | `Login_Test.java` | Yes |
| `MIXED_INVENTORY_BAD_IMPORT` | Yes | `FW_TEST_USES_PAGE` | `CheckoutStepOne_TestTodo.java` | Yes |
| `MISSING_PAGE` (control) | Yes | `FW_EXPECTED_PAGE_PRESENT` | `Login_Page.java` | Yes |

**Quality caveats:** `EMPTY_TEST_STUB` passing v2 does not prove behavioral fidelity. First run: `MISSING_PAGE_ASSERTIONS` fixture lacked assert steps (test defect, not Cursor).

## Final assessment

| Dimension | Rating |
|-----------|--------|
| **RECONCILIATION RELIABILITY** | **Qualified** — fail-closed boundaries; manifest; **not atomic** on I/O; no concurrent support |
| **LIVE REPAIR QUALITY** | **Qualified with caveats** — 8/8 accepted one session; compile not exercised |
| **ACCEPTANCE SAFETY** | **Strong** — deterministic promotion guards; Phase 5 unchanged |
| **PRODUCTION READINESS** | **No** — flags off; no compile-on SLO; single-session matrix ≠ operations proof |

**Production readiness** requires: broader live pass rate, architecture review on each promoted artifact, compile-on in CI path, and operator runbooks — not merely selected scenarios passing once.