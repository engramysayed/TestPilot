# Codegen Control Identity Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make each proven control emit a unique, human field/method/property identity so Facebook-style forms (First name, Surname, Day, Month, Year, Gender, email, Password, Submit) do not collapse into `type_Type` / `select_Select` / one shared testdata key.

**Architecture:** Fix identity at the source — `CodegenNaming.semanticToken(ProvenStep)` — by extracting labels/`aria-label`/button text from locator strings before falling back to the action verb. Keep `PageAccumulator` uniqueness as a safety net. Add an emit-time smell check so packages with collapsed names fail `test-compile`/emit rather than shipping silent broken tests. Out of scope for this plan: re-proving Facebook live (re-emit from existing IR is enough to validate).

**Tech Stack:** Java 21, TestNG, existing `CodegenNaming` / `PageAccumulator` / `CodeWriter` / `TestDataPropertiesWriter`, Facebook IR under `delivery-store/facebook-com/prj_e9a9fc7313b2/ir/`.

## Global Constraints

- Prefer label / aria-label / link text over action-verb fallbacks (`type`, `select`, `assert`, `click`).
- Never invent site-specific Facebook rules; extractors must be locator-pattern generic.
- Distinct locator values ⇒ distinct field names, action methods, and `delivery-testdata.properties` keys.
- Preserve SauceDemo-style id/data-test naming (`add-to-cart-…`, `shopping-cart-link`).
- Emit must fail closed if smell patterns remain (`type_Type`, `select_Select`, `assert_Assert_`).
- Do not commit unless asked.

---

## Evidence (last Facebook run)

| Symptom | Cause |
|---------|--------|
| `type_Type` / `select_Select` | `semanticToken` returns `step.action()` when xpath/css has no `id=`/`name=`/`data-test=` |
| Label xpaths ignored | `//label[normalize-space(.)='First name']` not parsed |
| `aria-label='Select day'` ignored | `extractAttrValue` does not read `aria-label` |
| `assert_Assert_Is_Visible` | visible assert with blank expected → token = `"assert"` |
| Same prop key for all types | `CodeWriter.propKeyFor(tcId, methodName, value)` keyed only on collapsed method |
| Locators still distinct | Fields exist for First/Surname/… but **methods** collide so tests only call one |

---

## File map

| File | Role |
|------|------|
| `src/main/java/delivery/codegen/CodegenNaming.java` | Richer `semanticToken` / attr + label extractors |
| `src/test/java/delivery/codegen/CodegenNamingTest.java` | TDD for Facebook-shaped locators |
| `src/main/java/delivery/codegen/PageAccumulator.java` | Keep one method per field; no verb-only merges |
| `src/main/java/delivery/codegen/CodegenSmellCheck.java` (new) | Fail emit on collapsed names |
| `src/main/java/delivery/codegen/CodeWriter.java` | Call smell check after write |
| `src/test/java/delivery/codegen/FacebookIrCodegenIdentityTest.java` | Regen from Facebook IR; assert distinct methods/props |

---

### Task 1: semanticToken extracts label and aria-label

**Files:**
- Modify: `src/main/java/delivery/codegen/CodegenNaming.java`
- Create: `src/test/java/delivery/codegen/CodegenNamingTest.java`

**Interfaces:**
- Consumes: `ProvenStep(action, locatorStrategy, locatorValue, assertionType, assertionExpected, rationale)`
- Produces: tokens like `First_Name`, `Select_Day`, `Password` — never bare `Type` / `Select` / `Assert` when locator carries a label

- [ ] **Step 1: Write failing tests from Facebook IR locators**

