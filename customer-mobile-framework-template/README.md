# customer-mobile-framework-template (M4-01)

**Status:** M4-01 fleshed on box — **not** customer-ZIP-ready (needs M4-04 packager + qualified M0-02 pins + device).  
**Date:** 2026-10-14 Africa/Cairo  
**Owner:** Keel Codegen

## What changed in M4-01
- Typed `MobileProperties` / `MobileConfig` (Android/UiAutomator2, required `mobile.udid`)
- `AndroidDriverFactory` — clear “configure local Appium” when `serverUrl` blank; options builder unit-testable
- AE-parity settle embed: `SettleAfterTable` + `SettleActionKind` + `SettleRule` + `ActionOutcome` + `ActionOutcomeClassifier` + `ScreenshotDiffThreshold` under `project.runtime.settle`
- `SettledMobileActions` with `AndroidDriver` + `By` signatures; settle short-circuit hooks; **no Thread.sleep**
- `MobileWait`, `MobileGestures`, `HideKeyboard`, `MobileAssertions`, `MobileReadOnlyRetryAnalyzer` (FLAKY on read-only retry)
- `BaseMobileTest` lifecycle (BeforeSuite props + reject parallel; BeforeMethod session; AfterMethod quit)
- Helper unit tests + `pom-helper-tests.xml` (draft pins only)

## Still stubbed / follow-on
- `AuthCoordinator` (M4-03)
- POM pins remain `PLACEHOLDER-M0-02` (lock is `pinned-m0-draft`, not qualified)
- M4-04 ZIP packager + deny-list strip of helper tests
- M4-02 `MobileCodeWriter` (portal) — separate patch
- Live device NEW/UPDATE (M4-06)

## Packager deny-list (document for M4-04)
Strip from customer ZIP:
- `pom-helper-tests.xml`
- `src/test/java/project/config/*Test.java`
- `src/test/java/project/runtime/**/*Test.java`
- `src/test/java/project/utils/retry/*Test.java`
- `src/test/java/project/naming/**` (when present)
- helper Surefire output / `target/`

## How to run helper tests (box)
```bash
cd scaffolds/customer-mobile-framework-template
mvn -f pom-helper-tests.xml -o test
```
Draft pins: java-client 10.1.1 / Selenium 4.43.0 / TestNG 7.11.0 / Allure 2.31.0 — **not** qualified.

## Design
`/workspace/testpilot/reviews/m4-mobile-framework-codegen-design.md`

## One class per screen
`templates/Screen.java.ftl` only — never `*_Locators` / `*_Actions`.
