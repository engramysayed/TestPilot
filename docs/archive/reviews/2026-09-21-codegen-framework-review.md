# Automation code generation and customer framework review

Date: 21 September 2026. Reviewed working tree above `a4dc94e9fc4faffff4c8915693dd5fd2817cf7ef`, including the uncommitted DOM/grounding work. Review only: no generator or customer-template production code changed in this review. Launch HOLD and release candidate are unchanged.

## Conclusion

The architecture is reasonable, but generated replay is not yet a faithful representation of every proven sequence. The highest risks are silent action failures, assertion/step identity collisions, data substitution, and loss of whole test cases. These can survive Java compilation. Fix these before treating successful compilation as evidence that the downloaded suite reproduces the proof.

The user's naming style can stay. Underscores and `_Actions`/`_Locators` are stylistic choices, not defects. Names must not serve as the internal identity of a control, assertion, step occurrence, or case.

## What writes the code

`EmitPhase` loads IR, clusters page names, applies optional final review and prerequisite handling, then calls `CodeWriter`. `PageAccumulator` builds locator/action/assertion models; FreeMarker templates produce Java page classes and TestNG tests. `TestDataPropertiesWriter` writes values. Maven `test-compile` runs before publication. Static revise notes, locator maps, evidence, and the ZIP follow.

This is primarily deterministic template generation. Optional Ollama naming changes test-method names only; Cursor choosing a browser target does not mean Cursor writes every Java file.

## Confirmed findings

### F01 — P1: failed click/type can leave replay green

Source: `customer-framework-template/src/main/java/project/utils/Actions/ElementsHandler.java:55–77`.

Both helpers catch exceptions, log, and return normally. They neither rethrow nor record a failed validation. A failed setup action can therefore allow the body to run; a case without a later assertion that catches the missing effect can pass.

**Reproduced (CG09):** compiled the actual template helpers, supplied a WebDriver that throws `NoSuchSessionException`, called click and type, then called `Validation.assertAll()`. All returned normally. This is a separate false-green path from the previously fixed soft-assertion lifecycle.

**Fix:** throw typed execution failures; retain `finally` cleanup. Test generated setup and body actions against failed navigation, intercepted click, missing element, and dead browser.

### F02 — P1: absence assertions pass on infrastructure errors

Source: `customer-framework-template/src/main/java/project/validations/Assertion.java:145–155`.

`elementNotVisible` sets `ok=true` for every exception. A dead browser or invalid selector is treated as proof of absence.

**Reproduced (CG10):** the real helper plus `assertAll()` passed against a dead-session WebDriver.

**Fix:** distinguish a successful query returning no visible match from an unsuccessful query. Fail on session/selector/transport errors; explicitly define whether all matches must be hidden.

### F03 — P1: generated method names collapse different behavior

Source: `src/main/java/delivery/codegen/PageAccumulator.java:65–82`; naming in `CodegenNaming`.

Methods and assertions are retained by name only. A second semantic operation with the same readable name is silently discarded before `CodegenSmellCheck` sees it.

**Reproduced (CG03):** assert country selected = France, then = Germany. Only the France assertion model exists; both calls use the same method.

**Reproduced (CG04):** type into IDs `first-name` and `first_name`. Both locator fields survive, but only one action method exists. Both calls target that method. The smell check accepts both examples.

**Fix:** assign symbols using exact operation identity, including page/control identity, action, assertion type and expected value. Parameterize assertion values where practical. Resolve name collisions explicitly and make call generation use the allocated symbol rather than independently recomputing it.

### F04 — P1: repeated edits replay the first value

Source: `CodeWriter.java:262–278`; `TestDataPropertiesWriter.java:55–62`.

Data keys are case ID + method name. `putIfAbsent` retains the first value, so repeated calls to the same field share it. Different pages with the same action name can collide too.

**Reproduced (CG01):** email changed from `first@example.invalid` to `second@example.invalid`; generated data retains only the first value, and both calls read its key.

**Fix:** use stable step-occurrence identity in data keys, preserving prerequisite ownership. Test initial fill, correction, cross-page fields, and repeated prerequisite occurrences.

### F05 — P1: distinct valid case IDs overwrite one Java test

Source: `CodegenNaming.java:59–68,448–455`; `CodeWriter.java:117,143–146`.

