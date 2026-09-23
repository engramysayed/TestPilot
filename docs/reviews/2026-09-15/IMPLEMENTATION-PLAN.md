# Keel launch and product development plan

**Prepared:** 16 September 2026. **Status:** proposed execution backlog; no item below is marked implemented by this plan.

**Source:** [public-launch review](README.md), including its reproduction evidence, limitations, and findings F00–F13. The audit describes revision `0aca9c6` and its reviewed working tree; validate current behavior before implementing each item. This plan does not claim a new code audit or fresh test run.

**Objective:** release a dependable public multi-user product on **shared hosting**, then expand collaboration, diagnosis, and integration capabilities.

**2026-09-20 first-release scope:** shared hosting only. Dedicated installations and private runners remain **implemented** and documented; they are **deferred from launch support** and are not first-launch gates. Validation candidate stays **`2721e6d`**. Public rollout stays HOLD.

## 1. Delivery strategy

Deliver in seven phases, numbered 0–6. Phases 1–4 establish launch readiness; Phase 5 validates a controlled pilot and production release; Phase 6 expands the product. A feature being present in the codebase is insufficient: its acceptance criteria must pass in the release candidate.

Recommended order:

1. **Phase 0: contracts and reproducible baseline.** Establish decisions, isolated tests, and the release benchmark.
2. **Phase 1: trustworthy results and framework Update.** Make green results meaningful and downloaded frameworks replay correctly.
3. **Phase 2: customer isolation and data controls.** Establish safe storage, workers, provider access, and minimal team permissions.
4. **Phase 3: durable operations and reproducible inputs.** Recover jobs, preserve artifacts, protect concurrent edits, and finish safe deletion.
5. **Phase 4: release engineering and product clarity.** Verify deployments, documentation, security configuration, and result explanations.
6. **Phase 5: pilot and launch gates.** Exercise **shared** staging under realistic failure and customer workflows. Dedicated and private-runner deployment validation is deferred.
7. **Phase 6: prioritized enhancements.** Build deeper triage, reviewed discovery, and integrations on the stable contracts. Private runners stay implemented and out of first-launch support.

Phase 2 architecture decisions can progress alongside Phase 1. Phase 3 implementation depends on the immutable identities and storage boundaries from Phase 2. Do not introduce a second competing job scheduler, artifact store, or revision mechanism while implementing individual fixes.

No launch date is estimated here: staffing, supported application scope, operating capacity, and privacy policy are unresolved. Estimate each work package after its design and regression reproduction are accepted. Assign a named accountable owner before starting; suggested roles below are responsibilities, not assumed staffing.

## 2. Phase 0 — agree contracts and establish evidence

### P0-01 — approve the first-release contract

**Owner:** product lead with engineering/security input. **Dependencies:** none.

- [ ] Record supported browsers, application types, authentication patterns, input formats, authoring engines, and generated-framework support boundaries.
- [ ] Define PASS, FAIL, TODO, PARTIAL, BLOCKED, REUSED and SIMULATED in one contract; specify how each appears in jobs, exports, and downloaded tests.
- [ ] Define dry-run behavior explicitly. Decide whether all-TODO dry runs are valid simulations or failed authoring jobs; do not let tests decide policy accidentally.
- [ ] Decide whether dependent tests require replayable setup in the first release. Recommended: support explicit setup chains; if deferred, block unsupported dependent cases from being advertised as replay-ready.
- [ ] Create decision records for hosted target access, AI data policy, tenant/team model, retention, expected concurrency, and support ownership.

**Acceptance:** product contract has an owner and approval date; each unresolved decision has an owner, deadline relative to its dependent phase, and affected work packages. First-release support is **shared hosting only**; dedicated and private-runner support commitments are explicit deferrals, not launch promises.

### P0-02 — repair the deterministic test baseline

**Owner:** test/engineering lead. **Dependencies:** P0-01 for disputed behavior.

- [ ] Re-run the scoped delivery/driver suite against the implementation starting point and record revision, command, environment, results, and exclusions.
- [ ] Resolve the audit's 16 failures individually: authoring-value expectations (2), naming expectations (2), dry-run contract (5), runtime CSV dependency (1), bypassed generation provider doubles (3), import persistence doubles (2), outdated Execute UI hint (1).
- [ ] Replace runtime-storage dependencies with isolated fixtures; ensure deterministic tests fail immediately on unexpected provider/network calls.
- [ ] Separate unit/service, controlled-browser, model-integration, and customer-target tests. Explicitly gate legacy suite hooks and live smoke tests.
- [ ] Convert the audit probes into focused regression tests alongside the owning fixes; preserve the original evidence.

**Acceptance:** deterministic suite passes from a clean workspace without a real model or customer website. Every prior failure has a documented resolution; no meaningful assertion is removed merely to turn the suite green. Excluded tests and skips have named reasons.

### P0-03 — build the release benchmark

**Owner:** QA with framework engineering. **Dependencies:** P0-01.

- [x] Create controlled pages and datasets for positive and deliberately false assertions, negative login, shared page methods, stateful non-login prerequisites, and sensitive fields.
- [x] Define NEW → downloaded replay → UPDATE → downloaded replay → repeated UPDATE sequences.
- [x] Include unchanged PASS reuse, TODO retry, PASS→TODO, case removal, changed prerequisite, changed environment, and concurrent same-host jobs.
- [x] Record expected outcomes before running; keep proof coverage, assertion correctness, replay success, and stability as separate metrics.

**Acceptance:** benchmark runs reproducibly, identifies deliberately wrong behavior, and emits evidence tied to source revision, library revision, environment, and artifact identity.

