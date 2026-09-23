# Codegen close-out — 21 September 2026

Working-tree close-out. **No commit, no publish, no candidate replace, no launch-gate close.** First-release **HOLD** is unchanged. Validation candidate remains `2721e6d`.

This codegen correctness batch is **locally validated**. Stop expanding its product scope. No optional features were added after that verdict.

## Open / out of scope (not closed)

These items were deferred from this batch. They remain **open** or **out of scope**. They are **not closed**.

| Item | Status |
|---|---|
| iframe / shadow-DOM authoring and replay | **Open / out of scope** |
| Naming polish | **Open / out of scope** |
| Stable suffix allocation | **Open / out of scope** |
| Complete secret redaction | **Open** (remainder documented; no complete-redaction claim) |
| Representative-app live G→E→A NEW → download → replay → UPDATE → replay | **Open** (next planned target: local Medusa shop; see `docs/reviews/2026-09-21-medusa-acceptance-plan.md`) |
| Wiring the ZIP UPDATE benchmark to `ReuseEligibility.authorIds` / `copyForReuse` | **Out of scope** for this batch. ZIP UPDATE stays fixture-IR regenerate. Production reuse is covered by `CodegenUpdateReuseIntegrationTest`, not by the ZIP benchmark. |

Do not treat the table above as a close list.

## Verdict

| Item | Result |
|---|---|
| Full `-Pdeterministic` suite | **1365 run, 0 fail, 2 skip**, BUILD SUCCESS. No codegen-batch regressions found; no production fix in this pass. |
| Allure reporting | Named steps wrap the real type/select/click/assert work. Typed values are not step parameters. Failed actions mark the named step `failed`. Soft-assertion failures mark the named step `failed` without throwing. |
| ZIP UPDATE vs production reuse | **Distinct pipelines.** See section below. |
| Customer self-tests | `ValidationAssertAllTest` stays in the template; excluded from copy+ZIP. Nested NEW 13 and UPDATE 14 downloaded tests match generated cases. |
| FAIL TestNG | Per-case **test-method** status is separate from **configuration** failures. `TC_FAIL_SETUP_LEAF` `@Test` is **SKIP**; `setUp` is a config FAIL. |
| Privacy | Typed password canary is absent from Allure JSON. Assertion expected, failure messages, testdata, IR, screenshots, and some Allure attachments still contain sensitive/synthetic data. **Not complete redaction.** |
| Launch | **HOLD** |

## Full repository deterministic suite (required evidence)

Command (this workstation, 21 September 2026):

```text
mvn -B -Pdeterministic test
```

Profile `-Pdeterministic` excludes `*LiveSmoke*`, `SharedInstallDrillTest`, and `DedicatedInstallDrillTest`. Surefire still prints those two drill executions as skipped after the main suite.

| Field | Value |
|---|---|
| Finished at | `2026-09-21T14:14:50+03:00` |
| Maven total time | 06:31 min |
| TestNG elapsed | 388.7 s |
| Result | **BUILD SUCCESS** |
| Tests run | **1365** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **2** |
| Passed | **1363** (`testng-results.xml`: `passed="1363" failed="0" skipped="2"`) |

### Named skips (not failures)

Neither skip is a codegen-batch regression. Both are missing Facebook IR on this tree (`delivery-store/facebook-com/prj_e9a9fc7313b2/ir` is absent).

| Class | Method | Reason |
|---|---|---|
| `delivery.authoring.FacebookSubmitLiveRebindTest` | `liveRegPageSubmitBindPrefersButtonWhenPresent` | `SkipException`: Facebook IR missing |
| `delivery.codegen.FacebookIrCodegenIdentityTest` | `facebookIrRegeneratesWithDistinctControlIdentity` | `SkipException`: Facebook IR not present |

### Profile-skipped executions (not in the 1365)

After the main TestNG suite:

```text
surefire:test (shared-install-drill) — Tests are skipped.
surefire:test (dedicated-install-drill) — Tests are skipped.
```

Live-smoke classes were excluded by the same profile (not executed).

### Earlier subset (not a substitute for the full suite)

