# Keel Mobile Framework Rules

Canonical reference for the **Android** test framework Keel emits via `customer-mobile-framework-template/`. The agent, codegen, and runtime must conform. The web rules live in [FRAMEWORK_RULES.md](FRAMEWORK_RULES.md); this document only covers things that differ.

- **Source of truth for emit shape**: [`customer-mobile-framework-template/templates/*.ftl`](../customer-mobile-framework-template/templates/)
- **Runtime helpers that every emit calls**: [`customer-mobile-framework-template/src/main/java/project/runtime/`](../customer-mobile-framework-template/src/main/java/project/runtime/)
- **Mobile runner** (settle, bind, precision, evidence): [`mobile-runner/src/main/java/delivery/mobile/runner/`](../mobile-runner/src/main/java/delivery/mobile/runner/)
- **Keep this doc in sync**: any change to mobile codegen emit, template contracts, runtime classes, locator tiers, or precision gate rules MUST update this file in the same commit.

---

## 1. Emit shape

### KEEL-M-1 — 1 screen = 1 class
`Screen.java.ftl` emits exactly **one class per screen**: locator fields + action methods + assertion / capture methods, in that order. No `*_Locators` / `*_Actions` split. Same discipline as `Page.java.ftl` on web (KEEL-R-1 / KEEL-R-2).

### KEEL-M-2 — Appium only
Imports are locked to `io.appium.java_client.AppiumBy`, `io.appium.java_client.android.AndroidDriver`, `org.openqa.selenium.By`. Never emit `WebDriver`, `ChromeDriver`, `Playwright`, or `UiAutomator2Driver` directly.

### KEEL-M-3 — Locator fields use `By` plus `AppiumBy`
```java
protected final By signInButton = AppiumBy.accessibilityId("login_submit");
```
CSS selectors are **never** allowed in native screens (`LocatorStrategy.nativeAllowed()` rejects `CSS`). Use `By.id(...)` only through `AppiumBy.id` / `AppiumBy.xpath` — raw Selenium `By.cssSelector` triggers a bind error.

### KEEL-M-4 — Action methods are one-liners into `SettledMobileActions`
Every public action delegates to [`SettledMobileActions`](../customer-mobile-framework-template/src/main/java/project/runtime/settle/SettledMobileActions.java). The template never calls `driver.findElement(...).click()` directly. The helper owns Allure wrap + settle + outcome classify.

```java
public void tapSignIn() {
    SettledMobileActions.tap(driver, signInButton, "tapSignIn");
}
```

### KEEL-M-5 — No step-on-value methods
Same discipline as web KEEL-R-STEP-SAFE: if a method takes a value parameter (type, select), the emitted body does NOT also treat the parameter as a step name. `SettledMobileActions.typeSecret` registers the value with `Secrets.register` before any Allure attachment.

### KEEL-M-6 — TODO stubs render as `@Test(enabled = false)`
`TodoMobileTest.java.ftl` emits an **always-disabled** `@Test` method. A suite that is all-TODO still compiles but is never counted as proven — mirrors web `KEEL-R-TEST-DESC`.

### KEEL-M-7 — Tests stay thin
`GeneratedMobileTest.java.ftl` is screen-var-declarations plus chronological setup / body calls. **No logic, no asserts in the test method** — those live on the screen class. Mirrors web KEEL-R-3a.

---

## 2. Locator ranking

Mobile has its own fixed tier list in [`RankTier`](../mobile-runner/src/main/java/delivery/mobile/runner/bind/RankTier.java). Lower ordinal wins:

| Tier | Strategy | Why |
|---|---|---|
| 0 — `USER_OVERRIDE` | whatever the user pinned on the project-detail "Add a locator manually" panel | Explicit human intent |
| 1 — `RESOURCE_ID` | `AppiumBy.id("com.app:id/signin")` | Survives copy changes and translations |
| 2 — `ACCESSIBILITY` | `AppiumBy.accessibilityId("login_submit")` | Compiler-friendly, screen-reader-stable |
| 3 — `LABEL_SIBLING` | XPath walking a labelled sibling node | For un-ided controls that have a labelled neighbour |
| 4 — `TEXT_CLASS` | `AppiumBy.androidUIAutomator("new UiSelector().text(...)")` or className+text | Visible text + class, brittle across translations |
| 5 — `XPATH` | Relative XPath, never absolute | Last resort before failing |
| 6 — `OTHER` | Reserved for future strategies | Should never appear in generated code today |

**Codegen rule**: when the binder returned more than one validated candidate for a control, the generated screen keeps the **lowest-tier (highest-ranked)** one and drops the rest. No mobile analogue of web's `FirstOf.of(...)` fallback bag — mobile control bind is deterministic per-step at runtime.

**User-facing-first** is NOT a project-level choice on mobile. A fixed tier order gives stable Android tests across releases.

---

## 3. Runtime contract

### KEEL-M-RT-1 — Settle after every action
`SettledMobileActions` runs a `SettleExecutor` probe after every action and classifies the outcome (`NO_CHANGE`, `UI_CHANGE`, `CRASH`). Jobs that don't settle within the configured budget fail closed.