**Run 2026-09-16 (certified):** [release-benchmark/run-2026-09-16.md](release-benchmark/run-2026-09-16.md) — clean checkout of `3a806d3`, host suite 29/29 green (`ReleaseBenchmarkP003Test` + `ReuseEligibilityTest`). Concurrent same-host remains an expected isolation failure until P2-02. Live proof coverage was not measured (fixture IR, not LLM NEW). Credential reuse uses an opaque revision id; password hashes are not stored in prove-context or customer ZIPs.

## 3. Phase 1 — trustworthy results and generated frameworks

### P1-01 — propagate and isolate customer assertions · F00

**Owner:** framework engineering. **Dependencies:** P0-03 fixture definitions.

- [ ] Replace shared static assertion state with a per-test lifecycle compatible with parallel TestNG execution.
- [ ] Propagate assertion failure to the test result; prevent listener/teardown double handling from hiding or misattributing it.
- [ ] Reset state after each test and guarantee browser cleanup even when assertion aggregation fails.
- [ ] Verify generated text, URL, selected-state, and other assertion paths use the corrected behavior.

**Acceptance:** a deliberately false generated assertion produces a failed TestNG result and nonzero Maven exit. A subsequent passing test remains clean. Parallel tests do not exchange failures, and browser cleanup completes in every path.

### P1-02 — preserve proof and restrict Update reuse · F03

**Owner:** pipeline/IR engineering. **Dependencies:** result contract in P0-01.

- [ ] Define reusable proof eligibility: prior verified success, complete compatible IR, and matching relevant inputs.
- [ ] Include case content, prerequisite changes, environment identity and relevant engine/schema changes in invalidation rules.
- [ ] Preserve original proven steps, login/setup representation, evidence references, and verdict when reusing.
- [ ] Re-prove TODO/PARTIAL/failed cases; treat missing or incompatible proof conservatively.
- [ ] Expose reuse provenance instead of implying a fresh browser execution.

**Acceptance:** unchanged TODO never becomes PASS through reuse; unchanged eligible PASS retains complete proof through repeated updates. A changed prerequisite or execution context invalidates affected descendants. Corrupt or missing stored IR cannot silently pass.

### P1-03 — regenerate a complete, consistent framework · F04

**Owner:** code-generation engineering. **Dependencies:** P1-02; coordinate atomic publication with P2-02/P3-03.

- [ ] Merge retained eligible IR with newly proven cases into one complete target suite model.
- [ ] Regenerate all generated page objects, tests, data and manifests into staging; remove obsolete generated outputs deterministically.
- [ ] Define generated versus customer-editable files and an explicit preservation/conflict policy.
- [ ] Compile and replay the staged result before publishing; retain the previous valid version when validation fails.
- [ ] Handle case additions, removals, renames and PASS/TODO transitions without duplicate executable classes.

**Acceptance:** existing unchanged tests keep required methods/data; PASS→TODO leaves no stale passed class; removed cases disappear intentionally. Failed generation leaves the prior package available. Repeated emission of identical inputs yields the same generated content apart from documented metadata.

### P1-04 — make prerequisite chains replayable · F13

**Owner:** IR and framework engineering. **Dependencies:** P0-01, P1-02, P1-03.

- [x] Represent prerequisite ordering, required shared session state, failure behavior, and reusable setup explicitly in IR.
- [x] Emit setup for each dependent execution; do not rely on incidental TestNG class ordering.
- [x] Define cycle detection, failed-prerequisite blocking, repeated leaves, and cleanup behavior.
- [x] Give each execution occurrence its own evidence identity while retaining its logical TC ID.
- [x] Invalidate dependent proof when prerequisites change.

**Acceptance:** a checkout case requiring cart creation passes from a fresh downloaded framework, and a failed cart prerequisite blocks checkout with a clear reason. Repeated prerequisites retain separate evidence; dependency cycles produce actionable validation errors.

### P1-05 — unify test-case identity validation · F05

**Owner:** ingestion/IR engineering. **Dependencies:** none; coordinate occurrence identity with P1-04.

- [x] Enforce the same ID validation at the engine boundary for portal, workbook, CLI, and direct invocation.
- [x] Use storage keys that cannot collide after filename normalization; retain the original display ID separately.
- [x] Define migration/error behavior for legacy ambiguous drafts instead of silently choosing one.

**Acceptance:** `TC/1` and `TC_1` cannot overwrite one another through any entry point. Invalid IDs receive consistent errors; valid repeated execution occurrences remain distinguishable.

**Phase 1 exit:** the full P0-03 framework benchmark passes, including negative assertions and a second downloaded replay. Compile success alone does not satisfy this gate.

**2026-09-16 status:** P0-03 execution gates passed on clean checkout `3a806d3` (false assertion, failed-setup skip+quit observed at runtime, NEW→UPDATE preservation, TODO/PASS→TODO/repeated reuse, compile-fail retains previous ZIP SHA-256). This certifies **fixture replay correctness**, not live AI proof coverage and not customer isolation. P0-01 named product/security approval remains open. Next engineering stream is Phase 2 (customer isolation and data controls).

## 4. Phase 2 — customer boundaries and data controls

### P2-01 — establish immutable tenant, project and job identity

**Owner:** backend architecture. **Dependencies:** P0-01 team model.

- [ ] Define a customer workspace/tenant ID and carry authenticated tenant context through APIs, persistence, caches, jobs, workers, credentials and exports.
- [ ] Introduce minimal owner/admin/member permissions and server-side membership checks; define project-level restrictions if required by launch scope.
- [ ] Map existing user-owned projects into workspaces through a reversible, versioned migration.
- [ ] Document dedicated single-tenant behavior using the same identity model.

**Acceptance:** two customers targeting the same host and using identical TC IDs cannot read, mutate, execute or export each other's data. Tests cover direct IDs, artifact routes, settings, caches and worker requests, not only project listing.

**2026-09-16:** tenant-storage **groundwork** only — P2-01 remains open. See [P2-01-resolutions.md](P2-01-resolutions.md). Tenant ids are opaque persisted `ws_`+32 hex; membership and display slugs are separate. This is not completed customer isolation.

