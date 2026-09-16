# P0-02 — audit failure resolutions

**Date:** 2026-09-16  
**Baseline command:** `mvn -B "-Dtest=delivery/**/*Test,drivers/**/*Test,!FacebookSubmitLiveRebindTest,!*LiveSmokeTest" test`  
**Audit:** 1,051 tests / 16 failed at review `0aca9c6`. This file records how each class of failure was resolved. No meaningful assertion was deleted to go green.

**Re-run (this branch):** `0aca9c6` working tree, 2026-09-16. **Tests run: 1055, Failures: 0, Errors: 0, Skipped: 1.** BUILD SUCCESS in 1:10. Skip: `FacebookIrCodegenIdentityTest` — local `delivery-store/facebook-com/.../ir` is absent (customer-target fixture, not a weakened assertion).

| Audit class | Count | Resolution |
|-------------|-------|------------|
| Authoring invented-value expectations | 2 | Tests now expect blank TestData when Excel has no literal (`AnonymousInputBindingTest`, `AuthoringServiceTest`). Matches P0-01: invention is off unless permitted. `DummyValueInventor.invent()` still covers heal fillers. |
| Naming/catalog | 2 | Tests updated to current codegen: catalog class `TC_1` (not `TC_1Test`); alias fallback `LoginPage` (not `Login`). |
| Dry-run API expects COMPLETED | 5 | **Product contract:** dry-run all-TODO is a valid simulation. `ConversionWorker` / `ExecuteWorker` skip `ALL_CASES_TODO` when `delivery.dry-run=true`. Live all-TODO still fails. |
| Runtime CSV conversion | 1 | `FacebookInvalidLoginConversionTest` no longer reads `delivery-store/`. Isolated in-memory cases + `DryRunConversionService`. Live Facebook replay is a customer-target test, not this class. |
| Generate/compare stubs bypassed | 2–3 | Stubs override `callOllamaDetailed(...)` (the production path). Deterministic tests must not hit a real Ollama. |
| Import persistence doubles | 2 | `TcImportServiceTest.TrackingWorkbooks` stubs `updateCoverageNotes` so save-without-disk does not throw `NO_GENERATED_WORKBOOK`. |
| Execute MVC design-ref hint | 1 | Hint lives in page script, not `<main>`. Assertion now searches the full page body. |

**P1-01 probe conversion:** [TemplateAssertionProbe.java](TemplateAssertionProbe.java) is preserved as original evidence. Behavioral coverage is `customer-framework-template` `ValidationAssertAllTest` (false assertion fails TestNG; later test stays clean; parallel isolation; quit in `finally`) plus keel `ValidationLifecycleContractTest`.

**Excluded from this deterministic suite (named reasons):**

- `FacebookSubmitLiveRebindTest`, `*LiveSmokeTest` — live browser / customer site.
- `FacebookIrCodegenIdentityTest` — skips when Facebook IR is not in `delivery-store/` (customer-target fixture).
- Legacy `Runner.BaseOrchestrator` `@BeforeSuite` Chrome launch — gated by not using `-Dtest=*`.