### KEEL-M-RT-2 — No blocking sleep
`Thread.sleep` / `TimeUnit.*.sleep` is banned from `project.runtime` and from generated screens. Use `MobileWait` for timed polls; never sleep in a hot path.

### KEEL-M-RT-3 — `BaseMobileTest` owns the session
Driver creation, pre-test auth, teardown all live on `BaseMobileTest`. Generated tests inherit; they never create their own `AndroidDriver`.

### KEEL-M-RT-4 — Secrets go through `Secrets.register`
Passwords and OTPs typed by `typeSecret` are registered before any Allure attachment so they can be redacted from reports.

### KEEL-M-RT-5 — Allure + logs stay single-sink
All action methods wrap via `AllureSteps.run(name, () -> …)` (no `@Step` **and** also calling `AllureSteps.run` — pick one). `LogsManager` is the only logger; `System.out` / `System.err` are banned.

---

## 4. Precision gate rules

Mobile Precision lives in [`MobilePrecisionGate`](../mobile-runner/src/main/java/delivery/mobile/runner/precision/MobilePrecisionGate.java). It validates a Cursor response AFTER the call — it does not make the call itself.

### KEEL-M-P-1 — Reject secret canary
Any response that contains the `SECRET_CANARY_OTP_999888` sentinel is rejected with `SECRET_REDACTION`. No exceptions.

### KEEL-M-P-2 — Reject code-looking responses
Responses that contain `Runtime.getRuntime()`, `.exec(`, `mobile: shell`, or triple-backtick code fences are rejected with `CODE_REJECTED`. Cursor returns picks, not Java code.

### KEEL-M-P-3 — Batch unsupported
`PrecisionInput.batch == true` always returns `BATCH_REJECTED`. Mobile precision is single-intent. The web engine's batched `groundRankBatch` is a different code path.

### KEEL-M-P-4 — Provider gates
Falls back to Keel with reason `UNAVAILABLE` / `DISALLOWED` / `EXHAUSTED` without burning a call when the provider is off, disallowed by policy, or past the `callCap`.

### KEEL-M-P-5 — Context must match
The chosen candidate's `contextKind` must equal the step's `stepContext`. A pick that would type into a button is rejected with `WRONG_CONTEXT`.

### KEEL-M-P-6 — User override wins
When the step has a `userOverride` candidate and the Cursor response names a different id, the gate rejects with `USER_OVERRIDE`. The human's pin outranks Cursor.

---

## 5. Cursor model selection on mobile

The admin-level pin (`delivery.authoring.precision.cursor-model`) and the per-project override on **Project Settings → Cursor model** flow into `PrecisionJobConfig.cursorModel`, same as for web. The mobile Precision caller passes the configured model to Cursor's chat endpoint via `heal.mjs`; the `MobilePrecisionGate` only validates the response.

Precedence end-to-end for a mobile job:

```
KEEL_CURSOR_MODEL env / -Ddelivery.authoring.precision.cursor-model
  > per-project pin (Project Settings)
  > workspace pin (Admin → LLM & API keys)
  > YAML delivery.authoring.precision.cursor-model
  > Cursor Auto router
```

---

## 6. Not supported on mobile today

Honest list of web features that are **not** yet ported to mobile. Landing these is a conscious scope decision, not a bug.

- **Figma Validate per-step**: web stores `design-references/<tcId>/step-NNN.png` and compares in `DesignComparePhase`. Mobile evidence writes `step-NNN.png` but no comparator reads it against uploaded mockups.
- **Pre-run authoring review**: `AuthoringReviewService` runs against any project (now with the mobile prelude), but mobile projects have no separate "Review cases before run" toggle on their Execute page.
- **`FirstOf` locator fallback bag**: web emits a primary + verified fallbacks per locator; mobile picks one and relies on healing at run time.
- **`locator-outcomes.jsonl` win-ratio**: web's self-updating locator memory with Laplace-smoothed win ratio is not written during a mobile run.

---

## 7. What codegen must never emit for mobile

- `By.cssSelector(...)` — CSS is not valid in `NATIVE_APP` context.
- `driver.get(url)` or any URL navigation — the app has no URLs.
- `WebDriverWait` keyed on CSS / page title.
- `ExpectedConditions` on anything that only exists in a browser (alerts, iframes, window handles).
- `Thread.sleep` / `TimeUnit.*.sleep` anywhere in a screen class.
- Direct `driver.findElement(...)` inside a screen action — must go through `SettledMobileActions`.
- Any import from `org.openqa.selenium.chrome` / `edge` / `firefox` / `safari`.

---

## 8. Sync with the web rules

Per-TC rules that apply **identically** on mobile (don't repeat them — just defer):

| Web rule | Still applies on mobile |
|---|---|
| KEEL-R-1 (1 TC = 1 test class) | yes |
| KEEL-R-3c (`Validate.*` takes a locator) | yes — `MobileAssertions.*` takes a `By` |
| KEEL-R-STEP-SAFE | yes |
| KEEL-R-TEST-NO-BY (tests do not define raw `By`) | yes |
| KEEL-R-NO-SYSOUT | yes |
| KEEL-R-TEST-NAME | yes |

The `FrameworkContractValidator` currently runs against web emit only; a mobile analogue is pending (see §6).
