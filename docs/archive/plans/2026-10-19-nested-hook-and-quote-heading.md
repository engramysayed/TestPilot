# Nested preferred hook + leftover Expected quoting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Named clicks use a unique inner preferred hook when one exists on the chosen row; leftover Expected is quoted in review after stripping filler, then Execute upgrades to the live heading only when exactly one heading/card title matches.

**Architecture:** `NestedPreferredHook` rewrites a bound click/type locator by looking inside the resolved node for one document-unique preferred/test hook. Bind also picks that hook among same-name named matches instead of AMBIGUOUS when exactly one hook is in the set. Vision’s `elementFromPoint` uses `PreferredHooksStore` plus a unique child. `LeftoverExpectedQuoter` is a deterministic post-pass after the review LLM. `LiveHeadingQuote` runs on ASSERT_VISIBLE intents before bind (not heal, not a library write).

**Tech Stack:** Java 21, Jsoup, TestNG, existing `HtmlLocatorPresence`, `PreferredHooksStore`, `ProvenStep`, `AuthoringReviewService`, Selenium JS in `SeleniumGroundingBrowser`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-19-nested-hook-and-quote-heading-design.md`
- TDD: failing TestNG test before production code. Watch it fail.
- PowerShell: quote Maven tests: `mvn -pl keel-portal "-Dtest=ClassName"`. Always `"-Dmaven.compiler.release=21"`. CrowdStrike: prefer `cmd.exe` / `.bat`.
- Author/committer `rsayed <rsayed@axisapp.com>`. No `Co-authored-by: Cursor`. Do not push. Do not commit `META-INF/`, `target/`, portal DB, `api-keys.local.bat`.
- No Claude / Anthropic. Site-agnostic: no AxisPay hostnames in production code.
- Do not change OutcomeKind quote-wins-first. Asserts never heal. Execute must not write the library. Do not treat twin Settings buttons as one control.
- Unique hook / unique heading means **exactly one in the whole document** (or heading list), not unique only inside a subtree.
- After Java edits the running jar is stale until rebuild + restart + Ctrl+F5.

## File map

| File | Role |
|------|------|
| `NestedPreferredHook` (new) | Look inside chosen locator; pick one same-name hook |
| `ProvenStep` | `withLocator` copy |
| `StepIntentBinder` / `AuthoringService` | Named-match pick + rewrite after bind |
| `SeleniumGroundingBrowser` / `ElementGrounder` | Preferred hooks + unique child |
| `LocatorRanker` / `ProveWiring` | Preferred hook scores as test id |
| `ExpectedQuoteFilter` + `LeftoverExpectedQuoter` (new) | Filler strip + leftover quotes |
| `AuthoringReviewService` + `heal.mjs` | Prompt + Java post-pass |
| `LiveHeadingQuote` (new) + `ProvePhase` | Unique live heading before assert bind |

---

### Task 1: NestedPreferredHook + ProvenStep.withLocator

**Files:**
- Create: `src/main/java/delivery/authoring/NestedPreferredHook.java`
- Modify: `src/main/java/delivery/codegen/ProvenStep.java` (add `withLocator`)
- Test: `src/test/java/delivery/authoring/NestedPreferredHookTest.java`

**Interfaces:**
- Produces: `ProvenStep withLocator(String strategy, String value)` — copy all fields, replace locator
- Produces: `static boolean isPreferredHookLocator(DomCandidate c)` — CSS/xpath value contains a preferred or built-in test-hook attr
- Produces: `static Optional<DomCandidate> pickNamed(List<DomCandidate> matches)` — if exactly one preferred-hook locator in the set, return it; else empty
- Produces: `static ProvenStep rewrite(ProvenStep step, String slimHtml)` — click/type/clear only; resolve locator; one unique hook on node-or-descendant → css `tag[attr='value']`; else same step
- Produces: `static List<ProvenStep> rewrite(List<ProvenStep> steps, String slimHtml)`

- [ ] **Step 1: Write the failing tests**

```java
package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

public class NestedPreferredHookTest {
    private static final String ROW = """
            <html><body><aside class="sidebar">
            <li role="menuitem">
              <a data-axis-test-id="side-nav-bill-payment-settlement-link"
                 href="/bill-payment-settlement">Bill Payment Settlement</a>
            </li>
            </aside></body></html>
            """;