### P2-02 — isolate work files and shared knowledge · F01, F02

**Owner:** storage/pipeline engineering. **Dependencies:** P2-01.

- [ ] Use exclusively created tenant/project/job work directories based on immutable IDs rather than second-resolution timestamps.
- [ ] Scope preferred hooks and locator memory by tenant and project; permit workspace sharing only through an explicit policy.
- [ ] Propagate scope into cache keys and background tasks.
- [ ] Migrate known legacy files with a backup and manifest. Quarantine ambiguous domain-shared data rather than assigning it to an arbitrary tenant.
- [ ] Establish project publication locking; define behavior across multiple server processes, not just threads.

**Acceptance:** simultaneous same-host jobs with overlapping TC IDs and distinct sentinels retain independent files, proof, packages and memory. Version publication cannot lose or overwrite a concurrent result.

**2026-09-16:** tenant-storage **groundwork** only — P2-02 remains open. Hosted paths fail closed; roles gate operate vs membership including generate/compare/workbook/pipeline; MEMBER read-only and OWNER-only delete are a product contract; migrator recover is interruptible and idempotent; OS lock was probed on this volume. Dedicated-install identity/network notes: [dedicated-install.md](dedicated-install.md). Deployment filesystem lock remains this volume only.

**2026-09-17 (`f44c76a`):** P0-03 concurrent same-host sequence 10 re-run **PASS** ([run-2026-09-17-concurrent-f44c76a.md](release-benchmark/run-2026-09-17-concurrent-f44c76a.md)). This is not completed customer isolation.

### P2-03 — enforce browser network and credential scope · F08

**Owner:** infrastructure/security engineering. **Dependencies:** hosted-target decision, P2-01.

- [ ] Separate browser workers from control-plane services and administrative credentials.
- [ ] Define shared-host allowed schemes, origins and destinations; enforce outbound restrictions at the worker network boundary.
- [ ] Cover redirects, subresources, DNS changes, IPv4/IPv6, loopback, link-local and private address ranges in the threat model and tests.
- [ ] Restrict credentials to approved target origins; reject unexpected navigation with an auditable reason.
- [ ] Provide a distinct dedicated-installation policy for authorized private targets; do not globally weaken shared-host restrictions.

**Acceptance:** controlled integration tests show prohibited destinations remain unreachable through navigation, redirects and subresources. Approved targets work. Dedicated private-target access is explicitly configured and cannot broaden another tenant's access.

**2026-09-20 first-launch acceptance:** shared-host prohibited destinations stay unreachable on **shared staging**. Dedicated private-target access remains implemented and is **not** required to close first launch.

**2026-09-17 (`f44c76a`):** application-layer policy and per-thread PAC are in place for Hunt and ProvePhase Chrome/Edge. Dedicated CIDRs do not appear in the shared PAC. Loopback page loads still use `WorkerNetworkGuard`. This is not a kernel firewall. **No deployed shared or dedicated installation was available.** P2-03 remains open until those drills and P0-01 hosted-target sign-off.

**2026-09-20:** first-launch P2-03 is **shared-host** worker-network isolation on staging. Dedicated private-CIDR validation is deferred; the dedicated policy implementation is preserved.

### P2-04 — sanitize sensitive data and enforce provider policy · F09

**Owner:** AI platform/security engineering. **Dependencies:** AI policy decision, P2-01.

- [ ] Define a central sanitation boundary for DOM, URLs, logs, JavaScript results, model prompts, screenshots and exported packs.
- [ ] Remove or mask credential values and configured sensitive fields; preserve enough structure for testing and report when masking limits analysis.
- [ ] Apply sanitation before model dispatch and before persistence/export; protect intermediate debug paths too.
- [ ] Enforce tenant/deployment provider allowlists, including every fallback path. If local-only is supported, fail closed when no allowed provider is available.
- [ ] Record actual provider/model use and policy decisions without recording secrets.
- [ ] Set retention/access rules for raw evidence where retention is explicitly authorized.

**Acceptance:** synthetic secret canaries do not appear in intercepted provider requests, stored DOM/logs, screenshots covered by the masking policy, or exported packs. Disallowed cloud fallback is blocked. Tests cover engine selection, fallback and JavaScript output.

**2026-09-17 (`f44c76a`):** `SecretSanitizer` plus `ScreenshotRedactor` at capture; Cursor sidecar stdin canary intercepted (`SidecarCanaryTest`). Fail-closed `ProviderPolicy` unchanged. P2-04 remains open until named privacy sign-off ([APPROVALS.md](APPROVALS.md)).

**Phase 2 exit (first release):** shared-host isolation tests and **shared staging** worker-network tests pass. Dedicated deployed isolation is deferred. Tenant/data policy is approved and documented; an undecided privacy policy cannot be advertised as a guarantee.

## 5. Phase 3 — recoverable jobs, stable artifacts and revisions

### P3-01 — introduce durable job claims and recovery · F10

**Owner:** backend/platform engineering. **Dependencies:** P2-01/P2-02.

- [ ] Define persisted job/stage state transitions, immutable input snapshots, attempt IDs, leases, heartbeats and terminal outcomes.
- [ ] Claim work durably with atomic ownership; fence stale workers so an expired attempt cannot publish late results.
- [ ] Reconcile queued/running jobs on startup; retry only stages with a defined safe replay policy.
- [ ] Handle admission rejection without stranded QUEUED records. Apply tenant and installation concurrency/queue limits.
- [ ] Preserve selected library revision, environment, engine, provider policy and budget for each attempt.
- [ ] Treat potentially side-effecting browser actions carefully: mark interrupted uncertain execution for review when automatic replay could duplicate external actions.

