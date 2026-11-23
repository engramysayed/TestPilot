# Keel Mobile Framework Rules

Canonical reference for the **Android** test framework Keel emits via `customer-mobile-framework-template/`. The agent, codegen, and runtime must conform. The web rules live in [FRAMEWORK_RULES.md](FRAMEWORK_RULES.md); this document only covers things that differ.

- **Source of truth for emit shape**: [`customer-mobile-framework-template/templates/*.ftl`](../customer-mobile-framework-template/templates/)
- **Runtime helpers that every emit calls**: [`customer-mobile-framework-template/src/main/java/project/runtime/`](../customer-mobile-framework-template/src/main/java/project/runtime/)
- **Mobile runner** (settle, bind, precision, evidence): [`mobile-runner/src/main/java/delivery/mobile/runner/`](../mobile-runner/src/main/java/delivery/mobile/runner/)
- **Host prerequisites** (adb / appium / apkanalyzer / aapt2 / runner process / env vars) are documented once in [`ENVIRONMENT.md`](ENVIRONMENT.md).
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

**Codegen rule**: when the binder returned more than one validated candidate for a control, the generated screen wraps them in [`MobileFirstOf.of(...)`](../customer-mobile-framework-template/src/main/java/project/utils/locators/MobileFirstOf.java) — the mobile analogue of web `FirstOf`. The primary (lowest-tier) candidate is tried first; each slot records a WIN / MISS line via [`MobileLocatorOutcomes.append`](../customer-mobile-framework-template/src/main/java/project/utils/locators/MobileLocatorOutcomes.java) so the portal's locator-outcomes ledger can rank them later. Single-candidate locators still emit as plain `AppiumBy.*`.

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

The admin-level pin (`delivery.authoring.precision.cursor-model`) and the per-project override on **Project Settings → Cursor model** flow into `PrecisionJobConfig.cursorModel`. The **authoring review** path (`AuthoringReviewService`) uses that pin when it calls Cursor for an Android suite — the mobile prelude at `prompts/authoring/mobile-prelude.md` is added on top.

**Mobile Precision bind path (shipped 2026-11-23, phases 7a + 7b):**

```
runner — MobileCaseRunner.bind()
  (ladder: RESOURCE_ID → ACCESSIBILITY_ID → TEXT → LABEL_SIBLING → text-contains)
  ends with > 1 actionable text-contains hit
  │
  │ MobilePrecisionClient.pick(target, hits)
  │
  │ POST /api/v1/runners/mobile/runs/{runId}/precision/bind
  │   { intent, candidates[{id,label,contextKind,userOverride}],
  │     stepContext, snapshotExcerpt }
  ▼
portal — MobilePrecisionBindService
  ├── resolve the project's Cursor model pin via PortalStore.precisionConfigForProject
  ├── call CursorHealClient("pick") with a mobile-shaped table
  ├── feed response + provider state into MobilePrecisionGate.rank(...)
  └── return the gate outcome
     { code, chosenId, usedProvider, fallbackReason, countsAsCursor }
  ▼
runner trusts chosenId only when it maps back to one of the hits it sent;
anything else (code != "", provider fallback, HTTP error, foreign id) → Optional.empty()
and the runner's existing "element not found" path takes over.
```

The sidecar invocation stays on portal so runners don't need Node / sidecar jars / Cursor API keys — the round-trip over the bearer-authenticated runner wire is the cost. The runner is best-effort: any error (transport, non-200, invalid id, provider fallback, parse failure) keeps the pre-Precision not-found behavior, and every error path logs to `System.err` so a dead portal or a bad sidecar leaves a breadcrumb.

**Hardening (shipped 2026-11-23, phase 7c):**

- **Per-run call budget.** `MobilePrecisionBindService` holds a `PrecisionCallBudget` per run (sized from `PrecisionJobConfig.maxCallsPerJob`, default 50). Once exhausted, further binds return `fallbackReason=BUDGET_EXHAUSTED` without reaching Cursor; `clearRun(runId)` releases the entry, and the map is capped at 1024 orphaned entries so a lost run-end callback can't leak unbounded memory.
- **Shortlist cap.** Both sides cap the candidate list at 25 before building the sidecar prompt — the picker is less accurate on huge tables.
- **Mobile preamble.** The shortlist table is prepended with `# Mobile Android native candidates — pick the native UI node the user's intent refers to.` so the sidecar's generic `pick` prompt lands with the right context (rows are UiAutomator nodes, not DOM elements).
- **Real `snapshotExcerpt`.** The runner builds a small XML blob of the candidate nodes (class, bounds, resource-id, content-desc, clickable, text) and ships it with the bind — the gate truncates at 8 KB.
- **Per-run pick cache.** The client caches `chosenId` keyed on `(intent, sorted candidate indices)`; a retried step on the same screen does not pay a second sidecar call.
- **Controller split.** `POST /precision/bind` now returns `404` only for `run not found`; empty-candidates / blank stepContext are `400` so the runner can distinguish wire problems from real misses.
- **Admin dashboard.** `/admin/mobile-precision` renders a live table of the last 500 picks (in-memory ring, cleared on portal restart). JSON feed at `GET /api/admin/mobile-precision/picks`.

