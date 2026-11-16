# Research and compatibility record

Checked 2026-09-27. Primary documentation only. These references inform proposed design; no toolchain or device was installed or validated during planning.

## Appium server

Appium 3 documents Node `^20.19.0 || ^22.12.0 || >=24.0.0`, npm >=10 and Windows/Linux/macOS hosts. Choose a supported LTS Node release and pin its patch version in qualification. Server and platform-driver requirements are separate. [Appium 3 requirements](https://appium.io/docs/en/3.0/quickstart/requirements/).

Decision: Appium 3 family, loopback managed service on the local device host. Exact server patch is frozen and tested in M0 rather than asserting that a moving latest release is compatible. No automatic installation.

## UiAutomator2

Driver 5+ requires Appium 3; driver 6+ raises the minimum Android API to 26. Its docs distinguish UDID from display deviceName, which does not select the device. The driver requires Android SDK tooling and provides a doctor command. [Official driver requirements and capabilities](https://github.com/appium/appium-uiautomator2-driver#requirements).

Decision: qualify a pinned 6+ driver with Appium 3; select by exact UDID. Use native accessibility hierarchy for Flutter, with explicit WebView context handling. Do not use Appium's Flutter driver, instrumentation of Dart internals, or friendly-name device selection.

## Java client and Selenium compatibility

The published matrix lists Java client 10.1.1 with Selenium 4.42.0 and 4.43.0. Appium's client uses Selenium dependencies and recommends explicit version management. The inspected root and existing web template instead specify Selenium 4.49.0; this plan does not assume they are compatible. [Official Java client matrix](https://github.com/appium/java-client#compatibility-matrix).

Decision: qualify Java 21 + Java client 10.1.1 + Selenium 4.43.0 in an isolated mobile worker/customer build. Preserve root/web versions. Maven convergence and real session tests must pass before locking the pair. If implementation occurs after the matrix changes, revise the qualification record intentionally.

## Flutter semantic identifiers

Flutter documents `SemanticsProperties.identifier` as an identifier in the native accessibility hierarchy, including Android resource-id mapping. It is distinct from user-facing labels. [Flutter identifier API](https://api.flutter.dev/flutter/semantics/SemanticsProperties/identifier.html).

Decision: inspect the actual APK's hierarchy, preserve exact exposed values, and use the supplied TestEmp semantic-label PDF for symbol conventions. Do not assume a Flutter Widget Key, merged subtree or label naming convention guarantees an independently targetable node. Recommend developers expose stable identifiers where possible; Keel does not modify their APK to add them.

## Android tooling

ADB provides device discovery/targeting and device communication. APK Analyzer can inspect manifest metadata. [ADB documentation](https://developer.android.com/tools/adb), [APK Analyzer CLI](https://developer.android.com/tools/apkanalyzer).

Decision: execute allowlisted CLI argument arrays with exact serial, deadlines and protected logging. Use inspected manifest metadata plus user-confirmed activity overrides; support explicit installed-app identity when APK bytes are unavailable. AAB/split uploads are not silently treated as single APKs.

## Repository decisions and rejected alternatives

- Reuse tenant/job/library/publication infrastructure; reject a second independent mobile portal or security model.
- Isolated mobile worker rather than altering Selenium dependencies in the existing web runtime. Cost: an explicit build/DTO boundary; benefit: no unqualified shared-classpath coupling.
- Typed mobile IR rather than stuffing context/gestures/auth into web `ProvenStep.rationale` or locator strings. Cost: platform dispatch and codecs; benefit: strict replay semantics and migration safety.
- Explicit auth profiles rather than assuming every app uses username/password or home-screen login detection.
- Device lease plus host lock/teardown fencing rather than TTL-only ownership. A stale HTTP worker can otherwise keep controlling the phone.
- Fresh proof on mobile UPDATE rather than immediate proof reuse. Saves a risky new invalidation subsystem while keeping candidate/name reuse; future proof-reuse optimization requires its own acceptance.
- Fresh vision replay rather than fixed tap coordinates. Precision proposes candidates; the deterministic executor owns side effects.

## Qualification record to create in M0

`mobile-toolchain.lock.json` must record exact Java/Maven/Appium server/UiAutomator2/Node/npm/Java-client/Selenium/ADB/SDK-build-tools/Chromedriver versions, distribution checksums where available, host OS, tested Android APIs/ABIs, dependency tree digest, source revision, commands and results. An absent qualification record is a preflight/support error, not permission to substitute latest dependencies.

Compatibility remains an implementation test gate; the architecture and task decisions above are complete without pretending that future binary versions have already passed it.
