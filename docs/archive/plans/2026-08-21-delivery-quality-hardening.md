# Delivery Quality Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Tasks 0–6 implemented (2026-08-21). Remaining work is live SauceDemo re-prove to confirm healTier=none / overview enrichment on a fresh run.

**Architecture:** Fix codegen page-field merge (root cause of broken Inventory page), gate packaging on `mvn test-compile`, harden `VisionAssertionGate` against prompt-template echoes, enrich expected-result → assert mapping in prove/emit, prefer stable locators over body-text XPaths, write typed values to properties/JSON fixtures, and emit a per-delivery `docs/DOMAIN_CATALOG.md` + README appendix from codegen metadata.

**Tech Stack:** Java 21, TestNG, Selenium, FreeMarker page templates, Maven Surefire, Ollama ui-tars/Qwen vision prompts, existing `PageAccumulator` / `EmitPhase` / `FrameworkPackager` / `VisionAssertionGate`.

## Global Constraints

- Do not weaken Security / Maintainability / Performance priority order.
- Do not swallow exceptions; catch specific types; uncertain vision results must not become PASS.
- Prefer existing helpers (`JobSecretCrypto`, `PostActionSettle`, `LocatorPreference`, `CodegenNaming`) over new frameworks.
- Keep customer ZIP free of real authoring passwords unless already handled by current packaging rules.
- Every packaging path that writes `vN.zip` must fail the job if `mvn -q test-compile` fails inside the framework tree.
- Preserve intentional same-element merge (login `id` vs `button[type=submit]`); ban unrelated multi-button merges (backpack vs cart).

---

## Findings snapshot (last SauceDemo run)

| Area | Verdict |
|------|---------|
| Static TAF README | Good — documents drivers/utils/validations/listeners |
| Generated pages / tests in README | **Gap** — README says tests are “not described here”; no per-page catalog for this delivery |
| `docs/AUTOMATION_SCORE.md` | Score only — not architecture docs |
| Framework compile | **FAIL** — `test-compile` missing `Inventory_Actions.click_Add_To_Cart_Sauce_Labs_Backpack_Button()`; cart locator stored under backpack field name via over-merge |
| Job portal status | Misleading green (PASSED) while ZIP would not compile for the customer |

**Therefore:** Task 0 (codegen + package gate) is mandatory before treating the five quality issues as “done.”

---

## File map

| File | Responsibility |
|------|----------------|
| `src/main/java/delivery/codegen/PageAccumulator.java` | Stop over-merging distinct buttons; keep same-element upgrades |
| `src/test/java/delivery/codegen/PageAccumulatorMergeTest.java` | Regression: backpack + bike light + cart stay three fields |
| `src/main/java/delivery/packager/FrameworkPackager.java` (or emit hook) | Run `mvn test-compile` before zip; fail job on non-zero |
| `src/main/java/delivery/vision/VisionAssertionGate.java` | Reject template placeholders / empty evidence |
| `src/main/java/delivery/vision/QwenVisionProvider.java` | Safer ASSERT prompt examples (non-copyable placeholders) |
| `src/test/java/delivery/vision/VisionAssertionGateTest.java` | Tests for placeholder / echo rejection |
| `src/main/java/delivery/job/PostActionSettle.java` (+ prove call sites) | Stronger settle where heal/retry clustered |
| `src/main/java/delivery/job/ProvePhase.java` / assertion binder | Stronger expected-result asserts (item + total on overview) |
| `src/main/java/delivery/codegen/*` / locator policy | Prefer id/data-test/css over body text XPath when available |
| `src/main/java/delivery/packager/*` + FreeMarker test templates | Externalize invented type values to properties/JSON |
| `src/main/java/delivery/codegen/DomainCatalogWriter.java` (new) | Write `docs/DOMAIN_CATALOG.md` listing pages + tests |
| `customer-framework-template/README.md` | Point to DOMAIN_CATALOG + how to run tests |

---

### Task 0: Fix PageAccumulator over-merge + fail packaging on test-compile

**Files:**
- Modify: `src/main/java/delivery/codegen/PageAccumulator.java` (`findMergeableBtnClickField` / `mergeClickFields`)
- Modify: `src/test/java/delivery/codegen/PageAccumulatorMergeTest.java`
- Modify: `src/main/java/delivery/job/EmitPhase.java` or `src/main/java/delivery/packager/FrameworkPackager.java` — add compile gate
- Test: `PageAccumulatorMergeTest`, manual `mvn test-compile` on a regenerated SauceDemo tree

