# 0082 — Codegen W1 Automate emit

**Date:** 2026-11-02 ~18:11 Africa/Cairo (EET)
**Live tip (dirty tree):** `adfc4e5c598f015f9ed59b98153e698f470e2dac` (ancestor of task tip `cfa220e`)
**Scope:** preferred-hook-first emit, assert-from-element normalize, one-class Page + `isLoaded()`, parameterized row locators (`productRow(String name)`).

## Deliverables
- `EmitStepNormalizer` — preferred hooks promote primary; assert aliases (text/valueEquals/intent:ASSERT_VISIBLE:value)
- `RowLocator` — inventory/product row ? `By` factory; container `data-test='inventory-container'` stays static
- `PageAccumulator` / `Page.java.ftl` — parameterized fields + `loadedMarkerRef` / `isLoaded()`; empty hooks keep proven primary (no candidate-ladder promote)
- `Assertion.elementValue` / `elementTextEquals`
- `CodegenW1EmitTest`

## Surefire (focused, one Maven)
`mvn -B -pl keel-portal -am -Dtest=delivery.codegen.CodegenW1EmitTest,delivery.codegen.CodegenReviewFixesTest,delivery.codegen.CodegenNamingTest,delivery.codegen.PageAccumulatorAssertTest,delivery.codegen.PageAccumulatorIdentityTest,delivery.codegen.LoginPageHelperCodegenTest,delivery.codegen.CodegenEmitGateTest,delivery.codegen.CodegenSmellCheckTest -Dsurefire.failIfNoSpecifiedTests=false test`

**Result:** Tests run: **47**, Failures: **0**, Errors: **0**, Skipped: **0** — BUILD SUCCESS

## Explicit
No push / no commit by executor. Codegen-only paths. Do not wait on Phase 4 visual-diff.