```text
mvn -B "-Dtest=delivery.codegen.AllureStepsReportingTest,...delivery.store.LocatorMapBuilderTest" test
# Tests run: 127, Failures: 0, Errors: 0, Skipped: 1  BUILD SUCCESS
# skip: FacebookIrCodegenIdentityTest only
```

The 127-test codegen subset is superseded as completeness evidence by the 1365-test run above.

## Fixture ZIP replay vs production reuse (do not conflate)

| Check | What it is | What it is not |
|---|---|---|
| `GeneratedZipReplayBenchmarkTest` | Local fixture pages + headless Chrome. Packager NEW / UPDATE / FAIL ZIPs. Nested `mvn clean test` of the **downloaded** customer project. UPDATE = **fixture IR regenerate + `EmitPhase` overlay**. | Not `ReuseEligibility.authorIds`. Not `copyForReuse`. Not `TcDraftStatus.REUSED`. Not a representative shop. |
| `CodegenUpdateReuseIntegrationTest` | In-process UPDATE through `ReuseEligibility.authorIds` + `copyForReuse` + `EmitPhase`. Asserts retained PASS, changed re-author, PASS→TODO, prerequisite invalidation, repeated edits. **Passed** in the 1365-test run (`status="PASS"`, 7660 ms). | Not a downloaded ZIP. Not Chrome. Not Medusa. |

ZIP UPDATE remaining a fixture regenerate is **labeled**, not “closed as production reuse.”

This full-suite run also executed the ZIP benchmark (included in the 1365):

| Nested ZIP | Maven | Cases |
|---|---|---|
| NEW | exit 0 | 13 PASS (`TC_A_B`, `TC_LEAF_FULL`, `TC_LEAF_ELIDE`, `TC_UNICODE`, `TC_SELECT`, `TC_PAGES`, `TC_SIMILAR`, `TC_A__B`, `TC_ABSENT_OK`, `TC_READY`, `TC_EDIT`, `TC_CANARY`, `TC_SETUP`) |
| UPDATE | exit 0 | 14 PASS (NEW set plus `TC_UPD_SAVE`) |
| FAIL | exit 1 | test methods: `TC_FAIL_TYPE/SELECT/CLICK/EXPECT/ABSENCE` FAIL, `TC_SETUP_BAD` FAIL, `TC_FAIL_SETUP_LEAF` **SKIP**; config: `TC_FAIL_SETUP_LEAF=setUp=FAIL` |

SHA-256 from this full-suite ZIP run (`docs/reviews/codegen-review/zip-benchmark-results.txt`; hashes change per pack because generated artifacts are not bit-stable):

| Zip | SHA-256 |
|---|---|
| NEW | `3c7a3894a2930793a58191f0d43266bfadafdf34ec2b405d1d21adbe8eba400b` |
| UPDATE | `95aaa1b4e2f44a1192153b6f3412bbf2ddd9c694e1f9766717db9e6d6d3d0ce3` |
| FAIL | `baf4f2c0f2a9597fbe8afbd3d8e3bec9c5c7a76efb93d42a87b8d5fa105d5570` |

Evidence dir: `target/codegen-review/zip-benchmark/`. Nested NEW/UPDATE used fixture `http://127.0.0.1:4979`.

## Allure: execution wrapped, values not on the step

Empty `step_*()` companions are gone. Generated actions call `AllureSteps.run("readableName", () -> { actual work })`.

| Kind | Public method | What Allure records |
|---|---|---|
| type / select | `name(String value)` — value is a Java parameter only | Step name `type_*` / `select_*`. Work runs inside the step. `parameters` is empty. |
| click | `name()` | Step name `click_*` wrapping `click(...)`. |
| parameterized assert | `name(String expected)` | Step name `assert_*` wrapping the validation call. Expected is not a step parameter. If the soft assert records a failure, the step status is `failed` and the test continues until `assertAll()`. |
| no-arg assert | `name()` | Same wrapper. Soft failure marks the step `failed`. |

Thrown actions (`ReplayActionException` / missing element) fail the named step and rethrow.

