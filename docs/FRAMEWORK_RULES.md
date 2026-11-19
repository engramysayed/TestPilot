# Keel Framework Rules

Canonical reference for the test framework Keel emits. The agent and codegen must conform; the FrameworkContractValidator checks user-written tests against the same rules.

- **Source of truth for emit shape**: `customer-framework-template/templates/*.ftl`
- **Validator**: [`FrameworkContractValidator.java`](../src/main/java/delivery/agent/validation/contract/FrameworkContractValidator.java)
- **Severity today**: all FW_RULE_* findings are RECOMMENDED (warn, do not block emit). Promote to MANDATORY when tightening.

---

## 1. User-stated rules

### Rule 1 — 1 TC = 1 test class
Each TC renders to one test class file. Re-emits edit the same file; new TCs create new files.
- Enforced by: `CodegenTestClasses.classNameFor`, `CodegenTestClasses.groupKey`.
- Partial / failed TCs emit under `src/test/java/project/tests/todo/<Stem>_TestTodo.java` with `@Test(enabled=false)` and `Assert.fail("STOPPED HERE: ...")`.

### Rule 2 — Local reuse via private fields/methods (not constants)
Repeated literals/method bodies inside one test class become `private` fields or methods (not `private static final` constants).
- Validator: `FW_RULE_LOCAL_REUSE` (flags ≥ 2 occurrences of the same string literal in one class).

### Rule 3a — Reuse Actions / utils (don't call Selenium directly)
Tests and page objects call the project's Action/util classes; never `driver.findElement(...)`, `new WebDriverWait(...)`, or `new org.openqa.selenium.interactions.Actions(...)`.
- Validator: `FW_RULE_REUSE_ACTIONS`.
- Facades: `project.utils.Actions.Act`, `project.utils.Actions.Browse`, `project.utils.Actions.Alerts`, `project.utils.Actions.Frames`.

### Rule 3b — Static facades for reused Actions / assertions
Reused action/assertion entry points are static (no instance required), callable as `Act.click(by)`, `Browse.navigateToUrl(url)`, `Validate.elementVisable(by)`, `Verify.textContains(by, s)`.
- Internal facades resolve the driver via `WebDriverFactory.getDriver()` (`ThreadLocal<WebDriver>`).

### Rule 3c — Assertions take a locator, no HTML / body scan
Every assertion compares expected vs actual against a specific element locator, or checks that element's visibility. Never scan the page's HTML or body text.
- Validator: `FW_RULE_ASSERT_TAKES_LOCATOR` (flags `bodyTextContains`, `driver.getPageSource()`, `driver.getTitle()`, `driver.getCurrentUrl()`).
- Codegen: a `textContains` step with no locator demotes its TC to `TcStatus.PARTIAL` and lands in `tests/todo/` with a `failureReason` naming the broken assertion. See `CodegenEmitFilters.filterOutcome`.

### Rule 4 — Locator ladder
Field strategies, best first:
1. Project's user-preferred hook (`PreferredHooksStore.current()`)
2. `id`
3. `name`
4. Simple CSS attribute selector: `[test=""]`, `div[test=""]`, `data-*`, `aria-label`
5. Indexed xpath only when index is actually required: `(//test[@test=""])[2]`
- Validator: `FW_RULE_LOCATOR_LADDER`.
- Codegen: `KeelBy` ranker + `MultiLocatorBag` carry fallbacks; preferred-hook wins first.

### Rule 5 — No test-data env prefix
No `sit_`, `uat_` style prefixes baked into property keys. Switch envs via runtime (`-Denv=...`) if needed.

### Rule 6 — No comments in the generated framework
Generated page and test files ship without comments (block, Javadoc, line).
- Enforced by: `CodeWriter.stripJavaComments` on every emit. String literals (URLs, XPath values) are preserved.
- For load-bearing data a template needs to preserve through the stripper, use `LogsManager.info("…")` (like REVIEW notes do).

---

## 2. Rules the codebase enforces (implied / derived)

### Pages and element reuse
- **Page merging**: `Login` / `LoginForm` / `LoginPage` / `TargetLogin` collapse onto the `Login` page key. Other names use their own key.
- **Field reuse**: same `locatorIdentity(strategy, value)` + frame context in the same page → same field name returned (no duplicate `By ...`).
- **Action reuse**: `(pageKey, locator, action, secret, context)` uniquely keys a page method. Duplicates short-circuit at `allocateAction`.
- **Assertion reuse**: `(pageKey, assertionType, locator [+ expected], context)` uniquely keys a page assertion. Parameterized assertions key by type + locator only (expected is a parameter).
- **Load marker**: pages expose `boolean isLoaded()` returning whether a stable marker element (preferred hook / id / data-test) is visible.
- **Login helper**: a page with user + password + submit methods also emits `void login(String, String)`.

