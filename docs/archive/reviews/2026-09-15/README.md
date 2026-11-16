# Keel: public-launch readiness review

**Reviewed:** 15 September 2026. **Source revision:** `0aca9c6` plus the existing working-tree documentation changes. **Target:** public multi-user launch, supporting shared hosting and dedicated customer installations. Customer policy for sending data to cloud AI remains undecided.

## Verdict

**Keel is a feature-rich pre-release product, but it is not ready for a public multi-user launch.** The main product workflows exist. The remaining work is substantial correctness and operational hardening, especially around trustworthy test results, Update, isolation, and job lifecycle. Adding more AI features should follow these fixes.

The most consequential finding is in the downloadable customer framework: **soft assertion failures are swallowed**. Another confirmed defect promotes unchanged failed cases to passed during Update. These directly undermine the promise that green results mean a test was proven.

This review adds evidence and recommendations only. It does not change production code, amend approved product policy, or mark historical tasks complete.

## Scope and evidence limits

- Inventoried **142 documentation files**: README, changelog, product/handoff docs, all 137 Markdown files under the original `docs/` and `specs/`, the constitution, and the framework/engine READMEs. The [document inventory](document-inventory.json) records paths, titles, stated status, headings, task counts, and SHA-256 hashes.
- Read the primary product contracts and examined historical plans/specifications through their requirements, status, goals, and implementation references. Deep code inspection concentrated on shared state, ingestion, proving, Update/emission, customer assertions, portal authorization, jobs, retention, and Hunt. **This is a broad risk-based audit, not a claim that every historical document or source line received an equally deep review.**
- Ran the delivery/driver suite and compiled the customer template. Added isolated reproduction programs using synthetic data and temporary directories. Browser/network behavior in those probes is mocked; no customer target was attacked.
- Did not perform a fresh full live Generate → Execute → Automate → downloaded-suite replay, a load test, deployment penetration test, dependency vulnerability scan, browser UI walkthrough, or backup restoration exercise. Existing smoke notes are historical evidence, not a fresh release certification.
- Existing owner modifications to three vision smoke notes and the untracked `.agents/` directory were preserved.

## 1. What stage is each workstream at?

### Product surfaces: implemented, with incomplete release validation

**Projects and accounts:** invite/session authentication, owner checks, projects, credential profiles, archive/delete, artifacts, and summary views exist. Ownership is enforced in ordinary API routes, but domain-shared files and worker lifecycle do not yet provide a complete multi-customer boundary. The data model is user-owned projects; organization membership and team roles are not yet a complete customer-workspace model.

**Generate and library:** stories, model comparison, async jobs, CSV/JSON import, workbook storage, editing, quality gates, and AI review/accept/discard exist. Tests cover many contracts. Library files are mutable `latest.*` files without a revision conflict protocol; concurrent edits/reviews/heal patches deserve dedicated validation.

**Execute:** proof, recovery, evidence, design comparison, selected cases, cancellation, and results export exist. It still shares work-folder naming risks with other jobs of the same kind, and per-case evidence identity does not model repeated prerequisite executions separately.

**Automate:** prove → IR → revise → deterministic code emission → compile check → ZIP/version storage exists. **Customer assertion failures, Update reuse, incremental code emission, and dependency replay are release blockers.** A successful `test-compile` does not establish correct runtime assertions.

**Healing and vision:** extensive DOM binding, fallback, recovery, liveness, visual grounding/assertion, and evidence mechanisms exist. Prior notes explicitly record deferred live validation and at least one Qwen grounding miss despite a successful visual assertion. Treat provider/model accuracy as measured behavior per benchmark, not a universal capability claim.

**Precision:** implemented with job call budgeting, fallback metadata, and project-level selection. The newest commit moved configuration into project Settings; several earlier specs/handoff passages still describe per-job radio buttons. Keel fallback can itself use Cursor, so engine choice is not an enforced data-residency policy.

**Bug Hunter:** live/dry paths, page maps, strategies, coverage, grounded element actions, token substitution, dedupe, triage, and packs exist. Navigation and JavaScript have broader authority than locator grounding. Data sanitation and browser isolation need hardening. The two-pass DOM plan is explicitly deferred until live evidence justifies it; that is not an accidentally missing feature.

**Operations/release:** Windows startup instructions, retention and filesystem persistence exist. No root `.github/` release/test workflow was found. Customer CI templates do exist. Durable job scheduling, lifecycle reconciliation, release profiles, verified restore, tenant quotas, and an enforceable deployment/data policy remain release work.