    @Test
    public void rewrite_innerUniqueHook_becomesCss() {
        ProvenStep xpath = new ProvenStep("TC_3", "P", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true, "intent:CLICK");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = NestedPreferredHook.rewrite(xpath, ROW);
            Assert.assertEquals(out.locatorStrategy(), "css");
            Assert.assertTrue(out.locatorValue().contains("data-axis-test-id"));
            Assert.assertTrue(out.locatorValue().contains("side-nav-bill-payment-settlement-link"));
        }
    }

    @Test
    public void rewrite_duplicateHook_keepsXpath() {
        String html = ROW.replace("</aside>",
                "<a data-axis-test-id=\"side-nav-bill-payment-settlement-link\">copy</a></aside>");
        ProvenStep xpath = new ProvenStep("TC_3", "P", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true, "intent:CLICK");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = NestedPreferredHook.rewrite(xpath, html);
            Assert.assertEquals(out.locatorStrategy(), "xpath");
            Assert.assertEquals(out.locatorValue(), xpath.locatorValue());
        }
    }

    @Test
    public void pickNamed_oneHookAmongSameLabel_returnsHook() {
        DomCandidate hook = new DomCandidate("c1", "css",
                "a[data-axis-test-id='side-nav-bill-payment-settlement-link']",
                "a", "Bill Payment Settlement");
        DomCandidate li = new DomCandidate("c2", "xpath",
                "//li[normalize-space(.)='Bill Payment Settlement']",
                "li", "Bill Payment Settlement");
        Optional<DomCandidate> pick = NestedPreferredHook.pickNamed(List.of(hook, li));
        Assert.assertTrue(pick.isPresent());
        Assert.assertEquals(pick.get().id(), "c1");
    }

    @Test
    public void pickNamed_twoHooks_empty() {
        DomCandidate a = new DomCandidate("c1", "css", "button[data-axis-test-id='settings-1']",
                "button", "Settings");
        DomCandidate b = new DomCandidate("c2", "css", "button[data-axis-test-id='settings-2']",
                "button", "Settings");
        Assert.assertTrue(NestedPreferredHook.pickNamed(List.of(a, b)).isEmpty());
    }

    @Test
    public void rewrite_skipsAssert() {
        ProvenStep as = new ProvenStep("TC_3", "P", "elementAction", "assert",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "visible", "", true, "intent:ASSERT_VISIBLE");
        ProvenStep out = NestedPreferredHook.rewrite(as, ROW);
        Assert.assertEquals(out.locatorValue(), as.locatorValue());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=NestedPreferredHookTest" test`

Expected: FAIL (class `NestedPreferredHook` missing)

- [ ] **Step 3: Write minimal implementation**

Add on `ProvenStep`:

```java
public ProvenStep withLocator(String strategy, String value) {
    return new ProvenStep(
            tcId, pageName, actionType, action, strategy, value, value(),
            assertionType, assertionExpected, validated, rationale, screenshotRelPath,
            locatorCandidates, dropCandidates, sideEffects, secret);
}
```

`NestedPreferredHook` (package `delivery.authoring`):

- `rewrite(List)` maps each step.
- `rewrite(step, html)`: if action is not `click`/`type`/`clear`/`select`, return step. Resolve `HtmlLocatorPresence.matchingElements(strategy, value, html)`. Size != 1 → return step. Walk the match and descendants. For each attr in `PreferredHooksStore.current()` plus every attr where `DomCandidateExtractor.isTestHookAttr(name)`, if `GeneratedIdDetector.isTrustworthyIdentifier(value)` and `doc.select(css).size()==1` and that element is the match or a descendant, collect `css = tag + "[" + attr + "='" + escaped + "']"`. Distinct css values: size==1 → `step.withLocator("css", css)` else original.
- Do not walk parent/siblings.
- `isPreferredHookLocator`: strategy css/xpath/cssselector and (`PreferredHooksStore.matches(value, current())` or value contains `data-axis-test-id` / `data-testid` / `data-test=` / `data-qa=`).
- `pickNamed`: count `isPreferredHookLocator`; return that candidate iff count==1.

- [ ] **Step 4: Run tests and make sure they pass**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=NestedPreferredHookTest" test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/delivery/authoring/NestedPreferredHook.java src/main/java/delivery/codegen/ProvenStep.java src/test/java/delivery/authoring/NestedPreferredHookTest.java
git commit -m "Prefer a unique inner test hook over a wrapper xpath."
```

Author/committer `rsayed <rsayed@axisapp.com>`. No Co-authored-by.

---

### Task 2: Bind named matches + rewrite after authorIntent

**Files:**
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` (named-action AMBIGUOUS block ~332–360)
- Modify: `src/main/java/delivery/authoring/AuthoringService.java` (`authorIntent` after `bound.ok()`)
- Test: `src/test/java/delivery/authoring/NestedPreferredHookBindTest.java`
- Keep: `src/test/java/delivery/authoring/CandidateScorerTest.java` `twinSameNameIsTie` must still pass

**Interfaces:**
- Consumes: `NestedPreferredHook.pickNamed`, `NestedPreferredHook.rewrite(List, String)`
- Produces: `bindSingle` does not return `AMBIGUOUS:...:multiple named controls` when `pickNamed` is present
- Produces: `authorIntent` returns rewritten locators when `slimHtml` is non-blank

- [ ] **Step 1: Write the failing tests**

```java
package delivery.authoring;

import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class NestedPreferredHookBindTest {
    private static final String HTML = """
            <html><body><aside class="sidebar">
            <li role="menuitem">
              <a data-axis-test-id="side-nav-bill-payment-settlement-link"
                 href="/bill-payment-settlement">Bill Payment Settlement</a>
            </li>
            </aside></body></html>
            """;

    @Test
    public void bindSingle_sameNameHookAndLi_picksHook() {
        List<DomCandidate> cs = List.of(
                new DomCandidate("c1", "css",
                        "a[data-axis-test-id='side-nav-bill-payment-settlement-link']",
                        "a", "Bill Payment Settlement"),
                new DomCandidate("c2", "xpath",
                        "//li[normalize-space(.)='Bill Payment Settlement']",
                        "li", "Bill Payment Settlement"));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK,
                "Click the 'Bill Payment Settlement' in the Support sub-menu.");
        StepIntentBinder.BindResult bound = StepIntentBinder.bindSingle(intent, "TC_3", cs, List.of());
        Assert.assertTrue(bound.ok(), bound.rejectReason());
        Assert.assertTrue(bound.steps().get(0).locatorValue().contains("data-axis-test-id"));
    }

    @Test
    public void authorIntent_xpathOnly_rewritesInnerHook() throws Exception {
        AuthoringService authoring = new AuthoringService();
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK,
                "Click the 'Bill Payment Settlement' in the Support sub-menu.");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            List<ProvenStep> steps = authoring.authorIntent(
                    "TC_3", intent, HTML, null, false, List.of(), List.of(), null);
            Assert.assertFalse(steps.isEmpty());
            Assert.assertTrue(steps.get(0).validated());
            Assert.assertEquals(steps.get(0).locatorStrategy(), "css");
            Assert.assertTrue(steps.get(0).locatorValue().contains("side-nav-bill-payment-settlement-link"));
        }
    }
}
```

Also run existing `CandidateScorerTest#twinSameNameIsTie` in step 4.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=NestedPreferredHookBindTest" test`

Expected: FAIL (`AMBIGUOUS` or xpath not rewritten)

- [ ] **Step 3: Write minimal implementation**

In `bindSingle`, replace the `AMBIGUOUS:...multiple named controls` return with:

```java
DomCandidate preferredMatch = matches.stream().filter(c -> tieBreak.contains(c.id())).findFirst().orElse(null);
if (preferredMatch == null) {
    preferredMatch = NestedPreferredHook.pickNamed(matches).orElse(null);
}
if (preferredMatch == null) {
    return new BindResult(List.of(), "AMBIGUOUS:" + intent.kind() + ":multiple named controls");
}
match = preferredMatch;
```

In `AuthoringService.authorIntent`, when `bound.ok()`:

```java
List<ProvenStep> steps = bound.steps();
if (slimHtml != null && !slimHtml.isBlank()) {
    steps = NestedPreferredHook.rewrite(steps, slimHtml);
}
return steps;
```

Do not rewrite inside `stepsPreferringCandidate` (no slim HTML there). Vision/heal steps are rewritten in Task 3 on `ProvePhase` after `stabilizeSteps` using the live `html`.

- [ ] **Step 4: Run tests and make sure they pass**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=NestedPreferredHookBindTest,CandidateScorerTest,NestedPreferredHookTest" test`

Expected: PASS. Twin Settings still TIE.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/delivery/authoring/StepIntentBinder.java src/main/java/delivery/authoring/AuthoringService.java src/test/java/delivery/authoring/NestedPreferredHookBindTest.java
git commit -m "Bind a unique inner hook instead of tying wrapper and link."
```

---

### Task 3: Vision preferred hooks including unique child

**Files:**
- Modify: `src/main/java/delivery/vision/SeleniumGroundingBrowser.java` (`ELEMENT_FROM_POINT_JS`)
- Modify: `src/main/java/delivery/vision/ElementGrounder.java` (`dataTest` / `preferredMatches` already use `preferredAttribute`; keep using `buildLocator` preferred CSS)
- Modify: `src/main/java/delivery/job/ProvePhase.java` — after vision `stabilizeSteps`, `NestedPreferredHook.rewrite(stepBatch, html)`
- Test: `src/test/java/delivery/vision/NestedHookVisionGrounderTest.java`

**Interfaces:**
- Consumes: `NestedPreferredHook.rewrite`
- Produces: JS sets `preferredAttribute`/`preferredValue` from observed node **or** the single unique descendant hook; `dataTest` includes preferred hook values (`data-axis-test-id` not only testid/test/qa)
- Produces: ProvePhase vision path locators rewritten from slim html

- [ ] **Step 1: Write the failing test**

Use `ElementGrounder.buildLocator` via package-visible test of preferred CSS when `GroundedNode.preferredAttribute` is `data-axis-test-id` on tag `a` (simulates JS child hit). Add `NestedPreferredHook.rewrite` on a vision xpath step + ROW html from Task 1 (covers ProvePhase contract in unit form).

```java
package delivery.vision;

import delivery.authoring.NestedPreferredHook;
import delivery.codegen.ProvenStep;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

public class NestedHookVisionGrounderTest {
    @Test
    public void buildLocator_usesPreferredAxisHook() {
        GroundedNode node = new GroundedNode(
                "a", "", "", "", "", "link", true, true, "a",
                "Bill Payment Settlement", "data-testid",
                "data-axis-test-id", "side-nav-bill-payment-settlement-link");
        Optional<delivery.authoring.LocatorCandidate> loc = ElementGrounder.buildLocator(node);
        Assert.assertTrue(loc.isPresent());
        Assert.assertEquals(loc.get().strategy(), "css");
        Assert.assertTrue(loc.get().value().contains("data-axis-test-id"));
        Assert.assertTrue(loc.get().value().contains("side-nav-bill-payment-settlement-link"));
    }

    @Test
    public void rewriteAfterVisionXpath_usesChildHook() {
        String html = """
                <html><body><aside class="sidebar"><li role="menuitem">
                <a data-axis-test-id="side-nav-bill-payment-settlement-link">Bill Payment Settlement</a>
                </li></aside></body></html>
                """;
        ProvenStep vision = new ProvenStep("TC_3", "P", "elementAction", "click",
                "xpath", "//li[normalize-space(.)='Bill Payment Settlement']",
                "", "", "", true, "intent:CLICK [catalog xpath]");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            ProvenStep out = NestedPreferredHook.rewrite(vision, html);
            Assert.assertEquals(out.locatorStrategy(), "css");
        }
    }
}
```

Change `ElementGrounder.buildLocator` from `private` to package-visible (`static` stays) so `NestedHookVisionGrounderTest` in `delivery.vision` can call it.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=NestedHookVisionGrounderTest" test`