### Test method shape
- `@Test(description = "<TC title and summary>")` — the TC description carries over from the IR.
- Read-only cases: add `@KeelReadOnly`, `groups = {KeelReadOnly.GROUP}`, `retryAnalyzer = KeelRetryAnalyzer.class`.
- `@Step` annotation on each page method and assertion (via Allure's aspect weaver).
- **Secret-safe exception**: `type_/select_` methods that take `String value` and the login helper deliberately omit `@Step` so Allure's aspect weaver does not log parameter values. Those methods still appear in Allure via `AllureSteps.run("<methodName>", ...)`, which handles secret redaction via `Secrets.register` / `Secrets.redact`.

### Allure / reporting
- `AllureSteps.run(name, Runnable)` wraps every action and assertion in a soft-assert-aware step: starts a named step, checks `Validation.pendingFailureCount()` to decide PASSED vs FAILED, redacts secrets on exception, re-throws redacted `AssertionError` when the message carries a secret.
- Attachments and traces route through `AllureAttachmentManager`, `AllureDownloadManager`, `AllureEnviromentsManager`.
- Reports generated via `AllureReportGenerator`.

### Driver lifecycle
- `WebDriverFactory` holds a `ThreadLocal<WebDriver>`. One driver per TestNG thread.
- `WebDriverFactory.getDriver()` is the static accessor used by every facade.
- `BaseTest.@BeforeMethod setUp` creates the factory, navigates to `BASE_WEB`, then calls `loginIfConfigured()` (default no-op; generated tests override).
- `BaseTest.@AfterMethod tearDown` runs `Validation.assertAll()` then `driver.quit()`.

### Retry + flakiness
- `KeelRetryListener` wires retries via `KeelRetryAnalyzer`.
- Read-only cases retry; destructive cases do not (by default).

### Secrets
- IR `secret: true` values are **never** written to the shipped test-data properties file. They land in `delivery-secrets.local.properties` (local only, excluded from the customer ZIP).
- `Secrets.register(value)` in generated code marks a value for redaction across logs, Allure, and `keel-results` JSONL.
- `AllureSteps.run` scrubs `.getMessage()` via `Secrets.redact` before putting it on the failing step.

### Runtime contract pinning
- Pages that use smart locators emit a `static { KeelLocate.requireContract(N); }` block that fails fast when the framework runtime is older than the generated locators need.

### Portal UI signals
- Project page → Automation tab shows **"Delete current framework"** (removes live pages + generated tests, keeps version ZIPs and proven-case drafts).
- Project summary strip shows **Last AI task** (type, status, time) from the latest agent-run task.

---

## 3. Open rules worth adding

- **Test-data preservation on re-emit** — currently `delivery-testdata.properties` is overwritten wholesale. Manual edits are lost. Options:
  - (a) Add `delivery-testdata.override.properties` — user-owned, Keel never touches it, `PropertyReader` loads it last so overrides win.
  - (b) Merge mode — read existing file first; keep values where the key already exists (value-level preservation); overwrite only when the IR explicitly supplies a new value.
  - (c) Marker-based preservation — `# keel-preserve` comment above a key makes Keel skip rewriting it.
  - Recommended: (a). Cheapest, no merge logic, clear ownership.
- **Mandatory severity for Rule 3c and Rule 4** — today all four rules are RECOMMENDED. Make them MANDATORY once the recorder is trusted to emit compliant steps.
- **No direct Selenium calls in tests** — tests must call page methods only; pages call `Act`/`Browse`/`Validate`/`Verify`. Partially covered by `FW_RULE_REUSE_ACTIONS` (which only flags raw driver/wait/actions constructors). Expand to flag any `org.openqa.selenium.By` instantiation inside a `*Test` class.
- **@Test(description) non-blank check** — every generated test method must have a non-blank description. A hard emit assertion.
- **Page-object constructor is single-shaped** — only `public <Page>(WebDriverFactory driver)`. No no-arg, no overloaded constructors. Hard emit assertion.
- **Page objects fields are `protected final By`** — no mutable locators, no `public`/`private` locators (protected lets a hand-written subclass override for a specific project variant).
- **IR-level reject for `textContains` without a locator** — today the emit demotes to TODO; upstream could reject before codegen ever sees it (cleaner, stops bad IR earlier).

---

## 4. Agent validation flow

When the agent generates or edits a framework file:

1. **Automatic**: `FrameworkContractValidator.validate(root, …)` runs over all test + page files. Findings with code `FW_RULE_*` surface in the agent-run report.
2. **This doc**: pass the sections above as context to the LLM prompt so the agent knows the semantic rules the validator doesn't yet check (e.g., "no constants, use private fields"; "Allure @Step on no-arg methods only"; "preserve manual test-data edits via override file").
3. **Severity dial**: adjust `ValidationSeverity` per rule in [`FrameworkContractValidator.java`](../src/main/java/delivery/agent/validation/contract/FrameworkContractValidator.java) when a rule must block emit instead of warn.

---

## 5. Rule-to-code map

| Rule | Validator code | Primary file |
| --- | --- | --- |
| 2 Local reuse | `FW_RULE_LOCAL_REUSE` | [`FrameworkContractValidator.java`](../src/main/java/delivery/agent/validation/contract/FrameworkContractValidator.java) |
| 3a / 3b Reuse Actions | `FW_RULE_REUSE_ACTIONS` | same |
| 3c Assertion takes locator | `FW_RULE_ASSERT_TAKES_LOCATOR` | same |
| 4 Locator ladder | `FW_RULE_LOCATOR_LADDER` | same |
| 6 No comments | — | [`CodeWriter.stripJavaComments`](../src/main/java/delivery/codegen/CodeWriter.java) |
| Page merging | — | [`PageAccumulator.pageKey`](../src/main/java/delivery/codegen/PageAccumulator.java) |
| Field / action / assertion reuse | — | [`PageAccumulator`](../src/main/java/delivery/codegen/PageAccumulator.java) |
| TODO routing | — | [`CodegenEmitFilters.filterOutcome`](../src/main/java/delivery/codegen/CodegenEmitFilters.java) |
| Framework delete | — | [`ProjectController.deleteLiveFramework`](../src/main/java/delivery/portal/api/ProjectController.java), [`project-detail.html`](../src/main/resources/templates/project-detail.html) |
