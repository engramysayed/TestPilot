# Health-check fixes — 11 October 2026

Scope: all eight findings in `2026-10-11-project-health-check.md`, on the existing dirty working tree. No customer app, real customer library, deployment, or paid model was exercised. Nothing was committed or published.

## H01 — publication ownership and cancellation

ConversionWorker passes an immutable original lease into a PublicationGuard. Its cancellation supplier detects cancellation, lease expiry and changed ownership. Emit propagates that supplier to the supervised compile process. IR, locator map, evidence, hashes, prove context and last-job metadata are prepared in the job's `publication-state` directory instead of written early into the project.

ProjectStore invokes the guard after acquiring the publication file lock. PortalStore checks the current job, worker, attempt, generation, lease and project state, and holds its monitor while committing. Cancellation and project purge use that same monitor. Dry-run Automate follows this path too. This prevents a previously cancelled/stale attempt from publishing; a cancellation accepted after publication does not undo an already published version.

Regression: `HealthPublicationFenceTest` cancels during the compile boundary and rejects lost ownership at publication, checking prior hashes and absence of published IR/version/last-job. `HealthAdmissionRegressionTest` checks cancelled and expired portal leases. Existing ProcessSupervisor tests cover child-process cancellation and termination.

ConversionWorker also preserves a reconciled terminal outcome: losing a lease is not labelled as user cancellation and cannot overwrite `INTERRUPTED_UNCERTAIN`. `ConversionWorkerFenceTest` distinguishes explicit cancellation from lease loss.

## H02 — effective immutable environments

Initial admission reads and verifies the selected environment revision and its stored bytes/checksum. Missing, corrupt or unresolved credential references reject admission. The job receives the environment origin, credential profile and provider allowlist before its effective input hash is stored. Project storage identity does not change.

Positive `maxQueued` adds an environment active-job cap at admission. Positive `maxRunning` restricts claims, including private runners. Zero inherits installation limits. These caps are scoped to the selected project and immutable environment revision. Reruns retain their frozen credential values rather than resolving rotated credentials again.

Regression: origin/credential/provider resolution, active/running caps, missing revision/reference and damaged revision bytes.

## H03 — unavailable budgets reject admission

Scoped ledger parse/read/write errors now reject with `BUDGET_UNAVAILABLE` and HTTP 503. They no longer use the legacy unscoped exception path. An actual capacity rejection retains its admission error.

Regression: malformed tenant ledger, no admitted job and restored test ledger.

## H04 — recoverable idempotency

Public API submissions serialize key reservation and admission under the existing cross-process file lock. Keys have PENDING and ADMITTED states and are written using temporary-file replacement. A rejected submission removes its reservation unless a durable job already exists. Recovery checks the durable job before replaying or resuming a pending reservation.

A successful replay occurs before preparing a workbook or resolving current credentials. Temporary inputs are cleaned up if admission fails. Different request bodies still conflict. Existing legacy entries are recoverable through their job identity.

Recovery checks persisted job rows, and new memory-cache entries are installed only after successful transaction commit. A rolled-back admission cannot be replayed through a provisional cache entry. Existing missing-workbook and credential-selection error responses are preserved.

Regression: rejected submission followed by retry, replay after current workbook/credentials changed, crash-state recovery before/after job commit, and two concurrent submissions admitting one job.

## H05 — original runner attempt required

Runner job requests now carry `X-Keel-Attempt-Id`; lease requests also carry `X-Keel-Cancel-Generation`, both from the original claim response. The portal checks tenant, runner, original attempt, live lease and terminal state. Stage, input, upload and completion also reject stale attempts. Heartbeats cannot revive an expired lease.

The bundled agent supplies the headers, stops on rejected leases/inputs, and checks stage/upload/completion responses. **Portal and private agents must be rebuilt together for this protocol change.** Private-runner deployment support remains outside first-launch scope.

