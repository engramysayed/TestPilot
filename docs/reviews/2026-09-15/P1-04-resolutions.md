# P1-04 — replayable Call-before setup chains (F13)

**Date:** 2026-09-16

## Behavior

- Emit reconstructs each leaf’s Call-before chain from the workbook + IR and copies reusable prerequisite steps into the generated `@BeforeMethod` (after login, same fresh browser). Downloaded tests do not depend on TestNG class order.
- A leaf is not advertised as PASSED/REUSED when a prerequisite is missing or not reusable proof. Emit demotes it to TODO with `CALL_BEFORE_BLOCKED: {id} did not pass`.
- IR JSON records `setupTcIds` on each draft. Prerequisite proven steps remain on the prerequisite IR and are inlined at codegen.
- Cycles are `CALL_BEFORE_CYCLE` at quality-gate ingest and at expand time.
- Each prove occurrence writes evidence under `{tcId}__occ_{n}` while the logical TC ID stays on IR and TestNG. UPDATE reuse still finds the latest occurrence folder. Retention does not delete project `evidence/` or `ir/`, so distinct occurrence folders survive Update + work-dir cleanup.
- Prerequisite body login is skipped in setup when the leaf already emits login; otherwise the prerequisite login prelude is included so a fresh browser can recreate the session.

P1-02 already invalidates dependent reuse when a Call-before ancestor’s hash or proof changes.

## Shared prerequisites

Within one leaf, overlapping nested refs run **once**, first-seen depth-first (A←B, A←C, D←B,C ⇒ A, B, C, then D). Across leaves, each generated class **inlines** the shared prerequisite in its own `@BeforeMethod`; classes do not share session or TestNG order.

## Downloaded replay failure

`@BeforeMethod` flushes `Validation.assertAll()` after setup so a failed prerequisite assertion skips the leaf body. Selenium action failures already abort setup. `@AfterMethod(alwaysRun = true)` still quits the browser.

Inlined setup keeps the prerequisite’s testdata keys (`TC_CART.type_…`), assertions, and page methods.

## Verification

Launch baseline (excludes live smokes):

`mvn -B "-Dtest=delivery/**/*Test,drivers/**/*Test,!FacebookSubmitLiveRebindTest,!*LiveSmokeTest" test`

**1100 tests, 0 failures, 1 skip** (`FacebookIrCodegenIdentityTest` when IR is absent).

Regression: `CallBeforeSetupTest` (order, shared-prereq inlining, honesty), `CodeWriterTest.inlinedSetupKeepsPrereqTestDataAssertionsAndPageMethods`, `CallBeforeReplayContractTest.setupFailureFlushesAssertionsBeforeTheTestBodyAndAlwaysQuits`, `ReuseEligibilityTest.occurrenceFoldersStayDistinctAfterUpdatePreserveAndRetention`.