**Acceptance:** process termination before/after claim and before/after publication produces a recoverable or explicit terminal state. Duplicate claims cannot publish twice. Queue saturation is visible and bounded. Changing project settings does not reinterpret queued work.

**2026-09-17:** `DurableJobClaim` leases, attempt ids, cancel-generation fencing, input snapshots, and browser-stage `INTERRUPTED_UNCERTAIN`. Queue limits (`delivery.jobs.max-queued-per-tenant` / `max-running-per-install`) reject before a QUEUED row is stored. Precision max and provider allowlist are snapshotted at admit. Isolated backup restoration remains a Phase 4/5 deployment drill.

### P3-02 — enforce process deadlines and confirmed cancellation · F11

**Owner:** worker engineering. **Dependencies:** P3-01 state model.

- [ ] Drain compiler output asynchronously or redirect it safely; enforce a deadline covering the entire subprocess lifetime.
- [ ] Terminate child process trees on timeout/cancellation and capture bounded diagnostic output.
- [ ] Distinguish cancellation requested from worker termination acknowledged; integrate browser/model cancellation where supported.
- [ ] Fence publication after cancellation or lease loss.

**Acceptance:** a controlled hanging subprocess is killed within the configured deadline/tolerance, the worker slot is released, and no orphan process or post-cancel package publication remains.

**2026-09-17:** `ProcessSupervisor` drains output asynchronously, enforces a deadline, kills the process tree, and cooperates with cancel. `EmitCompileCheck` uses it. Cancel requested (`CANCELLING`) is distinct from worker ack (`CANCELLED`); publication is fenced after cancel or lease loss. Isolated OS-level orphan audits on production hosts remain a deployment check.

### P3-03 — bind jobs to immutable artifacts and stable project storage · F06, F07

**Owner:** storage/backend engineering. **Dependencies:** P2-02; P3-01 for attempt identity.

- [ ] Decouple project directory identity from editable base URL and provide a verified migration for existing folders.
- [ ] Persist job kind, artifact ID, version, checksum, provenance and availability; eliminate fallback to another job's latest ZIP.
- [ ] Publish artifacts atomically and serialize version allocation per project across workers.
- [ ] Mark expired artifacts explicitly and apply retention consistently across Automate, Execute, Hunt and batch outputs.
- [ ] Pin running jobs to an environment snapshot when a project target changes.

**Acceptance:** changing a host preserves historical libraries and artifacts. Expired v1 never downloads v2 or another artifact kind. Concurrent publication preserves both identities. Failed migration can roll back using the verified backup.

**2026-09-17:** Job downloads bind to that job’s own file (`ArtifactResolver`); missing/expired artifacts do not fall back to latest ZIP. `ProjectStore.saveVersion` records checksums and marks prior versions expired under the existing publication lock. Hosted project dirs remain tenant/project-id based so a URL edit does not rename storage. Isolated backup restoration is still a Phase 4 drill.

### P3-04 — complete deletion safely · F12

**Owner:** backend/security engineering. **Dependencies:** P3-01/P3-02/P3-03.

- [ ] Tombstone projects/users to reject new work; request and acknowledge worker termination before final cleanup.
- [ ] Delete associated credential records, artifacts, memory, revisions, caches and temporary work through one lifecycle path.
- [ ] Prevent stale workers from recreating deleted records or republishing files.
- [ ] Make cleanup retryable and visible to operators; define backup retention implications separately from live deletion.
- [ ] Route project deletion, delete-all and administrator user deletion through the same guarantees.

**Acceptance:** queued, running, cancelling and force-stopped deletion tests leave no live credentials/artifacts or resurrected records. Partial cleanup failure is retryable and accurately reported.

**2026-09-17:** Delete tombstones (archives) first, force-stops active jobs, deletes credential rows, then purges jobs and disk. Stale workers cannot claim tombstoned projects. Partial disk failure is logged after DB removal. Admin domain-folder delete is a separate legacy path and still needs the same credential purge.

### P3-05 — version the test library and detect conflicting edits

**Owner:** library/backend engineering. **Dependencies:** immutable storage identity, P3-01 input snapshots.

- [ ] Create immutable revisions for Generate, imports, manual edits, accepted AI review and accepted healing changes.
- [ ] Require a base revision for writes; reject stale updates with a readable conflict rather than overwriting a newer revision.
- [ ] Pin runs and generated packages to exact revisions; preserve author, timestamp, change source and parent revision.
- [ ] Add field-level diff and restore-as-new-revision. Keep historical run inputs intact during rollback.

**Acceptance:** two concurrent editors cannot silently lose changes. An AI review based on an old revision cannot overwrite a newer edit. A historical run's inputs can be reconstructed after rollback.

**2026-09-17 (`75e6996` candidate unchanged):** `LibraryRevisionStore` writes immutable revisions; stale `baseRevision` conflicts on case edit and upload. Generate/import/heal saves commit a revision. Jobs pin `libraryRevisionId` and copy that revision’s bytes. Field-level diff supports quoted/multiline CSV and all editable fields; `kind`/`field` filters; restore creates a new revision. Project Test cases tab shows author/source history and before/after diffs. Members can review history; restore remains an operable role. This is implemented product behavior, not a Phase 5 launch guarantee.

**Phase 3 engineering exit (local):** restart/lease, saturation, cancellation/timeout, retention sweeper, host-stable project identity, concurrent-edit conflict, and project deletion unit/API paths pass in this workspace. Database/artifact/key backup restoration in an isolated environment remains a **release blocker** (P4-02 / Phase 5).

## 6. Phase 4 — release engineering, observability and product clarity

### P4-01 — add release CI and build provenance

**Owner:** platform/QA. **Dependencies:** P0-02, Phase 1 benchmark.

