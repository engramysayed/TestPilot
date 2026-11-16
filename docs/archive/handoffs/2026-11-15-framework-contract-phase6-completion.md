# Phase 6 — Framework contract completeness (completion)

**Date:** 2026-11-15  
**Status:** READY WITH DOCUMENTED LIMITATIONS

## Shipped

- **`FrameworkExpectedInventory`** — built from `CodeWriter.accumulate` + `CodegenTestClasses` on the same `List<TcOutcome>` used at emit (not from workspace filenames).
- **`EmitPhase` → `AutomateFrameworkAcceptanceGate`** — passes inventory into acceptance and repair revalidation (Phase 5 emit order unchanged).
- **`FrameworkContractValidator` contract version 2** — when inventory is provided (`contractVersion=2`, `keel.framework-contract/v1` id preserved). Legacy single-arg validation remains v1 stem pairing only.
- **New mandatory findings:** `FW_EXPECTED_PAGE_PRESENT`, `FW_EXPECTED_TEST_PRESENT`, `FW_EXPECTED_PAGE_TEST_LINK`, `FW_ORPHAN_PAGE_CLASS`, `FW_ORPHAN_TEST_CLASS`, `FW_ARCH_DUPLICATE_PAGE`, `FW_ARCH_DUPLICATE_TEST`, `FW_PAGE_MISSING_LOCATORS|ACTIONS|ASSERTIONS`, `FW_TEST_PAGE_UNUSED`, `FW_TEST_STUB_EMPTY`, `FW_TEST_PAGE_FIELD`.
- **Recommended:** `FW_NAME_PAGE_CLASS`, `FW_NAME_LOCATOR`, `FW_NAME_TEST_CLASS` (inventory mode).
- **`GeneratedJavaStructure`** — lightweight Page/Test shape inspection for locators, methods, stubs, page var usage.

## How to use

Automate jobs with acceptance gate enabled automatically validate using emit-time inventory. Standalone:

```java
FrameworkExpectedInventory inv = FrameworkExpectedInventory.fromOutcomes(outcomes);
ValidationReport r = FrameworkContractValidator.validateWorkspace(projectDir, templateRoot, inv);
```

## Tests (keel-portal)

`FrameworkContractValidatorTest`, `FrameworkContractValidatorInventoryTest`, `GeneratedJavaStructureTest`, `AutomateFrameworkAcceptanceGateTest`, `ValidationReportTest` — **16 passed** (2026-11-15 local run).

## Known limitations

1. Structure checks use regex/heuristics, not a Java compiler/AST — unusual formatting may evade or mis-classify methods.
2. `FW_TEST_PAGE_UNUSED` counts `pageVar.` call sites only (matches generated template); does not prove full call-graph coverage.
3. Inventory reflects outcomes at acceptance time; it does not diff against post-Revise Excel mutations (Revise only touches test comments today).
4. Page↔test linkage is “≥1 test per expected page stem,” not strict 1:1 (codegen may merge TCs).
5. Helper reuse rules (`FW_HELPER_*`) remain **RECOMMENDED** and template-gated.
6. Live Cursor framework repair still not exercised end-to-end with inventory v2.

## Invariants preserved

Phase 4/5 acceptance boundary, READ_ONLY validation, isolated repair candidate, no `heal.mjs` / Policy A changes, Convert advisory validation separate.