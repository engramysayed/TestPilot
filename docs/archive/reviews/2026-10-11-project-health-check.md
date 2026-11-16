# Keel logic health check — 11 October 2026

Scope: current working tree on `de7f579`, including uncommitted performance, locator-memory, sidebar, and library fixes. This is a broad regression run and a targeted review of critical flows, not proof that every branch is correct. No production code or customer library data was changed during this health check.

## Findings

### H01 — P1: cancellation and attempt fencing happen after Automate publication

**Evidence: source trace.** `ConversionJobRunner.run` receives cancellation but passes it only into ProvePhase. It invokes `EmitPhase.emit` without a cancellation or attempt guard. Emit writes project IR, evidence, reuse fingerprints and then calls `ProjectStore.saveVersion`. Only after the runner returns does `ConversionWorker` check attempt ownership and cancellation.

**Impact:** a cancel or loss of ownership during code generation/compilation can still publish a project version. The worker can report cancellation or "skipping publish" after publication has already occurred. The file lock serializes writes; it does not establish which job attempt is entitled to publish.

**Repair:** pass cancellation and the immutable attempt/generation fence through emit and compile. Validate ownership inside the publication lock before writing authoritative project state and publishing the version. Stage IR/fingerprints with the version. Add cancellation-during-compile and stale-attempt publication tests.

Source: `src/main/java/delivery/job/ConversionJobRunner.java:92`, `src/main/java/delivery/job/EmitPhase.java:166`, `src/main/java/delivery/portal/worker/ConversionWorker.java:109`.

### H02 — P1: environment profiles do not determine the execution target; missing profiles are accepted

**Evidence: reproduced in an isolated portal JVM.** A job selecting an environment whose origin is `https://staging.example` retained `https://project.example` as its base URL. A job selecting `envrev_missing` was persisted successfully. Freeze reads only the profile provider allowlist; origin, credential reference, and profile admission caps do not participate in this path. Exceptions resolving the revision are swallowed.

**Impact:** selecting staging can execute against the project's original target instead, and the recorded environment pin overstates what actually governed the run.

**Repair:** resolve and validate the complete environment before admission, apply its target and credential reference, enforce its documented caps, and reject unknown or corrupt revisions. Hash the effective frozen values.

Source: `src/main/java/delivery/portal/service/PortalStore.java:972`, public submission `src/main/java/delivery/portal/api/v1/PublicJobApiController.java:187`.

### H03 — P1: broken budget storage allows hosted admission

**Evidence: reproduced.** Replaced only the diagnostic tenant's budget JSON with malformed text. `saveJob` still admitted the next scoped job. `reserveBudget` catches storage/parse exceptions and treats them as non-fatal, even with a nonblank hosted tenant.

**Impact:** a broken or inaccessible ledger disables budget enforcement while the UI still presents caps as enforced.

**Repair:** fail closed for scoped jobs on ledger errors; reserve legacy behavior only for explicit unscoped jobs. Report an actionable budget-storage error and test malformed JSON and write failure.

Source: `src/main/java/delivery/portal/service/PortalStore.java:1118`.

### H04 — P1: a rejected API submission consumes its idempotency key

**Evidence: reproduced.** Set the diagnostic queue limit to zero, submitted with an idempotency key, and received `JobAdmissionException`. After restoring the limit, retrying the same request returned `idempotentReplay=true` with a job ID absent from the job store. The key is persisted before `saveJob` can reject admission. Also, the replay lookup happens after materializing the current workbook and resolving current credentials, so retries unnecessarily depend on mutable current state and create unused temporary workbooks.

**Impact:** CI can receive a successful replay response for a job that never existed and cannot recover using the same key.

**Repair:** make idempotency reservation and admission recoverable as one state machine. Completed replay must resolve before creating inputs; rejected reservations must not masquerade as admitted jobs. Test queue/budget rejection, process interruption, and concurrent retries.

Source: `src/main/java/delivery/portal/api/v1/PublicJobApiController.java:149`, `:171`, `:200`.

### H05 — P2: another private runner in the same workspace can renew a job's lease

**Evidence: reproduced.** Runner A claimed a private job. Runner B, enrolled in the same workspace, successfully renewed A's lease. `heartbeatRunnerLease` checks tenant identity but creates a lease from the current job itself rather than verifying the caller's worker/attempt ownership. This is a same-workspace defect, not a cross-tenant leak. Private runners remain outside the shared first-launch support scope.

**Impact:** another agent can keep a lost job alive, delaying the uncertain/interrupted recovery decision. Ownership checks on stage, input download, and artifact upload do not repair this heartbeat path.

**Repair:** require worker ID and the original attempt ID/cancel generation on lease renewal; do not manufacture the caller's lease from mutable current job state. Apply attempt fencing to completion too.

