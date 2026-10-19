# Scoped nested preferred hook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a unique menu row contains one preferred hook that Ant also cloned outside that row, rewrite to a scoped inner-hook locator instead of keeping the row xpath.

**Architecture:** Extend `NestedPreferredHook.rewrite` only. Document-unique `tag[attr='value']` still becomes CSS. A hook unique inside the resolved row but duplicated in the document becomes `{originalLocator}//{innerHook}` (xpath) or `{originalCss} {innerHook}` (css). Twin Settings still skip because the bound locator is not exactly one node.

**Tech Stack:** Java 21, Jsoup, TestNG, `HtmlLocatorPresence`, `PreferredHooksStore`, `ProvenStep`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-19-scoped-nested-hook-design.md`
- TDD: failing TestNG test before production code. Watch it fail.
- Run tests: `cmd.exe /c "mvn -pl keel-portal -am -q -Dmaven.compiler.release=21 -Dtest=NestedPreferredHookTest,ProvePhaseRememberedHookTest test"`
- Author/committer `rsayed <rsayed@axisapp.com>`. No `Co-authored-by: Cursor`. Do not push.
- No Claude / Anthropic. Do not change leftover quoting, OutcomeKind, Admin batch, or browser type.
- Bare CSS that matches two nodes is forbidden. Do not treat twin Settings as one control.
- Jar is stale until `rebuild-portal.cmd` + `start-portal.cmd` + Ctrl+F5.

## File map

| File | Role |
|------|------|
| `src/main/java/delivery/authoring/NestedPreferredHook.java` | Document-unique CSS; else scoped locator under the unique row |
| `src/test/java/delivery/authoring/NestedPreferredHookTest.java` | Unique CSS, outside duplicate → scoped, two inner hooks → xpath |
| `src/test/java/delivery/job/ProvePhaseRememberedHookTest.java` | Remembered xpath with outside copy → scoped before execute |
| `docs/PRODUCT.md`, `guide.html` | Product copy |
| CHANGELOG, handoff, ENGINEERING_HANDOFF, RESUME | Finish-job docs |

---

### Task 1: Failing tests for scoped rewrite

**Files:**
- Modify: `src/test/java/delivery/authoring/NestedPreferredHookTest.java`
- Modify: `src/test/java/delivery/job/ProvePhaseRememberedHookTest.java`

**Interfaces:**
- Consumes: `NestedPreferredHook.rewrite(ProvenStep, String)`, `ProvePhase.rewriteRememberedLocator(ProvenStep, String)`
- Produces: assertions that an outside duplicate becomes xpath `//li[normalize-space(.)='Bill Payment Settlement']//a[@data-axis-test-id='side-nav-bill-payment-settlement-link']`

- [x] **Step 1: Write the failing tests**

Replace `rewrite_duplicateHook_keepsXpath` with scoped xpath. Add two-hooks-inside-row keeps xpath. Add remembered duplicate.

```java
@Test
public void rewrite_duplicateHookOutsideRow_becomesScopedXpath() {
    String html = ROW.replace("</aside>",
            "<a data-axis-test-id=\"side-nav-bill-payment-settlement-link\">copy</a></aside>");
    ProvenStep xpath = new ProvenStep("TC_3", "P", "elementAction", "click",
            "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
            "", "", "", true, "intent:CLICK");
    try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
        ProvenStep out = NestedPreferredHook.rewrite(xpath, html);
        Assert.assertEquals(out.locatorStrategy(), "xpath");
        Assert.assertEquals(out.locatorValue(),
                "//li[normalize-space(.)='Bill Payment Settlement']"
                        + "//a[@data-axis-test-id='side-nav-bill-payment-settlement-link']");
    }
}

@Test
public void rewrite_twoHooksInsideRow_keepsXpath() {
    String html = """
            <html><body><aside class="sidebar">
            <li role="menuitem">
              <a data-axis-test-id="side-nav-bill-payment-settlement-link">Bill Payment Settlement</a>
              <button data-axis-test-id="other-hook">x</button>
            </li>
            </aside></body></html>
            """;
    ProvenStep xpath = new ProvenStep("TC_3", "P", "elementAction", "click",
            "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
            "", "", "", true, "intent:CLICK");
    try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
        ProvenStep out = NestedPreferredHook.rewrite(xpath, html);
        Assert.assertEquals(out.locatorStrategy(), "xpath");
        Assert.assertEquals(out.locatorValue(), xpath.locatorValue());
    }
}
```