```java
@Test
public void tokenFromLabelForXpath() {
    ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "type",
            "xpath", "//input[@id=//label[normalize-space(.)='First name']/@for]",
            "Merna", "", "", true, "intent:TYPE_FIELD");
    Assert.assertEquals(CodegenNaming.actionMethodName(s), "type_First_Name");
}

@Test
public void tokenFromAriaLabelCss() {
    ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "select",
            "css", "div[aria-label='Select day']", "15", "", "", true, "intent:TYPE_FIELD");
    Assert.assertEquals(CodegenNaming.actionMethodName(s), "type_Select_Day");
    // or select_Select_Day — pick one convention and lock it in the test
}

@Test
public void visibleAssertUsesLabelNotAssertVerb() {
    ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "assert",
            "xpath", "//input[@id=//label[normalize-space(.)='Surname']/@for]",
            "", "visible", "", true, "intent:ASSERT_VISIBLE");
    Assert.assertEquals(CodegenNaming.assertMethodName(s), "assert_Surname_Is_Visible");
}

@Test
public void linkTextBecomesSignUpNotGeneric() {
    ProvenStep s = new ProvenStep("TC", "Reg", "elementAction", "click",
            "xpath",
            "//a[contains(normalize-space(.),'Sign up')][not(.//*[contains(normalize-space(.),'Sign up')])]",
            "", "", "", true, "intent:CLICK");
    Assert.assertTrue(CodegenNaming.actionMethodName(s).toLowerCase().contains("sign_up"));
}
```

- [ ] **Step 2: Run tests — expect FAIL** on current `semanticToken` fallback to action verb

Run: `mvn -q "-Dmaven.compiler.release=21" -Dtest=CodegenNamingTest test`

- [ ] **Step 3: Implement extractors in `CodegenNaming` (order matters)**

In `semanticToken`, before `return action`:

1. Keep existing textContains expected / xpath phrase / bare id / `id|name|data-test` paths.
2. **New:** `extractLabelPhrase(locator)` for  
   `label[normalize-space(.)='…']` / `label[normalize-space(.)="…"]`.
3. **New:** extend `extractAttrValue` to also match `aria-label='…'`.
4. **New:** `contains(normalize-space(.),'Sign up')` already partially handled via xpath phrase — ensure click uses it.
5. Strip leading verbs from aria labels when useful (`Select day` → token `Select_Day` or `Day` — **lock in test**: prefer full `Select_Day` to avoid colliding with bare `Day` text asserts).
6. Ban verb-only tokens: if computed token equalsIgnoreCase `type|select|assert|click|element`, treat as missing and keep searching; final fallback `Control` + short hash of locator (8 hex of locator hashCode) rather than the action verb.

- [ ] **Step 4: Re-run `CodegenNamingTest` — PASS**

- [ ] **Step 5: Commit** (only if user asks)

```bash
git add src/main/java/delivery/codegen/CodegenNaming.java \
  src/test/java/delivery/codegen/CodegenNamingTest.java
git commit -m "fix: derive codegen names from labels and aria-label"
```

---

### Task 2: PageAccumulator + testdata stay 1:1 with locators

**Files:**
- Modify: `src/main/java/delivery/codegen/PageAccumulator.java` (only if method collision still drops siblings)
- Test: extend `CodegenNamingTest` or add `PageAccumulatorIdentityTest`

**Interfaces:**
- Consumes: list of Facebook REG_02 proven type/select steps
- Produces: N distinct `MethodModel`s for N distinct locator values

- [ ] **Step 1: Failing accumulator test**

```java
@Test
public void facebookFormKeepsDistinctTypeMethods() {
    PageAccumulator acc = new PageAccumulator();
    acc.add(type("First name", "A"));
    acc.add(type("Surname", "B"));
    acc.add(selectCss("Select day", "15"));
    acc.add(selectCss("Select month", "Jan"));
    PageAccumulator.PageModel page = acc.pages().get("Reg");
    Assert.assertTrue(page.methods().size() >= 4, String.valueOf(page.methods()));
    Set<String> names = page.methods().stream().map(m -> m.name()).collect(Collectors.toSet());
    Assert.assertEquals(names.size(), page.methods().size());
    Assert.assertFalse(names.contains("type_Type"));
    Assert.assertFalse(names.contains("select_Select"));
}
```

- [ ] **Step 2: If Task 1 alone passes this, no PageAccumulator change. If not, when `methodNames.add` fails due to collision, disambiguate method with field name suffix (`type_First_Name_2`) instead of skipping.**

- [ ] **Step 3: Assert `TestDataPropertiesWriter` emits four distinct keys for the four values**

- [ ] **Step 4: Commit** (if asked)

---

### Task 3: Emit smell gate (fail closed)

**Files:**
- Create: `src/main/java/delivery/codegen/CodegenSmellCheck.java`
- Modify: `src/main/java/delivery/codegen/CodeWriter.java` (end of `write`)
- Create: `src/test/java/delivery/codegen/CodegenSmellCheckTest.java`

