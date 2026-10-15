# Dependency-ordered implementation tasks

Status: every task below is **not started**. Work only after the user schedules the mobile phase. Paths describe intended files/classes, not existing completed implementation. Each phase ends with bounded checks and a short evidence report; do not repeatedly run the whole suite after every minor edit.

## M0 — compatibility and contract foundation

Dependencies: none. Must finish before device runtime integration.

- [ ] M0-01 Record exact source baseline, preserve dirty web/Precision changes, inventory existing runner/admission/IR/codegen interfaces and create an isolated feature branch only when implementation is authorized. Acceptance: no unrelated changes lost.
- [ ] M0-02 Create `mobile-contracts/pom.xml`, DTO schema fixtures and independent `mobile-runner/pom.xml`; qualify Java-client/Selenium pair and freeze server/driver/Node/npm/ADB versions in `mobile-toolchain.lock.json`. Acceptance: dependency convergence, compile/load AndroidDriver, web Selenium remains 4.49.0, clean isolated mobile dependency tree.
- [ ] M0-03 Add mobile platform dispatcher contracts and strict `MobileIrCodec`; preserve existing web codecs. Acceptance: legacy web round trips unchanged, Android never reaches web codec/codegen, unknown schema/op rejected.
- [ ] M0-04 Write platform-specific constitution amendment proposal for Java/Appium packages and real-device proof, preserving existing web promise and no-false-green rules. This plan records the user-approved Android direction; it does not falsely mark broader product/security approvals signed.
- [ ] M0-05 Add feature flags and deterministic/live test separation. Proposed flags: `delivery.mobile.enabled=false`, `delivery.mobile.live-tests=false`. Acceptance: normal web startup requires no Android tooling and default CI never launches a device/provider.

Exit: build isolation and schema contract tests pass; exact compatibility is recorded as qualified or explicitly failed. No live installation is implied by M0.

## M1 — app/device configuration and runner

Dependencies: M0. New classes under portal `delivery.mobile` and isolated runner device/session packages.

- [ ] M1-01 Add platform, app/profile/device/lease entities and additive schema patches; update `PortalStore`/records. Tests: WEB backfill, migration reentry, platform immutability, tenant foreign references rejected, old web project unchanged.
- [ ] M1-02 Implement `ApkInspector`, tenant upload quarantine/limits/hash and immutable app revisions. Tests: valid/corrupt APK, oversized archive, traversal/unsafe names, multiple launcher activities, package mismatch, API/ABI/signature/downgrade rejection. No auto-uninstall fallback.
- [ ] M1-03 Implement ADB inventory and installed-package metadata using bounded argument-array commands. Tests: multiple devices, unauthorized/offline states, missing serial, Unicode names, unavailable SDK, malicious package/serial inputs; no first-device fallback.
- [ ] M1-04 Implement host-scoped physical device lock plus durable lease/fence and quarantine reconciliation. Tests: two concurrent jobs/tenants/agent processes, late heartbeat, expired lease with active old session, no release without cleanup acknowledgment.
- [ ] M1-05 Extend `PrivateRunnerApiController`/transport capability negotiation, mobile claims, input manifest and protected secret channel. Tests: web-only runner cannot claim mobile, cross-tenant 404, stale attempt/device token rejected, ZIP/HMAC limits, token redaction.
- [ ] M1-06 Add supervised mobile worker/Appium process lifecycle, owned-port/session cleanup and preflight. Tests: unavailable server, session-start timeout, process crash, cancel while starting, cleanup failure quarantine; do not terminate unrelated processes.
- [ ] M1-07 Extend current revamped project/settings/run UI with app source, editable activity, device name+serial, readiness and busy diagnostics. Keep disabled if mobile flag is off. Tests: OWNER/ADMIN forms, MEMBER read-only, no secrets echoed; manual browser check for both app modes.

Exit: authorized device reserve → app verification/setup → session → teardown works on a controlled emulator; second job cannot touch it. Physical device readiness/authorization states are diagnosed. No claim of completed case execution yet.

## M2 — hierarchy binding and Execute

Dependencies: M1. New snapshot/candidate/binder/executor/assertion components.

