# Architecture and technical decisions

All names marked **new** are planned. Existing class names below were inspected in the repository on 2026-09-27.

## 1. Boundaries and repository layout

Keep the Spring/Thymeleaf portal, library revisions, tenant membership, durable job claims, budget/provider policies, publication lock and artifact authorization. Extend `ProjectRecord`/`ProjectEntity`, `JobRecord`/`JobEntity`, their schema patches and `PortalStore` with platform-aware references. Existing records default to WEB.

New platform-neutral portal code lives under `delivery.mobile` for configuration, admission, manifests, DTOs, authorization and mobile result ingestion. It must not import Appium classes. A small `mobile-contracts/` Java 21 Maven artifact defines only versioned DTO/codec contracts. A standalone `mobile-runner/` Java 21 build depends on that artifact and owns Appium, its compatible Selenium version, extraction/binding, execution and mobile emission. Build these through explicit scripts or a separate aggregator; do not convert the current root application into an aggregator as an incidental change.

The existing `PrivateRunnerAgent`, `PrivateRunnerApiController`, `PrivateRunnerRules` and `PrivateRunnerArtifacts` supply the outbound claim/heartbeat/input/artifact pattern. Add platform capabilities and a dispatcher; the mobile dispatcher starts a supervised worker JVM using its own classpath. Do not run Android commands inside the browser `WebDriverFactory`, `TcExecutionService` or the Spring request thread. Refactor reusable transport into a dependency-neutral component rather than importing the whole root runtime into the mobile worker.

New worker components:

- `AdbDeviceDiscovery`, `ApkInspector`, `DeviceLeaseGuard`, `AppiumSessionFactory`.
- `MobileSnapshotService`, `MobileCandidateExtractor`, `MobileLocatorValidator`, `MobileBinder`, `MobilePrecisionBinder`, `MobileVisionResolver`.
- `MobileExecutionService`, `MobileAssertionService`, `MobileContextResolver`, `MobileGestureService`.
- `MobileAuthCoordinator`, `MobileSetupPlanner`, `MobileSecretResolver`.
- `MobileIrCodec`, `MobileProofStore`, `MobileCodeWriter`, `MobileFrameworkPackager`.

Reuse *contracts* from `TcIdentity`, `CodegenDataKeys`, `CaptureCompare`, library snapshots and failure reporting; extract dependency-light shared utilities only with web regression tests. Do not duplicate a changing assertion algorithm without parity fixtures. Existing web `ProvenStep`, `ProvenStepCodec`, `TcDraftStore`, `CodeWriter` and `FrameworkPackager` keep their web routing. Add explicit platform dispatch before these readers/writers, rejecting cross-platform payloads.

## 2. Toolchain and build isolation

Java 21, Maven, TestNG and Allure remain the application/framework conventions. Proposed qualification baseline: Appium 3, UiAutomator2 6+ and Android API 26+. This is a minimum target, not a promise that every OEM/API combination has been tested. First supported host: Windows laptop; Linux runner is a later portability qualification using the same contracts.

The official Java-client matrix currently lists Appium Java client 10.1.1 with Selenium 4.42.0/4.43.0. Qualify **10.1.1 + 4.43.0** in the isolated mobile runtime and customer template first. Record exact Appium server/UiAutomator2/Node/npm/ADB/SDK/Chromedriver versions in `mobile-toolchain.lock.json` during M0. Pin Maven transitive Selenium modules; ban version ranges and `LATEST` in delivered builds. If the matrix changes before implementation, update the lock through an explicit compatibility test, not an automatic upgrade. See [research](research.md).

Dependency installation is a future operator step, never an automatic side effect of opening a project or running a test. Doctor/preflight reports missing prerequisites without changing PATH, services, drivers or SDKs. No requirement to install Android Studio if supported command-line SDK tooling is already present.

## 3. Device discovery, app setup and admission

Runner executes allowlisted commands through argument arrays and bounded `ProcessSupervisor`-style deadlines. Always supply the exact ADB serial. Parse `adb devices -l`; expose unauthorized/offline states and model/API/ABI. Never choose the first available device when the selected serial disappears. Friendly device name is display-only.