- [ ] Add root CI for deterministic tests, template compilation, controlled browser acceptance and package replay.
- [ ] Gate model/customer-target tests separately with documented secrets and environments.
- [ ] Add dependency and secret scanning, artifact checksums, dependency inventory and release provenance.
- [ ] Require a reviewed resolution for release-blocking scan findings; retain logs and benchmark reports with the release candidate.

**Acceptance:** a clean runner builds the product and customer framework; deliberately broken assertions/Update behavior fail the pipeline. No real provider is called by deterministic CI.

### P4-02 — validate shared and dedicated deployment profiles

**2026-09-20:** first-launch close is **shared staging** install + restore. Dedicated profile validation is deferred; keep the dedicated docs and drills in tree.

**Owner:** platform/security. **Dependencies:** Phases 2 and 3.

- [ ] Provide supported installation/upgrade configuration, database/storage prerequisites, worker policy and health/readiness checks for each model.
- [ ] Reject default administrator credentials and missing mandatory secrets in production configuration.
- [ ] Verify TLS/session configuration, login throttling, operator access, encryption-key handling and least-privilege service access.
- [ ] Add migration preflight, backup and rollback instructions; test an upgrade from the supported previous data layout.
- [ ] Document which features/providers/private targets are supported in each profile.

**Acceptance (first launch, 2026-09-20):** the **shared** profile installs from documented instructions, rejects insecure required configuration, completes acceptance workflows, and passes an upgrade/restore drill on **shared staging** without losing artifact identity or tenant boundaries. Dedicated profile acceptance is deferred.

### P4-03 — show result integrity and actionable job diagnostics

**Owner:** product/frontend with backend. **Dependencies:** P1-02, P3-01/P3-03/P3-05.

- [ ] Display fresh proof, reused proof with date/source, blocked, unchecked and simulated results distinctly.
- [ ] Link assertion evidence, library revision, environment, provider use and downloaded replay status.
- [ ] Show queue/recovery/cancellation/expiry states with useful next actions.
- [ ] Publish separate metrics for queue wait, job duration, recovery, replay failures, provider failures, resource use and rejected work.
- [ ] Add alerts/runbooks for stuck leases, isolation errors, storage pressure, repeated subprocess timeouts and budget exhaustion.

**Acceptance:** a user can tell whether a case actually ran, why it did not run, what was reused and whether its download remains available. Operators can trace one job without seeing another tenant's data or secrets.

### P4-04 — reconcile documentation and support claims

**Owner:** product/documentation with engineering reviewers. **Dependencies:** approved policy and final implementation.

- [ ] Amend or explicitly supersede constitution claims about local-only AI and Excel-only input.
- [ ] Align README Automate row-selection rules, Hunt evidence paths, login ownership and project-level Precision settings with current behavior.
- [ ] Reconcile historical task status using evidence links; retain the historical record rather than rewriting past claims silently.
- [ ] Publish support scope, data-flow/retention policy, deployment differences, known limitations and recovery instructions.
- [ ] Add a document-to-feature ownership index so new implementation changes update the correct contract.

**Acceptance:** a new user/operator can follow the supported path without relying on contradictory historical plans. Every advertised privacy and replay guarantee has a matching enforcement test.

**Phase 4 engineering (2026-09-17, local):** CI workflow `.github/workflows/release.yml` (deterministic profile excludes `*LiveSmoke*`, template compile, sha256 provenance, secret grep). Production startup guard, login throttle, `/api/health`+`/api/ready`, `StoreBackup` unit restore of tenant path+checksum, job diagnostics (`FRESH`/`REUSED`/`BLOCKED`/`UNCHECKED`/`SIMULATED`/`INTERRUPTED`), library field-level diff, constitution 1.2.0 supersession, SUPPORT/deployment/runbook docs. **P4-02 shared-staging restore drill remains a first-launch blocker.** Dedicated restore is deferred. Phase 2 isolation/privacy gates stay open.

**Phase 5 validation (2026-09-17, candidate `75e6996`, superseded):** screenshot redaction and sidecar canary tests pass locally; concurrent same-host ProveEmit recorded PASS on `f44c76a`; backup/tenant/lock checks pass on this NTFS volume. Isolated shared/dedicated Spring Boot drills bind install-wide (JVM-wide) mode into worker policy and snapshot those store-roots; dry-run Generate-import→Execute→Automate→ZIP test-compile ran on the shared drill. **Launch decision: HOLD.**

**Phase 5 re-nomination (2026-09-17, candidate `23f9351` then `2721e6d`):** `75e6996` is explicitly replaced. Clean worktree drills + workstation restore ran on `23f9351`. Default `mvn test` on that SHA was **1301 run, 3 failed, 2 skipped** (stale preferred-hooks path + two live ui-tars misses). Test-only follow-up **`2721e6d`**; `mvn -Pdeterministic test` **1297 run, 0 failed, 2 skipped**. **Launch decision: HOLD.**

**2026-09-20 first-release scope:** candidate **`2721e6d` unchanged**. Remaining first-launch validation is **shared staging** worker-network isolation, cross-tenant rejection, restore on that store, and live Generate → Execute → Automate NEW → downloaded replay → UPDATE → replay on an authorized representative app. Dedicated deployment validation and private-runner launch support are deferred. Live UI-TARS click-grounding is **not** a supported first-release configuration ([vision-live-smoke-rca.md](vision-live-smoke-rca.md)). Staging procedure: [shared-staging.md](../../ops/shared-staging.md). Evidence: [phase5-validation.md](phase5-validation.md), [LAUNCH-DECISION.md](LAUNCH-DECISION.md), [APPROVAL-PACKET.md](APPROVAL-PACKET.md).

## 7. Phase 5 — controlled pilot and launch decision

### P5-01 — run release-candidate acceptance

**Owner:** QA/release lead. **Dependencies:** all Phase 1–4 exit criteria.