### Why task checkbox percentages would be misleading

The original delivery tasks show 61 checked / 7 open, portal tasks 9 / 0, and two-phase tasks 11 / 1. These are **document bookkeeping counts**, not readiness percentages. The original NEW and UPDATE live gates remain unchecked. Some implemented plans still have every task unchecked; the newer phase-1/2 Hunt gaps plan says implemented while retaining unchecked tasks.

The constitution still requires local-only AI and Excel-only input; current documented behavior includes Cursor/AgentRouter and CSV/JSON. Update the governing contract with explicit decisions and supersession links. Do not interpret historical local-only claims as a privacy guarantee for current deployments.

Other concrete drift: README's happy path suggests only AUTOMATE/blank rows for Automate, while its later clarification and current filter admit every non-MANUAL row. Bug Hunter smoke instructions reference top-level `cycles/`, while current live code writes `iterations/iteration-NN/cycles/`. Handoff still describes preferred-hook server login although current Hunt code and tests describe hunter-driven login.

## 2. Confirmed defects and release risks

Priority definitions: **P0** blocks public launch because it can produce false trust or compromise customer boundaries. **P1** blocks dependable release operation. **P2** is a narrower hardening issue. Reproductions below establish the stated component behavior; broader consequences are identified as risks, not incidents observed against real customers.

### F00 — P0: customer soft assertion failures do not fail tests