**Interfaces:**
- Consumes: `ProvenStep`, `LocatorPreference.prefer/score`
- Produces: one `FieldModel` + one click method per distinct element; packaging throws if `test-compile` ≠ 0

- [ ] **Step 1: Write failing regression test for multi-button Inventory**

```java
@Test
public void accumulatorDoesNotMergeUnrelatedInventoryButtons() {
    PageAccumulator acc = new PageAccumulator();
    acc.add(new ProvenStep("TC", "Inventory", "elementAction", "click",
            "id", "add-to-cart-sauce-labs-backpack", "", "", "", true, ""));
    acc.add(new ProvenStep("TC", "Inventory", "elementAction", "click",
            "id", "add-to-cart-sauce-labs-bike-light", "", "", "", true, ""));
    acc.add(new ProvenStep("TC", "Inventory", "elementAction", "click",
            "data-test", "shopping-cart-link", "", "", "", true, ""));
    PageAccumulator.PageModel page = acc.pages().get("Inventory");
    Assert.assertEquals(page.fields().size(), 3);
    Assert.assertTrue(page.methods().stream()
            .anyMatch(m -> m.name().contains("Backpack")));
    Assert.assertTrue(page.methods().stream()
            .anyMatch(m -> m.name().contains("Shopping_Cart") || m.name().contains("Cart")));
}
```

- [ ] **Step 2: Run test — expect FAIL on current merge heuristic**

Run: `mvn -q "-Dmaven.compiler.release=21" -Dtest=PageAccumulatorMergeTest#accumulatorDoesNotMergeUnrelatedInventoryButtons test`

Expected: FAIL (fields.size() < 3 or backpack method missing)

- [ ] **Step 3: Implement safe merge rule**

Only call `findMergeableBtnClickField` when locators are **same-element candidates**, e.g.:

```java
private static boolean sameElementCandidate(FieldModel existing, String strategy, String locatorValue) {
    String a = normalize(existing.value());
    String b = normalize(locatorValue);
    if (a.equals(b)) {
        return true;
    }
    // classic login: bare id "login" vs submit button CSS
    if (looksLikeSubmit(strategy, locatorValue) && looksLikeControlId(existing.strategy(), existing.value())) {
        return true;
    }
    if (looksLikeSubmit(existing.strategy(), existing.value()) && looksLikeControlId(strategy, locatorValue)) {
        return true;
    }
    return false;
}
```

Keep existing `PageAccumulatorMergeTest` login cases green. Do **not** merge solely because `score` gap ≥ 2.

- [ ] **Step 4: Re-run merge tests — all PASS**

- [ ] **Step 5: Add packaging compile gate**

After codegen into `projectDir`, run:

```text
mvn -q -f <projectDir>/pom.xml "-Dmaven.compiler.release=21" test-compile
```

On non-zero exit: set job FAILED / do not publish zip (or publish only with explicit FAILED score — prefer hard fail). Log full compiler stderr into job message / evidence.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/delivery/codegen/PageAccumulator.java \
  src/test/java/delivery/codegen/PageAccumulatorMergeTest.java \
  src/main/java/delivery/job/EmitPhase.java \
  src/main/java/delivery/packager/FrameworkPackager.java
git commit -m "fix: stop page button over-merge and gate ZIP on test-compile"
```

---

### Task 1: Visual assert honesty (template echo PASS)

**Files:**
- Modify: `src/main/java/delivery/vision/VisionAssertionGate.java`
- Modify: `src/main/java/delivery/vision/QwenVisionProvider.java` (`ASSERT_SYSTEM_PROMPT`)
- Create/Modify: `src/test/java/delivery/vision/VisionAssertionGateTest.java`

**Interfaces:**
- Consumes: `VisionAssertionResult(status, confidence, observation, evidence)`
- Produces: PASS only when observation/evidence look like real screenshot reasoning; else UNCERTAIN/FAIL

- [ ] **Step 1: Failing tests for last-run payload**

```java
@Test
public void rejectsPromptSchemaPlaceholders() {
    VisionAssertionResult raw = VisionAssertionResult.of(
            VisionAssertionStatus.PASS, 0.9, "what is visible", "why");
    VisionAssertionResult out = VisionAssertionGate.honestyCheck(
            raw, "Checkout complete page is visible with Thank you for your order!", null);
    Assert.assertNotEquals(out.status(), VisionAssertionStatus.PASS);
}

