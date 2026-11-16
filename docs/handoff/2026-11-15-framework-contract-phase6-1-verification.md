# Phase 6.1 — Adversarial contract verification

**Date:** 2026-11-15  
**Assessment (original):** VERIFIED WITH DOCUMENTED LIMITATIONS  

**Update (Phase 6.2):** Defects **D-6.1-01** and **D-6.1-02** fixed — see `2026-11-15-framework-contract-phase6-2-inventory-fixes.md`. Adversarial matrix rows 16 and multi-page todo now **pass**.

## 1. Inventory consistency

### Flow traced

| Stage | Implementation |
|-------|----------------|
| TcOutcome list at acceptance | `EmitPhase` after `CodegenEmitGate.prepare` (demoted outcomes) |
| Page models at emit | `prepared.pages()` in `CodeWriter.writeVerified` |
| Page inventory | `FrameworkExpectedInventory` → `CodeWriter.accumulate(outcomes)` |
| Test inventory | Same outcome list → `CodegenTestClasses.classNameFor` + `PageUnderTest.primaryPageName` |
| Gate | `AutomateFrameworkAcceptanceGate.run(..., expectedInventory)` |
| Validator | `FrameworkContractValidator.validateWorkspace(..., inventory)` |

### Documentation discrepancy resolved

Handoff text saying “build `PageAccumulator` from outcomes” is **imprecise**. Canonical path is **`CodeWriter.accumulate(outcomes)`**, which applies `CodegenEmitFilters.filterForEmit` and `EmitStepNormalizer` before folding steps into `PageAccumulator`. `CodegenEmitGate.prepare` returns the same page map used at emit (`prepared.pages()`); inventory page counts match `prepared.pages()` (verified in `FrameworkContractAdversarialVerificationTest.inventory_pagesMatchPreparedAccumulator_notRawPageAccumulator`).

### Caller contract

`FrameworkExpectedInventory.fromOutcomes` **must** receive **post-`prepare`** outcomes (as `EmitPhase` does). Undemoted raw outcomes can diverge from emitted bytes.

### TODO / generated tier

`ExpectedTest.generatedTier` mirrors `TcStatus.PASSED`; file path expectations use `generated/` vs `todo/`. Verified via `S15_todo_tier` and `CodeWriterTest` patterns.

## 2. Adversarial matrix (expected vs actual)

Automated in `FrameworkContractAdversarialVerificationTest` (data provider + focused cases).

| # | Scenario | Expected mandatory rule(s) | Actual (inventory v2) | Match |
|---|----------|------------------------------|------------------------|-------|
| 1 | Valid emit | — pass | pass | ✓ |
| 2 | Missing page | `FW_EXPECTED_PAGE_PRESENT` | same | ✓ |
| 3 | Missing test | `FW_EXPECTED_TEST_PRESENT` | same | ✓ |
| 4 | Extra orphan page | `FW_ORPHAN_PAGE_CLASS` | same | ✓ |
| 5 | Test without page file | `FW_EXPECTED_PAGE_PRESENT` | same | ✓ |
| 6 | Duplicate page | `FW_ARCH_DUPLICATE_PAGE` | same | ✓ |
| 7 | Duplicate test | `FW_ARCH_DUPLICATE_TEST` | same | ✓ |
| 8 | Page no locators | `FW_PAGE_MISSING_LOCATORS` | same | ✓ |
| 9 | Page no actions | `FW_PAGE_MISSING_ACTIONS` | same | ✓ |
| 10 | Page no assertions | `FW_PAGE_MISSING_ASSERTIONS` | same | ✓ |
| 11 | Import, no use | `FW_TEST_PAGE_UNUSED` | same | ✓ |
| 12 | Field, no use | `FW_TEST_PAGE_UNUSED` | same | ✓ |
| 13 | Empty @Test stub | `FW_TEST_STUB_EMPTY` | same | ✓ |
| 14 | Two TCs → one Login test | pass | pass | ✓ |
| 15 | TODO tier Cart TC | pass | pass | ✓ |
| 16 | Demoted dump stem | **should pass** | **fail** `FW_TEST_USES_PAGE` (phantom `Page_Page`) | ✗ defect |
| 17 | Real template emit | pass | pass | ✓ |

