# DOM binding, vision grounding, and Cursor routing fixes

Implementation work on 20–21 September 2026, on the working tree above `a4dc94e9fc4faffff4c8915693dd5fd2817cf7ef`. This is not a committed release candidate. Launch HOLD and candidate `2721e6d` are unchanged.

## User contract

UI-TARS remains a screenshot grounding model. It proposes a location; Keel resolves the live DOM target, validates the locator and action, and executes through Selenium. Selecting Precision means Cursor gets first choice for test-step binding. If Cursor is unavailable, fails to produce a valid target, or exhausts its budget, Keel may take over and the run must report why.

## Implemented

### DOM and binding

- Repeated test hooks no longer suppress unique IDs or collapse separate controls.
- Slimming avoids copying a control twice through both parent and child fragments. Source ordinals are preserved before hidden-node removal and truncation, including ARIA form controls. Original attribute ambiguity survives filtering.
- Hidden checkbox/radio controls are excluded. Flattened embedded contexts do not receive invented top-document ordinals.
- Named-action ambiguity is rejected. Text-only duplicate controls cannot become one apparently unambiguous selector through candidate deduplication. Candidate selectors must resolve to one snapshot node.
- Preferred/custom hook CSS includes its element tag. Shared locator validation covers deterministic binding as well as model-picked candidates, including XPath quote literals and role-scoped form ordinals.
- ID identity stays case-sensitive. ARIA control roles and input submit/button types are retained. Accessible-name precedence follows `aria-labelledby` before `aria-label`; binding labels are not shortened to display previews.
- Actionable controls are prioritized ahead of decorative IDs at the candidate cap. Explicit re-edit wording permits intentional reuse of a previously filled field.
- Model-written locators are resolved against the actual snapshot, including tag and case, rather than accepted because an attribute substring occurs somewhere. Empty evidence, duplicate targets, and incompatible action types are rejected.

### Grounding

- A Login/Submit click cannot be validated against a text input, even if the input has a misleading ID or model description.
- The node hit by the model coordinates keeps its actual identity. Equal labels and partial test-hook matches cannot substitute another node. Selenium verifies that the emitted locator uniquely resolves to the observed displayed/enabled element.
- Native UI-TARS points and boxes use the same 0–1000 contract. Edge-point padding preserves the point center. JSON point scaling uses one coordinate space for both axes.
- Screenshot observations include URL, viewport, scroll, and DOM-mutation version checks before accepting a target. Stale observations are rejected.
- Sweeps stop on provider errors, cancellation, unchanged captures, and elapsed budget. A thread-scoped deadline bounds nested local-model HTTP requests without moving execution to a different policy context. Failed sweeps restore starting scroll.
- Wide, shallow controls are accepted by the box-quality gate. Region-size and low-confidence checks remain.
- Vision diagnostics live under the current job/TC evidence directory, are credential-sanitized, and omit raw provider responses. Calls without an explicit evidence scope do not write to a shared global journal.
- Post-click body diagnostics consider the full normalized text rather than only the first 400 characters. These diagnostics remain supporting evidence, not a business-outcome assertion.

### Cursor / Precision

- Cursor's selected candidate is bound directly; deterministic scoring cannot silently replace it with another target.
- Precision does not execute cached Keel locators or speculative Keel form-fill shortcuts before consulting Cursor for the test step.
- An empty DOM shortlist can escalate to Cursor solve. Written solutions still require snapshot presence, unique identity, compatible action and locator validation.
- Provider exceptions and exhausted/unavailable providers return explicit fallback reasons. On fallback, normal Keel recovery, including enabled vision grounding, is available. Existing IR/status fallback reporting remains in use.
- Engine, Precision enabled flag, and call budget are frozen per admitted job, survive persistence/hydration, and carry into reruns. Project edits cannot change an already admitted job's engine.
- The public job API now uses the project's engine. Private-runner input packs carry the frozen engine/configuration instead of hard-coding Keel in the agent.
- Settings copy explains Cursor-first binding and visible fallback without promising higher accuracy.

