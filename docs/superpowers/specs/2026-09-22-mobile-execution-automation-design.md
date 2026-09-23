# Android mobile execution and automation design

Date: 2026-09-22
Status: conversational design approved; written specification awaiting review.
Implementation has not started. This specification does not close existing launch gates.

## Objective

Extend Keel Execute and Automate to Flutter Android applications through Appium
UiAutomator2. Reuse the existing library, job pipeline, evidence, profiles, history,
and reporting infrastructure. Implement separate mobile extraction, binding,
execution, and framework generation components instead of adding Android behavior
throughout Selenium-specific classes.

The user approved the five design sections: project/device setup, authentication
and prerequisites, execution, downloadable framework, and failure/acceptance rules.

## Scope

- Android emulators and physical Android devices connected through ADB.
- Appium with UiAutomator2, not the Flutter-specific Appium driver.
- Uploaded APKs and already-installed applications.
- One selected and exclusively reserved device per job; cases execute sequentially.
- Native Flutter-exposed controls and accessible embedded WebView contexts.
- Keel and Cursor/Precision authoring, with explicit reported fallback.
- Hierarchy-first binding with configured vision fallback.
- Separate Java/Appium/TestNG mobile framework ZIP.
- Local runner now, with the same runner model connecting to a hosted portal later.

Not included: iOS, parallel multi-device suites, automatic OTP retrieval, interactive
OTP prompts, and mobile Hunt exploration. Existing web behavior must remain intact.
No installations, production UI changes, commits or publication are authorized by
this design document itself.

## 1. Projects, applications, and devices

### Project type and app identity

New projects select Web or Android Mobile. Existing projects remain Web. Shared
library/history features remain available; platform-specific configuration and
automation packages are distinct.

For APK upload, detect package and launch activity when possible and expose editable
overrides. Ask for manual input when detection fails or is ambiguous. For an installed
app, select its package on the device and resolve/configure its launcher activity.
Distinguish launch activity from the activity to wait for after startup.

Record app package, version and observed build identity for each run. Uploaded APKs
have an immutable checksum. Do not claim byte-identical APK verification for an
installed application when those bytes were not obtained.

### Device selection and runner

Show connected device names with their ADB serial/UDID; the serial selects the
device, not its friendly name. A job reserves one device. Competing jobs cannot
interleave actions on it and must wait or receive an explicit busy result.

Extend existing runner interfaces where appropriate. The runner on the
device-connected host discovers devices, checks readiness, creates Appium sessions,
performs execution and returns evidence. It can share a laptop with the portal or
connect to a hosted portal later. Device ownership and tenant authorization apply
to discovery, reservation, execution, cancellation and evidence access.

### Configuration

Generated Java reads Appium configuration from properties. These include server URL,
UiAutomator2 driver configuration, UDID, optional APK path, package/activity,
wait activity, timeouts, permission policy and app reset behavior. Generated Java
must not contain machine-specific paths, addresses or device identifiers.

Device/app configuration is separate from authentication profiles. Secrets are
supplied locally and are excluded from exported packages. APK binaries are excluded
from framework ZIPs by default; replay users provide the configured APK or installed
app. Effective non-secret settings and build identity are recorded with evidence.

## 2. Authentication and preparation

### Authentication profiles

A profile names a login setup TC, protected values, a session check, and optional
logout/setup steps. Values may include phone, password, static or run-supplied OTP,
and PIN. Profiles can represent different accounts or roles.

OTP is a normal explicitly requested step in the login TC. The user supplies its
value before execution. There is no automatic OTP retrieval or interactive prompt.
An expired or incorrect supplied value fails login normally; it is not guessed.

### Case requirements

- Ensure logged in: default for authenticated flows; verify the selected account's
  session and run login only when needed.
- Fresh login: end the prior session and run login again.
- Logged out: establish a logged-out state.
- Login under test: do not run automatic authentication; use the test's own steps.

A home screen alone does not establish the correct account when an account-specific
check is available. Profile switches must establish and verify the requested account.
Clearing app data is a separate explicit operation, not a synonym for fresh login.

After authentication, execute the declared starting-state/navigation setup and
Call-before prerequisites. Selecting a single case still includes its prerequisites.
Connected chains retain state; independent cases establish their required state.
Failed login/setup blocks dependent cases rather than giving unexecuted tests PASS.

### Permissions

Ordinary runs grant permissions configured in the app/device profile. Cases testing
permissions override that policy and explicitly exercise Allow/Deny. Do not silently
grant every permission or assume every Android permission can be granted the same way.

## 3. Mobile extraction, binding, and execution

### Screen evidence

Collect Appium page-source XML and a screenshot of the current screen. Normalize
resource ID, content description, text, class, bounds and interaction state into
mobile candidates. Refresh after navigation, scrolling or meaningful UI changes.
Preserve raw locator values exactly, including casing and compound labels.

### Ranking and validation

1. User-supplied locator.
2. Stable accessibility ID or resource ID.
3. Text with control type and context.
4. Relative XPath.
5. Vision fallback.

