# Codegen Void Pages, Merge, and Assert Helpers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emit one page class pair per screen (no Login/LoginForm twins), void action/assert methods, prefer submit/button locators over form containers, and drive asserts through Assertion helpers instead of invented Freemarker bodies.

**Architecture:** Extract `PageNameNormalizer` + locator preference scoring used by `ProvePhase` stamps and `PageAccumulator`. Update Freemarker `PageActions.java.ftl` to `void` + helper one-liners. Add thin methods on customer-template `Assertion`. Keep prove-time waits in `TcExecutionService`; emit must not re-implement them inline.

**Tech Stack:** Java 21, TestNG, Freemarker templates under `customer-framework-template/`, existing `PageAccumulator` / `CodeWriter` / `ProvePhase`.

## Global Constraints

- Site-agnostic: no domain or the-internet hardcoding.
- Security > maintainability > performance (HelloGroup).
- No fluent page API (`return this` removed).
- Use existing Assertion helpers when enough; add new helpers only when needed.
- Do not commit unless the user explicitly asks (plan “Commit” steps are optional gates).
- Spec: `docs/superpowers/specs/2026-08-17-codegen-void-pages-and-assert-helpers-design.md`.

---

## File map

| File | Role |
|------|------|
| `src/main/java/delivery/codegen/PageNameNormalizer.java` | Alias blank/`Page`/`Login`/`LoginForm`/`TargetLogin` → URL stem |
| `src/main/java/delivery/codegen/LocatorPreference.java` | Score locators; prefer button/submit over bare form/container id |
| `src/main/java/delivery/codegen/PageAccumulator.java` | Merge fields; apply preference on click field collisions |
| `src/main/java/delivery/job/ProvePhase.java` | `stampPageNames` / `stampLoginNames` call normalizer (same stem) |
| `customer-framework-template/templates/PageActions.java.ftl` | `void` methods; helper-only asserts |
| `customer-framework-template/src/main/java/project/validations/Assertion.java` | New helpers |
| Tests under `src/test/java/delivery/codegen/` | Normalizer, preference, accumulator, CodeWriter template expectations |

---

### Task 1: PageNameNormalizer

**Files:**
- Create: `src/main/java/delivery/codegen/PageNameNormalizer.java`
- Create: `src/test/java/delivery/codegen/PageNameNormalizerTest.java`
- Modify: `src/main/java/delivery/job/ProvePhase.java` (`stampPageNames`, `stampLoginNames`)
- Modify: `src/main/java/delivery/codegen/PageClusterer.java` (`reclusterSteps` — today aliases `Login`/`TargetLogin` but **not** `LoginForm`; must use `PageNameNormalizer`)

**Interfaces:**
- Produces: `PageNameNormalizer.canonical(String pageName, String urlStem)` → `String`
- Produces: `PageNameNormalizer.isAlias(String pageName)` → `boolean`
- Consumes: `PageClusterer.pageNameFromUrl(String)` for stems (callers pass stem in)

- [ ] **Step 1: Write failing tests**

```java
@Test
public void aliasesCollapseToUrlStem() {
    Assert.assertEquals(PageNameNormalizer.canonical("Login", "FormAuthentication"), "FormAuthentication");
    Assert.assertEquals(PageNameNormalizer.canonical("LoginForm", "FormAuthentication"), "FormAuthentication");
    Assert.assertEquals(PageNameNormalizer.canonical("TargetLogin", "FormAuthentication"), "FormAuthentication");
    Assert.assertEquals(PageNameNormalizer.canonical("Page", "FormAuthentication"), "FormAuthentication");
    Assert.assertEquals(PageNameNormalizer.canonical("", "FormAuthentication"), "FormAuthentication");
    Assert.assertEquals(PageNameNormalizer.canonical("Secure", "FormAuthentication"), "Secure");
}

@Test
public void blankStemFallsBackToLoginForAliases() {
    Assert.assertEquals(PageNameNormalizer.canonical("LoginForm", "Page"), "Login");
    Assert.assertEquals(PageNameNormalizer.canonical("LoginForm", "Home"), "Login");
    Assert.assertEquals(PageNameNormalizer.canonical("LoginForm", ""), "Login");
}
```

- [ ] **Step 2: Run tests — expect FAIL (class missing)**

```bash
mvn -Dtest=PageNameNormalizerTest "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21" test
```

- [ ] **Step 3: Implement**