- [ ] M2-01 Implement secure XML snapshot parsing, geometry metadata and snapshot invalidation. Fixtures include real Flutter merged/unmerged semantics, Arabic labels, dynamic labels, password nodes, malformed/XXE/oversized XML.
- [ ] M2-02 Implement locator overrides and ranked candidates with live uniqueness/action eligibility; exact selector preservation and same-node deduplication. Tests: user rank wins, bad user locator records fallback, identical distinct controls remain ambiguous, invalid native CSS rejected, no invented Flutter children.
- [ ] M2-03 Add input grammar/override-sheet parsing and mobile case settings revision binding. Tests: quotes, Unicode, reordered steps, duplicate override, required secret refs, unknown operation, no inferred assertions.
- [ ] M2-04 Implement tap/type/clear, native context, bounded waits and action results. Tests: stale re-query, hidden/disabled control, transport failure, successful command but failed business assertion, no uncertain-action replay.
- [ ] M2-05 Implement assertions/capture/compare/signed-out contracts in shared dependency-light semantics where feasible. Tests: false expected fails, exact ID boundary, all-match absence, setup captures retained, cases isolated, missing/ambiguous capture fails, absent-vs-empty explicit.
- [ ] M2-06 Integrate mobile Execute admission/results/evidence/provider-independent path, one/many selected cases and fail-fast. Tests: no raw secrets, every selected case accounted for, SIMULATED excluded, device loss interrupts and blocks remaining work.
- [ ] M2-07 Add Execute UI platform-specific settings/results, observed app/device identity and locator origin/fallback details. Tests: model/MVC/authorization plus browser walkthrough with actual mobile run.

Exit: controlled Flutter native cases execute with true positive and intentional negative assertions; same test inputs produce deterministic honest status. XML/selector unit tests do not replace live emulator evidence.

## M3 — authentication, prerequisites, gestures and permissions

Dependencies: M2.

- [ ] M3-01 Add immutable auth profiles, input declarations, revision conflicts and protected account/credential refs. Tests: missing supplied OTP/PIN rejected before job, secret canary absent from manifests/logs, profile revision pinned after later edit.
- [ ] M3-02 Implement all four auth modes and account predicates. Tests: valid cached session, expired session, wrong account, fresh login, unknown session check, login-under-test without hidden prelude, explicit failure when no logout/reset path.
- [ ] M3-03 Implement prerequisite DAG and start-state setup planner. Tests: single selected leaf, transitive/shared prerequisites once per leaf, cycle, missing refs, conflicting accounts, setup failure blocks body, independent cases reset declared state.
- [ ] M3-04 Implement long press, bounded swipe/scroll, Android Back, keyboard handling, launch/restart. Tests: no-progress/max limit, rotation/geometry changes, obstructing keyboard, action cancellation, app restart without data wipe.
- [ ] M3-05 Implement permission policy and explicit data-clear/reset options. Tests: allow/deny case overrides, unsupported special permission, no automatic broad grants, no implicit clear-data for fresh login, device-setting restoration diagnostics.
- [ ] M3-06 Build profile/case-requirement UI in existing project tabs. Browser checks: phone→OTP→PIN example, no-OTP login, two account profiles, single-TC prerequisites, separate destructive reset choice.

Exit: login/session matrix and prerequisite failures pass on emulator and a physical device; no setup failure becomes a body PASS. Named account identity evidence is captured or explicitly marked weaker.

## M4 — mobile framework generation and downloaded replay

Dependencies: M3; initial native template must later accept M5 extensions.

- [ ] M4-01 Create `customer-mobile-framework-template/` with qualified POM and properties parser, AndroidDriver lifecycle, assertions/waits/gestures. Test helper runtime separately and strip helper test classes from ZIP.
- [ ] M4-02 Implement deterministic `MobileCodeWriter`, screen locators/actions and stable persisted naming map. Tests: underscore/suffix rules, Arabic selector preservation, Java keywords, duplicate identities, reordered UPDATE and title changes keep stable symbols.
- [ ] M4-03 Implement testdata/secret-ref separation and auth/setup/capture codegen. Tests: owner+phase keys, login/body collision rejection, no values in Allure parameters, assertions match prove semantics, per-leaf setup independence.
- [ ] M4-04 Add platform dispatch in emit/package services, complete generated-layer regeneration and compile-before-publish. Tests: no mobile dependency in web ZIP, obsolete files removed, config overlay preserved, failed compile preserves prior bytes, case coverage includes TODO.
- [ ] M4-05 Implement mobile proof invalidation and manifest fingerprints. Tests: new APK/revision/credential/locale/toolchain changes re-prove, unselected old-build cases stale/TODO, no web reuse shortcut, job download bound to exact version.
- [ ] M4-06 Run clean NEW → unzip → Maven test → UPDATE → unzip → Maven test with authentication, prerequisites and deliberate failures. Compare expected/actual cases and setup failures; verify teardown and package SHA-256. Do not substitute fixture IR regeneration for actual mobile UPDATE pipeline.
- [ ] M4-07 Add generated README/properties examples/self-hosted CI and portal package display. Tests: clean checkout works with documented local inputs; missing properties fail clearly; binary APK/secrets/machine identifiers absent from export.