Expected: FAIL until `buildLocator` is visible and preferred CSS includes `data-axis-test-id`.

- [ ] **Step 3: Write minimal implementation**

1. Package-visible `ElementGrounder.buildLocator`. Existing `preferredCss` already uses `preferredAttribute`. Confirm `dataTest` fallback in JS is not the only path — preferred attrs win first in `buildLocator` (already true).
2. In `ELEMENT_FROM_POINT_JS`, after choosing `el`:
   - `const attrs = (arguments[2] || []).concat(['data-testid','data-test','data-qa','data-cy']);`
   - Find unique hook on `el`; if none, collect unique hooks on `el.querySelectorAll('*')` keyed by `attr=value`; if **exactly one** key, set `el` to that node and use that hook.
   - `dataTest`: `el.getAttribute('data-testid') || el.getAttribute('data-test') || el.getAttribute('data-qa') || el.getAttribute('data-axis-test-id') || ''`
   - `dataTestAttribute`: matching attr name, including `data-axis-test-id` when that is the source.
3. ProvePhase after `stepBatch = pageCatalog.stabilizeSteps(...)`: `stepBatch = NestedPreferredHook.rewrite(stepBatch, html);`
4. After successful non-vision `authorIntent` the Task 2 rewrite already ran; do not skip vision path.

