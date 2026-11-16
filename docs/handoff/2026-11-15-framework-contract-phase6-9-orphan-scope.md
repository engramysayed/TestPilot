# Phase 6.9 — Generated test inventory scope and qualification evidence

**Date:** 2026-11-15

## Root cause (FW_ORPHAN_TEST_CLASS on BaseTest)

`FrameworkContractValidator.collectTestFilesByClass` used `isGeneratedTestFile`, which treated **any** `project/tests/**` file ending in `Test.java` as inventory-managed generated output.

`customer-framework-template` ships template infrastructure at `src/test/java/project/tests/BaseTest.java`. After `FrameworkPackager.copyTemplate`, inventory revalidation scanned `BaseTest` as a generated test class and flagged **`FW_ORPHAN_TEST_CLASS`** even when repair-candidate `test-compile` succeeded.

Inventory construction (`FrameworkExpectedInventory.fromOutcomes`) only lists **codegen** test classes (`*_Test`, `*TestTodo`), not template harness classes.

## Fix

**Scope + naming** (no BaseTest hardcode, rule not weakened):

- Discover tests only under `project/tests/generated/` and `project/tests/todo/` (same dirs as `CodeWriter`).
- Accept only `*_Test.java` and `*TestTodo.java` (codegen naming).

File: `FrameworkContractValidator.isGeneratedTestFile`.

## Evidence persistence

- `FrameworkCompileOnQualificationEvidenceWriter` writes **`docs/handoff/phase6-8-compile-on-<SCENARIO>.json`** before TestNG assertions.
- Preserves historical **`phase6-6-compile-on-*.json`** from Phase 6.6.

## Tests added

- `FrameworkContractGeneratedTestDiscoveryTest` (packaged template + stray generated/todo + wrong page + infrastructure at tests root).
- `FrameworkCompileOnQualificationEvidenceWriterTest`.

## Deterministic verification

Run:

```text
mvn -pl keel-portal test -Dsurefire.forkCount=0 -Dtest=FrameworkContractGeneratedTestDiscoveryTest,FrameworkContractValidatorInventoryTest,FrameworkContractPhase62RegressionTest,FrameworkContractAdversarialVerificationTest,FrameworkRepairWorkspaceReconcilerTest,FrameworkRepairReconcilerResilienceTest,FrameworkRepairArchitectureAssessorTest,FrameworkRepairCompileBaselineTest,FrameworkCompileOnQualificationEvidenceWriterTest,CustomerFrameworkTemplatePomTest
```

## Focused live recommendation

After deterministic tests pass, smallest live check:

- Re-run **one** previously compile-success / revalidation-fail scenario, e.g. `FrameworkAdaptationCursorCompileOnLiveIT#missingExpectedTest_compileOnQualification`, and confirm `phase6-8-compile-on-MISSING_TEST.json` shows `accepted: true` with inventory v2 pass (or new mandatory failures only if Cursor repair quality regresses).

Treat `MISSING_PAGE_LEGACY` and `MISSING_PAGE_ASSERTIONS` **INTERRUPTED** tasks as a separate Cursor reliability slice (not blocking orphan classification).

Do **not** rerun full 8-scenario matrix until focused live passes.

**No commit in this phase. Production flags unchanged.**

---

## Phase 6.10 focused live (MISSING_TEST)

- **Preflight:** `GENERATED_TEST_DIR` pattern + `Path.normalize()` in `isGeneratedTestFile`; relative-root regression added.
- **Command:** `FrameworkAdaptationCursorCompileOnLiveIT#missingExpectedTest_compileOnQualification`
- **Maven exit:** 0 (~107 s)
- **Accepted:** yes — compile, revalidation (no mandatory findings), architecture, promotion
- **Evidence:** `docs/handoff/phase6-8-compile-on-MISSING_TEST.json`, log `docs/handoff/phase6-10-missing-test-live.log`