- [ ] Run fresh Generate → library edit/review → Execute → Automate NEW → download/replay → UPDATE → download/replay on controlled representative applications.
- [ ] Include Bug Hunter evidence, triage and pack export; verify sensitive data handling throughout.
- [ ] Execute two-tenant identical-host tests on **shared** staging. Dedicated private-target scenarios are deferred.
- [ ] Run capacity tests at the agreed launch concurrency and queue limits; record latency/resource thresholds before execution.
- [ ] Exercise worker/server restart, queue rejection, provider outage, disk pressure, timeout, cancellation, retention, deletion and restore.
- [ ] Complete a browser UI walkthrough including error states, keyboard access, long-running feedback and expired downloads.
- [ ] Perform a deployment security review; component mocks do not substitute for deployed network/isolation validation.

**Acceptance:** agreed thresholds pass on the exact release candidate. No open P0/P1 from the review remains without an explicit scope change that removes the affected feature from the launch promise; shared hosting still requires its boundary controls. New findings are triaged by impact, not by a target launch date.

### P5-02 — pilot, then release both supported models

**2026-09-20:** first public rollout is the **shared** distribution only. Dedicated remains implemented and out of launch support.

**Owner:** product/release/operations. **Dependencies:** P5-01.

- [ ] Onboard a bounded pilot cohort with documented limits and a named support contact.
- [ ] Observe actual failure/replay rates, resource use, recovery and support friction over an agreed pilot window.
- [ ] Fix pilot blockers, rerun affected acceptance, and record the release decision with evidence.
- [ ] Publish release notes, known limitations, rollback triggers and escalation ownership.
- [ ] Roll out within tested capacity and verify installation health, job processing and artifact downloads after release.

**Acceptance:** the **shared** distribution meets its declared support contract. Pilot evidence supports launch, and an operator can execute rollback and recovery instructions. Dedicated/private-runner launch evidence is not required.

## 8. Enhancements and features worth building

These are proposed product priorities, not validated market demand. Foundational slices already included above should ship once their dependencies are ready; richer versions can follow the public launch. Avoid counting the same underlying work twice.

### E01 — versioned library, reviewable diffs and rollback

**Priority:** launch foundation, expanded after launch. **Foundation:** P3-05.

**User value:** understand what changed and reproduce an older result.

- [x] Ship immutable revisions, stale-edit protection, run pinning and basic diff/restore with the foundation.
- [x] Add side-by-side field diffs, change filtering, author/source history, and review of bulk edits.
- [ ] Add approvals only where pilot teams need them; distinguish review authorization from general edit permission.

**Done when:** any run can open its exact test revision; restore creates a new revision; reviewers can identify added, changed and removed scenarios before acceptance.

### E02 — result-integrity view

**Priority:** launch foundation. **Foundation:** P4-03.

**User value:** understand the strength and source of every result.

**2026-09-17 (E02, development after candidate `75e6996`):** dashboard proven pass rate excludes simulated dry-run cases from the numerator; blocked and unchecked stay in the denominator. Job status shows proof kind plus proof source (browser vs framework replay vs simulated). Proven case timelines expose expected vs observed and evidence links. This is implemented product behavior, not a Phase 5 launch guarantee.

- [x] Present fresh execution, reuse, partial coverage, blocked steps and simulation explicitly.
- [x] Show expected versus observed assertions and evidence links.
- [x] Explain the denominator of coverage/pass rates and distinguish generated-framework replay from browser proof.

**Done when:** users can trace every displayed success to current proof or clearly labeled historical reuse; unchecked work does not inflate the pass rate.

### E03 — collaborative customer workspaces

**Priority:** minimal roles for shared launch; richer collaboration next. **Foundation:** P2-01.

**User value:** collaborate without shared accounts.

- [x] Add invitations, membership removal, role management and audit history.
- [x] Add project access restrictions, ownership transfer and service identities as customer needs justify them.
- [x] Invalidate access promptly after membership removal; define what happens to the removed member's queued work.

**Done when:** role changes are enforced across APIs, workers and artifacts; one customer cannot manage another's membership. Administrative actions have an attributable audit trail.

**2026-09-17 (E03, development after candidate `75e6996`):** MEMBER is read-only; OWNER/ADMIN operate; only OWNER administers/deletes. Membership audit, ownership transfer, revocable `tp_svc_` identities, and cancel-on-remove for the member's active jobs are implemented. This is not a Phase 5 launch guarantee.

### E04 — environment profiles and private runners

**Priority:** environment snapshots in foundation; private runner after worker isolation stabilizes. **Dependencies:** P2-03/P2-04, P3-01.

**User value:** run the same project safely against staging/UAT and private applications.

- [x] Add named environments with target origin, scoped credential reference, approved provider policy and execution limits.
- [x] Pin jobs to environment versions; compare environments without changing project storage identity.
- [x] Design a customer-operated runner with outbound connection, tenant binding, enrollment/revocation, heartbeat and scoped job claims.
- [x] Define what evidence leaves the runner, how updates are trusted, and what happens when it goes offline.

**Done when:** environment changes cannot redirect existing jobs; revoked runners cannot claim work; private applications are tested without exposing their network to the shared control plane. Disconnection follows the durable job recovery contract.

**2026-09-17 live local validation (`0c49603`, development after `435799f`):** `PrivateRunnerLiveBrowserProcessTest` starts a separate `PrivateRunnerAgent` with `--dry-run false` and headless Chrome against controlled loopback pages. Recorded path: claim → frozen input zip → `BROWSER` stage → live click (`DOM_POST_CLICK` url-changed to `confirmed.html`) → HMAC artifact upload → `COMPLETED` with `passedCount >= 1`. Cancel during a hung BROWSER navigation finishes `CANCELLED`. Destroying the agent on `BROWSER` plus an expired lease yields `INTERRUPTED_UNCERTAIN`. In-process portal workers skip `runner=private`. Agent logs (`Starting Driver` / `CHROME` / `DOM_POST_CLICK`) plus the uploaded zip (IR `lastPageUrl`) are the execution evidence; the portal work-dir does not contain the job.