## Evidence

- Intermediate focused suite: 406 tests, zero failures, one skip, including a real Chrome locator-identity test. This command also selected the existing Qwen live smoke; it passed and refreshed its existing notes file.
- Expanded DOM/authoring/heal/vision/API suite: 461 tests, zero failures, one skip (20:13:31 +03:00). Subsequent duplicate-text/coordinate hardening is covered by the final run below.
- Local Chrome fixture: original visible checkbox selected after slimming; the second of two Delete buttons resolved to its own DOM identity; duplicate hooks rejected; DOM mutation version changed as expected.
- Real UI-TARS on a disposable in-memory merchant login: one provider call, no provider error, exact Login target accepted and executed, no wrong-target execution. Record: `target/uitars-local-grounding-result.json`; run log: `target/uitars-local-live.log` (20:16:40 +03:00).
- Full deterministic suite: `mvn -B -Pdeterministic -l target/dom-vision-final-regression.log test` — 1,326 tests, zero failures, zero errors, two skips; BUILD SUCCESS at 2026-09-20T20:29:54+03:00. Its compilation preceded the final duplicate-text selector and coordinate hardening; final-tree verification is recorded separately below. Shared/dedicated install drills were skipped by their profile configuration.
- Final-tree verification initially found two failures in 469 tests: a sidebar control with a child span was incorrectly removed by selector validation, and the private-runner disconnect test's reconciliation-count assertion failed. The selector now excludes same-tag wrappers while keeping decorative label spans. The runner test passed in isolation; its artificial lease expiry/reconciliation now holds the same store monitor as heartbeat updates to prevent a late heartbeat renewing that test lease. The final FAILED/INTERRUPTED_UNCERTAIN assertions remain unchanged. The focused correction run passed 31/31 at 2026-09-21T01:24:36+03:00; combined final verification follows.
- Final source-tree combined verification: **469 tests, zero failures, zero errors, one skip**, BUILD SUCCESS at **2026-09-21T01:27:52+03:00**. Log: `target/dom-vision-final-targeted-v3.log`. The skipped `FacebookSubmitLiveRebindTest.liveRegPageSubmitBindPrefersButtonWhenPresent` requires a missing local Facebook IR fixture. This run includes live local Chrome identity checks, concurrent same-host Prove/emit, private-runner live execution/disconnect/cancel, engine persistence, rerun pins, and DOM/authoring/heal/vision regressions. No source changes followed this successful run.

Final verification command:

```text
mvn -B "-Dtest=delivery/authoring/**/*Test,delivery/heal/**/*Test,delivery/vision/**/*Test,!*LiveSmokeTest,HtmlSlimmerTest,AuthoringEngineApiTest,PrivateRunnerApiTest,RerunSupportTest,ConcurrentSameHostProveEmitTest,PrivateRunnerLiveBrowserProcessTest,GroundingDomLiveSmokeTest" -l target/dom-vision-final-targeted-v3.log test
```

The full 1,326-test run and final 469-test run overlap; they are not additive. `git diff --check` passed for the source/test changes. Work remains uncommitted.

## Boundaries not certified by this patch

- One successful local UI-TARS case is not an accuracy benchmark on real customer applications. Medusa's full storefront/admin workflow and downloaded replay have not been run here.
- Cursor ranking/solve/fallback was exercised with controlled sidecars, not a paid live Cursor invocation.
- Full context-addressed iframe/shadow-root IR/replay and nested-container scrolling remain broader enhancements. Unsafe flattened ordinals are rejected; this does not claim complete embedded-context support.
- DOM changes alone cannot establish a requested business outcome. Assertions still need to express the expected result; generic change diagnostics do not prove checkout, authentication, or persistence semantics.
- Native UI-TARS confidence remains synthetic. Job-wide vision-model/preprocessor revision pinning is not added by the Precision configuration snapshot changes.
- Deployed isolation, production restore, customer-app acceptance, and named launch approvals are outside these local fixes.
