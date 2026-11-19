# Keel Framework Rules

Canonical reference for the test framework Keel emits. The agent and codegen must conform; the FrameworkContractValidator checks user-written tests against the same rules.

- **Source of truth for emit shape**: `customer-framework-template/templates/*.ftl`
- **Validator**: [`FrameworkContractValidator.java`](../src/main/java/delivery/agent/validation/contract/FrameworkContractValidator.java)
- **Keep this doc in sync**: any change to codegen emit, template contracts, runtime framework classes, or validator rules MUST update this file in the same commit. The agent uses this doc as its canonical rules reference.
- **Sync check**: [`FrameworkRulesDocSyncTest`](../src/test/java/delivery/agent/validation/contract/FrameworkRulesDocSyncTest.java) fails if a `FW_RULE_*` code lives in the doc but not the validator, or the other way round.

### Severities today

| Rule ID | Validator code | Severity |
| --- | --- | --- |
| KEEL-R-3c | `FW_RULE_ASSERT_TAKES_LOCATOR` | **MANDATORY** (blocks emit) |
| KEEL-R-STEP-SAFE | `FW_RULE_NO_STEP_ON_VALUE_METHODS` | **MANDATORY** (blocks emit) |
| KEEL-R-2 | `FW_RULE_LOCAL_REUSE` | RECOMMENDED |
| KEEL-R-3a | `FW_RULE_REUSE_ACTIONS` | RECOMMENDED |
| KEEL-R-4 | `FW_RULE_LOCATOR_LADDER` | RECOMMENDED |
| KEEL-R-TEST-NO-BY | `FW_RULE_TEST_NO_RAW_BY` | RECOMMENDED |
| KEEL-R-TEST-DESC | `FW_RULE_TEST_DESC_REQUIRED` | RECOMMENDED |
| KEEL-R-NO-SYSOUT | `FW_RULE_NO_SYSOUT` | RECOMMENDED |
| KEEL-R-BY-VIS | `FW_RULE_LOCATOR_FIELD_VISIBILITY` | RECOMMENDED |

---

## 1. User-stated rules

### KEEL-R-1 — 1 TC = 1 test class
Each TC renders to one test class file. Re-emits edit the same file; new TCs create new files.
- Enforced by: `CodegenTestClasses.classNameFor`, `CodegenTestClasses.groupKey`.
- Partial / failed TCs emit under `src/test/java/project/tests/todo/<Stem>_TestTodo.java` with `@Test(enabled=false)` and `Assert.fail("STOPPED HERE: ...")`.

### KEEL-R-2 — Local reuse via private fields/methods (not constants)
Repeated literals/method bodies inside one test class become `private` fields or methods (not `private static final` constants).
- Validator: `FW_RULE_LOCAL_REUSE` (flags ≥ 2 occurrences of the same string literal in one class).

### KEEL-R-3a — Reuse Actions / utils (don't call Selenium directly)
Tests and page objects call the project's Action/util classes; never `driver.findElement(...)`, `new WebDriverWait(...)`, or `new org.openqa.selenium.interactions.Actions(...)`.
- Validator: `FW_RULE_REUSE_ACTIONS`.
- Facades: `project.utils.Actions.Act`, `project.utils.Actions.Browse`, `project.utils.Actions.Alerts`, `project.utils.Actions.Frames`.

### KEEL-R-3b — Static facades for reused Actions / assertions
Reused action/assertion entry points are static (no instance required), callable as `Act.click(by)`, `Browse.navigateToUrl(url)`, `Validate.elementVisable(by)`, `Verify.textContains(by, s)`.
- Internal facades resolve the driver via `WebDriverFactory.getDriver()` (`ThreadLocal<WebDriver>`).

### KEEL-R-3c — Assertions take a locator, no HTML / body scan
Every assertion compares expected vs actual against a specific element locator, or checks that element's visibility. Never scan the page's HTML or body text.
- Validator: `FW_RULE_ASSERT_TAKES_LOCATOR` — **MANDATORY**, blocks emit. Flags `bodyTextContains`, `driver.getPageSource()`, `driver.getTitle()`, `driver.getCurrentUrl()`.
- Runtime: `bodyTextContains` is removed from `project.validations.Assertion` so a user cannot hand-write it and compile.
- Codegen: a `textContains` step with no locator demotes its TC to `TcStatus.PARTIAL` and lands in `tests/todo/` with a `failureReason` naming the broken assertion. See `CodegenEmitFilters.filterOutcome`. There is no legitimate code path that produces an assertion without a locator: if an element can't be located the step is marked TODO; if it can, the step emits with that locator.

