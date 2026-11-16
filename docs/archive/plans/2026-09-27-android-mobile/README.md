# Android mobile implementation plan

Date: 2026-09-27. Status: **planning complete; implementation deferred**.

This pack expands the [approved behavioral design](../../specs/2026-09-22-mobile-execution-automation-design.md). It does not authorize installation, device reset, implementation, publishing, or release certification. Technical defaults below are proposed implementation decisions, not claims of tested support. All task checkboxes deliberately remain unchecked.

## Reading order

1. [Architecture and technical decisions](architecture.md): integration boundaries, dependencies, execution lifecycle, locator acquisition, Flutter, authentication, vision, and privacy.
2. [Data model and contracts](contracts.md): persistence, runner/device fencing, versioned IR, API shapes, authoring grammar, failure codes.
3. [Generated framework and configuration](framework.md): properties, naming, screen objects, lifecycle, secrets, UPDATE, reproducible packages.
4. [Implementation tasks](tasks.md): six delivery phases plus prerequisite planning/build checks, dependencies, files, and exit gates.
5. [Acceptance and operator guide](acceptance.md): deterministic checks, real-device/provider checks, downloaded replay, fault injection, evidence requirements.
6. [Research and compatibility record](research.md): official sources checked during planning and the dependency qualification gate.

## Scope contract

- Android Flutter apps through **Appium + UiAutomator2**, on one ADB-connected emulator or physical device per job.
- APK upload or installed-app selection; package/activity discovery with editable overrides.
- Execute and Automate; a separate Java/Appium/TestNG ZIP. Existing web functionality and web packages remain supported unchanged.
- User locator first; then verified accessibility/resource IDs, contextual text, relative XPath, and configured vision fallback.
- Authentication profiles reference user-authored login cases. Ensure logged in, fresh login, logged out, and login-under-test modes are explicit.
- Phone, password, OTP and PIN are supplied through declared inputs. No OTP polling, interactive prompts, hidden default OTP, or automatic retrieval.
- Native and accessible WebView execution; Keel and Cursor/Precision; explicit fallback and vision dependencies.
- Call-before prerequisites, independent case state, meaningful assertions, evidence, NEW/UPDATE and clean downloaded replay.
- Local laptop/device runner first. Its outbound protocol can later serve a hosted portal; shared hosted mobile operation is a separate deployment qualification.

Not included: iOS, mobile Hunt, multi-device parallel suites, unattended device unlock, biometric bypass, real payment acceptance, private-runner auto-update/attestation, or new third-party connectors. Split APK sets/AAB ingestion are deferred; initial uploads are single installable APKs. Installed split-package apps may be exercised, with observed identity clearly distinguished from an uploaded APK checksum.

## Important decisions

- Keep Appium dependencies in an isolated mobile worker/runtime build. The existing root and web customer template both pin Selenium 4.49.0; do not silently change them to accommodate Appium.
- Use a typed mobile IR envelope, not overloaded web `ProvenStep` fields. Legacy web IR remains readable through the existing codec.
- The device is an exclusive physical resource. A database lease alone cannot stop an old Appium session; pair it with host locks, attempt fencing and session teardown acknowledgment.
- Login freshness and clearing app data are separate settings. A profile switch verifies account identity, not simply that a home screen exists.
- Mobile v1 proves selected cases on every run, including UPDATE. Locator memory can suggest candidates but cannot turn old mobile proof into a new PASS.
- No batches of mobile state-changing actions in v1. One fresh screen decision at a time, including Precision.
- Do not count a tap, successful Appium call, overlay, dry-run, or generated compilation as a proven business assertion.

## Completion definition

Each phase exits through its own checks before the next phase. Mobile is complete only when **all six phases** pass, including native/WebView, real Precision and vision tests, emulator and physical-device replay, failure/cancellation isolation, and the existing web regression gate. Early phases are useful engineering milestones, not reduced definitions of the agreed scope.

The current launch HOLD and its approvals are outside this document. This plan neither closes nor adds implementation evidence to those existing release gates.

## Current state and unresolved environment inputs

The behavioral specification existed before this pack; no mobile production adapter or Appium dependency was found in the current repository. The web revamp already has reserved mobile UI concepts, which remain disabled until corresponding capabilities are available.

Before real-device validation, the operator must provide a test device/UDID, authorized APK or installed package, permitted accounts/test data and installed Android/Appium prerequisites. Their actual values are configuration inputs, not architecture gaps. Exact tested toolchain patch versions are a mandatory M0 qualification output; no matrix is claimed tested by this plan.