The ID contract permits repeated underscores. Java-name sanitization collapses them and removes trailing underscores. Writes do not detect an existing case-to-file collision.

**Reproduced (CG02):** valid IDs `TC_A_B` and `TC_A__B` produce one `TC_A_B.java`, containing the latter case. Storage-key protection does not protect generated Java names.

**Fix:** preserve already-valid TC IDs exactly, check the full emitted symbol/file set before writing, and enforce emitted-case count versus input-case count. Account for case-insensitive filesystems.

### F06 — P1: typed credentials are logged by the downloaded framework

Source: `customer-framework-template/src/main/java/project/utils/Actions/ElementsHandler.java:74`; `project/utils/reports/AllureAttachmentManager.java:33–39`.

The success log includes the literal typed text. Generated login reads `TARGET_PASSWORD` and passes it to this same helper. Logs are attached to Allure. Portal-side sanitizers do not protect this independent downloaded runtime.

**Evidence:** direct source trace; no real credentials used or exposed by this review.

**Fix:** omit typed values from routine logs. If diagnostic values are needed, use explicit safe-field policy and secret redaction; add a synthetic credential canary test for log/report attachments.

### F07 — P1: previous template execution artifacts enter customer ZIPs

Source: `FrameworkPackager.java:26–34,169–196`; template POM build directory is `test-output/target`.

Template copying excludes root `target`, but not `test-output`. ZIP creation includes those files too. The current template has logs and Allure output under that directory. This can distribute old run evidence and stale build outputs; no claim is made that the inspected output contains real customer secrets.

**Reproduced (CG11):** a synthetic prior-run log at `test-output/Logs/previous-run.log` survives template copy and enters the ZIP.

**Fix:** package an explicit source/config/document allowlist. Exclude runtime output, compiled classes, reports, caches, and local secrets at both copy and ZIP boundaries. Compile in disposable output or keep compiler products outside the deliverable.

### F08 — P2: speculative locator merging removes required methods

Source: `PageAccumulator.java:152–200,230–239,261–270`.

Any sufficiently plain ID/name may be treated as the same control as a submit selector, without DOM identity evidence. Replacing the locator removes earlier click methods, but chronological calls still reference their old names. Identity normalization also lowercases case-sensitive locator values.

**Reproduced (CG05):** `id=promo` and `button[type='submit']` merge to one field and lose the earlier method. Generated source contains a call with no matching generated method; the normal compile gate should reject this package.

**Fix:** merge only proven equivalent identities, preserve case, and allocate aliases/calls from the same symbol registry. Do not use locator preference as evidence that controls are identical.

### F09 — P2: locator-free text assertions generate missing code

Source: `PageAccumulator.java:21–34`; `CodeWriter.java:188–202`.

The accumulator returns early for a blank locator unless the assertion is `urlContains`, before reaching its body-text fallback. The call writer explicitly permits locator-free `textContains`.

**Reproduced (CG07):** a `textContains("Saved")` step with no locator emits the assertion call and page import but no page class. The compile gate should reject it. Upstream paths that always supply a body XPath can mask this inconsistency.

**Fix:** share one supported-step contract between model construction and call emission; handle body-text fallback before the blank-locator return.

### F10 — P2: different page names overwrite the same file

Source: `CodegenNaming.java:72–95`; `CodeWriter.java:86–109`.

Pages are accumulated by raw name but written by sanitized stem, with no global output-name collision check.

**Reproduced (CG08):** `Order-Details` and `OrderDetails` both write `OrderDetails_Actions.java`; only the last page's methods remain. The compile gate should catch missing calls. This applies when distinct upstream page names reach emit; URL-derived names may instead collapse earlier.

**Fix:** separate stable page identity from display stem. Detect collisions globally and use deterministic suffixes only when needed.

### F11 — P2: test data does not round-trip unchanged

Source: `TestDataPropertiesWriter.java:39–41,66–70`; customer `PropertyReader.java:33`.

The writer uses UTF-8; the reader uses `Properties.load(InputStream)`. The escaping also omits leading-space and carriage-return handling.

**Reproduced (CG06, CG12):** Arabic/accented text changes during readback; leading spaces and CR-delimited input do not survive intact.

**Fix:** use a matching UTF-8 reader/writer and standards-compliant properties escaping, closing input streams. Test Unicode, CR/LF, backslashes, leading whitespace, and empty values.