Source: `src/main/java/delivery/portal/service/PortalStore.java:1306`.

### H06 — P2: deleting prerequisites leaves invalid Call-before references

**Evidence: reproduced.** Saved TC_SETUP and TC_LEAF with `CallBefore=TC_SETUP`, then deleted TC_SETUP. Deletion succeeded and the remaining TC_LEAF still referenced the removed ID.

**Impact:** the library accepts a broken suite that later fails dependency validation rather than telling the user at deletion time.

**Repair:** reject deletion with the dependent case IDs, or offer an explicit reviewed dependency update. Never silently clear prerequisites.

Source: `src/main/java/delivery/portal/service/GeneratedWorkbookService.java:876`.

### H07 — P2: vision hit-testing cannot identify clickable framework menu list items

**Evidence: reproduced in headless Chrome.** A visible `<li onclick=...>` inside an aside returned no GroundedNode at its center. Hit-testing accepts a limited set of native tags and ARIA roles, excluding onclick controls and inferred menu items recognized by DOM extraction.

**Impact:** even a correct vision box can fail grounding for a real clickable menu. The recent sidebar extraction fixes improve DOM binding but do not fix this separate vision path.

**Repair:** align live interactive-node detection with supported DOM control semantics, including custom click handlers and navigation roles. Preserve exact live-node identity and enabled/visibility validation.

Source: `src/main/java/delivery/vision/SeleniumGroundingBrowser.java:32`.

### H08 — P2: vision grounding ignores custom preferred locator attributes

**Evidence: reproduced in headless Chrome.** With `data-axis-test-id` active, DOM extraction produced the preferred CSS candidate for a button. Grounding then chose `id:fallback`. GroundedNode does not carry arbitrary preferred attributes; candidate matching ignores the preferred CSS candidate, and new locator construction chooses ID first.

**Impact:** the user's preference is respected by DOM extraction but is not a consistent contract across vision healing.

**Repair:** collect the configured preferred attributes from the observed live node, match existing candidates by exact node identity, and apply the same locator order when constructing a new candidate. Verify uniqueness live before accepting the preferred hook.

Source: `src/main/java/delivery/vision/SeleniumGroundingBrowser.java:66`, `src/main/java/delivery/vision/ElementGrounder.java:89` and `:103`.

## Diagnostic evidence

- `target/health-logic-probes.log`: isolated Spring portal; synthetic origins, tenant, jobs, environment, budget and two runner enrollments. No browser jobs submitted; API requests used the private-runner option.
- `target/health-core-probes.log`: temporary library and local headless Chrome data pages; prerequisite deletion, custom-menu hit-testing and preferred-hook grounding.
- Reproduction sources: `target/HealthLogicProbe.java`, `target/CoreHealthProbe.java`.
- `target/health-check-2026-10-11.log`: broad deterministic Maven regression. Final result is recorded in the completion section below when the run finishes.

## Limits and next work

No Axis/customer application was exercised. No live Cursor or Ollama quality/timing was tested. Shared/dedicated install drills and `*LiveSmoke*` are excluded by the deterministic profile. No deployment firewall, storage recovery, paid-provider spend accounting, or mobile implementation is certified by this check.

Priority: publication fencing; environment resolution; fail-closed budgets; recoverable idempotency; dependency-safe library deletion; unified preferred-locator and interactive-control semantics; private-runner attempt ownership before supporting private runners.

Previously fixed library replacement and misleading history source labels remain covered by regression tests. Their passing tests do not negate the new findings above. Full-suite success and launch readiness are separate decisions.

## Completed regression

`mvn -B -Pdeterministic test` completed with **BUILD SUCCESS** at **2026-10-11T17:36:39+03:00** in 9:49: **1,487 run, 0 failures, 0 errors, 3 skipped (1,484 passed)**. This tested the dirty working tree on `de7f579`; it was not a clean-checkout certification of that commit. The diagnostic probes ran separately and reproduced seven defects despite the existing suite being green.

Skipped cases:

- `CheckoutChainAssertionReplayTest.regenerateAndReplayExactOrderAndSignedOut`: opt-in downloaded replay flag was unset.
- `FacebookSubmitLiveRebindTest.liveRegPageSubmitBindPrefersButtonWhenPresent`: local Facebook IR was missing.
- `FacebookIrCodegenIdentityTest.facebookIrRegeneratesWithDistinctControlIdentity`: local Facebook IR was missing.

The shared and dedicated install executions were skipped by profile configuration; `*LiveSmoke*` tests were excluded. No fixes were applied or committed as part of this review.


## Repair follow-up

All eight findings were repaired in the working tree. Implementation and exact test outcomes, including the broad-run Chrome startup failure and successful isolated replay, are recorded in [2026-10-11-health-fixes.md](2026-10-11-health-fixes.md). The findings above remain the original before-repair review.