Ranking considers stability, uniqueness, action suitability and screen context.
The two ID strategies need not have an unconditional ordering against each other.
Duplicate, dynamic, positional and merged-container candidates receive appropriate
penalties or rejection. A user locator that fails can fall back, with that event
reported. No ambiguous candidate silently becomes an arbitrary first match.

Query candidates against the live device before execution. A binder accepting a
selector is not proof of successful action or assertion. Merged Flutter semantics
cannot be split into invented child locators. Missing individually exposed controls
require verified alternative targeting or a clear unsupported/blocked result.

### Naming

Use the user's existing underscore/suffix conventions, informed by
`TestEmp-Mobile Semantic Labels-210926-204201.pdf`. Examples include
`num_One_Btn_Locator`, `transactions_Tab_Locator` and `first_Name_Ar_Input_Locator`.
Generated symbol naming is distinct from the exact selector string in the app.
Normalize inconsistent sample casing/suffixes through one configurable naming
contract, avoid collisions and retain stable names across regeneration where identity
is unchanged. Do not change app identifiers or accessibility labels from Keel.

### Engines and actions

Keel uses the hierarchy with configured healing/vision. Cursor/Precision consumes
mobile evidence and proposes actions/locators; Appium executes them under the same
checks. Provider unavailability or exhausted call limits allow a clearly recorded
Keel fallback. Mobile support must be implemented and validated for both paths.

Initial actions: tap, type, clear, long press, bounded scroll/swipe, Android Back,
keyboard handling, app launch and restart. Assertions: visibility, text,
enabled/selected state and explicit capture/compare. Actions alone do not prove
business outcomes. Preserve failed assertions as failures in both execution modes.

### Vision and WebViews

Prefer verified reusable Appium locators. A vision-only step remains explicitly
vision-dependent, takes a fresh screenshot during replay and uses a configured
provider. Previously observed coordinates are not reusable locators. Unavailable or
unreliable vision must not produce a silent PASS.

Record native/WebView context explicitly in steps. Discover and select the intended
WebView rather than relying on whichever appears first. Switch before execution and
restore the required context for subsequent steps. An inaccessible required WebView
is a clear limitation/blocked result, not an implicit successful native substitute.

### Execution representation

The implementation plan must define mobile action, locator/context, input, assertion
and capability fields and their serialization compatibility with existing web IR.
Live execution and generated replay preserve those meanings. Code generation does
not infer additional assertions or weaken explicit ones.

## 4. Downloadable framework and updates

Generate a separate Java/Appium/TestNG ZIP with screen locators, screen actions,
tests, reusable waits/gestures/assertions, profile/setup helpers, testdata, properties
and Allure reporting. No Appium requirement is imposed on web-only packages.

Generated tests reproduce authentication and prerequisite rules, native/WebView
switching and explicit vision dependencies. Failed setup blocks the test body;
cleanup releases the session even after setup/action/assertion errors. Meaningful
Allure steps surround actual work. Sensitive inputs stay out of routine step
parameters and logs; screenshots and other evidence need explicit protection.

For new APKs, retain libraries/profiles and verify package identity. Re-prove selected
cases against the new build. Prior locators are candidates, not current proof.
Preserve historical results/packages with their original app identity. Updates
regenerate the required generated layer, remove obsolete output, preserve supported
customer configuration and publish only after compile succeeds. Failed generation
must leave the previous good package available.

## 5. Failure behavior and acceptance

Preflight checks device readiness, Appium connectivity and app configuration.
Record relevant logs/screenshots and precise failure reasons. Continue independent
cases only after their required starting state is restored. Block dependents.
Offer Stop on first failure.

Device loss or unrecoverable session failure stops execution. Record uncertain
in-flight outcomes; do not automatically repeat actions such as payment submission
whose outcome is unknown. Cancellation and runner disconnect must not leave a device
available for another job while an old worker can still act on it.

Acceptance covers Execute and generated clean replay on emulator and physical device:

- APK and installed-app modes; one and multiple selected cases.
- Login profiles, reuse, fresh login, supplied OTP/PIN and account switching.
- User locator priority, fallback, ambiguity and missing controls.
- Gestures, permissions, native/WebView transitions and vision-only steps.
- Actual Cursor/Precision execution and explicit fallback.
- Prerequisite failure, cancellation, device disconnect and cleanup.
- APK changes, package regeneration and historical artifact preservation.
- Tenant/device isolation and web regression checks for shared pipeline changes.

Local synthetic evidence, real model/provider evidence and representative application
acceptance are reported separately. Failures and unsupported capabilities cannot be
counted as proven passes.

## Delivery order

1. App/device configuration and mobile runner.
2. Hierarchy extraction, binding and Execute.
3. Profiles, prerequisites, gestures and permissions.
4. Framework generation and downloaded replay.
5. WebView, vision and Precision integration.
6. Consolidated acceptance and documentation.

All phases are in the agreed scope. Each has bounded checks before moving forward.
The implementation plan will map these contracts to existing interfaces and tests;
it must not reinterpret staged delivery as permanently dropping later capabilities.

## Related UI redesign

The user also requested a whole-portal redesign, to be shown before implementation.
That is a separate design workstream. Its prototype should include Web/Mobile project
selection and the mobile flows above, clearly labeled as planned. Approval of this
mobile architecture does not approve a particular visual design or production UI edit.