This local validation does **not** close P2-03 deployed isolation, production restore, representative customer-app acceptance, live Generate → Execute → Automate on a customer target, or named P0-01 approvals. Auto-update and attested runner builds are not included. Public rollout remains HOLD. **2026-09-20:** private-runner **launch support is deferred**; implementation is preserved. Validation candidate remains `2721e6d`.

### E05 — provider policy, budgets and spend visibility

**Priority:** policy/admission controls before launch; richer reporting next. **Dependencies:** P2-04, P3-01.

**User value:** control where data goes and avoid unexpected AI cost.

- [x] Reserve budget atomically before dispatch so concurrent jobs cannot all spend the same allowance.
- [x] Enforce per-job/per-tenant limits across retries, fallback and recovery; handle unknown cost explicitly.
- [x] Add usage history and configurable warning thresholds; distinguish estimated and reconciled cost.
- [x] Show permitted providers/models and actual fallback use per job.

**Done when:** concurrent requests respect the approved limit, forbidden providers are never invoked, and users can explain a job's provider usage and estimated/final cost.

**2026-09-17 (E05 remaining display, `bd0f7ae`, development after candidate `75e6996`):** each job persists `providersUsed` / `fallbackUsed` / `fallbackReason` from recorded IR (heal tier, Precision calls, `PRECISION_FALLBACK`) plus the frozen allowlist. `GET /api/jobs/{id}` and the run-status page show allowed vs used and Precision→Keel fallback. Browser: status page exposes Providers allowed/used/Fallback. This does not close live Generate → Execute → Automate on a customer target.

### E06 — failure triage and rerun stability

**Priority:** first post-launch product increment. **Dependencies:** E01/E02 and stable evidence identity.

**User value:** spend less time diagnosing failures and understand intermittent behavior.

- [x] Compare runs using pinned case/environment identities and show the first meaningful divergence.
- [x] Distinguish application assertion failures, automation/locator failures, provider failures and infrastructure failures; allow users to correct suggested classifications.
- [x] Rerun selected failures using the same pinned inputs and record a new attempt rather than overwriting evidence.
- [x] Track intermittent outcomes across comparable runs; show sample size and avoid declaring flakiness from one retry.

**Done when:** a rerun is reproducible from recorded inputs, earlier failure evidence remains available, and a later pass does not erase the original failure.

**2026-09-17 (E06 UI, `bd0f7ae`, development after candidate `75e6996`):** status page loads recorded execute-run IR for compare (`GET /api/jobs/{id}/compare?other=`), lists rerun attempts with pinned library/environment/providers, and shows intermittency verdict plus sample size (`MIN_SAMPLE=3`). `POST /api/jobs/{id}/rerun` mints `job_rerun_*` without rewriting the parent. Classification correction remains a separate store. Browser: Attempts and compare, Compare, Failure class, Rerun as new attempt. Public rollout remains HOLD.

**2026-09-17 (E06 rerun pin, `23f9351`):** if the job's temp workbook was already deleted, rerun rematerializes bytes from the pinned `libraryRevisionId` instead of failing with "Excel file not found".

### E07 — reviewed Bug Hunter promotion

**Priority:** after versioned library and trustworthy proving. **Dependencies:** E01, P1-02/P2-04.

**User value:** turn useful discoveries into maintainable regression scenarios.

- [x] Preview candidates with supporting evidence and editable steps/assertions.
- [x] Compare against a pinned library revision for exact and suggested semantic duplicates.
- [x] Require explicit acceptance; stale acceptance must rebase/review against the latest revision.
- [x] Preserve discovery provenance and require normal proving before claiming automation readiness.

**Done when:** Hunt never silently merges cases; accepted candidates create an attributable library revision; duplicates and conflicting edits are reviewable.

**2026-09-17 (E07, development after candidate `75e6996`):** in-page Hunt review/edit/accept requires `accept=true`; promotion mints `TC_HUNT_*` as a library revision. Not a Phase 5 launch guarantee.

### E08 — CI/API integration and connectors

**Priority:** after stable job/result/artifact contracts. **Dependencies:** P3-01/P3-03/P3-05, E03 service identities.

**User value:** run and retrieve trusted tests from existing delivery workflows.

- [x] Version the public job/result API and support tenant-scoped, revocable service credentials.
- [x] Add idempotent submission, status polling, cancellation, immutable result/artifact retrieval and documented rate limits.
- [x] Add signed webhooks with bounded retries, delivery IDs and duplicate handling.
- [x] Provide one CI reference integration first; validate demand before building TMS/Git connectors.
- [x] Require explicit destination configuration and permissions for outbound notifications or issue creation.

**Done when:** a repeated CI request cannot create duplicate logical work, revoked credentials stop access, webhook retries are safe, and CI fails on real assertion failure while reporting blocked/expired jobs accurately.

**2026-09-17 (E08 reconcile, `bd0f7ae`, development after candidate `75e6996`):** `/api/v1` job API, `tp_svc_` credentials, idempotency, and `docs/ops/ci-job-api.md` remain in tree. Job-status webhooks now deliver: OWNER/ADMIN configure `PUT /api/projects/{id}/webhook` (URL + secret); MEMBER can read the destination without the secret; missing destination is a no-op. `WebhookDispatcher` POSTs HMAC `X-Keel-Signature` with `X-Keel-Delivery-Id`, up to 5 attempts, and skips duplicate delivery ids. Browser: Settings → Job-status webhook (explicit destination; not an issue tracker). **Issue-tracker / TMS / Git destinations are not built.** Runner auto-update and attestation remain deferred.

### Phase 6 close-out (product acceptance)

