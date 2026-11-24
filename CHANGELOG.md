# CHANGELOG

# Keel changelog

## 2026-11-24 — Android Bug Hunter taps the app, and the phone screen plays in the page

- On an Android project, Bug Hunter opens the saved app and taps controls it has not tried. It does not type. A crash or “keeps stopping” dialog is saved in the hunt pack you download from the job.
- Execute and Bug Hunter show the phone’s screen while a phone is connected. The picture is a live capture from the device, refreshed about twice a second for a few minutes, then the page reconnects.

## 2026-11-24 — Android start names the phone, and a busy phone does not start

- Automate and Execute ask before starting an Android run. The dialog names the connected phone, or says none is connected, and names the app package. A busy or unplugged phone shows "This run cannot start" and nothing is built.
- Execute on an Android project no longer asks for a website address. A workbook upload runs only those rows. The library runs the cases you checked, including ones accepted in pre-run review. Figma comparison still runs when the phone run finishes.

## 2026-11-24 — Android Automate walks the phone, or writes text shells

Automate on an Android project no longer opens Chrome. When the project has a saved phone that is connected and free, and an app package, conversion starts a mobile run. The runner follows each workbook line and records the locator it actually bound (resource id, then content description, then visible text). Those locators are written into the Android framework zip.

If mobile is off, no phone is saved, the phone is busy or unplugged, no package is set, or no runner claims the session within 45 seconds, the same zip is still produced. Each control is a text shell (`UiSelector().text("…")` from the Excel label) instead of a phone locator. The job message says which path ran and how many of each locator it wrote.

Known limits: the phone is the latest saved device profile, not a picker on the Automate page. The zip is packed without a Maven compile. Every case lands on one `MainScreen`. A missed bind, and every step after a failed step, stays a text shell. Swipe steps do not get a real locator.

## 2026-11-24 — Case sign-in panel: real dropdown, collapsed by default, Call-before removed

- **TC selectors are real `<select>` dropdowns now.** `Test case ID` and `Start-state test case ID` were text inputs with a datalist (still looked like inputs). Switched both to `<select>` populated from the project's mobile library via `ensureLibCases()`. Picking a TC from the dropdown now **auto-loads** its saved requirements — the `Load requirements` button is kept as a secondary trigger but is no longer necessary.
- **Panel collapses into a `<details>` block** with a one-liner summary ("only needed if you use Sign-in profiles above"), so users who aren't doing sign-in orchestration don't see the whole form open by default.
- **Call before (prerequisites) field removed from the UI.** It duplicated the workbook's `callBefore` column. The hidden input `#mobile-case-req-call-before` is kept so any pre-existing value round-trips on save, but the chips / search / results UI (~15 lines of HTML) is gone.
- **Locator ranking also now Thymeleaf-gated**: `th:if="${projectPlatform != 'ANDROID'}"` on the web dropdown + help; `th:if="${projectPlatform == 'ANDROID'}"` on the Android tier table (dropping the `hidden` attr that JS used to toggle). Same cache-proof pattern as Base URL / Preferred hooks.

## 2026-11-24 — Mobile settings polish: hide web fields server-side, TC dropdown

- Base URL / Preferred test hooks / the muted "Comma-separated attribute names…" help line now carry `th:if="${projectPlatform != 'ANDROID'}"` on their `<label>`s so Thymeleaf drops them from the HTML entirely on Android projects. Earlier JS-only hiding worked but was defeated by stale browser JS cache; the server-side gate makes a hard refresh unnecessary.
- Case sign-in requirements panel: `Test case ID` and `Start-state test case ID` inputs now use a shared `<datalist id="mobile-case-req-tc-list">` populated from `/api/projects/{id}/generated-workbook/cases`. Typing or arrow-down shows the project's actual mobile cases; still accepts manual input for cases that don't exist yet. Zero new endpoints — reuses the same `ensureLibCases()` cache already used by the Call-before search.

## 2026-11-24 — App source: tile-style radios

Two small plain-text radios ("App file (APK)" / "Already installed on the phone") replaced with proper card tiles — grid of two, each with a bold title and a one-line explanation. Selected tile gets a teal border + mint background (dark theme: teal border + accent-soft fill). CSS-only (`:has(input:checked)`); no JS change, same input `name="source"` / values so the existing `syncSourceUi()` toggle keeps working.

## 2026-11-24 — Mobile picker: busy-aware device labels

Device dropdown in the pick-from-phone modal now shows which phones are busy and ranks them so free phones come first. Each row is `name · short-serial · <state>` with the state in plain English (`available`, `reserved for setup`, `running a test`, `releasing`, `quarantined`) instead of the raw enum. Available phones sort first; quarantined last. When the selected phone isn't available, the status line under the list explains why picking is still fine: "this phone is running a test; picking is read-only." (The package list fetch is pure adb and doesn't touch the Appium session.)

## 2026-11-24 — Mobile picker: per-device selection when host has >1 phone