**Interfaces:**
- Consumes: `Map<String, PageAccumulator.PageModel>` or generated Actions source paths
- Produces: throws `IllegalStateException` listing smells

- [ ] **Step 1: Failing test — Actions containing `type_Type` must throw**

- [ ] **Step 2: Implement check**

Reject method names matching:

- `^(type|select|click)_?(Type|Select|Click|Assert)$` (case-insensitive)
- `assert_Assert_`
- duplicate method names within a page
- duplicate `propKey` for different locator values in one TC (optional but valuable)

- [ ] **Step 3: Wire `CodegenSmellCheck.verify(pages)` at end of `CodeWriter.write` before return**

- [ ] **Step 4: Tests PASS**

---

### Task 4: Re-emit Facebook IR and prove package is sane

**Files:**
- Create: `src/test/java/delivery/codegen/FacebookIrCodegenIdentityTest.java`
- Touches (generated only): `delivery-store/facebook-com/prj_e9a9fc7313b2/framework/**`

**Interfaces:**
- Consumes: IR JSON via `TcDraftStore` + `EmitPhase.toOutcome`
- Produces: regenerated pages/tests/testdata; assertions below

- [ ] **Step 1: Test regenerates from IR and asserts**

```java
// After CodeWriter.write(framework, outcomes):
String actions = Files.readString(framework.resolve("src/main/java/project/pages/Reg_Actions.java"));
Assert.assertFalse(actions.contains("type_Type"));
Assert.assertTrue(actions.contains("type_First_Name"));
Assert.assertTrue(actions.contains("type_Surname"));
String test02 = Files.readString(... "TC_FB_REG_02Test.java");
Assert.assertTrue(test02.contains("type_First_Name"));
Assert.assertTrue(test02.contains("type_Surname"));
Assert.assertFalse(test02.contains("type_Type("));
String props = Files.readString(... "delivery-testdata.properties");
Assert.assertTrue(props.contains("First_Name"));
Assert.assertTrue(props.contains("Surname"));
// mvn test-compile in framework dir — exit 0
```

- [ ] **Step 2: Run test; fix any remaining token gaps (gender ordinal xpath → token `Gender` or `Combobox_4` with unique suffix)**

- [ ] **Step 3: Sync template README into package if still garbled (optional one-liner in test)**

- [ ] **Step 4: Delete or keep the IR regen test as a regression fixture (prefer keep, gated on IR path existing)

---

### Task 5 (thin, optional follow-up): Submit vs Sign up bind

**Done (keep-working session).** Prefer click candidates whose name/role matches Submit/sign-up **button** over bare `<a>Sign up</a>` when intent says Submit.

- `StepIntentBinder.formSubmitPreference` / `looksLikeBareSignUpAnchor`: boost button/literal submit; demote bare Sign-up anchors; skip anchor +1 under Submit intents.
- Tests: `SubmitVsSignUpBindTest`
- Also: ordinal `@role` xpath → `Combobox_N` in `CodegenNaming` (gender control readability)

- [x] Prefer Submit/sign-up button over bare Sign up link
- [x] Ordinal combobox naming (`select_Combobox_4`)

---

## Verification (definition of done)

1. `CodegenNamingTest` covers label xpath, aria-label, visible-assert, link text — PASS.
2. Facebook IR regen: no `type_Type` / `select_Select` / `assert_Assert_`.
3. `TC_FB_REG_02Test` calls distinct type/select methods; testdata has distinct keys per field.
4. `mvn "-Dmaven.compiler.release=21" test-compile` in Facebook framework — PASS.
5. Smell check fails a deliberately broken fixture.
6. SauceDemo-style id naming still works (`PageAccumulatorMergeTest` still green).

## Self-review

- Spec coverage: identity root cause → Task 1; collision safety → Task 2; ship gate → Task 3; real package proof → Task 4; submit misbind deferred → Task 5.
- No TBD placeholders.
- Does not require a live Facebook re-prove to validate the codegen fix.

---

## Execution handoff

Plan saved to `docs/superpowers/plans/2026-08-21-codegen-control-identity.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute in this session with checkpoints  

Which approach?
