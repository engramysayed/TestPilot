# Generated Appium framework and properties

## Deliverable structure

New `customer-mobile-framework-template/` is independent of `customer-framework-template/`. Proposed generated ZIP:

```text
pom.xml                         # exact qualified Appium/Selenium/TestNG/Allure pins
testng.xml                      # one device, no parallel methods/classes
README.md
src/test/java/screens/locators/*Locators.java
src/test/java/screens/actions/*Actions.java
src/test/java/tests/TC_*.java
src/test/java/tests/todo/*TodoTest.java
src/test/java/runtime/           # driver, config, waits, gestures, assertions, auth
src/test/resources/config/appium.properties
src/test/resources/config/profiles.properties
src/test/resources/config/secrets.example.properties
src/test/resources/delivery-testdata.properties
src/test/resources/mobile-ir.json
docs/locator-map.json            # no typed/sample/secret values
docs/package-manifest.json
docs/name-map.json
.github/workflows/mobile-replay.yml
.gitignore
```

Runtime helpers are versioned, deterministic template content. Do not ship internal helper test classes, dependency caches, APK binaries, runner tokens, user credentials, raw screenshots, machine paths or prior Surefire/Allure output. Every requested case is represented: proven runnable test, or explicit TODO/blocked stub with reason. A missing case fails packaging.

## Properties contract

Java reads configuration; it never emits a user's server URL, UDID, package/activity, APK path or credentials as Java constants. Precedence: CLI `-D` overrides → designated local properties → shipped non-secret example defaults. Secrets are a separate resolver (environment or excluded local secret file), never copied to `System.getProperties()` or command-line arguments. Resolve escaped Windows paths and Unicode using documented UTF-8 handling.

Example, all machine/app values intentionally blank:

```properties
mobile.platformName=Android
mobile.automationName=UiAutomator2
mobile.serverUrl=
mobile.udid=
mobile.deviceName=
mobile.appSource=INSTALLED
mobile.appPath=
mobile.appSha256=
mobile.appPackage=
mobile.appActivity=
mobile.appWaitActivity=
mobile.noReset=true
mobile.fullReset=false
mobile.commandTimeoutSeconds=120
mobile.lookupTimeoutSeconds=15
mobile.sessionStartTimeoutSeconds=120
mobile.adbTimeoutSeconds=30
mobile.scroll.maxSwipes=5
mobile.scroll.maxNoProgress=2
mobile.permissions.policy=PROFILE
mobile.permissions.allowed=
mobile.reset.clearData=false
mobile.context.default=NATIVE
mobile.chromedriver.path=
mobile.auth.profile=
mobile.auth.mode=ENSURE_LOGGED_IN
mobile.secretsFile=
mobile.vision.enabled=false
mobile.vision.provider=
mobile.vision.endpoint=
mobile.vision.model=
mobile.evidence.rawEnabled=false
mobile.failFast=false
```

Parser converts these into typed `UiAutomator2Options` and runner policy. Only actual Appium capabilities receive the `appium:` namespace; `mobile.auth.*`, vision and evidence options are Keel runtime settings, not blindly forwarded capabilities. Platform/automation type remain user-visible properties but must validate as Android/UiAutomator2 for this template.

Require UDID even if only one device exists. Require appPath + checksum for APK mode, package/activity for INSTALLED mode. Allow APK manifest detection to supply package/activity, but record resolved settings. Reject contradictory fullReset/noReset/reset policies, negative/oversized timeouts, unknown capabilities and remote APK URLs. Explicit destructive clear-data/uninstall needs a run setting and clear UI/operator notice; fresh login alone never enables it. `autoGrantPermissions=true` is not the default permission implementation: apply only allowlisted declared runtime permissions through qualified operations.

The template performs a readable prerequisite check before testing: selected device online/authorized, Appium reachable, supported driver/toolchain, APK/installed identity compatible, required secrets and vision/WebView capabilities available. Never installs Appium/SDK/Chrome drivers on the customer's behalf. Runtime app installation is allowed only for explicitly selected APK mode.

## Naming and locator rules

Preserve the user's underscore and suffix style. Examples: `num_One_Btn_Locator`, `transactions_Tab_Locator`, `first_Name_Ar_Input_Locator`; actions such as `tap_Sign_In_Btn`, `type_Phone_Number_Input`, `assert_Order_Reference_Text`. Define one suffix vocabulary (Btn/Input/Tab/Text/Checkbox/Dropdown/Icon) configurable at project naming revision; do not alternate Button/Btn accidentally.

