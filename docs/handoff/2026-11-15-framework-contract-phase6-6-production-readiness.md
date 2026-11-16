# Phase 6.6 — Production readiness qualification

**Date:** 2026-11-15  
**Flags:** unchanged (`delivery.agent-harness.enabled=false`, `automate-framework-repair.enabled=false`)

## What shipped (code)

| Area | Change |
|------|--------|
| Staged reconciliation | Merge in `framework-acceptance/repair-staging/{taskId}/`, backup + restore repair-candidate on apply failure; `reconciliation.lock` file lock |
| Compile observability | `framework-acceptance/emit-compile-check.ok` records compiled path; failure detail includes `docs/EMIT_COMPILE_FAIL.log` |
| Architecture quality | `FrameworkRepairArchitectureAssessor` (advisory, not mandatory contract) |
| Compile-on live IT | `FrameworkAdaptationCursorCompileOnLiveIT` + evidence `phase6-6-compile-on-*.json` |
| Fixtures | `FrameworkPackager.copyTemplate` seeds compilable tree for qualification |
| Tests | `FrameworkRepairArchitectureAssessorTest`, `FrameworkRepairCompileBaselineTest`, extended resilience tests |

## Staged reconciliation guarantees

1. Agent changes merged only into **staging** copy of repair-candidate.
2. **Backup** of repair-candidate taken before apply; **restore** on apply `IOException`.
3. Staging + backup cleaned in `finally`.
4. **Lock:** `tryLock` on `framework-acceptance/reconciliation.lock`; concurrent reconcile rejected.
5. **Not guaranteed:** atomic apply of permitted files (apply loop is multi-file); cross-filesystem rename swap not used.
6. **Concurrent:** second reconcile on same `workDir` → `RECONCILIATION_FAILED` (lock).

## Compile-on live matrix

**Command:**

```text
call api-keys.local.bat
mvn -pl keel-portal test -Dkeel.agent.cursor.live.framework=true -Dkeel.agent.cursor.compile.on=true -Ddelivery.emit-compile-check=true -Dtest=FrameworkAdaptationCursorCompileOnLiveIT
```

### Run A (pre-fix fixtures — no `pom.xml`)

All 8 scenarios → `COMPILE_FAILED` (`missing pom.xml`). Classified **FIXTURE_DEFECT** (not Cursor repair).

### Run B (with `FrameworkPackager` seed)

Evidence files regenerated in Run B; baseline local test shows **customer-framework-template** `pom.xml` **duplicate dependency** (`allure-environment-writer`) blocks Maven model read. Classified **INFRASTRUCTURE / TEMPLATE_DEFECT**.

Until template POM is healthy, compile-on cannot prove repair quality on isolated temp projects.

**Inventory v2 + reconcile** still ran before compile failure in both runs (manifest present in evidence).

## Verdicts (Phase 6.6)

### 1. COMPILE-ON LIVE QUALIFICATION — **NOT MET**

- Gate correctly **does not promote** on `COMPILE_FAILED`.
- No scenario reached compile success on temp qualification projects (fixture then template POM).
- Uncovered: qualification must use Phase-5-shaped project tree + fix template POM for local `test-compile`.

### 2. PROMOTED ARTIFACT ARCHITECTURE QUALITY — **PARTIALLY MET**

- Assessor + negative stub test (`TEST_STUB_EMPTY` ≠ functional complete).
- Live compile-on failures prevent post-promotion checks; repair-candidate architecture checks in evidence show `functionallyComplete: true` for some failed-compile cases (v2-shaped repair without compile proof).

### 3. RECONCILIATION TRANSACTION SAFETY — **MET (with documented limits)**

- Staging + backup restore implemented and tested (`stagingFailure_restoresRepairCandidate`).
- Lock serializes per `workDir`.
- Not full atomic directory swap.

### 4. PROMOTION AND RECOVERY SAFETY — **MET**

- `COMPILE_FAILED` / `RECONCILIATION_FAILED` / `REVALIDATION_FAILED` → no promotion (existing + compile-on live).
- FAKE provider unchanged.

### 5. PRODUCTION READINESS — **NOT READY**

## Recommendation

**NOT READY FOR CONTROLLED ROLLOUT**

Required before rollout:

1. Fix **customer-framework-template** POM duplicate dependency (compile baseline green).
2. Re-run **compile-on** full matrix with seeded fixtures; require `emit-compile-check.ok` + published == repair-candidate digest + architecture assessor pass.
3. Repeat live matrix across multiple sessions (SLO / nondeterminism).
4. Optional: architecture assessor as **advisory gate** in staging (not mandatory contract) before promote.

## Evidence locations

- `docs/handoff/phase6-6-compile-on-*.json`
- `docs/handoff/phase6-6-compile-on-run-full.log` (may be empty due to stdout redirect)
- Deterministic: `FrameworkRepairReconcilerResilienceTest`, `FrameworkRepairArchitectureAssessorTest`