- [ ] **Step 4: Run tests and make sure they pass**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=NestedHookVisionGrounderTest,NestedPreferredHookTest,NestedPreferredHookBindTest" test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/delivery/vision/SeleniumGroundingBrowser.java src/main/java/delivery/vision/ElementGrounder.java src/main/java/delivery/job/ProvePhase.java src/test/java/delivery/vision/NestedHookVisionGrounderTest.java
git commit -m "Let vision use the site preferred hook on a unique child."
```

---

### Task 4: Ranker treats preferred hooks as test ids

**Files:**
- Modify: `src/main/java/delivery/snapshot/LocatorRanker.java` (`TEST_ID_ATTRS`)
- Modify: `src/main/java/delivery/job/ProveWiring.java` (`currentScore`)
- Test: `src/test/java/delivery/snapshot/LocatorRankerPreferredHookTest.java` (new) or extend existing ranker test if one exists

**Interfaces:**
- Consumes: `PreferredHooksStore.current()`
- Produces: `TEST_ID_ATTRS` effective list = store hooks + `data-testid`, `data-test`, `data-qa`, `data-cy`
- Produces: `currentScore` returns `LocatorRanker.TEST_ID` when locator value contains `[data-axis-test-id=` or any current preferred attr

- [ ] **Step 1: Write the failing test**

```java
package delivery.job;

import delivery.codegen.ProvenStep;
import delivery.snapshot.LocatorRanker;
import delivery.store.PreferredHooksStore;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class PreferredHookRankerTest {
    @Test
    public void currentScore_axisHook_isTestId() {
        ProvenStep s = new ProvenStep("T", "P", "elementAction", "click",
                "css", "a[data-axis-test-id='side-nav-bill-payment-settlement-link']",
                "", "", "", true, "x");
        try (var ignored = PreferredHooksStore.activate(List.of("data-axis-test-id"))) {
            Assert.assertEquals(ProveWiring.currentScore(s), LocatorRanker.TEST_ID, 0.001);
        }
    }
}
```

If `currentScore` is package-private, test stays in `delivery.job`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PreferredHookRankerTest" test`

Expected: FAIL (score is CSS_CLASS)

- [ ] **Step 3: Write minimal implementation**

`LocatorRanker.testIdAttrs()` returns a new list: `PreferredHooksStore.current()` then the four built-ins, deduped lowercase.

Loop `TEST_ID_ATTRS` in `rank` uses `testIdAttrs()`.

`ProveWiring.currentScore`: if `PreferredHooksStore.matches(v, PreferredHooksStore.current())` or `v.contains("data-axis-test-id")` or existing testid regex → `TEST_ID`.

- [ ] **Step 4: Run tests and make sure they pass**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=PreferredHookRankerTest" test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/delivery/snapshot/LocatorRanker.java src/main/java/delivery/job/ProveWiring.java src/test/java/delivery/job/PreferredHookRankerTest.java
git commit -m "Score site preferred hooks as test ids in the ranker."
```

---

### Task 5: ExpectedQuoteFilter + LeftoverExpectedQuoter

**Files:**
- Create: `src/main/java/delivery/authoring/ExpectedQuoteFilter.java`
- Create: `src/main/java/delivery/authoring/LeftoverExpectedQuoter.java`
- Test: `src/test/java/delivery/authoring/LeftoverExpectedQuoterTest.java`

**Interfaces:**
- Produces: `ExpectedQuoteFilter.FILLERS` — `area`, `button`, `label`, `title`, `page`, `section`, `field`, `menu`, `item`, `list`, `visible`, `shown`, `displayed`
- Produces: `static List<String> tokens(String clause)` — lowercase words, filler removed, quotes stripped
- Produces: `static boolean distinctive(List<String> tokens, String remainderDisplay)` — size>=2 or one token length>=3 with an uppercase letter in original remainder
- Produces: `LeftoverExpectedQuoter.quoteCase(ManualTestCase tc)` — rewrite `expectedResult` rows; skip rows that already contain a `'` or `"` quoted phrase
- Produces: `static String quoteClause(String clause, String steps, String title)`

- [ ] **Step 1: Write the failing tests**

```java
package delivery.authoring;

import delivery.excel.ManualTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LeftoverExpectedQuoterTest {
    @Test
    public void leftoverListArea_quotesUploadTransactions() {
        ManualTestCase tc = new ManualTestCase("TC_3",
                "Verify Bill Payment Settlement",
                "",
                "1. Click the 'Support' in the sidebar.\n2. Click the 'Bill Payment Settlement' in the Support sub-menu.",
                "1. The Support section expands and sub-menu items are shown.\n2. The Bill Payment Settlement page is displayed with the title 'Bill Payment Settlement' and the Upload Transactions list area is visible.",
                "P1", "");
        ManualTestCase out = LeftoverExpectedQuoter.quoteCase(tc);
        Assert.assertTrue(out.expectedResult().contains("'Upload Transactions'"), out.expectedResult());
        Assert.assertFalse(out.expectedResult().toLowerCase().contains("'upload transactions list area'"),
                out.expectedResult());
        Assert.assertTrue(out.expectedResult().contains("'Bill Payment Settlement'"));
    }

    @Test
    public void leftoverTokensAlreadyQuotedInSteps_reusesQuote() {
        ManualTestCase tc = new ManualTestCase("TC_1", "t", "",
                "1. Click the 'Save Draft' button.",
                "1. The Save Draft button is shown.",
                "P1", "");
        ManualTestCase out = LeftoverExpectedQuoter.quoteCase(tc);
        Assert.assertTrue(out.expectedResult().contains("'Save Draft'"), out.expectedResult());
    }

    @Test
    public void fillerOnly_unchanged() {
        ManualTestCase tc = new ManualTestCase("TC_1", "t", "",
                "1. Open home",
                "1. The page is shown",
                "P1", "");
        ManualTestCase out = LeftoverExpectedQuoter.quoteCase(tc);
        Assert.assertEquals(out.expectedResult(), tc.expectedResult());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=LeftoverExpectedQuoterTest" test`

Expected: FAIL (class missing)

- [ ] **Step 3: Write minimal implementation**

`quoteClause`:
1. If clause already has `'...'` or `"..."` leave it (quoted title already correct). For a clause with one quoted title **and** leftover after `and`, split on outcome-and (`ExpectedClauseSplitter.clauses`) and quote only unquoted parts, then join with ` and `.
2. `tokens` = words `[A-Za-z0-9]+` minus FILLERS (whole word, case-insensitive).
3. If not `distinctive`, return clause.
4. Scan steps+title for `'...'` / `"..."` quotes. If any quote’s `tokens` equal the leftover tokens (set equality), return clause with that exact quoted string replacing the leftover noun phrase, or wrap that quote if the whole clause is leftover.
5. Else wrap remaining display words (original casing, filler stripped, spaces collapsed) as `'Upload Transactions'`.

`quoteCase`: map `ExpectedClauseSplitter.rows`, join `"\n"`. Preserve `callBefore` and other record fields.

For TC_3 expected row 2, `clauses` yields title-quoted part plus leftover `the Upload Transactions list area is visible` → quoted leftover `'Upload Transactions'`.

- [ ] **Step 4: Run tests and make sure they pass**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=LeftoverExpectedQuoterTest" test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/delivery/authoring/ExpectedQuoteFilter.java src/main/java/delivery/authoring/LeftoverExpectedQuoter.java src/test/java/delivery/authoring/LeftoverExpectedQuoterTest.java
git commit -m "Quote leftover Expected after stripping filler words."
```

---

### Task 6: Review post-pass + heal.mjs prompt

**Files:**
- Modify: `src/main/java/delivery/portal/service/AuthoringReviewService.java`
- Modify: `tools/cursor-heal/heal.mjs` (`authoringReviewPrompt`)
- Test: `src/test/java/delivery/portal/service/AuthoringReviewServiceTest.java` (new method)

**Interfaces:**
- Consumes: `LeftoverExpectedQuoter.quoteCase`
- Produces: after `TcImportRepair.repairCases`, map each case through `quoteCase` before `toRowMap` / csv
- Produces: SYSTEM_PROMPT line: leftover Expected distinctive names must be quoted after dropping area/button/label/title/page/section/field/menu/item/list/visible/shown/displayed; do not quote those filler words

- [ ] **Step 1: Write the failing test**

In `AuthoringReviewServiceTest`, LLM returns Expected still containing `the Upload Transactions list area is visible` (and the quoted Bill Payment Settlement title). Assert preview `expectedResult` contains `'Upload Transactions'` and does not contain `list area` inside quotes.

Reuse `fixture(...)` / `reviewCases` like the existing `review_ollama` test. Unique `projectId` per test (`review-quote-` + nanoTime) so the shared store does not collide.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=AuthoringReviewServiceTest" test`

Expected: FAIL (leftover unquoted)

- [ ] **Step 3: Write minimal implementation**

```java
List<ManualTestCase> quoted = new ArrayList<>();
for (ManualTestCase tc : repaired) {
    quoted.add(LeftoverExpectedQuoter.quoteCase(tc));
}
repaired = quoted;
```

Insert after `repairCases`, before `GenerateQualityGate.validate`.

Add to `SYSTEM_PROMPT` (and the same sentences in `heal.mjs`):

```
Leftover Expected that names a heading must quote the distinctive words only. Never quote area, button, label, title, page, section, field, menu, item, list, visible, shown, displayed. Example: "the Upload Transactions list area is visible" → 'Upload Transactions'.
```

- [ ] **Step 4: Run tests and make sure they pass**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=AuthoringReviewServiceTest,LeftoverExpectedQuoterTest" test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/delivery/portal/service/AuthoringReviewService.java tools/cursor-heal/heal.mjs src/test/java/delivery/portal/service/AuthoringReviewServiceTest.java
git commit -m "Apply leftover Expected quotes after authoring review."
```

---

### Task 7: LiveHeadingQuote before assert bind

**Files:**
- Create: `src/main/java/delivery/authoring/LiveHeadingQuote.java`
- Modify: `src/main/java/delivery/job/ProvePhase.java` (ASSERT_VISIBLE intent, after `authoringHtml`, before bind/memory)
- Test: `src/test/java/delivery/authoring/LiveHeadingQuoteTest.java`

**Interfaces:**
- Consumes: `ExpectedQuoteFilter.tokens`
- Produces: `static String maybeRewrite(String clause, String liveHtml)` — unchanged unless exactly one collected heading contains every leftover token
- Heading selectors: `h1, h2, h3, [role=heading], .ant-card-head-title, [class*=card-head-title], [class*=card-title]`
- Displayed: skip `[hidden]`, `display:none` in style attr, `aria-hidden=true`
- Match: case-insensitive, title text contains every token from `tokens(clause)` (quotes stripped too). If tokens empty, no rewrite.
- On one match: return `'Live Title Here'` as the full clause (quoted live trim, collapse whitespace) so `OutcomeKind` is QUOTED_TEXT. Do not call vision. Do not save library.

- [ ] **Step 1: Write the failing tests**

```java
package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class LiveHeadingQuoteTest {
    @Test
    public void oneCardTitle_rewritesExactHeading() {
        String html = """
                <html><body>
                <h1>Bill Payment Settlement</h1>
                <div class="ant-card-head-title">Upload Transactions List</div>
                </body></html>
                """;
        String out = LiveHeadingQuote.maybeRewrite(
                "the Upload Transactions list area is visible", html);
        Assert.assertEquals(out, "'Upload Transactions List'");
    }

    @Test
    public void twoMatchingTitles_unchanged() {
        String html = """
                <html><body>
                <h2>Upload Transactions List</h2>
                <div class="card-title">Upload Transactions Archive</div>
                </body></html>
                """;
        String clause = "the Upload Transactions list area is visible";
        Assert.assertEquals(LiveHeadingQuote.maybeRewrite(clause, html), clause);
    }

    @Test
    public void shortQuotedLeftover_upgradesToLiveHeading() {
        String html = """
                <html><body><h1>Upload Transactions List</h1></body></html>
                """;
        Assert.assertEquals(
                LiveHeadingQuote.maybeRewrite("'Upload Transactions'", html),
                "'Upload Transactions List'");
    }

    @Test
    public void zeroHeadings_unchanged() {
        String clause = "the Upload Transactions list area is visible";
        Assert.assertEquals(LiveHeadingQuote.maybeRewrite(clause, "<html><body><p>x</p></body></html>"),
                clause);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=LiveHeadingQuoteTest" test`

Expected: FAIL (class missing)

- [ ] **Step 3: Write minimal implementation**

`LiveHeadingQuote.maybeRewrite` as specified. Escape single quotes in the live title with `''` or use double quotes if the title contains `'`.

In `ProvePhase` single-intent loop, after `html = ProveWiring.authoringHtml(...)` and before locator memory / bind, when `intent.kind() == ASSERT_VISIBLE`:

```java
String rewritten = LiveHeadingQuote.maybeRewrite(intent.text(), html);
if (!rewritten.equals(intent.text())) {
    intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.ASSERT_VISIBLE, rewritten, intent.testData());
}
```

Do not add heal calls. Do not call `locatorMemory.remember` for this rewrite. Do not call `applyReviewedCases`.

- [ ] **Step 4: Run tests and make sure they pass**

Run: `mvn -pl keel-portal -am "-Dmaven.compiler.release=21" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=LiveHeadingQuoteTest,LeftoverExpectedQuoterTest,NestedPreferredHookTest,NestedPreferredHookBindTest,NestedHookVisionGrounderTest,PreferredHookRankerTest,AuthoringReviewServiceTest" test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/delivery/authoring/LiveHeadingQuote.java src/main/java/delivery/job/ProvePhase.java src/test/java/delivery/authoring/LiveHeadingQuoteTest.java
git commit -m "Rewrite leftover Expected from a unique live heading."
```

---

### Task 8: Product docs

**Files:**
- Modify: `docs/PRODUCT.md` (Execute bind / Review cases — user-visible: inner test hooks; leftover Expected quotes; unique heading wait)
- Modify: `CHANGELOG.md` — replace “spec only” with shipped behavior after the code tasks
- Modify: `docs/history/handoffs/2026-10/2026-10-19-nested-hook-and-quote-heading.md` — Status Implemented
- Modify: `docs/superpowers/specs/2026-10-19-nested-hook-and-quote-heading-design.md` — Status Implemented; Plan path
- Modify: `docs/ENGINEERING_HANDOFF.md` / `D:\priv\testpilot\handoffs\RESUME-2026-10-15.md` — implemented

**Interfaces:** none

- [ ] **Step 1: Update docs to match shipped behavior** (no production code)

State how to use it: preferred hooks in Settings; Accept still saves library; Execute may wait on `'Upload Transactions List'` when that heading is unique; duplicate menu hooks stay xpath.

- [ ] **Step 2: Commit**

```bash
git add docs CHANGELOG.md
git commit -m "Document nested preferred-hook bind and leftover Expected quotes."
```

---

## Spec coverage (self-review)

| Spec rule | Task |
|-----------|------|
| Look inside chosen row; one unique document hook → CSS | 1, 2 |
| Duplicate hook → xpath | 1 |
| Twin Settings stay AMBIGUOUS/TIE | 2 (`CandidateScorerTest`) |
| Vision preferred list + unique child | 3 |
| Ranker / currentScore preferred as test id | 4 |
| Review filler denylist + leftover quotes; reuse step quotes | 5, 6 |
| heal.mjs same wording | 6 |
| Execute unique heading; 0/2+ unchanged; no heal; no library write | 7 |
| PRODUCT / changelog | 8 |