```java
public final class PageNameNormalizer {
    private PageNameNormalizer() {}

    public static boolean isAlias(String pageName) {
        if (pageName == null || pageName.isBlank()) return true;
        String n = pageName.trim();
        if ("Page".equals(n)) return true;
        return "Login".equalsIgnoreCase(n)
                || "LoginForm".equalsIgnoreCase(n)
                || "TargetLogin".equals(n);
    }

    public static String canonical(String pageName, String urlStem) {
        String stem = urlStem == null ? "" : urlStem.trim();
        if (!isAlias(pageName)) {
            return pageName.trim();
        }
        if (stem.isBlank() || "Page".equals(stem) || "Home".equals(stem)) {
            return "Login";
        }
        return stem;
    }
}
```

- [ ] **Step 4: Wire ProvePhase stamps + PageClusterer.reclusterSteps**

Replace bodies of `stampPageNames` / `stampLoginNames` so every step gets:

```java
String stem = PageClusterer.pageNameFromUrl(url); // loginFormUrl for login stamp
String name = PageNameNormalizer.canonical(s.pageName(), stem);
out.add(s.withPageName(name));
```

In `PageClusterer.reclusterSteps`, replace the inline Login/TargetLogin checks with:

```java
page = PageNameNormalizer.canonical(page, defaultPage);
```

Remove the old behavior that remapped `"Login"` to URL stem while leaving `LoginForm` as a different stem.

- [ ] **Step 5: Re-run `PageNameNormalizerTest` — PASS**

- [ ] **Step 6: Commit (only if user asked)** — `feat: normalize login page aliases to one URL stem`

---

### Task 2: LocatorPreference + PageAccumulator click merge

**Files:**
- Create: `src/main/java/delivery/codegen/LocatorPreference.java`
- Create: `src/test/java/delivery/codegen/LocatorPreferenceTest.java`
- Modify: `src/main/java/delivery/codegen/PageAccumulator.java`
- Create: `src/test/java/delivery/codegen/PageAccumulatorMergeTest.java`

**Interfaces:**
- Produces: `LocatorPreference.score(String strategy, String value)` → `int` (higher = better for click/control)
- Produces: `LocatorPreference.prefer(String s1, String v1, String s2, String v2)` → `boolean` (true if first better)
- Consumes: `PageAccumulator.add(ProvenStep)` field collision path

- [ ] **Step 1: Failing tests**

```java
@Test
public void submitButtonBeatsBareFormId() {
    Assert.assertTrue(LocatorPreference.score("cssSelector", "button[type='submit']")
            > LocatorPreference.score("id", "login"));
    Assert.assertTrue(LocatorPreference.prefer(
            "cssSelector", "button[type='submit']", "id", "login"));
}

@Test
public void accumulatorKeepsSubmitOverFormIdForSameClickField() {
    PageAccumulator acc = new PageAccumulator();
    acc.add(new ProvenStep("TC", "FormAuthentication", "elementAction", "click",
            "id", "login", "", "", "", true, "click_login"));
    acc.add(new ProvenStep("TC", "FormAuthentication", "elementAction", "click",
            "cssSelector", "button[type='submit']", "", "", "", true, "click_login"));
    PageAccumulator.PageModel page = acc.pages().get("FormAuthentication");
    Assert.assertEquals(page.fields().size(), 1);
    Assert.assertEquals(page.fields().get(0).value(), "button[type='submit']");
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
mvn -Dtest=LocatorPreferenceTest,PageAccumulatorMergeTest "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21" test
```

- [ ] **Step 3: Implement `LocatorPreference`**

Generic heuristics only (case-insensitive on value):

- Boost: contains `button`, `type='submit'`, `type=\"submit\"`, `type=submit`, `role=button`, `input[type=submit]`, `/button`, `btn`
- Penalize: strategy `id` or `name` whose value has no button/submit hint (bare container/form ids)
- Identical scores → keep first

- [ ] **Step 4: Update `PageAccumulator.add`**

When `fieldNames` already contains the computed field name and the new locator differs:

- If action is `click` (or existing field was used for click): compare `LocatorPreference`; if new wins, **replace** `FieldModel` value/strategy in place (same field name); do not uniquify to `_2_Locator`.
- If new loses, keep existing field; still attach methods/asserts to that field name.
- If scores tie and values differ, keep existing (first wins).

Semantic token may differ (`login_Btn` vs `click_Btn`) — also merge when both are click actions on the same page and one field is clearly preferred: if `actionMethodName` would collide (`click_Login_Button` vs `click_Click_Button`), prefer the higher-scoring locator’s field and **do not** add a second click method; map both steps to the winning field/method.