Remembered:

```java
@Test
public void rememberedWrapperXpath_duplicateOutside_rewritesToScopedHook() {
    String html = HTML.replace("</aside>",
            "<a data-axis-test-id=\"side-nav-bill-payment-settlement-link\">copy</a></aside>");
    ProvenStep remembered = new ProvenStep(
            "TC_3", "Page", "elementAction", "click",
            "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
            "", "", "", true,
            "memory:CLICK|click the 'bill payment settlement' in the support sub-menu.");
    try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
        ProvenStep out = ProvePhase.rewriteRememberedLocator(remembered, html);
        Assert.assertEquals(out.locatorStrategy(), "xpath");
        Assert.assertEquals(out.locatorValue(),
                "//li[normalize-space(.)='Bill Payment Settlement']"
                        + "//a[@data-axis-test-id='side-nav-bill-payment-settlement-link']");
        Assert.assertTrue(out.rationale().startsWith("memory:"), out.rationale());
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `mvn -pl keel-portal -am -q -Dmaven.compiler.release=21 -Dtest=NestedPreferredHookTest,ProvePhaseRememberedHookTest test`

Expected: FAIL — duplicate outside still keeps the original `li` xpath.

- [x] **Step 3: Commit tests**

```bash
git add src/test/java/delivery/authoring/NestedPreferredHookTest.java src/test/java/delivery/job/ProvePhaseRememberedHookTest.java
git commit -m "Test scoped inner hook when Ant clones the submenu link."
```

---

### Task 2: Scoped locator in NestedPreferredHook

**Files:**
- Modify: `src/main/java/delivery/authoring/NestedPreferredHook.java`

**Interfaces:**
- Consumes: `HtmlLocatorPresence.matchingElements`, `PreferredHooksStore.current()`, `GeneratedIdDetector.isTrustworthyIdentifier`
- Produces: `rewrite` may return CSS (document-unique) or scoped xpath/css (row-unique descendant hook)

- [x] **Step 1: Implement the rule in `rewrite`**

Keep collecting CSS per hook. Count `doc.select(css).size()` and how many nodes inside `root.getAllElements()` carry that same tag+attr+value.

- Exactly one document match → `step.withLocator("css", css)` (unchanged).
- Else exactly one **wrapper** match, hook element is not `root` →
  - xpath original: `original + "//" + tag + xpathAttrSelector(attr, value)`
  - css original: `original + " " + css`
  - Accept only if `HtmlLocatorPresence.matchingElements` on the scoped locator returns size 1.
- Hook element is `root` and CSS is not document-unique → keep original.
- Zero or 2+ wrapper-unique hooks → keep original.

Reuse `cssAttrSelector`. Add `xpathAttrSelector` with the same quote rules (`'` unless the value contains `'`, then `"`).

- [x] **Step 2: Run tests to verify they pass**

Run: `mvn -pl keel-portal -am -q -Dmaven.compiler.release=21 -Dtest=NestedPreferredHookTest,ProvePhaseRememberedHookTest,NestedPreferredHookBindTest test`

Expected: PASS, including existing unique-CSS and bind tests.

- [x] **Step 3: Commit**

```bash
git add src/main/java/delivery/authoring/NestedPreferredHook.java
git commit -m "Scope the inner preferred hook under the unique menu row."
```

---

### Task 3: Product copy and handoff

**Files:**
- Modify: `docs/PRODUCT.md` Execute click bullet
- Modify: `src/main/resources/templates/guide.html` Execute howto item
- Modify: `CHANGELOG.md`, `docs/ENGINEERING_HANDOFF.md`, `docs/handoff/README.md`, `docs/handoff/2026-10-19-nested-hook-and-quote-heading.md`
- Create: `docs/handoff/2026-10-19-scoped-nested-hook.md`
- Modify: `D:\priv\testpilot\handoffs\RESUME-2026-10-15.md` (outside git)

- [x] **Step 1: Update copy**

PRODUCT / Guide: unique-in-page CSS as today; a second copy outside the unique row becomes a scoped inner-hook locator.

- [x] **Step 2: Commit docs**

```bash
git add docs/PRODUCT.md src/main/resources/templates/guide.html CHANGELOG.md docs/ENGINEERING_HANDOFF.md docs/handoff docs/superpowers
git commit -m "Document scoped inner hook for cloned Ant submenu links."
```
