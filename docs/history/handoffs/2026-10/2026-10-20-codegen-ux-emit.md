# 2026-10-20 — Codegen UX emit

**Status:** Implemented on main.

Spec: `docs/superpowers/specs/2026-10-20-codegen-ux-emit-design.md`

## What shipped

- **Emit filters:** drop `:echo`, `:unknown-echo`, `:pageShown`, html-only visibility, and brittle `intent:ASSERT_VISIBLE:field=html` steps from the customer ZIP (IR unchanged).
- **Login collapse:** leading type/click login sequences collapse to `login(user, pass)` on the page class; asserts between login steps no longer block collapse.
- **Assertions:** proven locators for `textContains` (including `//body`); locator-free text still uses `bodyTextContains`.
- **Page + test classes:** from **page under test** (IR `pageName` / URL leaf), e.g. `Login_Page.java` + `Login_Test.java` for `/login`. Excel titles are not used for class names. **Same page stem → one test class** with multiple `@Test` methods (descriptions still carry TC id + title). Unknown page → `TC_IDTest` per case.
- **Primary page pick:** last **non-login** body step when present (multi-page flows).
- **Test methods:** short page stem (e.g. `login()`); `@Test(description)` keeps `TC_ID — Excel title`. Optional Ollama polish capped at 11 words.
- **Test layout:** class-level page fields, init in `@Test`; `@BeforeMethod` runs prerequisite setup via a private helper with **local** page vars only.
- **Test data:** `PropertyReader.getProperty` / `Secrets.property` in generated calls. Login placeholders `${TARGET_USERNAME}` / `${TARGET_PASSWORD}` map to **`{env}_username` / `{env}_password`** when project **Test data env** is set; otherwise **`username` / `password`** (no env prefix). TC-scoped keys unchanged for normal typed fields.
- **Project setting:** optional **Test data env** on Project → Settings (`testDataEnv` API + DB column).
- **Surefire:** customer template includes `**/*Test.java` under generated tests.

## How to use

1. Rebuild jar, restart portal, Ctrl+F5.
2. Optional: Project → Settings → **Test data env** (e.g. `sit`) before Automate **NEW** or **UPDATE**.
3. Fill `delivery-testdata.properties` (and secrets sidecar) with the keys the ZIP references.
4. Re-run Automate on a project to refresh generated tests and pages.

## Verification

- `mvn -pl keel-portal test "-Dtest=delivery.codegen.GeneratedZipReplayBenchmarkTest"`
- Non-live `delivery.codegen.*` suite (exclude `*Live*` and benchmark if you need a quick pass).

## Known limits

- Page **stems** still follow URL/host normalization (meaningful screen titles in page class names are a follow-up).
- File upload steps remain unsupported in authoring/codegen.
- Pre-existing unrelated test failures outside `delivery.codegen` may still exist on main.