Uploads stream to tenant/project quarantine with a configurable size limit (proposed 500 MiB), SHA-256, content validation and bounded manifest inspection. Use SDK `apkanalyzer` or a qualified `aapt2` fallback; never execute uploaded content on the portal host. Extract package/version/min SDK/launcher candidates/signing identity when available. Ambiguous launcher requires a choice; distinguish launch activity and wait activity. Disallow path traversal, symlink escapes and remote APK URLs. Refuse app ID mismatch, incompatible API/ABI, unapproved downgrade/signature replacement and install failures with useful diagnostics. Do not silently uninstall to make an install succeed.

Installed-app mode resolves a package on the selected device; require explicit package/activity override if resolution fails. Query version code/name and record observed identity with `verification=OBSERVED`, not uploaded-byte proof. App package is part of a profile revision; changing packages creates a new app revision and invalidates prior mobile bindings/proof.

Admission checks membership/operate role, project platform, supported runner capability, device ownership, app/profile revisions, secret presence, provider budget, prerequisite graph and APK readiness. Freeze all inputs before enqueue. Initial busy policy is explicit `409 DEVICE_BUSY`, not an unbounded invisible queue. Acquire the device lease atomically before a runnable job is admitted; roll back budget/lease if admission fails.

## 4. Reservation, execution and cleanup

Unique device binding: `(hostId, adbSerial)` may belong to only one tenant at a time. A local host lock uses this physical identity, not a tenant-specific directory, so different tenants cannot obtain separate locks for the same phone. Raw serial is restricted to authorized operators; UI can display a shortened value.

Lease states: AVAILABLE → RESERVED → ACTIVE → RELEASING → AVAILABLE; errors/disconnect/uncertain cleanup → QUARANTINED. Admission increments a monotonic fencing token; claim binds device + attempt ID + token + input hash. Every command checks the local lease/cancel guard. Proposed heartbeat 5s, expiry 30s, with bounded HTTP/ADB/Appium calls. Expiry stops new commands immediately; it does not release the physical device until teardown is confirmed.

Worker owns session ID, allocated system/Chromedriver ports and child PIDs. Use a per-job loopback Appium service for portal-managed runs, with pinned executable and allowlisted configuration. Customer ZIPs connect to a configured operator-managed Appium URL. Do not kill unrelated servers/Chrome/ADB processes or run `adb kill-server`. Reject insecure broad server flags and arbitrary `mobile: shell`/model-generated commands. Do not expose Appium or ADB directly to the hosted portal/internet.

Lifecycle: reserve → preflight → verify/install chosen build → create session → apply explicit permission policy → prepare auth/start state → execute sequential cases → capture result → quit → stop owned server → acknowledge release → publish. In-flight device/session loss is `INTERRUPTED_UNCERTAIN`; preserve evidence and fail the job, without automatically replaying the business action. Before device activation an abandoned admission may safely be requeued after reconciliation. Unknown worker termination quarantines the device until recovery proves the old session/process cannot act.

Cancellation is checked between steps and during bounded waits. Terminate owned worker/server on deadline; revoke publication eligibility with existing attempt fences. A late upload from an old attempt is rejected. `finally` cleanup must run after login/setup failures and assertion failures as well as successful cases. Teardown failure is visible and blocks device reuse.

## 5. How Keel fetches and chooses locators