Minimal rule that fixes TC2: when adding a click field and an existing click field exists whose preference loses to the new one (or vice versa), keep a single click field if `CodegenNaming.locatorFieldName` differs only by token but both are `_Btn_Locator` — prefer higher score and one method name from the winning step.

Simpler implementable rule (use this):

1. Same strategy+value → reuse field (existing).
2. Else if same `locatorFieldName` → preference replace / keep.
3. Else if both clicks and both end with `_Btn_Locator` and one clearly outscores the other by ≥2 points → replace weaker field, remove weaker method name if unused, keep one `click_*_Button` from the winner’s `actionMethodName`.

- [ ] **Step 5: Tests PASS**

- [ ] **Step 6: Optional commit** — `fix: prefer submit button locator over form id on merge`

---

### Task 3: Assertion helpers on customer template

**Files:**
- Modify: `customer-framework-template/src/main/java/project/validations/Assertion.java`
- Create: `src/test/java/delivery/codegen/AssertionHelperSignatureTest.java` (source-text smoke: file contains method signatures — no Selenium)

**Interfaces (add to `Assertion`):**

```java
public void textContains(By locator, String expected);
public void bodyTextContains(String expected);
public void urlContains(String expected);
public void elementSelected(By locator, String expectedOptionOrEmpty);
public void elementUnchecked(By locator);
public void elementNotVisible(By locator);
```

Behavior:

- `textContains`: wait visible if possible; `String actual = element.getText(locator)`; `softTrue(actual != null && actual.contains(expected), ...)`.
- `bodyTextContains`: `driver.findElement(By.tagName("body")).getText()` contains expected → `softTrue`.
- `urlContains`: current URL contains expected → `softTrue`.
- `elementSelected`: port the select/custom-picker logic currently in Freemarker into this method (expected empty → `isSelected()` only).
- `elementUnchecked`: `!isSelected()`.
- `elementNotVisible`: no displayed matches.
- Reuse existing `elementVisable` for `visible`.
- Log errors via `LogsManager` like existing methods; do not swallow without softTrue.

- [ ] **Step 1: Failing signature test** reading `Assertion.java` for `void textContains` / `void bodyTextContains` / etc.

- [ ] **Step 2: Implement helpers**

- [ ] **Step 3: Test PASS**

- [ ] **Step 4: Optional commit** — `feat: add Assertion helpers for generated page asserts`

---

### Task 4: Void PageActions template + helper-only asserts

**Files:**
- Modify: `customer-framework-template/templates/PageActions.java.ftl`
- Modify: `src/test/java/delivery/codegen/CodeWriterTest.java` (`textContainsUsesBodyGetTextContains` → expect helpers; add void check)

**Template rules:**

```ftl
public void ${method.name}(...) {
  driver.element().type(...);  // or click / select
}

public void ${assertion.name}() {
  <#if visible> driver.validation().elementVisable(${field});
  <#elseif textContains>
    driver.validation().bodyTextContains("${expected}");
    <#-- if field is not body-only, also ok to call textContains(field, expected) when field present -->
  <#elseif urlContains> driver.validation().urlContains("${expected}");
  <#elseif checked|selected> driver.validation().elementSelected(${field}, "${expected}");
  <#elseif unchecked> driver.validation().elementUnchecked(${field});
  <#elseif notVisible> driver.validation().elementNotVisible(${field});
  <#else> ... softTrue false unsupported
  </#if>
}
```

For `textContains`: call `bodyTextContains(expected)` always (matches prove primary check); if `fieldName` is present and not a pure body xpath, also call `textContains(field, expected)` **or** prefer a single helper that does body-first then element — implement as one call `bodyTextContains` only if that matches prove; spec allows body overload. Prefer **one** call: `driver.validation().bodyTextContains("${expected}")` for textContains to avoid inventing dual logic in the page. Optionally: `textContains(field, expected)` when locator is not `//body` / body tag.

Recommended emit rule:

- If locator value is `//body` or strategy/xpath clearly body → `bodyTextContains(expected)`
- Else → `textContains(field, expected)` then if that fails prove already used body — for emit honesty with prove, use: `bodyTextContains` for all textContains (prove primary). Element locator remains for documentation in Locators class.

Use: **`bodyTextContains` for all `textContains` asserts** (locator still emitted on Locators for secondary use). Page assert method one line.

- [ ] **Step 1: Update `CodeWriterTest.textContainsUsesBodyGetTextContains`**