@Test
public void rejectsVeryShortEvidenceOnPass() {
    VisionAssertionResult raw = VisionAssertionResult.of(
            VisionAssertionStatus.PASS, 0.9, "ok", "ok");
    VisionAssertionResult out = VisionAssertionGate.honestyCheck(
            raw, "Thank you for your order!", null);
    Assert.assertNotEquals(out.status(), VisionAssertionStatus.PASS);
}
```

- [ ] **Step 2: Run — expect FAIL before gate change**

- [ ] **Step 3: Gate rules + safer prompt**

In `honestyCheck`:
- Reject observation/evidence matching placeholders: `what is visible`, `why`, `currentObservation`, empty after trim, length &lt; 20 for PASS.
- Keep existing echo/paraphrase checks.
- Change ASSERT prompt example values to clearly invalid sentinels the model must not copy, e.g. `"observation":"<describe concrete UI text you see>"`.

- [ ] **Step 4: Tests PASS; decide product policy** — if visual assert becomes UNCERTAIN, TC should not stay silent green without note (prefer mark needs-review / soft-fail per existing revise path).

- [ ] **Step 5: Commit**

```bash
git commit -m "fix: reject dishonest visual PASS placeholder echoes"
```

---

### Task 2: Reduce prove heal/retry flakiness (settle)

**Files:**
- Modify: `src/main/java/delivery/job/PostActionSettle.java`
- Modify: `src/main/java/delivery/job/ProvePhase.java` (call settle after click navigations: cart, checkout, continue, finish)
- Test: unit test settle predicates if present; else SauceDemo dry re-run comparing heal screenshot count

**Interfaces:**
- Consumes: WebDriver after action
- Produces: wait until URL/DOM stable before next intent

- [ ] **Step 1: Capture baseline** — document last run had heal shots at i7–i13 (`heal-TC_SD_E2E_01-i*.png`).

- [ ] **Step 2: Extend settle for navigation clicks**

After actions that change URL or major DOM (cart icon, checkout, continue, finish), wait for:
- document ready
- URL change or key landmark text
- short quiet period (no DOM mutation) using existing settle helpers

- [ ] **Step 3: Re-run SauceDemo NEW on same Excel** — expect `healTier=none` or fewer heal files; case still PASSED.

- [ ] **Step 4: Commit**

```bash
git commit -m "fix: settle after navigation clicks to cut prove retries"
```

---

### Task 3: Stronger expected-result coverage

**Files:**
- Modify: assertion planning in prove/author (`ProvePhase` / intent binder / expected-result splitter)
- Test: unit test that overview expected text “remaining items and a total” yields ≥1 assert beyond header title

**Interfaces:**
- Consumes: Excel `expectedResult` lines
- Produces: proven assert steps that include product name and/or total when expected mentions them

- [ ] **Step 1: Failing binder test**

Given expected line: `Checkout Overview shows remaining items and a total` and page text containing Backpack + Total, planner must request asserts for item and/or `Total`, not only `Checkout: Overview`.

- [ ] **Step 2: Implement minimal enrichment** — when expected mentions items/total and cart/overview page is active, add `textContains` for known remaining item from prior steps and for `Total` if present in DOM.

- [ ] **Step 3: Regenerate SauceDemo — `CheckoutStepTwo` / test gains stronger assert(s).

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: map overview expected results to item and total asserts"
```

---

### Task 4: Prefer durable locators over body-text XPath

**Files:**
- Modify: locator selection / assert emission (`delivery.authoring` / `delivery.codegen` / validation helpers)
- Prefer: for `ASSERT_VISIBLE:text`, use `Validation.bodyTextContains(expected)` **without** storing giant XPath as a page locator field when no element id exists (already partially true in Actions — align Locators generation)

**Interfaces:**
- Consumes: assert steps with `assertionType=textContains`
- Produces: Actions that call `bodyTextContains`; Locators omit brittle body XPath fields unless needed for click/type

- [ ] **Step 1: Unit test** — assert-only step does not create `*_Lbl_Locator` with `//body//*[contains...]` when strategy is visibility-of-text.