**Evidence:** [Validation.java](../../../customer-framework-template/src/main/java/project/validations/Validation.java#L34), [PageActions template](../../../customer-framework-template/templates/PageActions.java.ftl), [probe output](template-assertion-results.txt).

`Validation.assertAll()` catches `AssertionError`, logs it, and returns normally. Its `finally` creates a local `SoftAssert` rather than resetting the static field; `used` also stays true. Generated assertions use `driver.validation()`, and both the listener and teardown call this swallowing method. A failed text/URL/selected assertion can therefore leave TestNG green. Shared static state also crosses test boundaries.

**Reproduced:** compiled the current customer template, called `softTrue(false, ...)`, then `assertAll()`. No assertion failure propagated.

**Fix:** propagate failures into the TestNG result; isolate/reset assertion state per test; guarantee browser cleanup with `finally`. **Acceptance:** run a generated test against a controlled page with deliberately wrong expected text; Maven must exit nonzero and the report must mark that test failed. A subsequent passing test must not inherit its failure, including parallel execution.

### F01 — P0: same-host jobs can share work files

**Evidence:** [ProjectNaming.java](../../../src/main/java/delivery/util/ProjectNaming.java#L13), [ConversionJobRunner.java](../../../src/main/java/delivery/job/ConversionJobRunner.java#L74), [ExecuteJobRunner.java](../../../src/main/java/delivery/job/ExecuteJobRunner.java#L38).

Work folders use only normalized host plus a timestamp to the second; Execute adds a fixed `-exec` suffix. There is no project, owner, or job ID in that name. Executors permit two workers. Jobs of the same kind for the same host starting in one second can overwrite or consume each other's drafts, evidence, generated code, and ZIP.

**Reproduced:** distinct paths on the same host, 600 ms apart, produce the same folder name. Cross-customer artifact mixing is a resulting risk, not an observed production breach.

**Fix:** immutable tenant/project/job work paths with exclusive creation; atomic artifact publication. Test simultaneous jobs with overlapping TC IDs and distinct sentinel content. Also serialize concurrent publication to the same project's framework/version store; `ProjectStore.saveVersion()` currently deletes/copies/increments without a project lock.

### F02 — P0 for shared hosting: settings and locator memory cross account boundaries

**Evidence:** [PreferredHooksStore.java](../../../src/main/java/delivery/store/PreferredHooksStore.java#L27), [DomainLocatorMemory.java](../../../src/main/java/delivery/store/DomainLocatorMemory.java#L27), [PortalStore.java](../../../src/main/java/delivery/portal/service/PortalStore.java#L193).

Both stores use a domain-level path without owner/project scope. An authenticated customer can configure a project with the same host as another customer's project and change shared preferred hooks. Domain locator memory also aggregates knowledge without a tenant namespace. Owner checks on the project route do not protect these shared files.

**Reproduced:** saving hooks for a second project URL on the same host changes what the first URL reads.

**Fix:** tenant-scoped storage and caching, with cross-project sharing only inside an explicitly authorized workspace. Test two users targeting the same SaaS domain. Host normalization also merges some different host spellings and ignores ports; a domain slug is not an identity boundary.

### F03 — P0: Update turns an unchanged TODO into PASSED and discards proof

**Evidence:** [ProvePhase.java](../../../src/main/java/delivery/job/ProvePhase.java#L212), [EmitPhase.java](../../../src/main/java/delivery/job/EmitPhase.java#L153), [TcDiffService.java](../../../src/main/java/delivery/store/TcDiffService.java).

Hashes are saved for every case, including failed cases. Update compares only the content hash. Every unchanged case becomes `REUSED` with empty proven/login steps, and `EmitPhase.toOutcome()` maps REUSED to PASSED. Emit writes these empty drafts over the stored IR. Previously failed cases can become green without running; previously passed cases lose their proof details and locator-map contribution.

**Reproduced:** seeded a stored TODO and unchanged hash, invoked ProvePhase with a mocked browser factory, and observed REUSED/PASSED with no proof.

**Fix:** reuse only eligible proven cases; preserve full IR, evidence identity and prior verdict. Re-prove TODO/PARTIAL cases. Include prerequisite and relevant execution-environment changes in invalidation. Test mixed PASS/TODO updates and repeated updates.

### F04 — P1: incremental Update corrupts the retained generated suite

**Evidence:** [EmitPhase.java](../../../src/main/java/delivery/job/EmitPhase.java#L95), [CodeWriter.java](../../../src/main/java/delivery/codegen/CodeWriter.java#L95), [TestDataPropertiesWriter.java](../../../src/main/java/delivery/codegen/TestDataPropertiesWriter.java#L26).

Update copies the old framework but emits only changed cases. Page files are overwritten using only those cases; old tests can reference methods that disappeared. The test-data file is also rewritten using only changed cases. A PASS → TODO transition writes a TODO class without removing the old executable class.

**Reproduced independently:** old method removed while old test remains; old property keys removed; both passed and TODO classes remain for one TC. The compile gate can catch missing methods, but not every missing-data or stale-test error.

**Fix:** merge current and retained IR, regenerate the entire generated layer deterministically, remove superseded classes, and publish atomically. Preserve intentionally editable configuration under an explicit merge policy. Test NEW → UPDATE with new cases, changed cases, pass/fail transitions, and shared page objects.

### F05 — P2, CLI/direct-engine scope: different TC IDs can overwrite one IR file

**Evidence:** [TcDraftStore.java](../../../src/main/java/delivery/ir/TcDraftStore.java#L128), [ExcelTcReader.java](../../../src/main/java/delivery/excel/ExcelTcReader.java#L75).

`TC/1` and `TC_1` both map to `TC_1.json`. Two writes leave one draft. **The portal GenerateQualityGate rejects slash-containing IDs**, so this probe is not proof of a portal exploit. The CLI/direct runner reads the workbook without that same ID gate, making inconsistent ingress validation relevant there.

**Fix:** enforce one ID contract at the engine boundary and use collision-resistant storage identifiers. Separately, repeated Call-before occurrences intentionally share TC IDs: consider distinct execution occurrence IDs so earlier evidence/verdicts are not overwritten by the last occurrence.

### F06 — P1: changing a project's host makes its artifacts disappear from normal lookup

**Evidence:** [PortalStore.java](../../../src/main/java/delivery/portal/service/PortalStore.java#L187), [DomainStorePaths.java](../../../src/main/java/delivery/store/DomainStorePaths.java#L87).

Changing base URL updates the DB without migrating or preserving the disk identity. Given a nonempty new domain, resolution selects its new nested path and does not search the old domain. Existing libraries/frameworks can appear missing; a later write can split one project across folders. Host changes are also allowed while work is running.

**Reproduced:** existing metadata under the old host resolves to a nonexistent directory after the host changes.

**Fix:** decouple immutable project storage identity from editable target URL, or perform a coordinated migration with rollback and a job lock. Test URL changes after Generate, Automate, and Execute.

### F07 — P1: historical downloads can silently return another job's package

**Evidence:** [PortalStore.resolveZip](../../../src/main/java/delivery/portal/service/PortalStore.java#L495), [RetentionSweeper.java](../../../src/main/java/delivery/portal/service/RetentionSweeper.java).

When a job's file is missing, resolution returns the latest project version. Retention deletes work folders that contain original job ZIPs. An old v1 job can consequently download v2 while showing v1's score. The same generic resolver is used for other job kinds, so a missing Hunt/batch artifact can resolve to an unrelated Automate package.

**Reproduced:** an expired old job resolves to a synthetic `v2.zip`.

**Fix:** persist an immutable artifact/version ID for each job; return an explicit expired/unavailable response when that artifact is gone. Retention must update artifact availability and cover every job kind consistently.

### F08 — P0 for shared hosting: browser navigation lacks a target-network boundary

**Evidence:** [HuntActionExecutor.java](../../../src/main/java/delivery/hunt/HuntActionExecutor.java#L132), [HuntActionGuard.java](../../../src/main/java/delivery/hunt/HuntActionGuard.java#L64), project base-URL creation/update routes.

Project URLs are checked for presence, and Hunt navigation is passed directly to WebDriver. Locator grounding does not restrict navigation. The application code inspected contains no end-to-end scheme/address policy or isolated worker network boundary. A public customer can potentially reach services visible from the worker, including loopback/private services; redirects and page subresources also matter.

**Reproduced:** a loopback `navigate` action passes the guard and reaches a mocked WebDriver. No real internal request was made; actual exposure depends on deployment controls not supplied here.

**Fix:** separate browser workers from the control plane, restrict outbound traffic, validate targets, and scope navigation/credential use to approved origins. Dedicated installations may deliberately need internal targets; make that a deployment policy rather than a global exception. See [OWASP SSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html).

### F09 — P0 before promising secret-safe cloud processing: DOM values are not redacted

**Evidence:** [HtmlSlimmer.java](../../../src/main/java/parsingLayer/HtmlSlimmer.java#L14), [LiveHuntService.java](../../../src/main/java/delivery/hunt/LiveHuntService.java#L168), [HuntPackWriter.java](../../../src/main/java/delivery/hunt/HuntPackWriter.java#L100).

Credential tokens protect the direct typing action, but do not sanitize observed HTML. A password present in a DOM `value` attribute survives slimming; Hunt persists that DOM and can include it in planner context. The pack includes evidence files. Arbitrary `execute_js` results also have no central secret scrub. Normal typed password properties are not always serialized as HTML attributes, so the DOM leak is conditional on the target page's representation.

**Reproduced:** a synthetic password value survives the actual HTML preparation function. This is not evidence that real credentials were leaked in a previous run.

**Fix:** centrally sanitize all model inputs and artifact outputs, including DOM attributes, URLs, logs and JavaScript results; separately mask screenshot regions where needed. Enforce provider policy per customer and record actual provider use. Keel versus Precision selection is insufficient as a privacy control.

### F10 — P1: persisted jobs are not a durable queue

**Evidence:** [AsyncConfig.java](../../../src/main/java/delivery/portal/config/AsyncConfig.java), [JobController.java](../../../src/main/java/delivery/portal/api/JobController.java#L174), worker submit methods, PortalStore hydration, PipelinePoller.

Submission saves QUEUED before calling an in-memory executor. No startup job reconciliation/requeue path was found. A restart can strand QUEUED/RUNNING records. When the bounded executor rejects submission, the saved job can also remain QUEUED. A force-stop endpoint exists as a manual escape, but does not restore the lost work. Pipeline polling cannot complete a generation stage whose in-memory worker disappeared.

**Evidence level:** static execution-path review, not a crash/load reproduction.

**Fix:** durable claims/leases, startup reconciliation, idempotent stage transitions, rejection handling, and per-customer admission limits. Persist full immutable job inputs and engine/budget settings so recovery does not reinterpret changed project settings.

### F11 — P1: compile timeout is applied after potentially infinite output reading

**Evidence:** [EmitCompileCheck.java](../../../src/main/java/delivery/job/EmitCompileCheck.java#L44).

The worker reads Maven stdout synchronously until EOF before calling `waitFor(10 minutes)`. If Maven hangs while holding stdout open, the timeout is never reached. A worker can stay occupied indefinitely; cancellation also does not stop this subprocess through the current call signature.

**Evidence level:** static blocking-control-flow review. The template compiled successfully in this audit; that does not exercise a hung compiler.

**Fix:** drain output asynchronously or redirect it to a file, enforce one deadline around process lifetime, and terminate the child process tree on timeout/cancel. Test with a controlled child that deliberately never exits.

### F12 — P1: deletion does not wait for worker termination or consistently remove credentials

**Evidence:** [ProjectController.delete](../../../src/main/java/delivery/portal/api/ProjectController.java), [PortalStore.deleteOwnedProject](../../../src/main/java/delivery/portal/service/PortalStore.java#L280), [AdminUserService.java](../../../src/main/java/delivery/portal/service/AdminUserService.java).

Project delete/delete-all lacks the active-job check used by artifact deletion. Running workers retain job objects and can continue writing/saving after deletion. Force-stop marks CANCELLED immediately, allowing some deletes before the worker has actually exited. Credential records are not explicitly deleted in these removal paths. Admin user deletion removes DB project/job rows but does not use the artifact cleanup path.

**Evidence level:** static lifecycle review; no destructive production test performed.

**Fix:** tombstone project → stop/acknowledge workers → delete associated records/artifacts/secrets → finalize deletion. Ensure stale workers cannot recreate records and include credential cleanup. Test queued, running, force-stopped and deleted-owner cases.

### F13 — P1: Call-before proof does not establish replayable dependency behavior

**Evidence:** [CallBeforeExpander.java](../../../src/main/java/delivery/job/CallBeforeExpander.java), [GeneratedTest.java.ftl](../../../customer-framework-template/templates/GeneratedTest.java.ftl#L16), [call-before design](../../superpowers/specs/2026-09-08-call-before-test-design.md).

Proving can execute a prerequisite and its dependent case in one browser session. Emission retains per-TC classes, starts a new browser before every generated test, and has no Call-before dependency representation in its test model. A dependent checkout case that passed because a prior TC created its cart does not receive that setup when replayed. The design explicitly avoided copying prerequisite steps, so this is partly a missing design contract rather than a simple missed checkbox.

**Evidence level:** static prove/template comparison; a full live dependent-suite replay remains required.

**Fix:** represent reusable setup chains in IR and emitted tests, or explicitly restrict dependent cases from durable Automate output until supported. Test a non-login dependency, repeated leaves, failed prerequisites, and Update where only the prerequisite changed.

## 3. Verification results

### Delivery/driver baseline

Command: `mvn -B "-Dtest=delivery/**/*Test,drivers/**/*Test,!FacebookSubmitLiveRebindTest,!*LiveSmokeTest" test`

**1,051 tests: 1,034 passed, 16 failed, 1 skipped.** The log is [delivery-suite.log](delivery-suite.log). This is a red baseline, not 16 independently confirmed product bugs.

Failure interpretation from source review:

- **2 authoring tests** still expect invented nonblank values. Current requirements say invention is off unless permitted; reconcile those tests with the intentional contract.
- **2 naming/catalog tests** expect old names (`Login` or `TC_...Test`) after the naming redesign.
- **5 dry-run API tests** expect COMPLETED; dry-run now produces TODOs and workers hard-fail all-TODO runs. Clarify the intended dry-run contract.
- **1 conversion test** depends on a pre-existing generated CSV under runtime storage rather than an isolated fixture.
- **3 generation/compare failures** use stubs overriding `callOllama`, but the production path now calls `callOllamaDetailed`; the test double is bypassed and real local provider calls occur. The observed errors were `OLLAMA_TRUNCATED`. These do not prove the golden fixtures themselves are rejected.
- **2 import tests** stub the save method but do not provide the stored workbook read back by the current service; they fail `NO_GENERATED_WORKBOOK`.
- **1 Execute MVC test** asserts an outdated design-reference API hint in HTML. A UI walkthrough is still needed to confirm the current affordance.

An isolated rerun of six affected classes produced **20 tests, 7 failures**, confirming those failures are not solely an interaction with the large suite. See [focused-failures.log](focused-failures.log).

The initial broad `-Dtest=*` attempt included legacy `Runner.BaseOrchestrator`, whose `@BeforeSuite` launches Chrome. A sandbox browser startup failure skipped almost the whole suite; that first attempt is not counted as a product regression. Scope/gating of legacy/live tests needs cleanup.

### Customer framework and focused probes

- `mvn -B -f customer-framework-template/pom.xml test-compile`: **BUILD SUCCESS**. See [template-compile.log](template-compile.log).
- [LaunchAuditProbe.java](LaunchAuditProbe.java): eleven component-level observations reproduced. F05 is explicitly constrained to CLI/direct-engine validation. See [probe-results.txt](probe-results.txt).
- [TemplateAssertionProbe.java](TemplateAssertionProbe.java): false assertion swallowed in the freshly compiled customer framework. See [template-assertion-results.txt](template-assertion-results.txt).
- These programs report faulty behavior; “DEFECT REPRODUCED” is **not** a passing acceptance test. Use them to construct regression tests for the subsequent fixes.
- Reproduction commands are in [run-probes.ps1](run-probes.ps1). They read Maven's classpath from the test report and use synthetic temporary artifacts.

## 4. Recommended release sequence

### Gate A — trustworthy automation

Fix F00, F03, F04 and F13. Establish a deterministic benchmark with at least: positive case, deliberately wrong assertion, negative login, failed prerequisite, shared-page Update, PASS→TODO transition, TODO retry, unchanged PASS reuse, and a second replay of the downloaded ZIP. Measure intended coverage, verified failures, and rerun stability separately from compile success.

### Gate B — shared-customer isolation

Fix F01/F02/F08/F09 and define tenant identity. Isolate work files, domain memory, workers, credentials, network access, and resource accounting. Add cross-user tests for projects targeting identical hosts and IDs. Domain slugging must never substitute for authorization. [OWASP's multi-tenant guidance](https://cheatsheetseries.owasp.org/cheatsheets/Multi_Tenant_Security_Cheat_Sheet.html) supports tenant-aware storage and cache boundaries.

### Gate C — recoverable operations

Fix F06/F07/F10/F11/F12. Add per-project publication locking, immutable artifact IDs, queued-job limits, confirmed cancellation and retention states. Run restart/rejection/delete tests and a restore drill for the database, artifacts and encryption key. Exercise both dedicated and shared deployment profiles.

### Gate D — release evidence and policy

Make the baseline green without weakening meaningful tests. Separate deterministic, browser-fixture, model-integration and customer-target suites. Add root CI and dependency/secret scanning. Reconcile the constitution and product docs, document support boundaries and actual provider/data flow, require secure production configuration, and complete fresh live NEW/UPDATE + downloaded-suite acceptance. Default admin credentials must not be accepted in a production profile; verify TLS, login throttling, operator access and backup behavior on the actual deployment.

## 5. Enhancements and features worth building

These are proposed priorities based on Keel's existing architecture, not commitments or market validation.

1. **Versioned test library with reviewable diffs and rollback.** Every Generate, human edit, accepted AI review and heal patch creates a revision. Runs pin a revision. This helps concurrency, auditability and customer trust immediately.
2. **A result-integrity view.** Show proven now, reused from a dated run, blocked, not checked, and simulated separately. Include assertion evidence and replay result; never collapse these into one unexplained pass rate.
3. **Customer workspaces, membership and roles.** Needed for the shared model so a customer's team can collaborate without sharing a login. Tenant identity should reach every store, cache and worker request.
4. **Environment profiles and a private runner.** Separate staging/UAT targets and credentials from project identity. A dedicated runner can test private apps without granting the shared control plane access to customer networks.
5. **Provider policy and spend controls.** Explicit local-only or approved-cloud policy, recorded provider/model use, per-job and per-customer budgets, and visible fallback behavior. Decide the data-sharing policy before advertising privacy guarantees.
6. **Failure triage and rerun stability.** Compare runs, distinguish automation defects from application failures, track flaky cases and let users rerun a selected failure against the same pinned inputs.
7. **Reviewed Bug Hunter promotion.** Preview candidate scenarios, edit and deduplicate against a library revision, then explicitly accept them. Preserve the existing rule against automatic merging.
8. **CI/API integration after correctness.** An immutable job/result contract and service credentials can support CI triggers and notifications. Add TMS/Git connectors only after the release foundation is dependable.

Defer another authoring engine, another output language and Hunt's two-pass DOM work until benchmarks show they solve a measured limitation. The current constraint is trust and recoverability, not a shortage of product surfaces.

## 6. Decisions still needed

- **AI data policy:** whether customers may require local-only execution; which cloud paths are allowed; what is persisted/exported. Owner answer in this review: not decided.
- **Hosted target policy:** allowed public apps, customer authorization, private-network testing and worker isolation. Dedicated installs can have different controlled access policies.
- **Supported first-release scope:** a documented web-app/browser/authentication set and measurable acceptance benchmarks. Avoid claiming universal site support from unit tests or one happy-path demo.
- **Customer/team model and operating capacity:** expected simultaneous jobs, tenant quotas, retention expectations and support ownership. These determine sizing and deployment gates; no arbitrary launch percentage or date is justified yet.

**Recommended next work:** fix the result-integrity and Update defects first, then establish the shared/dedicated deployment boundary and durable job lifecycle before expanding features.