Both installed-packages endpoints now accept an optional `?deviceId=…` to target a specific phone instead of always the first AUTHORIZED one. The modal gains a **Phone** `<select>` at the top (hidden when there's only one authorized device on the host — the common case stays single-click). Changing the dropdown re-fetches the package list for that phone; picking a row then uses the same deviceId for the details fetch so Version + Launch screen come from the right device.

## 2026-11-24 — Mobile Project Settings polish: modal picker, auto-fill, hide web-only fields

Follow-ups on today's "Load packages from connected phone" button.

- **Modal picker replaces the `<datalist>`.** Clicking **Pick from connected phone…** now opens a proper modal with a filter box and a scrollable list — consistent across browsers, searchable, and accessible (Esc / backdrop click closes). Capped at 500 rendered rows to stay snappy on devices with a lot of installed apps.
- **Auto-fill on pick.** When you click a row in the modal, the portal also pulls the current **Version name**, **Version code** and **Launch screen** for that package off the phone and fills them in. Backed by a new `GET /api/projects/{id}/mobile/apps/installed-packages/{pkg}/details` plus [`AdbDeviceDiscovery.resolveLauncherActivity(serial, pkg)`](src/main/java/delivery/mobile/adb/AdbDeviceDiscovery.java) (shells `adb shell cmd package resolve-activity --brief <pkg>`, handles the `.ShortName` fully-qualifier case). Endpoint always returns 200 so a missing field is a silent fallback, not a red banner.
- **Hide web-only fields on Android projects.** `Base URL` and `Preferred test hooks` (both DOM/web concepts) now hide when `platform = ANDROID` — same pattern the existing Locator ranking swap uses to show the Android tier table instead of the web dropdown.

## 2026-11-24 — Project settings: fix mobile panels, pick package from phone, drop "Run on a phone"

Bug fix (the real one the user hit). The mobile IIFE in [`project-detail.js`](src/main/resources/static/js/pages/project-detail.js) lives OUTSIDE the outer project-detail IIFE, so `apiUrl` and `API_HEADERS` (declared inside the outer IIFE) were not in scope. Every `loadMobileApp()` / `loadMobileDevices()` call threw `ReferenceError: apiUrl is not defined`, caught by the try/catch, and surfaced as the generic red banners "Could not load app settings" / "Could not load phones" even though the backend returned 200 with the device + revisions. Fix: redeclare `projectId`, `API_HEADERS` and `apiUrl` inside the mobile IIFE (6 lines).

New: **"Load packages from connected phone"** button under Package name. Reads third-party packages from the first AUTHORIZED device on this host via `adb -s <serial> shell pm list packages -3`, dedup-sorts them into a `<datalist>` so typing in Package name filters by them. Minimal UI — no modal, no device picker; picks the first eligible phone automatically (the common case).

Backend wiring:
- [`AdbDeviceDiscovery.listInstalledThirdPartyPackages(serial)`](src/main/java/delivery/mobile/adb/AdbDeviceDiscovery.java) — shells out, parses `package:` lines, returns sorted + deduped names.
- `GET /api/projects/{projectId}/mobile/apps/installed-packages` on [`MobileAppDeviceController`](src/main/java/delivery/portal/api/MobileAppDeviceController.java) — resolves fingerprint → live serial via `DeviceSerialResolver`, calls the helper, returns `{packages, source, message?}`. Always 200 so the UI never shows a red banner on a missing/offline phone — just a muted note.

Removed: the "Run on a phone" panel on Project Settings. It mirrored the Execute page and confused the settings-vs-run distinction (user noted: "here i only save mobile and apk — then in execute it uses this settings only"). Deleted the panel from [`project-detail.html`](src/main/resources/templates/project-detail.html) and the now-dead [`mobile-run.js`](src/main/resources/static/js/mobile-run.js) (~290 lines gone; no test or template still references it).

`mvn -pl keel-portal test -Dtest='*Mobile*,*Precision*'` → 92 tests green.

## 2026-11-23 — Flow map lives with the diagrams

- The product flow map is `docs/archive/ops/diagrams/keel-product-flows.md`, next to the older flow images.
- No product behavior changed.

## 2026-11-22 — Product flow map

- **Flows** — one picture of how Generate, Record, the library, Execute, Automate, Bug Hunter, suites, prove/heal, credits, and the tunnel connect. Moved on 2026-11-23 to `docs/archive/ops/diagrams/keel-product-flows.md`.
- No product behavior changed. Linked from `docs/README.md`, `docs/ENGINEERING_HANDOFF.md`, and `docs/knowledge/CURRENT.md`.

## 2026-11-23 — Host environment doc

New `docs/ENVIRONMENT.md` — single source of truth for the host binaries + env vars the portal + runner shell out to: JDK 21, Maven 3.9+, Node 18+, adb, apkanalyzer, aapt2, appium + uiautomator2, mobile runner process, `KEEL_PORTAL_URL`, `KEEL_RUNNER_TOKEN`, `CURSOR_API_KEY`. Each row names the role, install command per OS (Scoop on Windows, Homebrew on macOS), override property, and what breaks on the user's screen when it's missing. Section 3 records which pieces are present on the current dev host.

`README.md` Prerequisites expanded with the mobile extras and a pointer to `docs/ENVIRONMENT.md`. `docs/MOBILE_FRAMEWORK_RULES.md` gains the same pointer in its header.

## 2026-11-23 — Mobile default flipped ON

`delivery.mobile.enabled` now defaults to `true` in both the base `application.properties` and `src/test/resources/application.properties`. Every mobile feature shipped over phases 1 → 7c is live on a vanilla portal boot without needing `SPRING_PROFILES_ACTIVE=product` or any system property. `live-tests` stays `false` (it drives a real Appium server; flipping it requires a device on the host). The `*FlagOffApiTest` SpringBootTests still work unchanged — they override `delivery.mobile.enabled=false` via `@SpringBootTest(properties=…)`.

`MobileFeatureFlagDefaultsTest` updated: `applicationPropertiesDefaultMobileDisabled` → `applicationPropertiesDefaultMobileEnabled`; `defaultJvmWithoutOverridesKeepsMobileOff` removed. The dual-opt-in test (`dualOptInStillRequiresBothSystemProperties`) is kept so shell scripts that gate on `delivery.mobile.enabled + keel.mobile.ci-opt-in` keep their contract.

`scripts/ci/check-mobile-flag-off.sh` repurposed: now asserts the flag is explicitly declared (either value), not that it's `false`. Both callers already live in `.github/workflows.disabled/`.

## 2026-11-23 — Mobile phase 7c follow-up: sidebar + clearRun + Spring wiring

Closes the three "not checked" items the 7c commit left:

- **Sidebar entry.** `fragments.html` now links `/admin/mobile-precision` under the Admin section, gated on `headAdmin` like the other admin links.
- **`precision.clearRun(runId)` on run finish.** `MobileRunOrchestrator.finish(...)` now releases the per-run budget. To break the orchestrator ↔ precision-service circular dependency, the orchestrator holds an `ObjectProvider<MobilePrecisionBindService>` and resolves the bean on each finish. First attempt used `@Lazy`, which tried to CGLIB-subclass `MobilePrecisionBindService` and failed with `No default constructor found` — surfacing a regression in 7c that broke every Spring Boot test loading the portal context (58 of the 160 TestSuite rows). `ObjectProvider` sidesteps the proxy entirely.
- **`@Autowired` hint** on `MobilePrecisionBindService(MobileRunOrchestrator, PortalStore, MobilePrecisionPickLog)` so Spring picks it over the package-private test constructor without guessing. The 58 failures in the earlier run were masked by Spring's "context failure threshold" — once the first context load failed, every subsequent test in the JVM also failed with "threshold exceeded". After this fix, the full `-Dtest='*Mobile*,*Precision*'` filter runs 93 tests green.

## 2026-11-23 — Mobile phase 7c: hardening, module split, dashboard

Finishes the mobile Precision roadmap by picking up every follow-up phases 7a + 7b left on the table — plus two defects that were silently hobbling the build.

**Defect: portal reactor was broken.** Phase 7a's `MobilePrecisionBindService` imported `delivery.mobile.runner.precision.*` from the `mobile-runner` jar, but `keel-portal` depends on `mobile-contracts` only (the mobile-runner pom has a MANDATORY ban on the reverse dep). Phase 7a shipped green because I only ran `mvn -f mobile-runner/pom.xml ...`, which never compiles the portal. Full reactor build was failing. Fix: `PrecisionCandidate`, `PrecisionInput`, `PrecisionOutcome` and `MobilePrecisionGate` moved from `mobile-runner/src/main/java/delivery/mobile/runner/precision/` to `mobile-contracts/src/main/java/delivery/mobile/runner/precision/` (same package, zero import churn); `MobilePrecisionGateTest` moved with them.

**Defect: stale lambda capture.** `src/main/java/delivery/authoring/PrecisionBindService.java:98` referenced a reassigned `ranked` variable from a lambda, which the compiler rejects as not effectively final. The file hadn't been touched since commit `991feed`, but no one ever ran `mvn -q -o -pl keel-portal compile` on it. Fixed by pinning to a `final GroundRankResult picked = ranked;` local before the lambda.

**Follow-ups now shipped.**
- **Per-run call budget.** `MobilePrecisionBindService` now holds a `ConcurrentHashMap<runId, PrecisionCallBudget>`. Each `bind()` consumes one; once drained, further binds return `fallbackReason=BUDGET_EXHAUSTED` without hitting Cursor. `clearRun(runId)` releases the entry; the map is hard-capped at 1024 to bound an orphaned-callback leak.
- **Shortlist cap of 25** on both client and service. A pathological page wouldn't ship 100 candidates to the sidecar anymore.
- **Mobile preamble** on the `pick` request table so the sidecar's generic prompt sees "Mobile Android native candidates" instead of inferring a web page.
- **Real `snapshotExcerpt`.** `MobilePrecisionClient.buildExcerpt(...)` builds a small XML blob of the candidate nodes (class, bounds, resource-id, content-desc, clickable, text), XML-escaped. The gate truncates at 8 KB.
- **Per-run pick cache.** The client caches the chosen node index keyed on `(intent, sorted candidate indices)` — a retried step on the same screen pays no second sidecar call.
- **System.err logging** on every `MobilePrecisionClient.pick` failure path (transport, non-200, bad JSON, non-integer id, fallback reason): previously the runner swallowed all failures silently.
- **Controller 400 vs 404 split.** `POST /precision/bind` returns `404` only when the service says `run not found: …`; every other `IllegalArgumentException` (empty candidates, blank stepContext) now returns `400` with a `BAD_REQUEST` code so the runner can distinguish wire bugs from "not my run."
- **Admin dashboard.** New page at `/admin/mobile-precision` (served by `PortalUiController.adminMobilePrecision`) renders a live table of the last 500 picks: when, run, project, intent, candidate count, outcome (cursor / keel / rejected), chosen id, fallback reason. In-memory ring in `MobilePrecisionPickLog`, JSON feed at `GET /api/admin/mobile-precision/picks?limit=…`. No persistence — portal restart clears it, by design (operational, not historical).

**Tests.** `MobilePrecisionBindServiceTest` grows coverage for budget exhaustion, `clearRun`, oversized shortlist truncation, mobile-preamble presence and pick-log recording. `MobilePrecisionClientTest` covers the XML excerpt (class + bounds + clickable flag) and attribute escaping. Full `mobile-runner` and `mobile-contracts` suites still pass.

## 2026-11-23 — Mobile phase 7b: runner-side Precision caller

`mobile-runner/src/main/java/delivery/mobile/runner/precision/MobilePrecisionClient.java` closes the loop on phase 7a. When `MobileCaseRunner.bind()` ends with more than one actionable text-contains hit (the ambiguity branch that previously returned `element not found`), the runner now POSTs the candidates to the portal's `/api/v1/runners/mobile/runs/{runId}/precision/bind` endpoint and asks the gate to pick one. The returned `chosenId` is trusted only when it maps back to one of the hits the runner sent; any other outcome (HTTP error, `code` set, provider fallback, parse failure, foreign id) → `Optional.empty()` and the runner keeps the pre-Precision not-found behavior. `MobileRunnerAgent` wires the client with its existing portal URL / bearer token.

`MobileCaseRunner` grows a `PrecisionBinder` functional interface + a 5-arg constructor so the hook stays null-safe for unit tests. Five TestNG tests on `MobileCaseRunnerPrecisionTest` cover: ambiguous-with-precision pick, ambiguous-without-precision fallback, out-of-list pick rejected, binder throws → fallback, and single-hit does not call precision.

`docs/MOBILE_FRAMEWORK_RULES.md` §5 now diagrams the full flow (hits → client → portal → gate → trust check) and dates it; §6 moves "runner-side caller" from "not supported today" into "Recently landed".

## 2026-11-23 — Mobile phase 7a: portal-side Cursor caller for mobile Precision

Takes the "Mobile Precision Cursor caller" item off the "not supported today" list on the portal side. The runner side (phase 7b — runner client + `MobileLocatorBinder` integration) still owes a separate commit.

**Why portal, not runner.** The mobile runner is a thin remote agent. Owning `heal.mjs` on the runner would mean shipping Node + the Cursor sidecar jar + a Cursor API key to every runner host. A bearer-authenticated HTTP round-trip over the existing `/api/v1/runners/mobile` wire is cheaper and keeps credentials on portal.

**What landed.**
- `MobilePrecisionBindService` resolves the project's Cursor model pin via `PortalStore.precisionConfigForProject`, invokes `CursorHealClient.pickCandidateId` with a mobile-shaped candidates table, and feeds the response through `MobilePrecisionGate.rank(...)` with provider state filled in. Returns the gate `PrecisionOutcome` verbatim.
- `POST /api/v1/runners/mobile/runs/{runId}/precision/bind` on `MobileRunnerApiController`. Body: `{ intent, candidates[{id,label,contextKind,userOverride}], stepContext, snapshotExcerpt }`. Response: `{ code, chosenId, usedProvider, fallbackReason, countsAsCursor }`. Tenant isolation comes from the bearer token's enrollment; a run from another tenant → 404.
- `MobilePrecisionBindServiceTest` covers: chose-valid-id, invalid-id, cursor-not-ready → UNAVAILABLE fallback, cross-tenant run → 404, empty-candidates → 400, sidecar runtime failure → UNAVAILABLE fallback.
- Docs: `docs/MOBILE_FRAMEWORK_RULES.md` §5 now diagrams the flow and dates it; §6 shrinks the "not supported today" bullet from "Cursor caller" (full absence) to "phase 7b — runner side" (half-shipped).

**Scope deliberately skipped (lazy).**
- Credit ledger / `PrecisionCallBudget` integration — mobile cap is a follow-up.
- Per-intent cache — the runner-side client in 7b can cache locally.
- Mobile-specific prompt catalog entry — `heal.mjs` `pick` mode accepts an intent + table already.
- Persistence of accepted picks for telemetry — matches the web caller's current state.

## 2026-11-23 — Mobile phase 9 follow-ups: active-revision snapshot + APK preflight

Picks up the three items the 2026-11-22 phase 9 commit left as follow-ups.

- **9a — Active APK revision + list under one snapshot.** `GET /api/projects/{id}/mobile/apps/revisions` now runs inside `@Transactional(readOnly=true)`, so the `listAppRevisions` call and the `findActiveAppRevision` call come from a single consistent commit point. An APK upload that lands between them can no longer leave the client with an `activeRevisionId` that isn't in its rows. `registerAppRevision` was already `@Transactional` on the write side — no change there.
- **9b — Preflight that the APK is actually on the phone.** `MobileRunOrchestrator.prepare()` now calls `AdbDeviceDiscovery.resolveInstalledPackage(serial, appPackage)` right after resolving the serial, before burning ~30 s on `SupervisedAppiumLifecycle.start()`. `PACKAGE_NOT_FOUND` → `IllegalStateException` with a message that names the serial and prints the exact `adb -s … install` command. Other ADB errors (unauthorized / offline / missing SDK) are intentionally passed through — Appium's own start path owns those. Blank `appPackage` (launcher-only runs) skips cleanly. This replaces the originally-planned "runner-side aapt verification" because `ApkInspector` already runs `aapt dump badging` server-side at upload time, so the stored `packageName` is the aapt-reported one — the real gap was the phone not having the APK yet.
- **9c — Post-timeout APK uninstall: NOT SHIPPING.** [`ApkRevisionPolicy.autoUninstallFallbackAllowed()`](src/main/java/delivery/mobile/apk/ApkRevisionPolicy.java) returns `false` by design and the policy class explicitly asserts "this policy never authorizes auto-uninstall". The runner doesn't install APKs itself (only the user / Appium does, and this codebase uses `noReset:true`), so there's also no runner-side install-in-progress that an orchestrator timeout could orphan. The follow-up as framed in the original phase 9 commit was based on a misread of the architecture; the honest fix is to leave the policy alone.

## 2026-11-23 — Mobile phase 6b: schedule UI picks a phone + APK package

Finishes the backend of phase 6. `/suites/schedule` now renders a Phone + APK package picker when the pinned project's `platform` is `ANDROID` (hidden for web). The two selects reuse the same `/mobile/devices` and `/mobile/apps/revisions` endpoints the Execute page uses. Values prefill from the saved schedule and ride on the existing `PUT /suites/{suiteId}/schedule` as `mobileDeviceId` / `mobileAppRevisionId`. A client-side preflight blocks "turn on automatic runs" without a pinned phone so the dispatcher's `DEVICE_REQUIRED` fault never gets a chance to fire from the UI. `SuiteController.ScheduleRequest` grew two nullable fields with convenience constructors for the 4-arg and 6-arg shapes so no caller breaks; the controller routes to `SuiteService.putMobileSchedule` only when at least one is set.

Docs:
- `docs/MOBILE_FRAMEWORK_RULES.md` §4 now tells the truth about `MobileFirstOf` (the mobile analogue of web `FirstOf` is wired through the codegen template).
- `docs/MOBILE_FRAMEWORK_RULES.md` §6 drops "Figma Validate per-step on mobile" and "Mobile suite scheduling" from the not-supported list; moves them under a new "Recently landed" sub-section with commit dates.

## 2026-11-22 — Mobile roadmap phases 1-9 (minus 7)

Executed the phased plan agreed on with the user. Each phase is a single commit; 7 (Cursor caller on mobile Precision binds) is paused pending a design decision on the runner↔portal round-trip for the sidecar.

- **Phase 1 — Discoverability**: `/execute` shows a banner on web projects when the server has mobile enabled, pointing at the Android create flow. `/projects` post-create redirects Android straight to `settings#mobile-devices-panel`; `project-detail.js` scrolls the (initially hidden) panel into view after reveal.
- **Phase 2 — Pre-run guard**: `mobile-run.js` runs `preflightPhone()` before `POST /mobile/runs` with inline alerts for no phones registered / none picked / picked phone not AVAILABLE. `MobileRunOrchestrator.start` throws `IllegalStateException` for busy devices (→ HTTP 409 with a user-friendly message).
- **Phase 3 — APK revision dropdown**: new `GET /api/projects/{id}/mobile/apps/revisions`; **App package** `<select>` on the mobile run row; `MobileRunOrchestrator.start(..., appRevisionId)` resolves the chosen revision instead of always using `findActiveAppRevision`.
- **Phase 4 — Runner step-screenshot capture**: `MobileCaseRunner` gains a `StepCapture` callback; `MobileRunnerAgent` uploads each PNG via a new `POST /api/v1/runners/mobile/runs/{runId}/cases/{tcId}/steps/{stepIndex}/image`; orchestrator writes to `<storeRoot>/mobile-runs/<runId>/evidence/<tcId>/step-NNN.png`. Toggle: `KEEL_RUNNER_CAPTURE_STEP_PNG`.
- **Phase 5 — `MobileDesignComparePhase`**: fires on run finish (COMPLETED / FAILED). Mirrors the web `DesignComparePhase` for whole-TC + per-step compare; same `DesignCompareGate` + `DesignCompareEvidence` used end-to-end.
- **Phase 6 — Mobile suite scheduling (pinned device)**: `SuiteDiskStore.putMobileSchedule(..., mobileDeviceId, mobileAppRevisionId)` + `SuiteService.putMobileSchedule`. `SuiteScheduleDispatchService.dispatchDue` branches on project platform; Android path calls `MobileRunOrchestrator.start` with the pinned device + revision; records `DEVICE_REQUIRED` / `DEVICE_OFFLINE` / `DISPATCH_ERROR` on the schedule for the UI to surface. UI form is Phase 6b.
- **Phase 8 — Dedicated mobile prompt stack**: new `generate/capability-manifest-mobile.md` + `generate/testing-techniques-mobile.md`; `TcGenerateService.buildSystemPrompt(project)` builds `stories-to-json + mobile-capability + mobile-techniques + mobile-prelude` for Android projects. Falls back to web prompts if a mobile file is missing.
- **Phase 9 — APK upload error surfacing**: generic `catch (Exception)` in `MobileAppDeviceController.uploadApk` now logs the stack trace and sends the real cause instead of a canned "APK_INVALID". Rejections also log at WARN with the code + message so repeat submissions are grep-able.

## 2026-11-21 — Mobile parity: platform-aware prompts + MOBILE_FRAMEWORK_RULES

- **Mobile-aware generate**: `TcGenerateService.buildSystemPrompt(project)` appends `prompts/generate/mobile-prelude.md` when the project's platform is `ANDROID`. The prelude rewrites the web phrasing (URLs → "Launch the app", "Click" → "Tap", "page" → "screen"), adds mobile-specific coverage hints (permissions, keyboard dismissal, system back), and bans browser-only concepts (tabs, hover, viewport, CSS).
- **Mobile-aware authoring review**: `AuthoringReviewService.systemPrompt(project)` appends `prompts/authoring/mobile-prelude.md` for Android projects. The reviewer now rewrites `Open the … page at /path`, `Click the … button`, URL-contains assertions, and viewport language on sight when it's an Android suite.
- **New `docs/MOBILE_FRAMEWORK_RULES.md`**: canonical mobile emit rules (one screen = one class; Appium imports locked; CSS banned in NATIVE_APP; actions delegate to `SettledMobileActions`), the fixed locator tier list (USER_OVERRIDE → RESOURCE_ID → ACCESSIBILITY → LABEL_SIBLING → TEXT_CLASS → XPATH), the `MobilePrecisionGate` validator rules (secret canary + code rejection + context match + user-override wins), Cursor model precedence on mobile, and an honest "not supported today" section (no Figma Validate per-step on mobile yet, no mobile `FirstOf` fallback bag, no mobile locator-outcomes ledger).
- **Web doc cross-link**: `docs/FRAMEWORK_RULES.md` now opens with a one-liner pointer to the mobile rules doc.

## 2026-11-21 — Precision engine tuning + Figma Validate per-step

- **Precision engine cache**: job-scoped ConcurrentHashMap in `PrecisionBindService` keyed by `(intent, shortlist-hash, html-hash)` so retries of the same bind on the same page snapshot reuse the earlier Cursor pick without burning a budget slot. Batch cap raised from 5 → 10 intents per `groundRankBatch` call.
- **Precision confidence floor**: new workspace property `delivery.authoring.precision.min-confidence` (`medium` default; `high` is the stricter option). `GroundRankResult.isAcceptableAt` routes `medium`-confidence picks to the solve fallback when `high` is set.
- **Cursor model picker** (Precision engine): new property `delivery.authoring.precision.cursor-model` (default `auto`, also respects `KEEL_CURSOR_MODEL` env). Project Settings grows a **Cursor model** dropdown that overrides the workspace default. Admin → LLM & API keys grows the workspace-wide equivalent. `heal.mjs` accepts `req.model` with a tight family regex (`claude-*`, `gpt-*`, `gemini-*`, `grok-*`, `deepseek-*`); unknown ids fall back to `auto` so a stale pin never fails a run.
- **"Design mockups" → "Figma Validate" rename + per-step mockups**: upload a PNG per `(tcId, stepIndex)` to `design-references/<tcId>/step-NNN.png`; `DesignComparePhase` now compares each step's screenshot against the matching Figma and writes `design-compare-step-NNN.json` + `design-actual-step-NNN.png` beside the per-TC evidence. Project Settings shows a thumbnail per uploaded step with click-to-preview; the library TC-row step expansion grows a 32×32 Figma tile per step (filled thumbnail / "+" to attach).
- **Pre-run review ↔ credit ledger**: `/api/projects/{id}/pre-run-authoring-review` now preflights against the `AI_VALIDATION` add-on cost (HTTP 402 when under) and debits once per successful review with a stable id. Admin review batch cap configurable via `delivery.authoring.review.cases-per-batch` (default 10).
- **Review modal**: edited cases get a left accent border plus an "edited" pill; click toggles a before/after diff (red/green) for the changed field(s).
- **/account cleanup**: dead Vision model radios removed from the account page — the live vision model is set server-side via Admin → LLM & API keys (`delivery.vision.model` system property).

## 2026-11-16 — Portal UX Phase 11 (portal accessibility)

- **Skip to main content** link in the sidebar; `main` gets `id="keel-main-content"` on load.
- **Keyboard** — feature `?` help opens on Enter/Space; Escape closes and restores focus; nav group toggles expose `aria-expanded`.
- **Focus-visible** rings on sidebar, top bar, Guide TOC, and help popovers; calmer `?` default opacity; reduced-motion tweaks for KPI pulse.
- Handoff: `docs/handoff/2026-11-16-phase11-portal-a11y.md`.

## 2026-11-16 — Portal UX Phase 10 (Guide + feature-help sync)

- **Guide** — new **Feature glossary** (`/guide#feature-glossary`) lists sidebar features with deep links; TOC and overview cards use current nav names (TCs Generation, Playback, History, …).
- **`?` tooltips** — `feature.help.*.guide` URLs now match Guide section anchors (single source of truth with the glossary table).
- New Guide sections: **Test blueprints**, **Run memory**.
- Handoff: `docs/handoff/2026-11-16-phase10-guide-glossary-sync.md`.

## 2026-11-16 — Portal UX Phase 9 (run memory + Bug Hunter triage)

- **Run memory** — fix table layout (removed erroneous `status` grid class); proper `table` + empty state; section `?` help; sidebar copy matches “saved locators” wording.
- **Bug Hunter** — triage renamed **Review last hunt report** with intro + `?` help (recent runs removal was Phase 5).
- Handoff: `docs/handoff/2026-11-16-phase9-run-memory-bug-hunter.md`.

## 2026-11-16 — Portal UX Phase 8 (Execute tunnel copy)

- Execute tunnel badge uses plain labels (**Tunnel inactive**, **Tunnel active**, …) without `delivery.tunnel.*` property hints.
- Badge row links to **Guide § Private app tunnel**; optional one-line hint under the project chip.
- Handoff: `docs/handoff/2026-11-16-phase8-execute-tunnel-copy.md`.

## 2026-11-16 — Portal UX Phase 7 (suites scheduling)

- Scheduling page: friendly suite picker, cron presets, credential dropdown, Account-based failure alerts, sticky **Save schedule settings** for schedule + alerts + watch.
- Handoff: `docs/handoff/2026-11-16-phase7-suites-schedule.md`.

## 2026-11-16 — Portal UX Phase 6 (Generate layout)

- **Generate** page split into stacked cards: story generate (primary), bulk CSV, paste external AI, copy prompt — each with section **`?`** help.
- Removed nested “More ways…” disclosure; bulk **Recent jobs** table stays under the bulk card only.
- Handoff: `docs/handoff/2026-11-16-phase6-generate-layout.md`.

## 2026-11-16 — Portal UX Phase 5 (dedupe recent job tables)

- **Execute** and **Bug Hunter** no longer show duplicate “recent runs” tables; both link to **History** for past jobs.
- Live execute progress/results still mount on Execute when you start or resume a run (`execute.js` uses `#run-detail-host` only).
- Generate **Recent bulk jobs** and dashboard top-5 recent activity unchanged.
- Handoff: `docs/handoff/2026-11-16-phase5-dedupe-recent-tables.md`.

## 2026-11-16 — Portal UX Phase 4 (English file inputs)

- Custom English file picker chrome on all visible file inputs (`keel-file-input.js`); pin `lang="en"` on document.
- Top bar language-looking **EN** control renamed to **Account** (account/logout menu only).
- Handoff: `docs/handoff/2026-11-16-phase4-english-file-inputs.md`.

## 2026-11-16 — Portal UX Phase 3 (shared picker + TC multiselect)

- `keel-project-picker.js` and `keel-tc-multiselect.js` loaded on all portal pages.
- Fixed Multi-model generate project dropdown (generate.js guarded listeners + shared picker).
- Suites scheduling uses shared picker; Generate from knowledge library ingest uses TC multiselect.
- Handoff: `docs/handoff/2026-11-16-phase3-shared-components.md`.

## 2026-11-16 — Portal UX Phase 2 (overview dashboard)

- Healthcare-inspired Overview: softer KPI cards, execution **trends** line/area chart (replaces stacked bars), card-style attention list.
- Dashboard `?` help on overview, trends, attention, and recent activity; recent table capped at **5** rows with link to **History**.

## 2026-11-16 — Portal UX Phase 1 (sidebar IA + feature help)

- Sidebar navigation regrouped and renamed (TCs Generation, Playback, Test blueprints, Test suites, TCs execution/automation, History, Bug Hunter under Execute & automate).
- Click **`?`** on any main nav item for a one-line feature description (popover + Guide link).
- URL aliases: `/playback`, `/history`, `/test-blueprints` redirect to existing routes.
- Unified brand mark: tab favicon and login logo match the sidebar keel icon (`keel-mark.svg` / `favicon.svg`).
- Plan + revision: `docs/handoff/2026-11-16-ux-overhaul-master-plan.md`, `docs/handoff/2026-11-16-phase1-sidebar-ia.md`.

## 2026-11-15 — Framework contract Phase 6.16 (production readiness audit)

- Production execution-path audit, flag matrix, isolation and rollback assessment, controlled rollout plan: `docs/handoff/2026-11-15-phase6-16-production-readiness-audit.md`.
- Verdict **READY AFTER SPECIFIED BLOCKERS** — no flags enabled; 6.15 staging artifacts are not production evidence.

## 2026-11-15 — Framework contract Phase 6.15 (staging load qualification)

- **Not executed** — preflight blocked (no staging host, no ops approval, `CURSOR_API_KEY` unset in run environment).
- Execution checklist and gated runner (`FrameworkStagingLoadQualificationIT`) for approved staging only: `docs/handoff/2026-11-15-framework-contract-phase6-15-staging-load-qualification.md`.

## 2026-11-15 — Framework contract Phase 6.14 (operational observability)

- Wired `AutomateFrameworkRepairBridge` and `AutomateFrameworkAcceptanceGate` to emit `framework-acceptance/framework-repair-operational.json` with operator terminal reasons and bridge/timing fields (no timeout or Policy A changes).
- Deterministic tests: `FrameworkRepairBridgeOperationalTest`, `FrameworkRepairTerminalReasonClassifierTest`; compile-on evidence may embed operational report.
- Operator runbook and staging load plan: `docs/handoff/2026-11-15-framework-contract-phase6-14-operational-observability.md`.

## 2026-11-15 — Framework contract Phase 6.7 (customer template compile baseline)

- Fix duplicate `allure-environment-writer` in `customer-framework-template/pom.xml`; TestNG `compile` scope; `Assertion.java` multi-catch.
- `CustomerFrameworkTemplatePomTest`; `FrameworkRepairCompileBaselineTest` passes with real packager + codegen.

## 2026-11-15 — Framework contract Phase 6.6 (production readiness qualification)

- Staged reconciliation with lock, backup/restore; `FrameworkRepairArchitectureAssessor`; compile-on live IT.
- Compile-on matrix blocked by qualification fixture/template POM; promotion safety on `COMPILE_FAILED` verified.

## 2026-11-15 — Framework contract Phase 6.5 (Cursor repair qualification)

- Live matrix fixtures (`FrameworkRepairQualificationFixtures`), expanded `FrameworkAdaptationCursorLiveIT`, evidence `phase6-5-live-evidence-*.json`.
- Reconciliation manifest persistence; repair objective lists mandatory rule IDs.
- Resilience / acceptance / architecture qualification tests (deterministic).

## 2026-11-15 — Framework contract Phase 6.4 (Cursor workspace reconciliation, D-6.3-01)

- After Cursor `COMPLETED`, `FrameworkRepairWorkspaceReconciler` merges permitted generated changes from `taskDir/workspace/` into `framework-acceptance/repair-candidate/` before compile and inventory v2 revalidation (`RECONCILIATION_FAILED` on boundary errors).
- FAKE repair unchanged. Tests: `FrameworkRepairWorkspaceReconcilerTest`, `FrameworkAdaptationCursorReconcileIT`.

## 2026-11-15 — Framework contract Phase 6.3 (live Cursor repair verification)

- Opt-in `FrameworkAdaptationCursorLiveIT`; live run scenario 1 (missing page): Cursor `COMPLETED`, gate `REVALIDATION_FAILED`, no promotion; inventory v2 context confirmed.
- Documented limitation **D-6.3-01**: Cursor edits in `taskDir/workspace` not merged to `repair-candidate` before revalidation. See `docs/handoff/2026-11-15-framework-contract-phase6-3-live-cursor-verification.md`.

## 2026-11-15 — Framework contract Phase 6.2 (inventory false-positive fixes)

- `CodeWriter.expectedUnionPageImportsForGroup` shared with `FrameworkExpectedInventory`; `linkedPageClassNames` per test group; demotion-safe empty page lists; multi-page test validation.
- Regression: `FrameworkContractPhase62RegressionTest`; gate test `inventoryV2_emitPath_acceptanceAndRepairUseSameInventory`.

## 2026-11-15 — Framework contract Phase 6 (inventory-driven validation)

- `FrameworkExpectedInventory.fromOutcomes` mirrors emit-time `PageAccumulator` and test class grouping; wired through `EmitPhase` into `AutomateFrameworkAcceptanceGate` (pre-zip acceptance unchanged).
- `FrameworkContractValidator` inventory mode (`contractVersion=2`): expected page/test presence, orphans/duplicates, page locator/action/assert shape, meaningful test page usage, naming checks; `GeneratedJavaStructure` helper.
- Tests: `FrameworkContractValidatorInventoryTest`, `GeneratedJavaStructureTest`; existing contract/acceptance tests green.
- **Limitations:** regex-based Java structure (not full AST); see `docs/handoff/2026-11-15-framework-contract-phase6-completion.md`.

## 2026-11-12 — Cursor reconcile Policy A (`INTERRUPTED` without `run.started`)

- `CursorAgentRuntime.start()`: reconcile with persisted `agentId`/`runId` accepts terminal sidecar response without `run.started` (Policy A poll-budget `INTERRUPTED`); verify ids when `run.started` is present.
- Tests: `CursorAgentRuntimeTest` (+fake-sidecar hooks), live harness `CursorActiveRunRecoveryLiveIT` / `CursorReconcilePollBudgetLiveIT` (opt-in; evidence in `docs/handoff/2026-11-12-cursor-live-recovery-evidence.json`).
- Production harness flags unchanged default **false**.

## 2026-11-12 — Agent harness Phase 3 (Convert pre-publish integration)

- Convert: `PublicationGuard.beforeLockedPublish`, `AgentAwarePublicationGuard`, `AgentHarnessJobBridge`, zip-extract workspace, `stage-index.json`, SHA-256 `package.zip` identity, locked zip re-check (`ArtifactIntegrityException` on mismatch).
- Coordinator: `INTERRUPTED` + agent/run ids reconcile; sidecar reconcile poll → `INTERRUPTED` (no default `cancelRun`).
- Hardening tests (focused suite **25** pass): lease IT on `ConversionWorker`, double report-persist fail-open, ZIP symlink-as-regular-file, stage-index resilience.
- Flags unchanged default **false** (`delivery.agent-harness.*`, `framework-validation.enabled=false`).

## 2026-11-12 — Agent harness Phase 3 plan revision (architecture only)

- Revised `docs/handoff/2026-11-12-agent-harness-phase3-plan.md`: deterministic task identity, `INTERRUPTED` reconcile transitions, lease remaining-budget mapping, advisory validation, **`PublicationGuard` pre-publish hook** (not post-`EmitPhase`). **No code wiring.**

## 2026-11-12 — Agent harness Phase 3 plan (architecture only)

- Initial `docs/handoff/2026-11-12-agent-harness-phase3-plan.md` — worker integration sketch. Superseded by revision above for hook point and identity model.

## 2026-11-12 — Agent harness Phase 2 finalize (pushed to keel)

- Phase 2 stack through `89479e6` pushed to `keel/main` (implementation, live validation, cross-process lock, active-run recovery).

## 2026-11-12 — Agent harness Phase 2 reliability (cross-process lock + recovery)

- `AgentStoreLockCrossProcessIT` + helper JVM mains prove OS file lock across processes; sidecar execute/cancel/reconcile acquire lock via `CursorSidecarClient`.
- Live `CursorActiveRunRecoveryLiveIT`: kill execute sidecar after `run.started`, recover via `Agent.getRun` poll (not `listRuns` / detached `wait()`).
- Coordinator `RUNNING` retry spawns reconcile sidecar; `agent.mjs` `reconcile`+`runId` path documented in spike notes.

## 2026-11-12 — Agent harness Phase 2 live Cursor SDK validation

- Opt-in live spike (`tools/cursor-agent/live-spike.mjs`) and `CursorAgentRuntimeLiveIT` (`-Dkeel.agent.cursor.live=true`) run against real `@cursor/sdk` 1.0.27; results in `docs/handoff/2026-11-12-cursor-sdk-spike-notes.md` and `cursor-sdk-live-spike-output.json`.
- Fixes from spike: first-run reconcile (no `INTERRUPTED` on missing marker + `PENDING`), tolerate non-JSON sidecar stdout lines, sidecar refuses create when `sdkTouched: true`.
- `AgentStoreLockTest` verifies exclusive `cursor-sdk-state.store.lock`. Harness flags still default **off**; no worker / EmitPhase integration.

## 2026-11-12 — Agent harness Phase 2 (Cursor runtime spike)

- `tools/cursor-agent/agent.mjs` — local `@cursor/sdk` sidecar (NDJSON protocol, isolated `taskDir/workspace`, `sdk-touch.marker` before create, no `shell`).
- `CursorAgentRuntime` + `CursorSidecarClient`; coordinator: workspace copy, reconcile/`INTERRUPTED`, cancel request file, per-parent-job concurrency unchanged.
- Tests: `fake-sidecar.mjs` + `delivery.agent.harness.*` (incl. SDK touch marker). Live Cursor IT disabled by default.
- Flags still default **off**; no worker / EmitPhase integration.

## 2026-11-12 — Agent harness Phase 1 (foundation only)

- Added `delivery.agent.harness.*`: task types, policy, tool registry, context builder, disk `agent-runs/` store, `FakeAgentRuntime`, and `AgentHarnessCoordinator`. **No Cursor SDK wiring** and no job-worker hooks yet.
- Feature flags default off: `delivery.agent-harness.enabled=false` (plus cursor, framework-validation, failure-investigation toggles). Existing Convert/Execute/Hunt/Generate flows unchanged.
- Tests: `delivery.agent.harness.*` (fake runtime end-to-end, routing, policy). Run: `cd keel-portal && mvn test -Dtest=delivery.agent.harness.*`.
- Repo sync: merged `alerts-members-and-cleanup` → `main` and pushed; portal UI polish (login, dashboard, refunds, suites, GFK, local `start-portal.cmd`).

## 2026-11-08 - Real alert senders, per-suite min severity, early-start fix

- Slack, Email and Telegram suite/system alerts now really send (webhook POST, `JavaMailSender`, Telegram bot `sendMessage`). The old stubs only logged. Each channel is off until its enabled flag is true and the address is set.
- Admin → `GET /api/admin/alerts/channels` reports readiness; `POST /api/admin/alerts/channels/{id}/test` fires a synthetic alert to confirm the wiring.
- Per-suite `alertMinSeverity` now actually filters alerts (it can only RAISE the channel floor, never lower it).
- Scheduled runs no longer start 1–2 minutes early: the portal worker and API claim windows are both 0; a slot is due only once `nextRunAt <= now`.
- `scripts/check-mobile-toolchain.cmd` verifies adb and Appium on the portal machine. `scripts/install-dev-tools.cmd` installs Agent Skills, Context7, Ponytail, Graphify, spec-kit and superpowers.

## 2026-11-08 - Scheduled suites: alerts that actually fire, no duplicate or piled-up runs

- Suite runs now alert their channel (the context was lost on the worker thread and channels were never registered). Any failing case alerts (all failed = critical, some = warning); crashed runs and refused scheduled starts (no credits, no workbook…) alert too.
- A schedule slot starts exactly one run: the engine claim API claims the slot, `suites.json` is locked and written atomically, and a slot is skipped while the previous scheduled run is still going.
- Manual runs no longer move the schedule. Slots missed while Keel was down are skipped (`MISSED`) instead of firing late. Cron limited to every 5 minutes.
- Schedules have a time zone and a login profile; the Suites page shows next run, last result, problems, and monitor status (green/degraded/red).

## 2026-11-07 - Mobile runs end to end, story traceability, Arabic UI, page scripts

- **Mobile:** Project → Mobile → *Run on a phone* reserves the phone, starts Appium, hands the session to a mobile runner (`scripts\mobile-runner.cmd`), and frees the phone when the run ends, the runner goes silent (45 s), or nobody claims it (5 min). Lease maintenance frees stuck devices every 30 s. Fixed a start race and the runner launch script.
- **Generation:** prompts moved into the prompt catalog (Admin → Prompts, versioned); every case links to its story (`requirementRef` → `req:` tag) with a *Story coverage* panel on Generate; Arabic/English rules; examples fixed.
- **UI:** Arabic interface (top bar → العربية, right to left, generated `keel-rtl.css`, page headings and top bar translated); browser pop-ups replaced with in-page dialogs; large inline scripts moved to `/js/pages/*.js` (also fixes the Suites page never pre-selecting the project from the URL).
- **Guide:** What's new, story coverage, language, and *Run on a phone*.

## 2026-11-07 - Shared locator policy, Arabic steps, refunds, durable leases, tunnel isolation

- **One locator ranking** (`delivery.locator.LocatorStabilityPolicy`) for bind, live proof, codegen and vision: preferred hook → test id → unique id → unique name → role + name → label → text → CSS/XPath → positional. Per-project **Locator ranking** setting (`stable-first` default, `user-facing-first`). A verified test id is always the primary, even without preferred hooks.
- **Arabic / Unicode steps:** `IntentText` normalizes Arabic (alef/teh-marbuta/yeh, tashkeel, Arabic-Indic digits), strips the article, splits any script, and maps Arabic verbs (اضغط، أدخل، اختر، تحقق…) to intents.
- **Matching:** whole-word matching (no "add" in "Address"), text XPath scored as a real option, O(1) attribute-uniqueness index, ASP.NET/JSF names and build-tool class names treated as brittle and dropped from fallbacks.
- **Codegen fix:** fallbacks were emitted as unused `_fbN` fields calling `By.css(...)` (does not exist in Selenium). Now one `FirstOf.of(...)` field (template `utils/locators/FirstOf.java`), first match wins, fallback wins logged.
- **Vision grounding:** new locators follow the policy; a generated id no longer aborts grounding.
- **Refund requests → Admin → Refunds:** charged jobs that fail for platform reasons open a request automatically; owners can request from the run page. The account owner sees user, project, run, charge, failure category/code/message, diagnostics, timeline, ledger and links (run, evidence, failure pack), then refunds in full/part or rejects with a reason. Idempotent `JOB_REFUND`.
- **Durable jobs:** `LeaseKeeper` renews leases for every worker kind; `JobLeaseReconciler` runs every 60 s; job error codes persist (`error_code`).
- **Tunnel:** relay only on a dedicated host (`TunnelHostIsolationFilter`); all `Set-Cookie` headers survive the relay (`multiHeaders`).
- **Prompts** `2026.11.07`: locator-policy and heal pick describe the new order and bilingual matching.
- **Docs:** `docs/knowledge/` (current truth), `docs/history/` (timeline + dated handoffs), README diagrams.
- Tests added/updated: `LocatorStabilityPolicyTest`, `IntentTextTest`, `RefundRequestTest`, `LeaseKeeperTest`, `TunnelHostPolicyTest`, `TunnelPublicProxyCookieTest`, codegen W2/W3, ranker, prompt version. **Not yet run** — see the session handoff.

## 2026-11-07 - A5 tunnel hardening (follow-up)

- **Enrollment reuse:** `keel tunnel start` caches `token` + `agentSecret` in `KEEL_HOME/tunnel/` (default `~/.keel`) and sends them as `reuseToken`/`agentSecret` to `POST .../tunnel/enroll`; the portal returns the same token (`reused: true`) so the relay URL is stable across restarts. `--re-enroll` forces a new token.
- **Stale index cleanup:** a new enrollment deletes the superseded `tunnel-index/{oldToken}.json` and drops its relay queue.
- **Heartbeat TTL:** `delivery.tunnel.heartbeat-ttl-seconds` (default 60). A tunnel with no recent heartbeat is `stale`: execute jobs fall back to project `baseUrl`, public proxy returns 503, badge says offline.
- **Relay fixes:** waiter registered before enqueue (fast agent completion no longer lost); timed-out requests removed from queue; query string forwarded through `/tunnel/p/{token}/…`; 504 on agent timeout and 503 + `Retry-After` when the queue is full (were 500).
- **Agent fixes:** poll/complete network errors back off instead of killing the loop; local target failures answer 502; `Expect`/hop-by-hop headers stripped (java.net.http rejected them); Basic auth no longer sent as `Bearer Basic …`; loop honors interrupt; 401 stops with a re-enroll hint. `tunnel start` exits 0 instead of falling through to help (2).
- **Local proxy:** empty/204/304/HEAD responses sent without a chunked body.
- Tests: `TunnelEnrollmentDiskStoreTest` (new), `TunnelRelayServiceTest`, `TunnelJobBaseUrlServiceTest`, `TunnelBrokerApiTest` (reuse + query string).

## 2026-11-07 - Adopt A5 (full broker) + heal memory text + CLI jar

- **Tunnel broker:** `GET/POST /tunnel/p/{token}/**` queues HTTP; agent `POST /api/tunnel/agent/poll|complete` with `agentSecret`; enrollment returns `agentSecret` + token index on disk.
- **Execute binding:** `TunnelJobBaseUrlService` overrides project `baseUrl` with `{delivery.tunnel.public-base-url}/tunnel/p/{token}/` when tunnel heartbeat is **active** (`ExecuteRunController`, `SuiteRunService`, `ExecuteWorker`).
- **CLI:** `keel tunnel start` runs local proxy, heartbeats, and relay loop (`TunnelAgentExecutor`).
- **Heal:** `RunMemoryHealHints` in Ollama heal user text + `heal-pick-payload.md` mentions approved run memory.
- **Packaging:** Maven profile `-Pkeel-cli` → `keel-portal/target/keel-cli.jar`.
- Tests: `TunnelRelayServiceTest`, `TunnelJobBaseUrlServiceTest` (plus prior adopt API tests).
- **Enable prod:** `delivery.tunnel.enabled=true`, set `delivery.tunnel.public-base-url` to portal HTTPS origin; run `keel tunnel start --project … --target http://localhost:APP`.
- **Bug fixes:** agent poll `X-Keel-Requested-With`; hop-by-hop headers in `TunnelLocalProxy` + public proxy response.
- **Live smoke:** `scripts/tunnel-live-smoke.ps1` — **PASS** on 2026-11-07 (local jar + python upstream).
- **Known gaps:** no mTLS; long-poll only (no WebSocket); token-in-URL auth (rate-limit at edge); no native `keel.exe`. (Re-enroll on each `tunnel start` fixed in the hardening follow-up above.)

## 2026-11-07 - Slice F: portal schedule dispatch (feature-flagged)

- `SuiteRunService` extracted; `SuitePortalScheduleWorker` + `SuiteScheduleDispatchService` claim due suites and enqueue schedule runs (`delivery.suites.portal-schedule-worker.enabled`, default false).
- `SuiteDiskStore.tryClaimDue` prevents double-claim; `schedule.lastFailure` / `lastClaimedAt` preserved in normalize.
- Handoff: `docs/knowledge/contracts/F-transaction-map.md`, `docs/history/handoffs/2026-11/2026-11-07-slice-f-cursor.md`.

## 2026-11-07 - Slice E: Suites test-case picker

- `/suites` replaces CSV TC entry with a searchable multi-select picker, ordered selection, save/reload membership, and Run disabled when empty or non-workbook IDs.
- New read-only `GET /api/projects/{projectId}/test-case-library` merges workbook + plain-case listings for the picker (`SuiteTestCaseLibraryService`).
- Contract map: `docs/knowledge/contracts/E-contract-map.md`. Handoff: `docs/history/handoffs/2026-11/2026-11-07-slice-e-cursor.md`.
- Offline tests: `SuiteSliceEApiTest`, `SuiteTestCaseLibraryServiceTest`. HOME live picker smoke: **PASS** (2026-11-07, `/suites` picker + structured plan panel).

## 2026-11-02 - Phase 3 AE: suite tag filter + schedule claim hook

- Runner honors suite `tcIds` / tag / suiteId via `SuiteCaseFilter` + read-only `SuiteStore` (`{projectRoot}/suites.json`, Backend SoT).
- Schedule claim: `ScheduleClaimClient` GET `/api/schedules/due` → POST `.../suites/{suiteId}/runs`; `EngineAgent` ticks via `ScheduleClaimPoller`.
- Monitor target → base URL via `MonitorTargetResolver`; fail/flake via bound `AlertChannel` (`SuiteAlertNotifier`, unbound = log only).
- Handoff: docs/history/handoffs/2026-11/2026-11-02-phase3-ae.md.


## 2026-11-02 - Phase 2 AE: failure replay + heal proposals + flaky tags

- On prove/execute failure, write evidence/{tcFolder}/failure/{stepIndex}/ (meta + best-effort screen/network/console) matching Backend interim contract.
- Heal emits pending heal-proposals/{id}.json only (propose-only default); never silent Java rewrite.
- Append laky-tags.json signals (timeout / heal_retry / intermittent / other).
- Flags: keel.phase2.failure.evidence, keel.phase2.heal.proposeOnly, keel.phase2.flaky.emit (defaults on).
- Handoff: docs/history/handoffs/2026-11/2026-11-02-phase2-ae.md.

## 2026-11-02 - Hunt: KnowledgePackReader HTTP (local service) swap

- Prod `KnowledgePackConfig` bean: `NoOpKnowledgePackReader` → `HttpKnowledgePackReader.local` against `KnowledgePackService` (same store as live pack GETs). Missing packs log+skip.
- `HttpKnowledgePackReader.http(baseUrl)` implemented for out-of-process; tests in `HttpKnowledgePackReaderTest`.
- Restart portal after deploy. Handoff: `docs/history/handoffs/2026-11/2026-11-02-hunt-knowledge-ae.md`.
## 2026-11-02 - Hunt: optional KnowledgePack guidance

- Bug Hunter accepts optional `knowledgePackIds` on start (`HuntRequest` / `POST .../hunt-runs`). Empty ids keep legacy Hunt.
- Pack markdown is composed into the hunt brief for planner sequencing; invent-extras stay evidence-gated. Preferred-hook binding unchanged.
- `KnowledgePackReader` + NoOp/HTTP stub (no second FS store). Flag: `delivery.hunt.knowledge-packs.enabled` (default true).
- Handoff: `docs/history/handoffs/2026-11/2026-11-02-hunt-knowledge-ae.md`.
## 2026-10-27 - Repo hygiene

- Ignore accidental root `META-INF/` (jar extract) and local `patches/` scratch; handoff notes tied to commit hashes.

## 2026-10-21 - Portal: delete version ZIPs and live framework

- **Automation tab:** workspace **Members** (anyone with project access) can delete **version ZIPs** (`versions/vN.zip`) when no job is running. **Owner/Admin** can delete individual page/test files under the live framework, and use **Delete current framework** to remove the whole live `framework/` tree (version ZIPs and prove IR stay).
- **Packages** workspace page: Delete on a version ZIP uses the same member rule.
- API: `DELETE /api/projects/{id}/artifacts?path=…` (ZIP vs framework file permissions); `DELETE /api/projects/{id}/framework` (operable only). Rebuild jar, restart, Ctrl+F5.

## 2026-10-20 - Codegen naming: page under test (not Excel title)

- **Page + test class names** come from the **screen under test** (IR `pageName`, usually URL path leaf such as `/login` → `Login_Page.java` and `Login_Test.java`). Excel titles like “Valid login” and words such as valid/invalid are **not** used for class names.
- **Test methods** use a short stem from that page (e.g. `login()`), with `@Test(description = "TC_ID — title")` keeping the Excel title for humans.
- When two cases share the same page stem, the second test class falls back to `TC_IDTest` (same rule as before, but keyed on page stem instead of title).
- **Revise phase** annotates the correct generated file (`Home_Test.java`, not `TC_REV.java`) using the same collision rules as emit.
- **Merge by page stem:** multiple Excel cases on the same screen (e.g. valid/invalid login) emit **one** `Login_Test.java` with several `@Test` methods; duplicate method names get a `TC_ID` suffix.
- **Primary page:** body steps prefer the **last non-login** screen (destination page), not login-heavy step counts.
- Re-run Automate **NEW** or **UPDATE** to regenerate the ZIP. Spec/handoff updated under `2026-10-20-codegen-ux-emit`.

## 2026-10-20 - Codegen UX (cleaner Automate ZIP)

- **Emit:** echo/html-only and `:pageShown` noise no longer appear in generated Java; login steps collapse to `login(...)` when possible; text asserts keep real locators instead of whole-page shortcuts where the IR had a locator.
- **Tests:** page-under-test class names (`Login_Test.java`, `Login_Page.java`); class-level page fields; prerequisites in `@BeforeMethod` via a private helper with local page vars.
- **Test data:** generated calls use `PropertyReader.getProperty` / secrets sidecar. Login placeholders map to **`username` / `password`** (and OTP-style TARGET keys similarly) by default. Set optional **Test data env** on Project → Settings (e.g. `sit`) to prefix keys as **`sit_username`**, **`sit_password`**, etc. Other fields keep TC-scoped keys like `TC_01.body.type_Email.1`.
- Rebuild jar, restart, Ctrl+F5; re-run Automate to regenerate the ZIP. Spec: `docs/superpowers/specs/2026-10-20-codegen-ux-emit-design.md`.

## 2026-10-20 - List-row-first click + extra Treat as kinds

- **List / grid clicks:** when a step names a shared cell (e.g. `with Pending edit status` or `"Pending Edit"`), Keel binds the **first** visible matching table/grid row and clicks that row’s name link (not the status badge). Quote a person when it matters: `Click on "Omar Nabil"`. Two **Login** buttons on the same page (not in a grid) stay unresolved.
- **Treat as:** Owner aliases and built-ins now include **Select**, **Clear**, **Drag**, **Drop**, **Hover**, **Double-click**, and **Right-click** where Execute can run them (`clear`, `select`, `drag`, `hover`, `doubleClick`, `rightClick`). Clear-the-field steps emit `clear`; drag steps need `drag X onto Y`. File upload / drag-and-drop upload stays unsupported.
- Guide `/guide#verbs` and Admin → LLM & API keys dropdowns updated. Rebuild jar, restart, Ctrl+F5. Spec: `docs/superpowers/specs/2026-10-20-list-row-first-and-treat-as-design.md`.

## 2026-10-20 - Admin LLM page actually loads

- Opening **Admin → LLM & API keys** no longer dies with `ERR_INCOMPLETE_CHUNKED_ENCODING`. Thymeleaf was treating `[[` in the page script as an expression, so the HTML never finished and Chat/Vision stayed on “refresh models.” The Ollama URL is now a real default (`http://127.0.0.1:11434`), not only a placeholder; Chat/Vision load from live Ollama (or the last cache if Ollama blips). Rebuild jar, restart, Ctrl+F5.

## 2026-10-20 - Admin LLM page paints verbs and cached models

- **Admin → LLM & API keys** now renders the read-only built-in verb table and the seeded `observe` alias in the HTML (so the block is not an empty header). Chat/vision dropdowns fill from the last saved Ollama tag cache, then refresh quietly. Click **Refresh models** if the list is still stale.

## 2026-10-20 - Guide: authoring verbs

- The Guide now has **Verbs Keel understands**: built-in click / type / assert / skip words, how the Owner adds aliases on Admin → LLM &amp; API keys, seeded `observe` → assert, and that aliases are not a status filter. Execute, Automate, Account, and the Steps column point at that section.

## 2026-10-19 - Authoring verb aliases

- Owner **Admin → LLM & API keys** now lists built-in click / type / assert / skip words (read-only) and lets you add aliases onto those same kinds (Click, Type, Assert visible, Ignore). Default seed: `observe` → assert, so Observe steps wait for visible instead of being skipped. Built-in words cannot be remapped. Max 50 aliases. Save applies to the next Execute / Automate / Generate review / Record bind without a JVM restart after this jar is running.
- Rebuild the portal jar and Ctrl+F5 after pulling. Spec: `docs/superpowers/specs/2026-10-19-authoring-verb-aliases-design.md`.

## 2026-10-19 - Scoped inner hook when a submenu clones the link

- Named click/type/clear/select: if the unique menu row contains one preferred hook that also exists elsewhere (Ant popup copy), Keel now clicks that inner hook scoped under the row (`//li[…]//a[@data-axis-test-id='…']`) instead of keeping the row xpath. Document-unique hooks still become CSS. Two different hooks inside one row still keep xpath. Twin Settings stay unresolved.
- Rebuild the portal jar and Ctrl+F5 after pulling. Spec: `docs/superpowers/specs/2026-10-19-scoped-nested-hook-design.md`.

## 2026-10-19 - Spec: authoring verb aliases

- Approved design (not implemented): Admin shows built-in click/type/assert verbs read-only. Owner adds aliases (Click / Type / Assert visible / Ignore), seeded with `observe` → assert. Spec: `docs/superpowers/specs/2026-10-19-authoring-verb-aliases-design.md`.

## 2026-10-19 - Admin review batch size + browser type

- Owner **Admin → LLM & API keys**: **Review cases per batch** (1–50, default 50) and **Browser** (Chrome, Chrome headless, Edge). Selecting more than 5 cases splits review into those batches (20 with 7 → 7+7+6). Combined preview after the last batch; Accept waits until every batch succeeded. Env still wins for `BROWSER_TYPE` / `BROWSER_HEADLESS`. Next job that opens a window uses the saved browser.
- Rebuild the portal jar and Ctrl+F5 after pulling. Spec: `docs/superpowers/specs/2026-10-19-admin-review-batch-and-browser-design.md`.

## 2026-10-19 - Spec: admin review batch size + browser type

- Approved design (now implemented): Owner sets review TCs per batch and Chrome / Chrome headless / Edge on Admin → LLM & API keys. Spec: `docs/superpowers/specs/2026-10-19-admin-review-batch-and-browser-design.md`.

## 2026-10-19 - Guide: review, leftover quotes, preferred hooks

- The Guide Execute / Automate / Expected sections now describe Review cases before run, Accept saving the library, leftover Expected quotes (filler words stay outside quotes), and inner preferred hooks on named clicks.

## 2026-10-19 - rebuild-portal.cmd

- Repo-root `rebuild-portal.cmd` packages `keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` (skip tests). It does not start the portal. After it finishes, run `start-portal.cmd` then Ctrl+F5.

## 2026-10-19 - Nested hook and quote heading follow-up

- **Execute asserts:** the live heading upgrade now runs on the text Execute actually binds. A leftover that became `Confirm the Upload Transactions list area is visible.` or `Confirm the text "Upload Transactions" is visible` can still become `'Upload Transactions List'` when that heading is the only match. Each part of a compound expected check is upgraded the same way before it is bound. Asserts still never heal, and Execute still does not write the library.
- **Remembered locators:** a saved click, type, clear, or select xpath is checked against the live page before it runs. One unique preferred hook inside that wrapper becomes CSS; otherwise the saved xpath is used.
- **Vision:** when the pointed-at node has exactly one document-unique preferred hook, that node and hook are kept even if a child also has a hook. Children are searched only when the pointed-at node has none, and only when those children yield exactly one hook.
- **Review cases:** dash bullets stay dash bullets after the leftover-quote pass (numbered rows were already numbered again). A quote the model already wrote that still contains a filler word, such as `'Upload Transactions list area'`, is replaced with the filtered name or the quote already used in Steps. A distinctive quote with no filler, such as `'Bill Payment Settlement'`, is left as written.

## 2026-10-19 - Nested preferred hook + leftover Expected quoting

- **Execute bind:** after a named click or type binds a wrapper row (for example an Ant Design `li`), Keel looks inside that node. When **exactly one** site preferred hook from project Settings matches one node in the whole document (for example `a[data-axis-test-id='…']`), the step uses that CSS; zero or two-or-more hooks keep the xpath. Twin Settings buttons stay AMBIGUOUS. Vision grounding uses the same preferred-hook list, including a unique child.
- **Review cases:** a deterministic pass after Cursor or Keel review strips filler words from leftover Expected (`area`, `button`, `label`, `title`, `page`, `section`, `field`, `menu`, `item`, `list`, `visible`, `shown`, `displayed`) and quotes the distinctive name (for example `the Upload Transactions list area is visible` → `'Upload Transactions'`). **Accept & run** still saves the library. **OutcomeKind** is unchanged: quoted text in Steps/Expected still means visible text at runtime.
- **Execute asserts:** before binding a leftover Expected check, Keel may upgrade **unquoted** page-shown leftover (filler stripped first) or a **quoted** leftover (library or review) to a wait on the **live** page title when **exactly one** visible heading or card title contains those tokens (for example `the Upload Transactions list area is visible` or `'Upload Transactions'` → `'Upload Transactions List'`). Zero or two-or-more matches leave the clause unchanged. Asserts still never heal; Execute does not write the library.
- Rebuild the portal jar and Ctrl+F5 after pulling. Spec: `docs/superpowers/specs/2026-10-19-nested-hook-and-quote-heading-design.md`. Plan: `docs/superpowers/plans/2026-10-19-nested-hook-and-quote-heading.md`. Handoff: `docs/history/handoffs/2026-10/2026-10-19-nested-hook-and-quote-heading.md`.

## 2026-10-18 - Add test case and review cases before run

- Project **Test cases** tab: **Add test case** opens the existing editor with the next `TC_` id. **Save** writes the library workbook (an empty library is fine — Keel creates the workbook).
- **Execute** and **Automate**: optional **Review cases before run** sends the selected cases to **Cursor** or **Keel** with Keel authoring rules. **Accept & run** saves the edited cases into the **library** (version history) and then starts the job. Uncheck review to run the library as-is.
- **Accept & run** after an Upload review now starts only the reviewed `TC_ID`s, does not ask for the old Excel file again, and keeps existing CallBefore links when the review omits them.
- Pre-run review is **not** charged. **Execute** and **Automate** still charge their run/framework prices when the job **starts** (after Accept when review is on).
- Runtime **OutcomeKind** is unchanged: quoted text in Steps/Expected still means visible text at execute time.

## 2026-10-18 - Generate AI model list follows live Ollama

- Generate → AI model, Compare two, and Review list chat models from Ollama `/api/tags` (same URL/token as Admin LLM Settings). Vision-like names (`vl`, `llava`, `ui-tars`, `vision`) are omitted.
- Last full tag list is saved as `cachedOllamaModels` in `llm-settings.json`. No cache yet: loader until Ollama answers. Cache present: show it, then “Updating…”. Ollama down: last list plus a short unreachable note.
- Admin Save still sets the install default. Account → Vision stays UI-TARS vs Qwen. Pull a new chat model and it appears after refresh — no code edit.

## 2026-10-18 - LLM Settings clear drops System props; env wins for URL/model

- Clearing Cursor / AgentRouter / Ollama keys in Admin LLM settings now clears the matching runtime System property when process env is unset, so the key drops without a portal restart.
- Non-blank env still wins: publish does not touch the System property when getenv is set.
- Stored Ollama base URL / chat model no longer overwrite in-memory DeliveryPortalProperties when delivery.llm-base-url / DELIVERY_LLM_BASE_URL, delivery.llm-model / DELIVERY_LLM_MODEL, or delivery.generate-model / DELIVERY_GENERATE_MODEL is already set in the environment (same env-wins rule as System properties).
- Secrets stay masked; never logged.

## 2026-10-17 - Heal API keys via Settings publish + optional Ollama Bearer

- Automation Engine resolves CURSOR_API_KEY / AGENTROUTER_API_KEY / OLLAMA_API_KEY through ApiKeySource: env then System property (portal LLM Settings publish) then DEV-only LocalApiKeyLoader (api-keys.local.*) last. Never logs secret values.
- CursorHealClient and AgentRouter invent/final-revise read via ApiKeySource for those three names (other AgentRouter fallbacks unchanged).
- LocalLlmClient sends Authorization Bearer on Ollama /api/chat when OLLAMA_API_KEY resolves non-blank; blank means local tokenless.
- Portal UI for keys stays Backend /admin/llm-settings (no AE portal UI). Midscene / split-engine secret fetch deferred.

## 2026-10-17 - Richer TIE handoff (Ollama + Cursor)

- Both Ollama and Cursor TIE picks share HealPickPayload: whyTied (margin + same-label), scored shortlist (role/tag, label, stability, intentMatch, totals), HTML neighborhood (parent snippets, NEIGHBORHOOD_MAX), optional screenshotPath when evidence already has a file (empty-ok; never invent capture).
- DOM grounding only: pick candidateId from the shortlist; no invent / Thought / written locator on the pick path; no Midscene vision-first.
- Cursor sidecar mode=pick prompt uses quotedName / whyTied / scoredShortlist / neighborhood; slimHtmlExcerpt stays empty on this path.


## 2026-10-17 — OutcomeKind enabled / nameless / toast

- ENABLED/DISABLED no longer map to visible (false-pass when present but disabled). Real execute types enabled/disabled use isEnabled in prove and generated Page asserts.
- Nameless/weak UNKNOWN confirm lines skip-echo (css/html + intent:ASSERT_VISIBLE:unknown-echo) instead of CandidateScorer TIE; body textContains only for distinctive tokens (expanded denylist).
- CONTROL_VISIBLE / FIELD_VISIBLE accept OTP / Username / Logout without requiring the word field/button.
- Unquoted toast lines (error toast is shown, success message appears) bind textContains on the kind token not a shortlist TIE.
- Asserts still never heal. Twin Settings clicks stay AMBIGUOUS.

## 2026-10-16 — Candidate scorer pack

- Outcomes are a checklist: confirm/expected lines are classified before CandidateScorer. Page-shown and "is clicked" no longer TIE the shortlist.

- Bind chooses from a scored DOM shortlist (CandidateScorer / BindDecision CLEAR|TIE|EMPTY).
- Catalog fingerprint ignores class-hash churn; write-back only after a live click (commitAfterProof).
- Ollama/Cursor pick only on TIE (max 5); ASSERT_VISIBLE still never heals.
- Fixture gate: CandidateScorerFixtureTest + bind-quality.json.
- Review fixes: ProvePhase records bind/ollama/vision/heal into bind-quality.json (flush in finally); Ollama TIE pick gets slimHtml neighborhood; catalog preferStable uses CandidateScorer.stability (testid over raw id).
- bind-quality.json holds **job totals** across TCs (not last-TC-only). proveOne still clears pageCatalog; it no longer zeros BindQualityMetrics.
- Execute: “Username is accepted” after a fill binds that field (input over a same-name hint; spent fill wins a TIE). Excel is not rewritten. Asserts still do not heal.


This is the **public story of what shipped**. When you publish, add a dated section here first. Write for everyone: what changed, why it matters, how to use it, and what still does not work.

The **full product** (every surface, not only the last week) is [`docs/PRODUCT.md`](docs/PRODUCT.md).

Internal design notes stay under `docs/superpowers/`. Engineers can start from `docs/ENGINEERING_HANDOFF.md`.

## 2026-10-16 — Smaller run price, clearer history, colored attention

**Surfaces:** **Execute**, **Automate**, **Bug Hunter**, **project Test cases**, **Overview**.

The **This run** price is a compact bar (count × unit on the left, total credits on the right), not a large card. The same bar is on Automate and Bug Hunter. Design mockups on Execute start collapsed, with a teal row you click to open. Version history copy is plain: Current (not “head”), Edited / Recorded / Generated instead of `CASE_EDIT`, and **Restore this version** sits on the same line as the filters. New Record and edit saves store your email as Author; old rows that stored `TC_3` show as —. Failed attention cards are red, blocked ones amber. Rebuild the portal jar and Ctrl+F5. Known defect: history rows saved before this still have a test-id author in the file; only the label is cleaned up.

## 2026-10-16 — Expected after each step, and a page locator catalog

**Surfaces:** **Execute**, **project test-case editor**.

Each click/type is followed by that row’s Expected cell when the cell is not empty. A leftover Expected on a blank “Step action” row folds into the last real step — there is no assert without a step. Numbered lists (`1. 2. 3.`), dash bullets, and outcome `and` split; “expands and sub-menu” stays one check. A failed assert stops; it does not start a 2-minute locator heal. Locators for a page are catalogued once and reused until the URL or the DOM fingerprint changes (navigation or a same-page expand). If bind misses, vision may confirm a catalog row or add a control it saw that the catalog did not. Rebuild the portal jar and Ctrl+F5. Known defect: Excel copy must still match the screen (`Upload Transaction List` vs `Upload Transactions List` still fails the assert, on purpose).

## 2026-10-16 — Overview chart, run progress, and missing screenshots

**Surfaces:** **Overview**, **Runs**, **Execute results**, **Record**.

The outcomes chart stacks proven vs blocked with rounder bars. Recent runs show a progress bar, a short status word (Done / Failed / Running), and Status / Results as buttons. Execute results now find screenshots stored under the per-run folder (`TC_01__occ_1`). A blocked bind also shows the heal screenshot. The Record name field no longer says “Describe it like you would to a colleague.” Rebuild and restart the portal; Ctrl+F5.

## 2026-10-16 — Named labels are clickable

**Surfaces:** **Execute**.

A step such as `Clicks "Support" in the sidebar` can land on a label that is not a button, link, or menu row. The quoted name is enough. The same rule applies in a toolbar, a dialog, a table, or anywhere else on the page. A wrapper whose text is longer than that name is not clicked. Two elements with the same name stay unresolved. Restart the portal from a new build before the next run; the copy that is already running does not have this yet.

## 2026-10-16 — Live credit total

**Surfaces:** **Bug Hunter**, **Execute**, **Automate**.

The cost sits in a card above the start button. Bug Hunter updates when iterations, cycles, or actions change. Execute and Automate update when you switch to the project library and change how many test cases are included. A workbook upload shows the price of one test case until the file is read at the start of the job. Hard-refresh if you still see the small grey sentence.

## 2026-10-16 — Dashes and dots on Execute

**Surfaces:** **Execute**, and a few labels that used the same broken characters.

A dash (`—`) and a middle dot (`·`) had been saved with the wrong encoding, so the page showed `Ã¢â‚¬â€` and `Ã‚Â·`. They read as punctuation again. The first row in **Credential profile** is the empty choice “— Select profile —”. It is not an account. Hard-refresh if the old letters are still on screen.

## 2026-10-15 — Project section tabs

**Surfaces:** a project page.

Summary, Test cases, Packages, and Settings sit in a short pill row. The open section is the filled pill, in light and in dark. Hard-refresh if the old underline is still on screen.

## 2026-10-15 — Prices per unit, and only the account owner can change them

**Surfaces:** **Pricing** (`/pricing`), **Prices** (`/admin/pricing`), **Administration**, **Roles**, **Execute**, **Automate**, **Bug Hunter**, **Record**, **Guide**.

### What you can do

- Open **Pricing** and read the price of one unit for each service. The table cannot be edited.
- As the account owner, open **Admin → Prices** and change the service name, **Charged as**, **What it covers**, and the price of one unit. Saving applies that to every account.
- A run charges the Execute price once per test case. A framework build charges the Automation price once per test case, including a build from a recording. A manual-only row is not charged.
- Bug Hunter charges once per step. A step is one click or one thing typed. The hunt page shows how many steps this run may try before you start.
- Writing test cases stays one price for the whole writing job.
- The service names on Pricing are **Generate**, **Execute**, **Automation**, and **Bug Hunter**.

### Why

A flat price treated a one-case run and a large run as the same job. Hiding an admin button was not enough to keep prices, users, and credit grants with the account owner.

### How to use it

1. **Pricing** shows the current unit prices.
2. The account owner opens **Prices**, edits the name, Charged as, What it covers, and the credits for one unit, and saves.
3. Start a run, a framework build, or a hunt as usual. Keel multiplies the unit price by the cases or steps in that job.
4. If 15 was the old price of a whole Bug Hunter run, lower the Bug Hunter unit. The default hunt may try 80 steps (2 × 8 × 5), so 15 each costs 1,200.

### What we did not change

- Product credits stay on. Grants are still manual.
- Writing test cases is still one charge for the job, not one charge per story.
- Domains stays available to a user whose role is Admin.

### Known limits

- The server refuses a non-owner, including a role named Admin, on prices, user management, credit grants, and roles. The check is the stored Owner role, not a hidden button.
- A default Bug Hunter at 15 credits per step is expensive until the owner sets a smaller unit.
- This note is written while those prices can still be edited again.

## 2026-10-15 — Record a test, then build the framework

**Surfaces:** **Record** (`/record`), **Account** alerts, **Generate**, **Execute**, **Automate**, **Guide**, project cards, roles.

### What you can do

- Open **Record** from the sidebar and click through the project website in Chrome. Keel writes the steps into the project library.
- Use the Keel bar inside that Chrome window: **Steps**, **Check this** (confirm visible text), and **This page** (confirm the address).
- On the last step, choose **Build the framework from this recording**. Keel writes the Selenium framework from the locators it already captured. Chrome does not open again.
- Turn on Telegram, email, or Slack on **Account**. Keel sends a message when a run fails or finishes with a block, after a confirmation send succeeds.
- See extra Generate, Execute, and Automate fields already open. Project cards keep Open, Archive, and Delete. Roles use one multi-select for permissions.

### Why

A saved recording was only English steps. Automate had to open the site again to find locators. Alerts were saved in the browser and never sent.

### How to use it

1. Create a project and set its website.
2. **Record** → name the test → start Chrome → click through the site. Use **Check this** on the text that must be true.
3. Stop, leave **Build the framework** checked, and save. Watch the job under Runs. The download appears under Code packages.
4. On **Account**, turn on a channel only after that channel is configured. Saving sends a confirmation first.

### What we did not change

- Product credits stay on. A framework build is an Automate job and spends credits.
- The normal Automate path still opens Chrome and proves the case.
- Bug Hunter findings still do not join the library by themselves.
- The engine dead-man alert stays off and log-only.

### Known limits

- Recording is web only. An Android project is refused.
- Passwords typed in Chrome are not stored. A sign-in note tells the later run to use a project login.
- A click with no stable locator stays as written English and is left out of the framework.
- One recording at a time, for at most 20 minutes.
- This machine has no Telegram token and no mail server, so those switches stay off until they are configured.

**Plan:** `docs/superpowers/plans/2026-10-15-record-to-framework.md`.

---

## How to add a publish note

1. List every user-visible change (screens, buttons, job behavior), not file names.
2. Group by **what a person can do now**.
3. Add a short **How to use it** for each group.
4. Add **What we did not change** and **Known limits** so nobody assumes a silent extra feature.
5. Point at the git commit(s).

---

## 2026-09-11 — Bug Hunter quality pass 2

**Surfaces:** **Bug Hunter** (`/bug-hunter`), shared `JobLoginService` login CSS.

### What you can do

- Pick a **credential profile** on Bug Hunter — the planner sees `$TARGET_USERNAME` / `$TARGET_PASSWORD` / `$TARGET_OTP` tokens only (password resolved at execute time, never written to prompts or pack files).
- Choose **Open site only** when you do not want auto-login.
- Benefit from **preferred-hook-aware login** when the project has locator hook attributes configured.
- Get fewer duplicate bugs: **fingerprint dedupe** plus an optional end-of-hunt **triage** pass (`bug-dedupe.json`, `bug-triage.json`).
- Hunts auto-**advance strategy** when happy-path is stuck on login; login-feature hunts no longer get false “unexpected redirect to login” oracle noise.
- Planner may use **`restart_browser`** (capped) and post-click **settle waits** for more reliable asserts.

### Why

Live hunts on Axis-style apps were noisy (dozens of duplicate bug rows) and often failed login because hooks and credential tokens were not wired through to the planner/executor.

### How to use it

1. Set project **base URL** and a **credential profile** (or Open site only).
2. Configure **preferred hooks** on the project if your app uses custom `data-*` test ids.
3. Start a hunt; download the pack and review `bug-triage.json` if triage ran.

### Known limits

- End triage adds one planner call; soft-skips on invalid JSON (keeps deduped list).
- Auto-login success may skip the login page — for login-page testing prefer Open site only or `restart_browser`.
- Phase 3 two-pass DOM neighborhoods still not built.

**Specs:** `docs/superpowers/specs/2026-09-10-bug-hunter-quality-pass2-design.md`.

---

## 2026-09-11 — Precision authoring engine (Keel vs Cursor)

**Commit:** `d4927cc` — *feat(authoring): add Precision engine with review fixes and pipeline toggle*  
**Surfaces:** **Automate**, **Execute**, **Generate → All in one**, CLI, job status API.

### What you can do

- Choose **Keel** (default) or **Precision (Cursor)** when starting Automate, Execute, or the All-in-one pipeline.
- Precision uses Cursor `groundRank` (multimodal) plus an optional one-shot `solve` per intent, with automatic **Keel fallback** when the API is missing, the cap is hit, or bind fails.
- See per-job fallback on the status page (`PRECISION_FALLBACK` banner) and in IR drafts (per-TC precision metadata).
- Run batch conversion from CLI: `--authoring-engine precision`.

### Why

Keel bind + heal is fast and deterministic but can struggle on dense or dynamic UIs. Precision trades API calls for stronger initial grounding while keeping Keel as the safety net.

### How to use it

1. Set `CURSOR_API_KEY` and start with `start-portal-with-cursor-heal.bat` (or equivalent env).
2. On **Automate**, **Execute**, or **Generate → All in one**, pick **Precision (Cursor)**.
3. Watch the job status — if Precision could not bind an intent, the banner explains Keel fallback.
4. Server-wide off switch: `delivery.authoring.precision.enabled=false` (portal warns when Precision is selected).

### What we did not change

- **Keel remains the default** for every surface unless you opt in.
- Generate / Compare / Bug Hunter planner paths are unchanged.
- Bug Hunter and Hunt changes in the working tree are **not** part of this publish.

### Known limits

- ~50 Cursor calls per job by default (`delivery.authoring.precision.max-calls-per-job`).
- Precision does not replace heal cascade — it improves the **first** bind; failures still flow through Ollama/Cursor invent/recovery (within the shared cap on Precision jobs).
- No Claude / Anthropic models (project rule).

**Specs:** `docs/superpowers/specs/2026-09-11-authoring-precision-engine-design.md`, fix plan `docs/superpowers/plans/2026-09-11-precision-engine-fixes.md`.

---

## 2026-09-10 — Bug Hunter (exploratory) + quality loop

**Commits:** `793a7a6` — *feat(hunt): ship Bug Hunter with page map, coverage, strategies, oracles* (plus docs follow-up).  
**Surfaces:** new top-nav **Bug Hunter**, job kind `HUNT`.

### What you can do

- Start an exploratory hunt from selected library TCs + optional user story (Ollama or Cursor planner).
- Cap invent scenarios, cycle ceiling, and actions per cycle (default 5; wait defaults to 5s).
- Choose DOM mode: Auto page map / map only / full slim.
- Optionally run the strategy playbook (happy → empty → boundary → abuse → session → invent).
- Download a hunter pack ZIP: bugs, candidate scenarios, per-cycle evidence (prompt/response/screenshot/page map), steps journal, coverage map.

### Why

Execute/Automate prove written cases. Bug Hunter **probes** for breakage and invents review-only edge scenarios without writing the library.

### How to use it

1. Set `delivery.dry-run=false` for a live browser hunt (dry-run still produces a stub pack).
2. Open **Bug Hunter**, pick project + TCs, start.
3. When the job completes, download the pack from Status / jobs — review bugs and candidates yourself.

### What we did not change

- No auto-merge of candidate scenarios into the generated library.
- Automate prove / Execute regression flows unchanged.

### Known limits

- **Phase 3** (two-pass DOM neighborhoods) not built — only if AxisPay still exceeds context after map + slim.
- Oracles: HTTP errors / asserts / alerts; not yet blank-main, unexpected URL, or full `net::ERR_*` promotion.
- Live AxisPay smoke still a human follow-up after portal restart.
- Screenshot remains one per cycle at cycle start (not after every action).

**Specs:** `docs/superpowers/specs/2026-09-09-bug-hunter-design.md`, `docs/superpowers/specs/2026-09-10-bug-hunter-quality-design.md`.

---

## 2026-09-07 — Project library, honest runs, optional Cursor pre-run

**Commit:** `abc06ce` — *Keep generated TCs editable in the project and stop silent invent/soft-pass.*

This release is for people who generate cases, keep them on a project, then run Execute or Automate without the product inventing data or calling a failed job “done.”

### 1. Generated cases stay on the project

**What you can do**

- Open a project → **Test cases** → **Generated library**.
- See the saved generated workbook (`latest.xlsx`).
- Edit a case (same save path as before).
- Delete selected cases. Keel will not delete the last remaining case.

**Why**

Generated TCs used to live mainly as a file you re-uploaded. You can now treat the project as the library and come back later.

**How to use it**

1. Generate (or import) until the project has a generated workbook.
2. Open the project **Test cases** tab.
3. Edit or delete in **Generated library**.
4. On Execute or Automate, pick those cases instead of starting from a blank upload.

**Design note:** `docs/superpowers/specs/2026-09-07-project-tc-library-design.md`

### 2. Execute / Automate: pick library cases and mix an upload

**What you can do**

- Use the **project library** as the source.
- Select one or more `TC_ID`s. Selecting none means **all** library cases.
- Optionally attach an `.xlsx` or `.csv` at the same time (**mix**).
- If the same `TC_ID` exists in both, **the upload wins**.

**Why**

You can rerun a subset, or patch a few cases from a file, without replacing the whole library every time.

**How to use it**

1. Open Execute or Automate.
2. Leave the library selected (or pick specific IDs).
3. Optionally choose a file to mix in.
4. Start the job.

### 3. CSV download can be uploaded again

**What you can do**

- Download the generated CSV from Generate.
- Upload that CSV on Execute or Automate (file picker accepts `.xlsx` and `.csv`).
- Keel converts Keel CSV into a workbook internally.

**Why**

The download used to be rejected because the run pages only accepted Excel.

### 4. Optional Cursor review right before a run

**What you can do**

- On Execute or Automate, check **Review selected TCs with Cursor before run**.
- Inspect findings and the proposed CSV.
- **Accept & run** starts the job with the reviewed suite.
- **Cancel** does not start the job.

**Why**

Generate already had **Review with AI** (Cursor or Ollama). That button is **manual** and only on Generate. It never ran automatically before Execute/Automate, which is why broken TCs could go straight to the browser.

This new checkbox is **propose-only**. It reviews the exact suite that job will run.

**How this differs from Generate Review with AI**

| | Generate **Review with AI** | Execute/Automate **Review before run** |
|---|---|---|
| When | After a workbook is saved | Right before a job |
| Providers | Cursor or Ollama | Cursor |
| Accept | Saves into the project workbook | Uses the proposal **for that job only** |
| If you skip it | Nothing happens | Job starts as usual |

**Known limit:** Accept & run does **not** write the proposal back into **Generated library**. To keep the reviewed text, edit/save on the project page, or Accept on Generate Review with AI.

If Cursor is down and the box is checked, uncheck it to run without review.

### 5. Generate does not invent TestData unless you allow it

**What you can do**

- Generated TestData stays blank or `<PLACEHOLDER>` unless you explicitly allow inventing values.
- Login username/password steps use `${TARGET_USERNAME}` and `${TARGET_PASSWORD}` (project credentials), not fake names.

**Why**

Keel was filling empty cells with faker data (for example a random username). Login then typed those values, failed, and still looked “successful” in some cases.

Heal fillers can still invent when they are repairing a live step. That is not the Generate column invent path.

### 6. Login cases run as the case, not a hidden prelude

**What you can do**

- A case that already has login steps (type user, type password, click login) is executed as written.
- Keel no longer injects a separate login prelude and then strips those body steps.

**Why**

Prelude + stripped body made login TCs type the wrong fields or skip the case the author wrote.

### 7. Jobs that cannot prove anything fail honestly

**What you can do**

- If every case is still `TODO` / not proven, the job status is **FAILED** (`ALL_CASES_TODO`).
- You do not get a green **COMPLETED** with `0` passed and `1` TODO.

**Why**

A white page or unresolved host used to finish as completed. That looked like a pass.

### 8. Locator memory does not treat every click as login

**What you can do**

- After login, clicks on cart / checkout / etc. are remembered for the **live page**, not as login locators.
- Auth-only locators (`login-button`, `user-name`, `password`, …) are rejected for non-login intents.

**Why**

SauceDemo (and similar apps) stored `login-button` against later clicks, so the next run clicked login again.

### 9. Login page wait before typing

**What you can do**

- After opening a login URL, Keel waits for the document to be ready and for a password field (about 12 seconds) before typing.

**Why**

SPA / slow pages opened a blank white window, the job typed too early, then the browser closed.

### 10. Execute Results button

**What you can do**

- From a recent Execute run, **Results** scrolls to and loads that run’s evidence.
- Empty or error states say so clearly instead of looking like a dead button.

---

## What this release did not change

- LLM Wiki (still parked).
- No Claude / Anthropic models.
- Generate **Review with AI** still exists and is still the way to **save** a reviewed suite into the project workbook.
- No version history of the library (one current generated workbook).
- Cursor pre-run is opt-in. Unchecked = no review.

---

## Known limits (read before you demo)

1. **Accept & run** is for that job only. The project library stays as it was until you edit it or Accept on Generate.
2. Cursor pre-run can still rewrite TestData in the **proposal**. Runtime invent-on-empty is what we stopped.
3. Convert jobs that **FAILED** may still offer a download if a ZIP was left behind. Treat that ZIP as incomplete.
4. After UI or prompt changes: **restart the portal** and **hard-refresh** the browser.

---

## Earlier published work (short)

These are already on the public history. Details live in older specs under `docs/superpowers/specs/`.

### 2026-09-06 — Generate authoring preflight review

**Commit:** `fbb1a28`

- Optional **Review with AI** on Generate (Cursor or Ollama).
- Findings + proposed suite; **Accept** saves the workbook, **Discard** leaves it.
- Banner when the quality gate finds issues; review never starts by itself.

### 2026-09-02 — Heal recovery written back to the workbook

**Commit:** `93e86c2`

- Successful heal **recovery** can patch the generated workbook (for example leave-empty + blank TestData) so the next run matches what the page needed.

### Portal publish — Generate, Automate, Execute

**Commit:** `2fc6832`

- Public portal surfaces: Generate, Automate, Execute, heal recovery, and product docs (`README.md`).

---

## Suggested demo path for this release

1. Restart Keel. Hard-refresh Generate, the project page, Execute, and Automate.
2. Generate a suite. Confirm TestData is blank / placeholder unless you allowed invent.
3. Open the project → **Test cases** → **Generated library**. Edit one row. Delete nothing required for the demo except a spare case.
4. Execute: select two library IDs, no file. Run.
5. Execute again: mix a CSV that overrides one `TC_ID`. Confirm the upload row is what ran.
6. Optional: check Cursor review, read findings, Accept & run. Confirm the library file did **not** change unless you saved it on the project.
7. Upload a Generate CSV on Automate and confirm it is accepted.
8. If you have a login-only case, confirm the job types `${TARGET_*}` credentials and does not invent a fake user.
