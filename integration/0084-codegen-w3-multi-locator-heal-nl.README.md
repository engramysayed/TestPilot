# 0084 — Codegen W3 Automate emit (multi-locator bag + DOM-diff asserts + heal Accept align + NL→By cache)

**Tip:** `e9ee27453b1fccd7ba69cabda79ca27f4190cf18` (short `e9ee274`)
**Status:** GREEN — live dirty; no commit/push by executor
**Depends on:** W1 (0082) + W2 (0083) dirty already on tree — do not regress

## Ships
1. Multi-locator bag (`MultiLocatorBag`) — primary + ordered fallbacks; empty hooks keep proven primary; W2 ladder/uniqueness
2. DOM-diff AI assert suggestions (`DomDiffAssertSuggestor`) — trimmed visible/notVisible/textContains/aria* into emit
3. Heal Accept align (`HealAcceptPatcher`) — Phase 2 contract; patches existing page Java only; refuses LoginPage2; 409 if missing
4. NL step → validated By cache (`NlLocatorCache` + `CodeWriter.write(..., nlCached)`) on the page class

## Apply
Patch is vs tip `e9ee274` for W3-touched paths; `PageAccumulator` / `Page.java.ftl` / `CodeWriter` also carry prior W1+W2 dirty delta vs tip.

## Verify
```
mvn -B -pl keel-portal -am -Dtest=delivery.codegen.CodegenW1EmitTest,delivery.codegen.CodegenW2EmitTest,delivery.codegen.CodegenW3EmitTest,delivery.codegen.CodegenReviewFixesTest,delivery.codegen.CodegenNamingTest,delivery.codegen.PageAccumulatorAssertTest,delivery.codegen.PageAccumulatorIdentityTest,delivery.codegen.LoginPageHelperCodegenTest,delivery.codegen.CodegenEmitGateTest,delivery.codegen.CodegenSmellCheckTest -Dsurefire.failIfNoSpecifiedTests=false test
```