1. Capture current context, package/activity, orientation/window size, Appium XML page source and screenshot, with sequence/time/geometry metadata.
2. Parse XML with DTD/external entities disabled, depth/node/byte limits. Normalize resource-id, content-desc, text, class, bounds, enabled/displayed/clickable/focusable/selected/password flags and parent relationships. Preserve original selector bytes and Unicode.
3. Create candidate identities within this snapshot. Coalesce multiple selectors for the same live node; do not conflate two distinct controls with identical labels.
4. Try the user's explicit typed locator first; validate allowed strategy/context and live uniqueness/action suitability. If it fails, record `USER_LOCATOR_REJECTED` and the reason before trying ranked fallback. Never execute arbitrary Java or UiSelector code supplied by a model.
5. Prefer stable unique accessibility IDs/resource IDs; use contextual exact text + role/class/container next, relative XPath last before vision. Penalize dynamic values and generated indices. A class or Android `name` attribute alone is not a stable unique identity. Absolute/positional XPath is not an automatic escape hatch.
6. Re-query the selected locator immediately before acting; require exactly one eligible match unless the assertion explicitly concerns a collection. A successful lookup is not proof that the intended business action happened.
7. Refresh after every action; navigation, scroll, permission dialogs, keyboard and orientation invalidate the snapshot. Cache selector candidates, not WebElement handles. Store memory by tenant/project/app revision/screen/context/locale; mobile v1 never uses cached proof as a fresh PASS.

Flutter limitation: UiAutomator2 sees the exposed Android accessibility tree, not Flutter widget keys or the Dart widget tree. Flutter semantic identifiers can surface as Android resource IDs; verify the actual shipped APK. `Semantics(label: ...)` may expose a content description, but merged/excluded semantics can combine or hide targets. Treat the supplied naming PDF as naming guidance, never evidence that every element exists in the hierarchy. For a merged PIN keypad, do not invent ten child locators; use independently exposed nodes, validated vision, or report the missing semantics.

Native strategy set: accessibilityId, resourceId, typed text+class/container, restricted generated UiSelector expressions, relative XPath. WebView strategy set: existing validated CSS/id/text/relative XPath rules. Never accept CSS in NATIVE_APP. Text selectors escape apostrophes/quotes/Arabic correctly. Rank ties between distinct viable controls fail as ambiguous.

## 6. Actions, assertions and state

Actions are typed enums: TAP, TYPE, CLEAR, LONG_PRESS, SCROLL_UNTIL, SWIPE, BACK, HIDE_KEYBOARD, LAUNCH, RESTART, SWITCH_CONTEXT. Use supported Appium gesture extensions or W3C actions through a typed allowlist. Swipe coordinates are bounded normalized geometry resolved at execution, not screen-resolution constants. Scroll-until caps swipes (default 5), duration and repeated-screen/no-progress attempts (default 2). No infinite scroll/heal loop.

Waits reacquire elements, retry stale/not-yet-present states within a deadline, and surface selector/transport/session errors. Absence checks examine every match. TYPE resolves secret/testdata references just before sending; CLEAR and replacement are explicit. HIDE_KEYBOARD checks whether a keyboard is present; failure to dismiss an obstructing keyboard is a real action error. RESTART terminates/activates only the selected package; it does not clear data.

Assertions: visible/notVisible, exactText/textContains, enabled/disabled, selected/unselected, captureText/compareCaptured and explicitly configured signedOut predicates. Capture scope is one case invocation including its prerequisites; setup assertion flushing does not erase captures. Extraction requires one unambiguous value; equality does not confuse KLA-1001 with KLA-10010. No inferred sign-out from an overlay, inferred payment from a tap, or weakening exact expectations during codegen.

Failed assertions make the case fail; unexecuted dependents are BLOCKED, never PASS. Preserve TODO/PARTIAL in package mapping. Continue independent cases only after their own auth/navigation setup succeeds; stop on device/session loss. Optional fail-fast stops after the first failure. No automatic retries of an uncertain mutating action, even for model/transport errors. Read-only lookup retries are bounded separately.

## 7. Authentication profiles and prerequisites

Immutable auth-profile revisions store login TC revision, session predicate, optional logout/setup TC, input declarations and logical account identity. Secrets are references, not strings inside profiles. OTP and PIN values are supplied before each run when the login TC requires them, including a static OTP; profiles store their declarations, not reusable values. Other login inputs may use an explicitly selected protected credential reference. Never invent or silently reuse an OTP from a previous job.

The protected signed-in predicate distinguishes the requested account where observable. If identity cannot be verified, ensure-logged-in performs the declared fresh-login sequence and verifies a configured success predicate; it must disclose weaker identity evidence, not claim a known account. A session check has three outcomes: signed-in-as-requested, signed-out/different-account, or unknown/error. Unknown/error does not mean permission to proceed.

