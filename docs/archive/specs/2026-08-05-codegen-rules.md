# Customer codegen rules (locked for Track A)

**Date**: 2026-08-05  
**Source**: Product owner rules for locators, POM pages, tests, soft asserts

## Locator priority (MUST)

Try in this order only:

1. **`id`**
2. **`data-testid` / `data-test` / `data-qa`** (treat as “data test id”)
3. **`name`**
4. **CSS** — only attribute form: `tag[attribute='value']`  
   - No pseudo-classes (`:nth-child`, `:hover`, etc.)  
   - No complex combinators unless unavoidable
5. **XPath** — only `//tag[@attribute='value']` form  
   - No absolute `/html/...`  
   - Prefer single-attribute predicates

Reject: dynamic ids (uuid/long hashes), absolute html xpath, CSS pseudo-classes.

## Page classes (POM only)

Package: `project.pages` only.

Per page stem (from live URL path), emit:

1. `{Stem}_Locators` — protected `By` fields with `_Locator` suffix (`username_Txt_Locator`, `submit_Btn_Locator`, …)
2. `{Stem}_Actions` — extends Locators; `@Step`-aligned methods (`type_Username`, `click_Submit_Button`, `assert_Logged_In_Successfully_Is_Visible`)

Do not freeze a separate `Login` page — login form steps use the login URL stem (e.g. `PracticeTestLogin_*`).

Constructor takes `WebDriverFactory` (TAF style). No new base packages.

## Test classes

Package: `project.tests.generated` (passed) or `project.tests.todo` (failed).

- Extend `project.tests.BaseTest`
- Import `project.pages.{Stem}_Actions` (no FQCN at call sites)
- `@Test(description = "{TC_ID} — {Excel title}")`
- Use TestNG `@BeforeMethod` / `@AfterMethod`
- Test methods call **Actions** page methods only
- Soft assertions; log failures (use TAF soft path / `Validation` + `LogsManager`)

## Hard limits

- Do **not** invent packages outside `project.pages` and `project.tests*`
- Do **not** invent free-form framework architecture (drivers/CI stay in TAF core)
- AI authors steps/locators only; Freemarker emits Java shape