## 3. False-positive / false-negative findings

| ID | Type | Description |
|----|------|-------------|
| **D-6.1-01** | ~~False positive~~ **Fixed 6.2** | Was: primary-page-only link metadata. |
| **D-6.1-02** | ~~False positive~~ **Fixed 6.2** | Was: phantom `Page_Page` on demoted empty outcomes. |
| — | Limitation | `FW_TEST_PAGE_UNUSED` only counts `pageVar.` call sites (matches template; not full data flow). |
| — | Limitation | Page structure checks compare emit-time **counts** to regex-parsed sources — formatting edge cases possible. |

**Proposed fixes (require approval before implementation):**

1. **D-6.1-01:** Build page↔test coverage from **union of page classes per test group** (same as `CodeWriter` `unionImports` / step chronology), not single `primaryPage`. Alternative: satisfy link if test source imports `project.pages.{pageClass}`.
2. **D-6.1-02:** When building `ExpectedTest`, use post-demote primary page consistent with emit (e.g. skip `Page_Page` when page not in inventory; or carry `CodegenEmitGate` stem demotion metadata on outcome).

Do **not** weaken mandatory rules without fixing inventory semantics.

## 4. Repair compatibility

| Check | Result |
|-------|--------|
| Repair receives failed `ValidationReport` | Yes — `framework-repair-context.json` + `contract-validation-report.json` via `failedReport.toApiMap()` |
| Mandatory vs recommended | Repair objective uses `mandatoryMessages()` only; recommended findings do not block `passed` |
| Original workspace | `FrameworkAcceptanceWorkspace.snapshotOriginal`; repair on `repair-candidate` |
| Revalidation inventory | Production `EmitPhase` passes same `expectedInventory` to revalidation |
| Promotion gate | `revalidated.passed()` required; `REVALIDATION_FAILED` otherwise |
| Phase 5 boundary | Unchanged — acceptance still post-Revise, pre-zip |
| Live Cursor | **Not verified** — `AutomateFrameworkAcceptanceGateTest` + `FixingFakeRuntime` only |
| Test gap | Gate unit tests call 7-arg `run()` **without** inventory → revalidation exercises v1 legacy layer only; production path uses v2 |

## 5. Contract version consumers

| Consumer | Uses `contractVersion`? | Notes |
|----------|-------------------------|-------|
| `ContractValidationReportWriter` | Persists via `toApiMap()` | JSON includes `contractVersion` + `metadata` |
| `AutomateFrameworkRepairBridge` | Full `toApiMap()` in task context | Agent can read v2 |
| `AutomateFrameworkAcceptanceOutcome` | Holds report record | No version branching |
| `buildObjective()` | No | Counts mandatory messages only |
| Convert `AgentHarnessJobBridge` | Different `ValidationReportWriter` (fingerprint advisory) | Unrelated to framework contract |
| Generate TC contract | TC id + v1 | Unaffected |

No consumer silently maps v2→v1. Nothing branches on `contractVersion` today (by design). Reports remain backward compatible: v1 default when inventory null.

## 6. Test commands and results

```text
mvn -pl keel-portal -Dtest=FrameworkContractAdversarialVerificationTest,FrameworkContractValidatorTest,FrameworkContractValidatorInventoryTest,AutomateFrameworkAcceptanceGateTest test
```

**Result:** 37 tests, **37 passed** (after matrix expectations updated for demonstrated defects on S16).

## 7. Final assessment

**VERIFIED WITH DOCUMENTED LIMITATIONS**

Inventory-driven validation reliably detects missing/orphan/duplicate artifacts and most structure violations on single-primary-page flows and merged tests. Two mandatory-rule false positives (D-6.1-01, D-6.1-02) block **VERIFIED** until approved fixes land. Phase 4/5/6 invariants preserved; no production code changed in Phase 6.1.