- ENSURE_LOGGED_IN: check; if wrong account, run logout and verify signed out; run login when needed; flush setup assertions and verify session/account.
- FRESH_LOGIN: run explicit logout or configured reset/setup, verify logged out, run login and verify. If no safe logout/reset is configured, block with `AUTH_RESET_UNCONFIGURED`.
- LOGGED_OUT: establish and verify signed out; never execute login implicitly.
- LOGIN_UNDER_TEST: skip automatic auth. Test body owns the login flow and input use.

Compile Call-before as a DAG with cycle/missing-reference validation; first-seen topological order, shared prerequisites once per leaf invocation. Each leaf gets its own setup closure, including a single selected TC. Conflicting authentication profiles in one closure require explicit transition/setup; otherwise reject. Run order is app baseline → auth policy → start-state setup → prerequisites → leaf. Prerequisite dependencies run before their bodies. Negative login cases cannot serve as successful authenticated setup.

Clear-data/reset is a separate opt-in recorded operation. Do not equate `noReset`, session creation, restart, logout and app data wipe. Permission-testing cases override ordinary grant behavior; unsupported special permissions produce a setup error rather than broad grant-all. Store and restore any device settings changed by the worker where feasible.

## 8. Precision, vision and WebView

Precision gets a redacted mobile candidate list + screenshot/context and returns schema-validated candidate choices or typed locator proposals. It does not return executable code. Apply the same uniqueness/action/permission rules as Keel. Freeze provider policy, call/time/spend budgets; unavailable/disallowed/exhausted provider falls back to Keel with recorded reason and actual provider usage. No mobile batching in v1; desktop batching must not be reused unmodified.

Vision is a separate resolver, not an unrecorded coordinate fallback. Record model/version, fresh screenshot geometry, target description, confidence and post-action evidence. Before tap, verify screen orientation/dimensions/context still match; otherwise recapture/re-ground. Do not re-tap when post-action outcome is uncertain. Replay calls the configured vision provider anew; a missing provider is a clear failure/TODO capability error, never static-coordinate success. Secure/blank screenshots explicitly block vision. A failed explicit user locator may fall back but the choice stays visible.

WebView selection matches package and configured URL/title predicate among discoverable contexts, requiring one result. Store the predicate, not volatile WEBVIEW PID names. Require a debuggable WebView and qualified compatible Chromedriver; configure its approved path, do not silently download executables at runtime. Switch before each step and restore native context when required, including after failure. DOM extraction only runs in WebView; system dialogs use native context. Inaccessible/ambiguous WebViews block the required step.

## 9. Privacy and network boundaries

Tenant-authorize every app/profile/device/job/evidence operation. MEMBER is read-only; OWNER/ADMIN operate; preserve existing OWNER-only project deletion and ownership administration. Device assignment/enrollment administration is OWNER-only; ADMIN may use authorized devices.

Reuse protected secret storage/transport patterns but audit runner input ZIPs: secrets must be separate encrypted, attempt-scoped material with short TTL, never an ordinary frozen-input archive or manifest field. Erase ephemeral plaintext on completion. No secrets in IR, names, Allure parameters, command lines, logs or exported Java/properties. Sensitive expected values use references too.

Sanitize XML password nodes, configured sensitive nodes and surrounding values; redact sensitive screenshot bounds before model transmission/report publication. If redaction cannot be established for a sensitive screen, withhold external transmission and mark protected evidence unavailable. Raw diagnostic capture is off by default, explicitly enabled only with restricted retention/access. Default proposed artifact retention 7 days, transient secrets until attempt end, with configurable organization policy. No blanket claim that masking known nodes removes all PII.

An APK is untrusted code running on a device. Browser URL policy does not constrain app networking. Local execution uses authorized test devices/accounts; any future shared mobile hosting requires separate device network/host isolation and cleanup qualification. Native app egress, external provider consent and WebView navigation policy must be explicit, not inherited as a false browser-only guarantee.