## Additional source-level gaps

- **Proof/replay assertion timing differs:** `TcExecutionService` waits up to 15 seconds for text and 10 seconds for absence; customer text/absence helpers query once. `document.readyState` plus 500 ms is not equivalent to waiting for an asynchronous application result. Add delayed-render replay fixtures and keep runtime semantics aligned.
- **Static review annotations are stale:** `RevisePhase.java:165` searches for `public void runCase()`, while generated tests use title-derived methods. Passed-test inline annotations can be absent even though `REVISE_NOTES.md` contains the issues. TODO annotation insertion still has its stop marker.
- **Context parity needs an explicit contract:** live proof uses `ContextSearch`; the template's element lookup is direct `driver.findElement`. Full iframe/shadow replay was already outside the previous patch and is not solved by readable page names.
- **Missing configuration becomes empty input:** generated calls convert absent properties to empty strings. Required secrets/data should fail early with a key-only error, while intentionally empty inputs remain allowed.

These source-level gaps were reviewed but not exercised as new browser scenarios in this review.

## Naming recommendations

Keep these familiar forms:

- Test class: exact valid `TC_01`; TODO class in its existing TODO package.
- Page pair: `LoginPage_Actions` and `LoginPage_Locators`.
- Locator: `email_Txt_Locator`.
- Action: `type_Email`, `click_Login_Button`.
- Test method: title-derived, with full title retained in TestNG description.

Improve the rules underneath them:

1. **Stable identity first, readable name second.** An output-name registry must map every step to the exact allocated method and field. Never silently choose the first definition.
2. **Control types should be truthful.** Currently every click gets a Button method suffix, and click actions get `_Btn_Locator` before link handling. A link, checkbox, tab, or switch should not be called a button merely because it is clicked. Carry control kind in IR, or use neutral names when unknown.
3. **Assertion names should say what they check.** `assert_Country_Is_Selected(String expected)` or `assert_Status_Text_Contains(String expected)` is clearer than baking every expected value into a name. Keep expectation details in reports.
4. **Page identity should include route context.** Normal routes mostly use the last URL segment, so `/admin/users` and `/shop/users` both become Users. Prefer semantic route patterns, handle SPA hash routes, and avoid one page class per record ID. Preserve explicit aliases through Updates.
5. **Title shortening should retain meaning.** Taking the first five words can reduce “Verify that the user can successfully reset a password” to an unhelpful prefix. Remove boilerplate and retain the action/object; keep optional model naming nonessential and persist its accepted result for stable diffs.
6. **Bound long names and retain a mapping.** Use a deterministic suffix on collisions and provide TC/step → generated file/method/data-key provenance. Do not solve collisions by changing established names on every generation.

There is no need for a wholesale camelCase migration unless the user wants that style. Correctness and stable Updates matter more than replacing underscores.

## Verification and limitations

Existing focused suite:

```text
mvn -B "-Dtest=delivery/codegen/**/*Test,EmitPhaseMappingTest,EmitGeneratedLayerContractTest" -l target/codegen-review/existing-suite.log test
```

Result: **73 run, 0 failures, 0 errors, 1 skip**, BUILD SUCCESS at 2026-09-21T01:38:10+03:00. The Facebook codegen fixture is unavailable. This pass does not cover the new edge cases.

Review-only probe: `docs/reviews/codegen-review/CodegenReviewProbe.java`. It generated small isolated projects, tested properties readback, compiled and invoked the actual customer runtime helpers with a deliberately broken WebDriver, and checked a synthetic ZIP. **12/12 defect scenarios reproduced**, not 12 successful product acceptance tests. Evidence is under `target/codegen-review/evidence/`; no live website, credential, or LLM was used. Missing-method findings were inspected in emitted source; this review did not run a fresh Maven replay of each broken fixture.

## Recommended repair order

1. Runtime correctness and privacy: F01, F02, F06, F07.
2. Identity and data preservation: F03, F04, F05, F08, F10, F11.
3. Emit/proof parity: F09, assertion waits, context support and configuration validation.
4. Naming readability and diagnostics after the above are protected by behavior tests.

Acceptance should compare the original IR sequence with generated replay: exact target, action, value, expectation, step count, and case count. Run these cases through NEW, UPDATE, downloaded `mvn clean test`, and prerequisite replay. A green compiler alone cannot catch the silent collisions above.