### KEEL-R-4 — Locator ladder
Field strategies, best first:
1. Project's user-preferred hook (`PreferredHooksStore.current()`)
2. `id`
3. `name`
4. Simple CSS attribute selector: `[test=""]`, `div[test=""]`, `data-*`, `aria-label`
5. Indexed xpath only when index is actually required: `(//test[@test=""])[2]`
- Validator: `FW_RULE_LOCATOR_LADDER`.
- Codegen: `KeelBy` ranker + `MultiLocatorBag` carry fallbacks; preferred-hook wins first.

### KEEL-R-5 — No test-data env prefix
No `sit_`, `uat_` style prefixes baked into property keys. Switch envs via runtime (`-Denv=...`) if needed.

### KEEL-R-6 — No comments in the generated framework
Generated page and test files ship without comments (block, Javadoc, line).
- Enforced by: `CodeWriter.stripJavaComments` on every emit. String literals (URLs, XPath values) are preserved.
- For load-bearing data a template needs to preserve through the stripper, use `LogsManager.info("…")` (like REVIEW notes do).

---

## 2. Rules the codebase enforces (implied / derived)

### KEEL-R-STEP-SAFE — @Step is forbidden on methods taking String args
`@Step` + a `String` parameter leaks the arg via Allure's aspect weaver. Value-bearing methods (`type_*` / `select_*` / `login`) deliberately omit `@Step`; they still appear in Allure via `AllureSteps.run("<methodName>", ...)`, which handles secret redaction via `Secrets.register` / `Secrets.redact`.
- Validator: `FW_RULE_NO_STEP_ON_VALUE_METHODS` — **MANDATORY**, blocks emit. Flags `@Step` on any method whose first parameter is `String`.
- Covers the generic form: `@Step("x") public <T> void foo(String s)` is still caught by the regex.

### KEEL-R-TEST-NO-BY — Tests don't instantiate raw By
Test classes must call page methods only. Locators belong on the page object.
- Validator: `FW_RULE_TEST_NO_RAW_BY` — flags any `By.cssSelector(...)`, `By.xpath(...)`, `By.id(...)`, etc. inside a `*_Test.java` file.

### KEEL-R-TEST-DESC — Every @Test carries a non-blank description
`@Test(description = "<TC title/summary>")`. The TC description carries over from the IR.
- Validator: `FW_RULE_TEST_DESC_REQUIRED` — flags `@Test` with no `description` or an empty `description`.

### KEEL-R-NO-SYSOUT — No System.out / System.err in generated code
Generated code must log via `LogsManager` only.
- Validator: `FW_RULE_NO_SYSOUT` — flags `System.out.print(ln|f)` and `System.err.print(ln|f)` in page and test files.