Locator strings retain exact app casing/Unicode independently of Java symbol cleanup. Java symbols normalize invalid characters/keywords and use deterministic short identity hashes for collisions, never traversal-order counters. Identity key includes context + stable screen identity + locator strategy/value + semantic role. `name-map.json` pins symbols by that key; reordering cases or UPDATE cannot rename unchanged identities. Renaming title is not automatically renaming screen objects. Screen identity comes from configured screen key/structural anchors and package/activity/context, not activity alone because Flutter often uses one activity for many screens.

Resource ID uses AppiumBy.id with exact observed value; accessibility ID uses AppiumBy.accessibilityId. Relative XPath/text locators are escaped by typed builders. No generic Appium `name` strategy invented to imitate Selenium. Locator-map diagnostics include origin/rank/context/verification and safe labels; never typed sample values.

## Test lifecycle and assertions

One test class per selected leaf, stable TC identity and readable test name. Suite reserves one device and rejects parallel configuration. Each leaf creates a fresh Appium session against the selected device without automatically resetting app data, executes its declared auth/start-state/prerequisite closure, then its body. App state may survive session teardown; session/account predicates are authoritative, never a Java static boolean.

`@BeforeMethod`: clear invocation captures, create session, apply per-case permission/auth/reset settings, run setup closure, flush setup soft assertions. `@Test`: execute emitted operations/assertions. `@AfterMethod(alwaysRun=true)`: flush body assertions, collect sanitized final evidence, quit in nested `finally`, clear captures/secrets even on failure. Suite-finally releases the local lock. Process interruption recovery is handled by runner lease reconciliation, not assumed guaranteed Java finally execution.

Setup failure produces failed configuration + skipped body in TestNG. Report these separately; do not double-count TestNG configuration failures as additional cases. Assertion failures propagate to nonzero Maven status. TODO stubs are explicit skipped/unproven cases with a manifest coverage gate; an all-TODO run cannot be reported as successful proof. Allure named steps wrap actual work and propagate failures; sensitive parameters/expected values are references, not step arguments. Runtime error messages and screenshot attachments require the same privacy treatment as the proving worker.

Every leaf inlines or references its own deterministic setup closure; no TestNG class ordering or cross-class browser/session dependency. Captured variables are invocation scoped and setup-to-body accessible. Owner/phase/step-key testdata keys prevent prerequisite login/body collisions. Validate conflicts before writing the properties file.

## Replay, NEW/UPDATE and publication

Execute validates the selected cases on the live app and stores mobile proof; Automate performs live proof of selected cases and emits the separate mobile ZIP. A generate-only preview is labeled unproven and cannot mint PASSED IR. Dry-run outputs stay SIMULATED and do not increase proven rate.

Mobile v1 UPDATE re-proves selected cases even on the same app revision. Reuse existing locator/name suggestions only; no `ReuseEligibility` web proof shortcut. App checksum/package/version, environment, locale, orientation, driver/toolchain, auth revision/credential revision, provider policy, hierarchy/naming schema and dependency closure are fingerprint inputs. Any new APK makes previous proof historical. Unselected cases on a new app revision become explicit stale/TODO in the current package, not silently carried-forward passes; their old packages stay downloadable with old manifests.

Regenerate the complete generated layer from current mobile IR, removing obsolete screen methods/testdata/tests; overlay only supported customer config files. Preserve naming-map identity and original app manifests for history. Compile under the isolated qualified POM before publishing under `PublicationLock`, then store ZIP SHA-256/template/toolchain/input hashes. Publication checks attempt and device fences, project tombstone and pinned input hash. A failure cannot replace the last good ZIP or redirect a historical job download to latest.

Customer replay verifies available app identity; checksum mismatch for APK mode blocks unless the operator intentionally creates a new unverified validation run. Installed mode records observed identity and warns about limited binary verification. A clean ZIP must run without Keel source checkout; local secrets/config and optional vision endpoint are the only expected external inputs beyond documented tooling/device/app.

CI example targets a user-managed runner with an exclusively assigned Android device. It documents secret injection, properties file preparation, Maven nonzero propagation and sanitized artifact retention. Do not claim ordinary hosted GitHub runners have a connected phone or automatically start emulators/install tools. No automatic real-payment workflow.