Regression: foreign runner within the same workspace; valid owner; wrong attempt; cancellation generation; same runner reclaiming with a new attempt while its old requests remain rejected. Existing API and separate-process tests are included in the full suite.

## H06 — dependency-safe library deletion

Deletion checks the retained cases' transitive prerequisite chains before creating a revision or changing workbook bytes. A dependency-breaking request reports `CALL_BEFORE_DEPENDENTS` with affected case IDs. Deleting an entire dependency chain together remains allowed when another case remains. Dependencies are never silently cleared.

Regression: rejected prerequisite deletion preserves HEAD and workbook bytes; reviewed whole-chain deletion succeeds.

## H07 — live custom navigation hit-testing

Vision recognizes onclick controls, supported interactive roles, content-editable controls and leaf navigation list items. Hit-testing still identifies the live element at the image coordinates and rejects hidden/disabled observations. Navigation grouping list items containing nested list items are excluded.

Regression: headless Chrome on a synthetic sidebar list item, including disabled-state rejection. No AI or customer page was used.

## H08 — preferred hooks survive vision grounding

The observed live node includes the configured preferred attribute/value. Unique preferred hooks take precedence over ID and other fallbacks, including when the candidate table lacks that hook. Existing candidates must resolve uniquely to the exact observed node. Duplicate hooks fall back to a unique stable candidate instead of accepting an ambiguous selector.

Regression: real Chrome observes a sidebar control with both ID and `data-axis-test-id`; vision selects the preferred CSS locator, can construct it when the table is empty, and selects the unique ID when hooks are duplicated.

## Validation

Focused suite before the final extra stale-attempt/corrupt-environment checks: **44 tests, zero failures**, BUILD SUCCESS at 2026-10-11T17:56:40+03:00.

First broad run: **1,503 tests, 2 failures, 3 skips**, at 2026-10-11T18:08:19+03:00. Both failures were JobCompareApiTest rerun fixtures referring to nonexistent `envrev_cmp`. The fixtures now create a real stored revision, retaining the original rerun/pin/history assertions.

Final focused regression: **55 tests, zero failures/errors/skips**, BUILD SUCCESS at 2026-10-11T18:09:46+03:00. Includes the worker/API final changes, transaction rollback, environment corruption, stale runner reclaim and repaired JobCompare fixtures. Log: `target/health-fixes-final-focused.log`.

Final full deterministic run: **1,508 tests, 1 failure, 0 errors, 3 skips**, finished 2026-10-11T18:19:58+03:00. The only failure was `GeneratedZipReplayBenchmarkTest.updateDownloadExtractAndMvnCleanTest`: Chrome failed to create a session (`chrome not reachable`) in the downloaded UPDATE package's `TC_EDIT.setUp`. It did not reach that test body. Log: `target/health-fixes-final-deterministic-2026-10-11.log`; XML snapshot: `target/health-fixes-final-broad-results.xml`.

Isolated `GeneratedZipReplayBenchmarkTest` rerun: **4 tests, zero failures/errors/skips**, BUILD SUCCESS at 2026-10-11T18:23:41+03:00. Downloaded NEW and UPDATE passed; deliberate FAIL package returned nonzero with prerequisite failure skipping the leaf. The Chrome startup failure did not reproduce. No code was changed between the broad run and this rerun. Log: `target/health-fixes-zip-recheck-2026-10-11.log`. This is additional evidence, not a replacement claim that the original full run was green.

Named skips: `CheckoutChainAssertionReplayTest.regenerateAndReplayExactOrderAndSignedOut` (opt-in flag unset), `FacebookSubmitLiveRebindTest.liveRegPageSubmitBindPrefersButtonWhenPresent`, and `FacebookIrCodegenIdentityTest.facebookIrRegeneratesWithDistinctControlIdentity` (local Facebook IR missing). Install drills and LiveSmoke tests are excluded by the deterministic profile.

The original health review remains a historical record of the defects before repair. These fixes and tests are working-tree evidence, not clean-commit or production-host certification.