### KEEL-R-BY-VIS — Locator fields are `protected final By`
Page classes declare every `By` field as `protected final By`. No `public`/`private` locators, no mutable locators, no bare package-private.
- Rationale: `protected` lets a hand-written project subclass override one locator without rewriting the page; `final` prevents accidental mutation at runtime.
- Validator: `FW_RULE_LOCATOR_FIELD_VISIBILITY` — flags any `By` field whose declaration is not exactly `protected final By` (parameterized row/list factory methods are not fields and are not flagged).

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
- `@Step` annotation on each no-arg page method and on every assertion (via Allure's aspect weaver, `import io.qameta.allure.Step;`).
- **Secret-safe exception**: see KEEL-R-STEP-SAFE above.
- `LogsManager.info("Step: <action> on <fieldName>")` fires after every action method body, and `LogsManager.info("Step: assert <assertionType> on <fieldName>")` after every assertion method body (outside the `AllureSteps.run` lambda so it only logs on success). The log describes what happened on the locator, never the value — value logging is Allure's job, redacted via `Secrets`.

### Allure / reporting
- `AllureSteps.run(name, Runnable)` wraps every action and assertion in a soft-assert-aware step: starts a named step, checks `Validation.pendingFailureCount()` to decide PASSED vs FAILED, redacts secrets on exception, re-throws redacted `AssertionError` when the message carries a secret.
- Attachments and traces route through `AllureAttachmentManager`, `AllureDownloadManager`, `AllureEnviromentsManager`.
- Reports generated via `AllureReportGenerator`.

### Local Allure report generation
The generated framework ships with `allure-maven` wired. Two helper scripts live at the framework root:
- `generate-allure-report.sh` (POSIX)
- `generate-allure-report.bat` (Windows)

Both run `mvn clean test` (failures ignored) then `mvn allure-maven:report`, producing `test-output/target/site/allure-report/index.html`. Users open or host that HTML. Server-side post-emit generation (running headless Chrome on the Keel server and bundling the report alongside the delivery ZIP) is tracked as open work in section 3.

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

### Test data override (user-owned, Keel never touches)
- `src/test/resources/test-data/delivery-testdata.override.properties` — user-maintained. Keel does not write to it on any Automate run.
- `PropertyReader.loadProperties` loads it **last**, so its values override any key that was regenerated into `delivery-testdata.properties`. Runtime `-D<key>=<value>` still wins over it.
- The template ships with a header-only version of the file explaining the convention.

### Runtime contract pinning
- Pages that use smart locators emit a `static { KeelLocate.requireContract(N); }` block that fails fast when the framework runtime is older than the generated locators need.

### Portal UI signals
- Project page → Automation tab shows **"Delete current framework"** (removes live pages + generated tests, keeps version ZIPs and proven-case drafts).
- Project summary strip shows **Last AI task** (type, status, time) from the latest agent-run task.

---

## 3. Open rules worth adding

- **Mandatory severity for KEEL-R-4** — `FW_RULE_LOCATOR_LADDER` still RECOMMENDED. Promote once the ranker is trusted end-to-end.
- **Mandatory severity for KEEL-R-2 and KEEL-R-3a** — `FW_RULE_LOCAL_REUSE` and `FW_RULE_REUSE_ACTIONS` still RECOMMENDED. Promote when ready.
- **Page constructor shape** — only `public <Page>(WebDriverFactory driver)`. No no-arg, no overloaded constructors. Hard emit assertion. (Validator rule not added yet.)
- **Page objects fields are `protected final By`** — no mutable locators, no `public`/`private` locators (protected lets a hand-written subclass override for a specific project variant). (Validator rule not added yet.)
- **IR-level reject for `textContains` without a locator** — today the emit demotes to TODO; upstream could reject before codegen ever sees it (cleaner, stops bad IR earlier).
- **Server-side post-emit Allure report** — run the generated suite headless after codegen packaging and bundle the Allure HTML report alongside the ZIP, so the user can open the report without running the tests themselves. Needs a headless Chrome image, Maven exec, Allure CLI, and output-pipeline wiring; scope as a separate initiative. Today the `generate-allure-report.sh`/`.bat` helpers give the user a one-command path to the report locally.

---

## 4. Agent validation flow

When the agent generates or edits a framework file:

1. **Automatic**: `FrameworkContractValidator.validate(root, …)` runs over all test + page files. Findings with code `FW_RULE_*` surface in the agent-run report. MANDATORY findings block emit; RECOMMENDED findings warn.
2. **This doc, as LLM context**: `AgentHarnessJobBridge` copies `docs/FRAMEWORK_RULES.md` into the agent's isolated workspace at `.keel/FRAMEWORK_RULES.md` right after extracting the framework ZIP. The agent is instructed (via its objective) to read that file first. Call sites with an LLM session (`CodeWriter` naming hooks, planner prompts) can also prepend the file contents as a `system`-role message before user instructions.
3. **Severity dial**: adjust `ValidationSeverity` per rule in [`FrameworkContractValidator.java`](../src/main/java/delivery/agent/validation/contract/FrameworkContractValidator.java) when a rule must block emit instead of warn.
4. **Sync discipline**: every commit that touches codegen emit, template contracts, runtime framework classes, or validator rules updates this file too. Enforced by [`FrameworkRulesDocSyncTest`](../src/test/java/delivery/agent/validation/contract/FrameworkRulesDocSyncTest.java) for FW_RULE_* codes; the broader sync is a memory rule (`framework-rules-doc`).

---

## 5. Rule-to-code map

| Rule ID | Validator code | Severity | Primary file |
| --- | --- | --- | --- |
| KEEL-R-2 Local reuse | `FW_RULE_LOCAL_REUSE` | RECOMMENDED | [`FrameworkContractValidator.java`](../src/main/java/delivery/agent/validation/contract/FrameworkContractValidator.java) |
| KEEL-R-3a/3b Reuse Actions | `FW_RULE_REUSE_ACTIONS` | RECOMMENDED | same |
| KEEL-R-3c Assertion takes locator | `FW_RULE_ASSERT_TAKES_LOCATOR` | **MANDATORY** | same |
| KEEL-R-4 Locator ladder | `FW_RULE_LOCATOR_LADDER` | RECOMMENDED | same |
| KEEL-R-STEP-SAFE @Step safety | `FW_RULE_NO_STEP_ON_VALUE_METHODS` | **MANDATORY** | same |
| KEEL-R-TEST-NO-BY No raw By in tests | `FW_RULE_TEST_NO_RAW_BY` | RECOMMENDED | same |
| KEEL-R-TEST-DESC @Test description | `FW_RULE_TEST_DESC_REQUIRED` | RECOMMENDED | same |
| KEEL-R-NO-SYSOUT No System.out | `FW_RULE_NO_SYSOUT` | RECOMMENDED | same |
| KEEL-R-BY-VIS Locator field visibility | `FW_RULE_LOCATOR_FIELD_VISIBILITY` | RECOMMENDED | same |
| KEEL-R-6 No comments | — | — | [`CodeWriter.stripJavaComments`](../src/main/java/delivery/codegen/CodeWriter.java) |
| Page merging | — | — | [`PageAccumulator.pageKey`](../src/main/java/delivery/codegen/PageAccumulator.java) |
| Field / action / assertion reuse | — | — | [`PageAccumulator`](../src/main/java/delivery/codegen/PageAccumulator.java) |
| TODO routing | — | — | [`CodegenEmitFilters.filterOutcome`](../src/main/java/delivery/codegen/CodegenEmitFilters.java) |
| Framework delete | — | — | [`ProjectController.deleteLiveFramework`](../src/main/java/delivery/portal/api/ProjectController.java), [`project-detail.html`](../src/main/resources/templates/project-detail.html) |
| Test-data override | — | — | [`PropertyReader.loadProperties`](../customer-framework-template/src/main/java/project/utils/dataReader/PropertyReader.java) |

---

## 6. Last changed

One line per rule-touching change. Newest first.

- 2026-11-19 — added KEEL-R-BY-VIS (`FW_RULE_LOCATOR_FIELD_VISIBILITY`, RECOMMENDED); built `AllureReportBuilder` + wired into `EmitPhase` behind `keel.framework.allure.autoreport=true`; rendered this doc on the admin **Prompts** page (read-only).
- 2026-11-19 — added KEEL-R-TEST-NO-BY, KEEL-R-TEST-DESC, KEEL-R-NO-SYSOUT (all RECOMMENDED); added KEEL-R-* IDs to every rule; `LogsManager.info` now logs `"Step: <action> on <fieldName>"` instead of the method name; wired `AgentHarnessJobBridge` to drop this doc into the agent workspace; added `FrameworkRulesDocSyncTest` to catch future drift; added `allure-maven` plugin + `generate-allure-report.sh`/`.bat` helpers to the template.
- 2026-11-19 — added KEEL-R-STEP-SAFE (`FW_RULE_NO_STEP_ON_VALUE_METHODS`, MANDATORY); promoted KEEL-R-3c to MANDATORY; added test-data override file + `PropertyReader.loadOverride`; removed `bodyTextContains` from `Assertion.java`; `@Step` on every no-arg page method.
- 2026-11-19 — added KEEL-R-3b static facades (Act, Browse, Validate, Verify); migrated `BaseTest` + `SettledActions` to the facades; `bodyTextContains` no longer emitted by codegen.
- 2026-11-18 — added KEEL-R-3c (`FW_RULE_ASSERT_TAKES_LOCATOR`, started RECOMMENDED); TODO demotion for locator-free `textContains`.