Exit: independent downloaded native package replay passes its expected positives and produces Maven failure for intended assertion/action failures on both device types. Package contains generated customer cases only.

## M5 — WebView, vision and Precision

Dependencies: M4. All are in agreed scope, not optional scope removal.

- [x] M5-01 Implement deterministic WebView discovery/matching and native restoration. Tests: multiple/late/disappearing contexts, inaccessible WebView, URL mismatch, system permission dialog, stale PID handle not persisted. Local unit tests only (2026-10-15). No device.
- [x] M5-02 Qualify Chromedriver mapping/path and DOM-in-WebView bridge. Tests: driver mismatch, unapproved runtime download blocked, native selector rejected in WebView, mixed-context generated replay matches proof. Local unit tests only (2026-10-15). Chromedriver binary was not qualified on a device.
- [x] M5-03 Implement mobile vision provider adapter and fresh geometry checks. Tests: stale screenshot, rotation/insets, low confidence, blank secure screen, redaction, no model available, no stored-coordinate replay, uncertain post-tap no retry. Local unit tests only (2026-10-15).
- [x] M5-04 Extend mobile Precision sidecar protocol/schema and budgets, single-intent ranking and fallback reporting. Tests: invalid/out-of-range IDs, wrong context, unavailable/disallowed/exhausted provider, secret redaction, candidate obeys user override rank, no arbitrary code or command execution. Local unit tests only (2026-10-15).
- [x] M5-05 Add vision/Precision/context fields to generated runtime and portal evidence. Tests: actual used provider vs allowed provider, fallback distinction, downloadable fresh vision dependency failure is honest, missing provider not PASS. Contract + generated runtime unit tests (2026-10-15).
- [ ] M5-06 Run real configured Cursor and local vision acceptance separately from mocked contracts, on permitted synthetic data. Record model/version/calls/outcomes; test required vision-only steps with positive and negative controls. Invocation alone is not a success gate. Open: not run.

Exit: mixed native/WebView + vision-dependent + real Precision scenarios prove and replay. Provider availability is an environment prerequisite; missing credentials keeps this gate open rather than counting a fallback run as Cursor coverage.

## M6 — consolidated acceptance and operator documentation

Dependencies: M1–M5 complete.

- [ ] M6-01 Run the [acceptance matrix](acceptance.md) on clean named code/template/toolchain revisions with emulator and physical device, APK and installed-app modes. Freeze expected outcomes before execution.
- [ ] M6-02 Run two-tenant/same-device contention, crash/kill/cancel/fence and late-upload drills. Assert no foreign evidence, commands or package publication; device quarantine/recovery is auditable.
- [ ] M6-03 Run credential/PII canaries over worker transport, XML/screenshots, Allure, logs, Surefire, IR, properties and ZIP. Explicitly document any withheld artifacts; synthetic screenshots only for canaries.
- [ ] M6-04 Run root deterministic web regression, web NEW/UPDATE downloaded benchmark, mobile contract/runtime tests and portal browser role walkthrough. Compare failures to a recorded clean baseline; do not silently exclude regressions.
- [x] M6-05 Write setup/troubleshooting/support/toolchain/migration/rollback docs and actual limits: Flutter semantics, WebView debug requirements, OTP supplied each run, device reset controls, unknown network isolation guarantees. See `docs/ops/mobile-operator-limits.md` (2026-10-15).
- [x] M6-06 Produce mobile acceptance report and support matrix. Distinguish planned/implemented/locally tested/host-qualified; record remaining device/OS/provider limitations. Do not change public launch decision or candidate without explicit release workflow. See `docs/reviews/mobile-m5-m6-acceptance-2026-10-15.md`. Device and provider rows stay open.

## Tracking rules

Each completed task records commit (or dirty-tree identity), commands, test names/counts, environment, evidence path and limitation. A phase report names only that phase's gate; no aggregate test count may substitute for missing physical-device/provider/replay evidence. Fix defects discovered within a phase before checking its exit box; do not weaken expected results to achieve green.
