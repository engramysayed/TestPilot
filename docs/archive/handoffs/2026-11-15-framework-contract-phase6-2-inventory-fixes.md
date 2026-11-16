# Phase 6.2 — Multi-page & demotion inventory fixes

**Date:** 2026-11-15  
**Assessment:** READY (defects D-6.1-01 and D-6.1-02 resolved; live Cursor still not verified)

## Root cause

| Defect | Cause |
|--------|--------|
| D-6.1-01 | `ExpectedTest` stored only `PageUnderTest.primaryPageName()`; `FW_EXPECTED_PAGE_TEST_LINK` and page-import checks ignored the **union page imports** that `CodeWriter` builds via `pageImportsFor(chron, before)` per grouped test. |
| D-6.1-02 | Same single primary page drove mandatory `FW_TEST_USES_PAGE` when demotion cleared all steps—inventory still expected `Page_Page` imports though emit emits **no** page imports for demoted todo tests. |

## Implementation

- **`CodeWriter.expectedUnionPageImportsForGroup`** — runs the same `buildTestMethodModel` + `pageImportsFor` path as `writeVerified` (shared deterministic source).
- **`FrameworkExpectedInventory`** — groups outcomes with `CodegenTestClasses.groupKey`; `ExpectedTest.linkedPageClassNames` from the helper above (`inventoryVersion` metadata → `2`).
- **`FrameworkContractValidator`** — link rule and test architecture checks iterate **each exact** linked page class (import, field, per-var usage); empty linked set skips phantom page rules (demoted stems).
- **`GeneratedJavaStructure.inspectTestPageFields`** — multi-page field + usage inspection.

## Files changed

- `src/main/java/delivery/codegen/CodeWriter.java`
- `src/main/java/delivery/agent/validation/contract/FrameworkExpectedInventory.java`
- `src/main/java/delivery/agent/validation/contract/FrameworkContractValidator.java`
- `src/main/java/delivery/agent/validation/contract/GeneratedJavaStructure.java`
- `src/test/java/delivery/agent/validation/contract/FrameworkContractPhase62RegressionTest.java` (new)
- `src/test/java/delivery/agent/validation/contract/FrameworkContractAdversarialVerificationTest.java`
- `src/test/java/delivery/agent/validation/automate/AutomateFrameworkAcceptanceGateTest.java`

## Tests

```text
mvn -pl keel-portal -Dtest=FrameworkContractPhase62RegressionTest,FrameworkContractAdversarialVerificationTest,FrameworkContractValidatorInventoryTest,FrameworkContractValidatorTest,AutomateFrameworkAcceptanceGateTest test
```

**44 passed** (local 2026-11-15).

## Invariants

Phase 5 acceptance order, READ_ONLY validation, repair isolation, contract IDs/severities, flags, Policy A, heal.mjs — unchanged.

## Gate test gap (closed)

`AutomateFrameworkAcceptanceGateTest.inventoryV2_emitPath_acceptanceAndRepairUseSameInventory` exercises production-style `run(..., FrameworkExpectedInventory)` with repair + `contractVersion=2` on initial and revalidated reports.