**2026-09-17:** Phase 6 **product acceptance is complete** for E01–E08 as specified above on **`23f9351`**. Optional enhancements stay deferred. This is **not** a Phase 5 launch guarantee.

**Suite:** `mvn test` (default `install.drills.skip=true`) **1301 run, 1299 passed, 0 failed, 2 skipped**. Shared/dedicated install drills were not forked from this pom.

**Populated history (project `prj_f935adf07758`, dry-run portal 8081, dedicated `127.0.0.0/8` for the local receiver only):**

- Recorded display: providers allowed `vision,agentrouter,cursor,ollama`; used `keel`; fallback `None`.
- Compare `exec_3a968739d47b` vs `exec_3cf07b61d74f`: pinned library/providers match; no recorded-step divergence. Original vs failed first rerun showed a presence divergence (original evidence kept).
- Classification: INFRASTRUCTURE → ASSERTION (API) then **LOCATOR** (browser); original `passedCount=0` / `todoCount=1` / `COMPLETED` unchanged.
- First rerun `job_rerun_44ef0cfe5650` failed because the temp workbook had been deleted. Rerun now rematerializes the pinned library revision; later attempts `job_rerun_d792bfc2b4bb` and `job_rerun_5d27c54aa366` **COMPLETED** with the same pins; original results unchanged.

**Webhooks (controlled `127.0.0.1:4079`):** first terminal job POSTed 3 times (500, 500, 200) with the same `X-Keel-Delivery-Id` and `sha256=` HMAC. Duplicate delivery ids are not posted again after the log file exists. `PUT` of `http://169.254.169.254/latest/meta-data/` returns 400 `webhook url blocked: blocked link-local destination` (browser Settings showed the same error; saved destination stayed `http://127.0.0.1:4079/hook`). Shared-mode unit tests refuse loopback/private destinations even if a destination file already exists. Public CI hosts such as `ci.example` remain allowed.

**Validation candidate:** **`2721e6d`** (test-only follow-up after nominated `23f9351` replaced `75e6996` on 2026-09-17). `75e6996` predates Phase 6. `23f9351` remains the Phase 6 product SHA; do not attribute `2721e6d` suite numbers to it. Documentation-only SHAs after `2721e6d` are not the code candidate. Any further **code** change requires naming a new candidate and rerunning affected checks.

### Deferred until evidence supports them

- Another authoring engine: first benchmark whether existing engines have a repeatable unmet need.
- Another generated language: first confirm customer demand and the cost of maintaining runtime assertions, setup semantics and replay parity.
- Hunt two-pass DOM processing: retain its intentional deferred status until recorded live failures show that it improves coverage/accuracy enough to justify complexity.
- Broad connector expansion: wait for a stable API and recurring requests from pilot customers.
- Dedicated installation launch support (implementation preserved).
- Private-runner launch support (implementation preserved), including auto-update and attested builds.
- Live UI-TARS click-grounding as a first-release guarantee.
- E01 named approvals beyond MEMBER vs OWNER/ADMIN.
- E08 outbound issue-tracker / TMS / Git destinations.

**Still HOLD / not closed by this local work**

- Public rollout; validation candidate is `2721e6d`.
- P0-01 named first-release approvals (shared scope).
- P2-03 **shared-host** worker-network isolation on staging.
- Shared-staging restore on that host’s storage (P4-02, shared only).
- Authorized representative-app Generate → Execute → Automate NEW → downloaded replay → UPDATE → replay.
- P5-01/P5-02 shared pilot and release.

## 9. Decisions and dependency checkpoints

- **AI data policy:** decide before P2-04. Recommended design supports an explicit allowlist with no silent fallback outside it; whether local-only is a supported commercial mode remains an owner decision.
- **Hosted network policy:** decide before P2-03. Recommended shared default permits approved public targets; private access belongs to an explicitly controlled dedicated/private-runner policy.
- **Workspace and roles:** decide before P2-01. Start with the smallest role set that supports customer administration and daily work.
- **First-release application/authentication scope:** decide before finalizing P0-03; this determines whether replay claims are supportable.
- **Capacity, budgets and retention:** set measurable limits before P3-01/P3-03 and performance acceptance. Do not invent concurrency or retention numbers from the audit alone.
- **Prerequisite semantics:** decide before P1-04; generated tests must either recreate setup or clearly decline unsupported dependent output.
- **Customer-editable framework files:** decide before P1-03 to avoid destructive Update behavior.
- **Pilot duration and release authority:** decide before P5-02; name who accepts release evidence and owns operational response.

## 10. Execution rules and completion evidence

For each work package, create implementation tasks containing the affected contract, regression scenario, data migration impact, dependencies, owner and acceptance criteria. Use the existing spec/plan workflow for changes that alter product behavior; this document is the cross-project roadmap, not a replacement for feature-level design.

Recommended task states: **proposed → ready → in progress → verification → complete**; use **blocked** with the specific unmet dependency. A checked historical task or passing compile does not advance a package to complete.

Each completed package must link:

1. The approved behavior/design and implementation revision.
2. Regression and acceptance results, with environment and excluded coverage.
3. Migration/rollback evidence where persistent data changes.
4. Updated user/operator documentation and residual limitations.

Keep defect traceability: F00→P1-01; F01/F02→P2-02; F03→P1-02; F04→P1-03; F05→P1-05; F06/F07→P3-03; F08→P2-03; F09→P2-04; F10→P3-01; F11→P3-02; F12→P3-04; F13→P1-04. Publication concurrency also spans P2-02/P3-03. Enhancements E01–E08 have explicit dependencies above.

**First implementation batch:** approve P0-01's result/dry-run contracts; establish P0-02/P0-03 fixtures; implement P1-01 and its downloaded-suite negative assertion test; then P1-02/P1-03 as a coordinated Update change. In parallel at the planning level, settle tenant identity, hosted target access and AI policy so Phase 2 is not blocked later.
