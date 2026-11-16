# Codegen implementation verification

Date: 21 September 2026. Scope: verify the supplied implementation report against the current working tree and the original codegen review. The implementation was already present when this verification began; this report does not claim authorship of those changes. No production code was changed during this verification. Work remains uncommitted above `a4dc94e9fc4faffff4c8915693dd5fd2817cf7ef`.

## Verdict

The main repair work is present and the focused tests pass. **Do not mark the full plan complete yet:** two additional behavior probes failed, and the original end-to-end NEW/UPDATE/downloaded-replay acceptance has not been performed for this batch.

The submitted report is broadly accurate about which files changed. Its F04 completion and F02 statement that absence checks every match need correction.

## Implemented changes verified in source

### Runtime and packaging

- `ElementsHandler.click` and `type` now throw `ReplayActionException` after failure instead of returning normally. Runtime helper tests compile and invoke the actual customer-template source.
- The absence helper now records browser/selector/session errors as failed assertions. Dead-session regression coverage passes.
- Routine type success logs no longer concatenate the typed value. The added test checks source text; it is **not** an end-to-end log/Allure credential-canary test.
- Template copying and ZIP creation now use a source/config/docs allowlist and exclude build output, logs, Allure output, local secrets files, and sample classes.
- Text assertions have bounded waits. Generated property reads throw for a missing key; an explicitly empty value is still allowed, including the empty credential placeholders supplied by the template. This is missing-key validation, not complete credential validation.
- Test-data properties use matching UTF-8 reading/writing with additional escaping; the reader closes its stream.

### Generator and naming

- `PageAccumulator` now allocates methods using operation identity instead of retaining only the first readable name. `CodeWriter` uses those allocated symbols.
- Selected/text/URL expectations are supplied as method arguments, preserving different expected values in chronological calls.
- Valid Java case IDs preserve repeated underscores. Output filenames are checked case-insensitively; generated case counts are checked.
- Speculative bare-ID versus submit-selector merging has been removed.
- Locator-free text assertions now create the page and body-text assertion method.
- Colliding page display stems receive separate suffixes and calls use their allocated page class.
- `CodegenDataKeys` adds occurrence suffixes; ordinary repeated edits within one emitted case retain their separate values. The cross-case prerequisite gap below remains.

The established `TC_01`, `_Actions`, `_Locators`, and underscore action naming style was preserved.

## Remaining reproduced defects

### V01 — P1: prerequisite data keys still collide across tests

Files: `src/main/java/delivery/codegen/TestDataPropertiesWriter.java:33–40,77`; `CodegenDataKeys.java`; corresponding call allocation in `CodeWriter`.

The properties map is global to the package, but occurrence counters reset for every outcome. A prerequisite's own login/body sequence can therefore assign a different occurrence number from the sequence where its body is inlined into a leaf that already performs its own login. Both refer to the same prerequisite owner ID.

Reproduction:

1. `TC_SETUP` login types `login@example.invalid` into email, then its body types `changed@example.invalid` into that same field.
2. `TC_LEAF` has its own login and inlines only `TC_SETUP`'s body, matching `CallBeforeSetup`'s skip-prerequisite-login behavior.
3. The prerequisite test's login reads `TC_SETUP.type_Email.1`.
4. The leaf's setup assigns that same key to `changed@example.invalid`, overwriting the original login value in the shared properties file.

Observed: `TC_SETUP.type_Email.1 = changed@example.invalid`; expected for the prerequisite's login: `login@example.invalid`.

**Required fix:** keys must identify the original step and its phase/occurrence independently of the emitted leaf's execution sequence. Alternatively namespace each emitted call context explicitly. Reject different values mapped to the same key rather than silently overwriting. Test the prerequisite standalone and in multiple leaf contexts, with and without login elision.

Status correction: **F04 partial**, not fully done.

### V02 — P1: absence check only considers the first match

File: `customer-framework-template/src/main/java/project/utils/WaitHandler.java`, `waitUntilElementNotVisible`.

The implementation delegates to `ExpectedConditions.invisibilityOfElementLocated(locator)`. It does not implement the report's stated “every match is missing or hidden” contract.

Reproduction used the compiled customer helper with a WebDriver whose first `.error` match is hidden and whose second match is visible. `elementNotVisible` and `Validation.assertAll()` passed.

**Required fix:** query all matches on every poll and require that none is visible, while continuing to fail on session/selector/transport errors. Test zero matches, all hidden, mixed visibility, delayed disappearance, and dead session.

Status correction: **F02 dead-session fix complete; claimed all-match absence behavior incomplete.**

## Verification performed

Fresh command:

```text
mvn -B "-Dtest=delivery/codegen/**/*Test,EmitPhaseMappingTest,EmitGeneratedLayerContractTest,FrameworkPackagerTest" -l target/codegen-review/implementation-verification.log test
```

Result: **89 run, 0 failures, 0 errors, 1 skip**, BUILD SUCCESS at **2026-09-21T02:44:40+03:00**. This includes the three packager tests beyond the supplied 86-test command. The Facebook fixture remains unavailable.

Independent probes: both V01 and V02 reproduced. Source: `docs/reviews/codegen-review/ImplementationVerificationProbe.java`. Recorded results: `docs/reviews/codegen-review/implementation-probe-results.txt`. Generated evidence lives under `target/codegen-review/implementation-probe/`.

These probes use synthetic data and a controlled WebDriver substitute; no real credentials, public-site actions, or paid model calls were used. No claim is made that a downloaded framework has passed a new live workflow after these changes.

## Remaining plan work and validation limits

- Full iframe/shadow-root replay remains deferred.
- `RevisePhase` still searches for `runCase()` when annotating passed tests, despite title-derived method names.
- Click/button naming, route-aware page names, and meaningful title shortening remain unchanged.
- Numeric collision suffixes are allocated in encounter order. They solve current output collisions but do not establish stable names across case reordering or insertion during Update.
- A full Allure/log redaction policy and runtime credential-canary test remain unimplemented.
- New helper tests set wait-related system properties without restoring previous values; isolate or restore those before relying on a broad suite's timing behavior.
- NEW → UPDATE → downloaded `mvn clean test` and representative-app replay remain necessary. Compilation and the focused suite do not prove semantic replay equivalence.

## Next repair sequence

1. Fix V01 and V02 with behavior regressions.
2. Restore test-global settings after helper tests; add runtime canary coverage.
3. Run the generated ZIP benchmark, including prerequisites and repeated edits through NEW and UPDATE.
4. Record the exact tested revision once the user requests a commit. Keep naming polish separate from correctness fixes.

Launch HOLD and the existing validation candidate are unchanged.