**Module boundary (also 2026-11-23, phase 7c):** `PrecisionCandidate`, `PrecisionInput`, `PrecisionOutcome` and `MobilePrecisionGate` moved from `mobile-runner` to `mobile-contracts` so portal (which depends on `mobile-contracts` only) can link against them — phase 7a was quietly breaking the portal reactor build.

Precedence (admin-pin chain):

```
KEEL_CURSOR_MODEL env / -Ddelivery.authoring.precision.cursor-model
  > per-project pin (Project Settings)
  > workspace pin (Admin → LLM & API keys)
  > YAML delivery.authoring.precision.cursor-model
  > Cursor Auto router
```

The **Figma Validate** comparator does **not** use Cursor at all — it uses the vision model (`UI-TARS` / `Qwen`) via `LocalLlmClient`, wired by Admin → LLM & API keys (`delivery.vision.model`).

---

## 6. Not supported on mobile today

Honest list of web features that are **not** yet ported to mobile. Landing these is a conscious scope decision, not a bug.

- **Appium Inspector inside the portal**: no built-in inspector view. Use the standalone Appium Inspector pointing at `http://127.0.0.1:4723` during manual sessions. A portal view would need a long-lived inspector lease + REST proxy — scoped as its own feature.

### Recently landed (what used to live here)

- **Mobile Precision — runner side** — shipped 2026-11-23 (phase 7b). [`MobilePrecisionClient`](../mobile-runner/src/main/java/delivery/mobile/runner/precision/MobilePrecisionClient.java) posts to the portal's `/precision/bind` endpoint when `MobileCaseRunner.bind()` ends with more than one actionable text-contains hit; the runner trusts the picked id only when it maps back to one of the hits sent, and treats every error as a best-effort fallback to the existing not-found path.
- **Figma Validate per-step on mobile** — shipped 2026-11-22 (phases 4 + 5). The runner captures per-step PNGs when `KEEL_RUNNER_CAPTURE_STEP_PNG=true` (default on), uploads them to the portal, and [`MobileDesignComparePhase`](../src/main/java/delivery/mobile/run/MobileDesignComparePhase.java) runs the comparator on `finish()`.
- **Mobile suite scheduling (pinned device + APK)** — shipped 2026-11-22 / 2026-11-23 (phases 6 + 6b). `SuiteScheduleDispatchService.dispatchMobile` pins a device + revision on the schedule; `/suites/schedule` grows a Phone + APK picker for Android projects, with a preflight that blocks "turn on" without a pinned device.

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

## 8. Validator codes

`MobileFrameworkContractValidator` enforces the subset of rules that have a direct emit analogue. `MobileFrameworkRulesDocSyncTest` fails if a code lives in the validator but is missing here, or vice versa.

| Validator code | Severity | KEEL-M rule | What it catches |
| --- | --- | --- | --- |
| `FW_RULE_M_APPIUM_ONLY` | **MANDATORY** | KEEL-M-2 | Screen imports a non-Appium / non-project package (Playwright, Chrome driver, …). |
| `FW_RULE_M_NO_CSS` | **MANDATORY** | KEEL-M-3, §7 | `By.cssSelector` appears anywhere in a Screen — invalid in NATIVE_APP. |
| `FW_RULE_M_NO_SLEEP` | **MANDATORY** | KEEL-M-RT-2 | `Thread.sleep` / `TimeUnit.*.sleep` in a Screen or Test. |
| `FW_RULE_M_SETTLE_DELEGATE` | **MANDATORY** | KEEL-M-4 | Screen action method calls `driver.findElement(…)` directly instead of going through `SettledMobileActions`. |
| `FW_RULE_M_NO_SYSOUT` | RECOMMENDED | KEEL-M-RT-5 | `System.out` / `System.err` in a Screen or Test. |
| `FW_RULE_M_TEST_BASE` | **MANDATORY** | KEEL-M-RT-3 | Generated test class does not extend `BaseMobileTest`. |
| `FW_RULE_M_TEST_NO_BY` | RECOMMENDED | KEEL-M-7 | Test file declares a raw `By` field; that belongs on the Screen. |

Not yet checked by the validator (see §6 for the honest "not supported today" list): page clustering + test ↔ screen import matching, codegen naming conformance, assertion-takes-locator discipline. These fire in the web validator (`FW_RULE_ASSERT_TAKES_LOCATOR` etc.) and are scoped as follow-ups.

## 9. Sync with the web rules

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