Inspected Allure `*-result.json` after downloaded replay (`target/codegen-review/zip-benchmark/{new,fail}-allure-steps.json`):

| Example | Parent test | Step | Status | Timing | Parameters | Secret canary |
|---|---|---|---|---|---|---|
| type success | `TC_CANARY — Password canary` | `type_Secret` | passed | stop > start (128 ms) | `[]` | absent from Allure JSON |
| select success | `TC_SELECT — Select size` | `select_Size` | passed | stop > start (204 ms) | `[]` | n/a |
| assert success | `TC_READY — Fixture heading` | `assert_Body_Text_Contains` | passed | stop > start | `[]` | expected remains in generated Java, not as a step param |
| type fail | `TC_FAIL_TYPE — Missing type target` | `type_Missing_Secret` | failed | stop > start | `[]` | locator in statusDetails, not typed text |
| select fail | `TC_FAIL_SELECT — Missing select target` | `select_Missing_Size` | failed | stop > start | `[]` | Selenium missing-element text |
| assert fail | `TC_FAIL_EXPECT — Missing assertion expected` | `assert_Body_Text_Contains` | failed | stop > start | `[]` | expected still in Surefire/logs/Allure attachments |

## Privacy boundaries (not complete redaction)

**Kept out of Allure step parameters:** typed password `CANARY_PW_zipreplay_7f2c9a`. NEW replay found it only in `delivery-testdata.properties` and the compiled copy of that file.

**Still present (intentional remainder; redaction remains open):**

1. Testdata properties — typed values including the password canary.
2. Generated test source — assertion expected inlined (`CANARY_ASSERT…`, `CANARY_FAIL_EXPECT…`).
3. Failure messages / Surefire / logs — assertion helpers echo expected text.
4. Allure result JSON and attachments for **failed assertions** — `CANARY_FAIL_EXPECT…` appeared in FAIL `*-result.json` and `*-attachment.txt`.
5. Work-dir IR JSON (not in the customer ZIP allowlist).
6. Screenshot pixels of visible assertion text on the fixture page.

## FAIL benchmark: test method vs configuration

Generated FAIL cases: 7. Nested Maven summary: `Tests run: 8, Failures: 7, Skipped: 1` — Surefire also counts the failed `@BeforeMethod`.

| Case | Test method | Configuration |
|---|---|---|
| `TC_FAIL_CLICK` | FAIL | — |
| `TC_FAIL_ABSENCE` | FAIL | — |
| `TC_FAIL_EXPECT` | FAIL | — |
| `TC_FAIL_TYPE` | FAIL | — |
| `TC_FAIL_SELECT` | FAIL | — |
| `TC_SETUP_BAD` | FAIL | — |
| `TC_FAIL_SETUP_LEAF` | **SKIP** (body did not run; body-probe not hit) | `setUp=FAIL` (`ReplayActionException` on `missing-setup`) |

Do not describe the skipped leaf body as a failed `@Test` method.

## Working-tree status (21 September 2026, after the suite)

Recorded with `git status --porcelain` / `git log -1`. **Not committed.**

| Field | Value |
|---|---|
| Branch | `launch/p0-baseline-and-p1-assertions` |
| HEAD | `a4dc94e` `docs(phase5): nominate 23f9351, record clean-checkout evidence, and pin 2721e6d` |
| Dirty | **89** modified (` M`), **27** untracked (`??`) |
| Facebook IR | absent (explains the two skips) |

The dirty tree mixes this codegen batch with unrelated in-progress work (Phase 5/6 ops docs, vision/DOM reviews, portal/heal/authoring edits, `.agents/` skills, and so on). Unrelated changes were **preserved**. This close-out does not stage, commit, or revert them.

This pass did not add product features. Documentation updates only: this file and `docs/reviews/2026-09-21-medusa-acceptance-plan.md`.

## Limitations

- Local fixture pages + headless Chrome for ZIP replay. That is not Medusa and not production reuse.
- Secrets are not fully redacted (open).
- Full `-Pdeterministic` is not live-smoke and not install drills.
- Public rollout remains **HOLD**.