```java
Assert.assertTrue(actions.contains("bodyTextContains(\"Welcome back\")"), actions);
Assert.assertFalse(actions.contains("By.tagName(\"body\")"), actions);
Assert.assertTrue(actions.contains("elementSelected("), actions);
Assert.assertTrue(actions.contains("public void assert_"), actions);
Assert.assertFalse(actions.contains("return this;"), actions);
```

Add:

```java
@Test
public void actionMethodsAreVoidNotFluent() throws Exception {
    // write one click page; assert "public void click_" and no "return this"
}
```

- [ ] **Step 2: Run CodeWriterTest — FAIL on old expectations**

- [ ] **Step 3: Rewrite `PageActions.java.ftl`**

- Remove unused imports if possible (`WebElement` only if selected helper needs none in page).

- [ ] **Step 4: All `CodeWriterTest` PASS**

- [ ] **Step 5: Optional commit** — `refactor: void page actions and helper-based asserts`

---

### Task 5: Integration smoke — one login page pair

**Files:**
- Modify/create: `src/test/java/delivery/codegen/LoginPageMergeCodegenTest.java`

- [ ] **Step 1: Test**

```java
@Test
public void loginPreludeAndBodyShareOnePageClass() throws Exception {
    Path temp = Files.createTempDirectory("codegen-login-merge");
    CodeWriter writer = new CodeWriter(Path.of("customer-framework-template/templates"));
    ProvenStep preludeUser = new ProvenStep("TC1", "LoginForm", "elementAction", "type",
            "id", "username", "u", "", "", true, "ok");
    ProvenStep preludePass = new ProvenStep("TC1", "LoginForm", "elementAction", "type",
            "id", "password", "p", "", "", true, "ok");
    ProvenStep preludeClick = new ProvenStep("TC1", "LoginForm", "elementAction", "click",
            "cssSelector", "button[type='submit']", "", "", "", true, "ok");
    ProvenStep bodyUser = new ProvenStep("TC2", "Login", "elementAction", "type",
            "id", "username", "u", "", "", true, "ok");
    ProvenStep bodyPass = new ProvenStep("TC2", "Login", "elementAction", "type",
            "id", "password", "bad", "", "", true, "ok");
    ProvenStep bodyClick = new ProvenStep("TC2", "Login", "elementAction", "click",
            "id", "login", "", "", "", true, "ok");
    // Normalize names as Emit/Prove would for stem FormAuthentication
    List<ProvenStep> login = List.of(preludeUser, preludePass, preludeClick).stream()
            .map(s -> s.withPageName(PageNameNormalizer.canonical(s.pageName(), "FormAuthentication")))
            .toList();
    List<ProvenStep> body = List.of(bodyUser, bodyPass, bodyClick).stream()
            .map(s -> s.withPageName(PageNameNormalizer.canonical(s.pageName(), "FormAuthentication")))
            .toList();
    writer.write(temp, List.of(
            new TcOutcome("TC1", "ok", TcStatus.PASSED, List.of(), "", null, true, login),
            new TcOutcome("TC2", "bad", TcStatus.TODO, body, "x", null, false, List.of())
    ));
    Assert.assertTrue(Files.exists(temp.resolve(
            "src/main/java/project/pages/FormAuthentication_Actions.java")));
    Assert.assertFalse(Files.exists(temp.resolve("src/main/java/project/pages/Login_Actions.java")));
    Assert.assertFalse(Files.exists(temp.resolve("src/main/java/project/pages/LoginForm_Actions.java")));
    String loc = Files.readString(temp.resolve(
            "src/main/java/project/pages/FormAuthentication_Locators.java"));
    Assert.assertTrue(loc.contains("button[type='submit']"), loc);
    Assert.assertFalse(loc.contains("By.id(\"login\")"), loc);
}
```

- [ ] **Step 2: Implement any remaining wiring (CodeWriter should already merge via accumulator)**

- [ ] **Step 3: PASS**

- [ ] **Step 4: Update spec status line to Approved**

- [ ] **Step 5: Optional commit**

---

## Spec coverage self-review

| Spec requirement | Task |
|------------------|------|
| Canonical page stem / aliases | Task 1 |
| No Login + LoginForm twins | Task 1 + 5 |
| Prefer button/submit over form id | Task 2 + 5 |
| Void methods | Task 4 |
| Assertion helpers + template one-liners | Task 3 + 4 |
| Unit tests listed in spec | Tasks 1–5 |

Placeholder scan: none intentional. Commit steps optional per user rule.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-17-codegen-void-pages-and-assert-helpers.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute tasks in this session with checkpoints  

Which approach?