- [ ] **Step 2: Implement** — skip field emission for pure body-text asserts; keep method `assert_*_Is_Visible` calling `bodyTextContains`.

- [ ] **Step 3: Confirm Inventory/Cart/CheckoutComplete locators shrink; tests still compile.

- [ ] **Step 4: Commit**

```bash
git commit -m "refactor: drop brittle body XPath fields for text visibility asserts"
```

---

### Task 5: Externalize invented type values

**Files:**
- Modify: test FreeMarker template + `EmitPhase` / packager property writer
- Modify: generated test pattern so `type_First_Name` reads `PropertyReader.getProperty("TC_SD_E2E_01.firstName")` or JSON fixture under `src/test/resources/test-data/`

**Interfaces:**
- Consumes: proven step `value` (invented or from Excel TestData)
- Produces: properties keys + test code using PropertyReader/JsonReader — no raw PII literals in test source when invent path used

- [ ] **Step 1: Failing emit snapshot test** — emitted test must not contain literal `Merna` if value came from invent; must contain property key lookup.

- [ ] **Step 2: Write `delivery-testdata.properties` (or per-TC JSON) during emit; wire test template.

- [ ] **Step 3: SauceDemo regen — CheckoutStepOne types from properties; README notes how to edit data.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: externalize invented form values into test-data properties"
```

---

### Task 6: Customer docs — DOMAIN_CATALOG + README appendix

**Files:**
- Create: `src/main/java/delivery/codegen/DomainCatalogWriter.java`
- Modify: `customer-framework-template/README.md`
- Modify: `EmitPhase` to write `docs/DOMAIN_CATALOG.md` into every package
- Optional: append short “This delivery” section into copied README at pack time

**Interfaces:**
- Consumes: `PageAccumulator.pages()`, generated test class names, TC titles from IR
- Produces: markdown listing each page (URL stem, methods) and each test (TC id, title, pages used, PASS/TODO)

Example `DOMAIN_CATALOG.md` shape:

```markdown
# Domain catalog (this delivery)

## Pages
| Page | Role | Key actions |
|------|------|-------------|
| Saucedemo | Login | type username/password, click Login |
| Inventory | Product list | add Backpack/Bike Light, open cart |
| Cart | Cart | remove item, checkout |
| ... | ... | ... |

## Tests
| Class | TC | Status | Flow |
|-------|----|--------|------|
| TC_SD_E2E_01Test | TC_SD_E2E_01 | PASSED | Login → Inventory → Cart → Checkout → Complete |

## How to run
mvn clean test
```

- [ ] **Step 1: Writer unit test** — given one page + one test model, markdown contains both names.

- [ ] **Step 2: Wire into EmitPhase after codegen.**

- [ ] **Step 3: Update template README** — replace “not described here” with pointer to `docs/DOMAIN_CATALOG.md` and `docs/AUTOMATION_SCORE.md`.

- [ ] **Step 4: Commit**

```bash
git commit -m "docs: emit DOMAIN_CATALOG for generated pages and tests"
```

---

## Verification checklist (definition of done)

1. `PageAccumulatorMergeTest` includes Inventory three-button case — PASS.
2. Regenerated SauceDemo framework: `mvn "-Dmaven.compiler.release=21" test-compile` — PASS.
3. Visual assert with observation `what is visible` cannot remain PASS.
4. New SauceDemo prove: fewer heal screenshots; still green path.
5. Overview expected coverage includes item and/or Total assert in IR or generated test.
6. No giant body XPath locator fields for pure text asserts (or documented exception).
7. Invented names not hardcoded in `*Test.java`.
8. ZIP contains `docs/DOMAIN_CATALOG.md` describing pages + tests; README links to it.
9. Packaging refuses to ship a non-compiling framework.

## Self-review

- Spec coverage: architecture/README gap → Task 6; compile-break → Task 0; five quality issues → Tasks 1–5.
- Placeholder scan: none left as TBD.
- Type consistency: `VisionAssertionResult` / `PageAccumulator` names match existing code.
- Login merge tests retained so Task 0 does not regress intentional same-element merge.

---

## Execution handoff

Plan saved to `docs/superpowers/plans/2026-08-21-delivery-quality-hardening.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute in this session with checkpoints  

Which approach?
