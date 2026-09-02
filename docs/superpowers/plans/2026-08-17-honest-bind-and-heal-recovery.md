# Honest Bind + Heal Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make bind honest on generic layout chrome and text-phrase asserts, then make execute-fail heal ban dead locators and try remaining named controls before Ollama — without any site-specific rules.

**Architecture:** Layer 1 is deterministic (`AccessibleName`, `StepIntentBinder`). Layer 3 extends `HealCascade` with a failed-locator ledger, optional liveness probe, and a named-row retry before Ollama. Ollama/Cursor stay `candidateId` pickers. `ProvePhase` records failed locators from execute and passes a Selenium probe.

**Tech Stack:** Java 21 (`-Dmaven.compiler.source=21 -Dmaven.compiler.target=21` on this machine), TestNG, Jsoup, existing Selenium `ContextSearch` / `SelectorParser`.

**Spec:** `docs/superpowers/specs/2026-08-17-honest-bind-and-heal-recovery-design.md`

## Global Constraints

- **No site hardcoding.** Production and tests must not branch on hostnames, brands, product names, known demo URLs, `href` hosts, or alt-text of a public site. Fixtures are synthetic layout HTML (`/upstream` links, `id="main-column"`, button “Add Item”).
- Do not add denylists for “fork”, “github”, “heroku”, “elemental”, or similar.
- TDD: failing test first, then minimal production code.
- Do not commit unless the user explicitly asks.
- Do not change login `${TARGET_USERNAME}` rewrite, invent provider, or AgentRouter final revise.
- Layer 3 must not guarantee success when the named control is absent.
- Phrase asserts use the existing `extractAssertTextPhrase` + `xpathContainsText` helpers; do not invent a second phrase parser.
- Compiler: `mvn "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21" ...`

## File map

- Modify: `src/main/java/delivery/authoring/AccessibleName.java` — caption-only adjacent text
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` — phrase short-circuit, same-control identity, word-ish tokens, `withoutFailedLocators`
- Modify: `src/main/java/delivery/heal/HealCascade.java` — ledger, named retry, liveness filter, banned text in failure reason
- Modify: `src/main/java/delivery/heal/HealResult.java` — document tier `retry`
- Modify: `src/main/java/delivery/job/ProvePhase.java` — collect failed locators, pass probe
- Create: `src/main/java/delivery/heal/FailedLocator.java`
- Create: `src/main/java/delivery/heal/CandidateLiveness.java`
- Create: `src/main/java/delivery/heal/CandidateLivenessProbe.java`
- Create: `src/main/java/delivery/job/SeleniumCandidateLivenessProbe.java`
- Create tests under `src/test/java/delivery/authoring/` and `src/test/java/delivery/heal/`
- Delete: `src/test/java/delivery/authoring/TheInternetBindDiagnosticTest.java` (dump of a real host; replace with generic fixtures)

## Target flows

```text
bindSingle(ASSERT_VISIBLE with phrase)
  → body xpath textContains (no scoring, no AMBIGUOUS)

bindSingle(CLICK named action)
  → score honest labels only (chrome link does not inherit the content column)

execute fail
  → HealCascade
       drop failed locators + same-control twins
       drop live size 0 / not displayed (if probe present)
       if remaining named rows: bind best, tier=retry  → execute
       else Ollama pick → Cursor solve (banned locators in failureReason)
       else invent (unchanged)
```

---

### Task 1: Adjacent caption (no layout-div theft)

**Files:**
- Create: `src/test/java/delivery/authoring/AccessibleNameCaptionTest.java`
- Modify: `src/main/java/delivery/authoring/AccessibleName.java` (`fromAdjacentText` / `siblingText`)
- Delete: `src/test/java/delivery/authoring/TheInternetBindDiagnosticTest.java`

**Interfaces:**
- Consumes: `AccessibleName.of(Element)`, `DomCandidateExtractor.extract`, `StepIntentBinder.bindSingle`
- Produces: caption siblings only; layout blocks never become a control’s name

- [ ] **Step 1: Write the failing tests**

```java
package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;

import java.util.List;

public class AccessibleNameCaptionTest {

    /** Banner link beside a content column — generic layout, not a real site. */
    private static final String BANNER_BESIDE_CONTENT = """
            <html><body>
              <a href="/upstream"><img alt="Project banner"></a>
              <div id="main-column">
                <h3>Items</h3>
                <button onclick="addItem()">Add Item</button>
              </div>
              <div id="page-end">Powered by Example Labs</div>
            </body></html>
            """;

    @Test
    public void bannerLinkDoesNotInheritContentColumnText() {
        String slim = HtmlSlimmer.slim(BANNER_BESIDE_CONTENT, 80000);
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slim);
        DomCandidate banner = candidates.stream()
                .filter(c -> c.value() != null && c.value().contains("/upstream"))
                .findFirst()
                .orElseThrow();
        String hay = (banner.label() + " " + banner.value()).toLowerCase();
        Assert.assertFalse(hay.contains("add item"),
                "layout sibling must not name the banner, got: " + banner);
    }

    @Test
    public void clickAddItemBindsTheButtonNotTheBanner() {
        String slim = HtmlSlimmer.slim(BANNER_BESIDE_CONTENT, 80000);
        List<DomCandidate> candidates = DomCandidateExtractor.extract(slim);
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.CLICK, "Click the Add Item button");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertTrue(r.steps().get(0).locatorValue().toLowerCase().contains("add item"));
        Assert.assertFalse(r.steps().get(0).locatorValue().contains("/upstream"));
    }

    @Test
    public void shortDivCaptionStillNamesAnAnonymousInput() {
        String html = """
                <html><body><form>
                  <div>Given name</div>
                  <input type="text">
                </form></body></html>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(html);
        Assert.assertTrue(candidates.stream().anyMatch(c ->
                        c.label().toLowerCase().contains("given name")),
                "leaf caption div must still name the input, got: " + candidates);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```text
mvn "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21" "-Dtest=AccessibleNameCaptionTest" test
```

Expected: `bannerLinkDoesNotInheritContentColumnText` or `clickAddItemBindsTheButtonNotTheBanner` FAIL because the banner label currently contains “Add Item”.

- [ ] **Step 3: Implement caption-only adjacent text**

In `AccessibleName.siblingText`, treat an element sibling as a caption only when `isCaptionSibling(sibling)`:

```java
private static final List<String> CAPTION_TAGS = List.of("span", "label", "p", "strong", "b");

private static boolean isCaptionSibling(Element sibling) {
    if (sibling == null) {
        return false;
    }
    String tag = sibling.tagName().toLowerCase(Locale.ROOT);
    if (!CAPTION_TAGS.contains(tag) && !"div".equals(tag)) {
        return false;
    }
    if (!sibling.select("a, button, input, select, textarea, [role=button], [role=link], [role=combobox]").isEmpty()) {
        return false;
    }
    if (sibling.select("*").size() > 2) {
        return false;
    }
    String text = clean(sibling.ownText().isBlank() ? sibling.text() : sibling.ownText());
    if (text.isBlank() || text.length() > 40) {
        return false;
    }
    return true;
}
```

Keep `span`/`label`/`p`/`strong`/`b` when they pass the interactive + length checks. Leaf `div` (“Given name” next to an input) stays allowed. A content column with a heading + button is rejected.

Do **not** special-case `href`, hosts, or alt text.

- [ ] **Step 4: Re-run `AccessibleNameCaptionTest` — expect PASS**

- [ ] **Step 5: Delete `TheInternetBindDiagnosticTest.java`** (it encodes a real host). Confirm `LabelAnchoredExtractionTest` still passes.

---

### Task 2: Phrase asserts skip scoring / AMBIGUOUS

**Files:**
- Create: `src/test/java/delivery/authoring/PhraseAssertBindTest.java`
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` (`bindSingle`, after `assertText` is extracted)

**Interfaces:**
- Consumes: `extractAssertTextPhrase`, `xpathContainsText`
- Produces: `BindResult` with `assertionType=textContains` and the body xpath when a phrase exists

- [ ] **Step 1: Write the failing tests**

```java
package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;
import parsingLayer.HtmlSlimmer;

import java.util.List;

public class PhraseAssertBindTest {

    @Test
    public void confirmTheTextUsesBodyXpathDespiteChromeCandidates() {
        String html = """
                <html><body>
                  <a href="/upstream"><img alt="Project banner"></a>
                  <div id="main-column">
                    <h3>Choice List</h3>
                    <select id="choice"><option>One</option></select>
                  </div>
                </body></html>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(HtmlSlimmer.slim(html, 80000));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm the text Choice List is visible");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "textContains");
        Assert.assertEquals(r.steps().get(0).assertionExpected(), "Choice List");
        Assert.assertEquals(r.steps().get(0).locatorStrategy(), "xpath");
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("Choice List"));
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("//body"));
    }

    @Test
    public void hiddenPhraseStillBindsBodyXpath() {
        String html = """
                <html><body>
                  <div id="start"><button>Start</button></div>
                  <div id="finish" style="display:none"><h4>Hello World!</h4></div>
                </body></html>
                """;
        List<DomCandidate> candidates = DomCandidateExtractor.extract(HtmlSlimmer.slim(html, 80000));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE,
                "Confirm the text Hello World! is visible");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "textContains");
        Assert.assertTrue(r.steps().get(0).locatorValue().contains("Hello World!"));
    }

    @Test
    public void controlVisibleAssertIsUnchanged() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "id", "logout", "a", "Logout"));
        var intent = new StepIntentBinder.IntentLine(
                StepIntentBinder.IntentKind.ASSERT_VISIBLE, "Confirm the Logout button is visible");
        StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", candidates, List.of());
        Assert.assertTrue(r.ok(), r.rejectReason());
        Assert.assertEquals(r.steps().get(0).assertionType(), "visible");
        Assert.assertEquals(r.steps().get(0).locatorValue(), "logout");
    }
}
```

- [ ] **Step 2: Run `PhraseAssertBindTest` — expect FAIL** on the first test (`AMBIGUOUS:ASSERT_VISIBLE:...`).

- [ ] **Step 3: Short-circuit in `bindSingle`** immediately after `assertText` is computed (before `scored.isEmpty()` / `AMBIGUOUS`):

```java
if (assertText != null && !assertText.isBlank()) {
    String xpath = xpathContainsText(assertText);
    boolean ok = new LocatorValidator().validate(
            new LocatorCandidate("xpath", xpath, "Page", "")).valid();
    return new BindResult(List.of(new ProvenStep(tcId, "Page", "elementAction", "assert",
            "xpath", xpath, "", "textContains", assertText, ok,
            ok ? "intent:ASSERT_VISIBLE:text" : "xpath allowlist rejected")), "");
}
```

Keep the existing `notVisible` / `selected` branches first. Remove the later duplicate `scored.isEmpty() && assertText` and `best.score < 2 && assertText` bodies so phrase asserts cannot fall through to candidate scoring.

Do **not** treat “Confirm the Logout button is visible” as a phrase (`extractAssertTextPhrase` already returns null unless wording is text/message/label/heading/quoted).

- [ ] **Step 4: Re-run `PhraseAssertBindTest` and `StepIntentBinderTest` — expect PASS**

---

### Task 3: Same-control identity (id vs css vs xpath)

**Files:**
- Create: `src/test/java/delivery/authoring/SameControlFingerprintTest.java`
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` (`selectorFingerprint`)

**Interfaces:**
- Consumes: `describesSameControl`
- Produces: `id=choice`, `select[id='choice']`, `//select[@id='choice']` compare equal

- [ ] **Step 1: Write the failing test**

```java
package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SameControlFingerprintTest {

    @Test
    public void idCssAndXpathOfSameIdAreOneControl() {
        DomCandidate id = new DomCandidate("c1", "id", "choice", "select", "choice");
        DomCandidate css = new DomCandidate("c2", "css", "select[id='choice']", "select", "choice");
        DomCandidate xpath = new DomCandidate("c3", "xpath", "//select[@id='choice']", "select", "choice");
        Assert.assertTrue(StepIntentBinder.describesSameControl(id, css));
        Assert.assertTrue(StepIntentBinder.describesSameControl(css, xpath));
        Assert.assertFalse(StepIntentBinder.describesSameControl(
                id, new DomCandidate("c9", "id", "other", "select", "other")));
    }
}
```

- [ ] **Step 2: Run it — expect FAIL** (`id|choice` vs `select|select id= choice`).

- [ ] **Step 3: Extend `selectorFingerprint`** to prefer a stable attr key:

```java
private static final Pattern ATTR_ID = Pattern.compile(
        "(?:\\[#?id\\s*=\\s*['\"]([^'\"]+)['\"]\\]|\\[@id\\s*=\\s*['\"]([^'\"]+)['\"]\\]|\\[id=['\"]([^'\"]+)['\"]\\])",
        Pattern.CASE_INSENSITIVE);

private static String selectorFingerprint(DomCandidate c) {
    String tag = c.tag() == null ? "" : c.tag().toLowerCase(Locale.ROOT);
    String strategy = c.strategy() == null ? "" : c.strategy().toLowerCase(Locale.ROOT);
    String value = c.value() == null ? "" : c.value();
    if ("id".equals(strategy) && !value.isBlank()) {
        return tag + "|id:" + value.toLowerCase(Locale.ROOT);
    }
    Matcher m = ATTR_ID.matcher(value);
    if (m.find()) {
        String id = firstNonBlank(m.group(1), m.group(2), m.group(3));
        if (id != null) {
            return tag + "|id:" + id.toLowerCase(Locale.ROOT);
        }
    }
    // existing normalize fallback
}
```

Keep the current string-normalize fallback when there is no id. Do not key on `href` hosts.

Also use this fingerprint inside `sameSpentTarget` / failed-locator exclusion (Task 5) so css+xpath twins drop together.

- [ ] **Step 4: Re-run `SameControlFingerprintTest` — expect PASS**

---

### Task 4: Word-ish token match

**Files:**
- Create: `src/test/java/delivery/authoring/HayContainsTokenTest.java`
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` (`hayContainsToken`; use it from `tokenOverlapScore`)

**Interfaces:**
- Consumes: distinctive-token and overlap scoring
- Produces: `element` ∉ `elemental`; `bike` ∈ `bike-light`

- [ ] **Step 1: Write the failing test**

```java
package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HayContainsTokenTest {

    @Test
    public void tokenIsNotAPrefixOfALongerWord() {
        Assert.assertFalse(StepIntentBinder.hayContainsToken("elemental selenium", "element"));
        Assert.assertTrue(StepIntentBinder.hayContainsToken("add item", "item"));
        Assert.assertTrue(StepIntentBinder.hayContainsToken("bike-light", "bike"));
        Assert.assertTrue(StepIntentBinder.hayContainsToken("user_email", "email"));
    }
}
```

(`hayContainsToken` is package-visible; test is in the same package.)

- [ ] **Step 2: Run — expect FAIL** (`elemental` currently contains `element`).

- [ ] **Step 3: Replace the body of `hayContainsToken`**

```java
static boolean hayContainsToken(String hay, String token) {
    if (hay == null || token == null || token.isBlank()) {
        return false;
    }
    String h = hay.toLowerCase(Locale.ROOT);
    String t = token.toLowerCase(Locale.ROOT);
    java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "(^|[^a-z0-9])" + java.util.regex.Pattern.quote(t) + "([^a-z0-9]|$)");
    return p.matcher(h).find();
}
```

Hyphen/underscore are `[^a-z0-9]`, so `bike-light` still matches `bike`. Change `tokenOverlapScore` to call `hayContainsToken` instead of `hay.contains(t)` so scoring and gates agree.

- [ ] **Step 4: Re-run `HayContainsTokenTest` and `StepIntentBinderTest` — expect PASS**

---

### Task 5: Failed-locator ledger types + exclusion

**Files:**
- Create: `src/main/java/delivery/heal/FailedLocator.java`
- Create: `src/test/java/delivery/authoring/WithoutFailedLocatorsTest.java`
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` (`withoutFailedLocators`)

**Interfaces:**

```java
package delivery.heal;

public record FailedLocator(String strategy, String value, String errorSummary) {}
```

```java
// StepIntentBinder
public static List<DomCandidate> withoutFailedLocators(
        List<DomCandidate> candidates, List<delivery.heal.FailedLocator> failed)
```

A candidate is dropped if `strategy+value` equals a failed locator **or** `describesSameControl` against a synthetic `DomCandidate` built from that locator.

- [ ] **Step 1: Write the failing test**

```java
package delivery.authoring;

import delivery.heal.FailedLocator;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

public class WithoutFailedLocatorsTest {

    @Test
    public void dropsFailedCssAndItsXpathTwin() {
        List<DomCandidate> candidates = List.of(
                new DomCandidate("c1", "css", "a[href='/upstream']", "a", "banner"),
                new DomCandidate("c2", "xpath", "//a[@href='/upstream']", "a", "banner"),
                new DomCandidate("c3", "xpath",
                        "//button[contains(normalize-space(.),'Add Item')]", "button", "Add Item"));
        List<DomCandidate> left = StepIntentBinder.withoutFailedLocators(
                candidates,
                List.of(new FailedLocator("css", "a[href='/upstream']", "zero size")));
        Assert.assertEquals(left.size(), 1);
        Assert.assertEquals(left.get(0).id(), "c3");
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (method missing or twins not dropped).

- [ ] **Step 3: Implement `withoutFailedLocators`** next to `withoutSpentControls`, reusing `describesSameControl` / `locatorEquals`. For href css vs xpath twins, also compare normalized locator values (strip `//`, `@`, quotes) so they drop together even when there is no id.

- [ ] **Step 4: Re-run — expect PASS**

---

### Task 6: Heal named retry before Ollama

**Files:**
- Modify: `src/main/java/delivery/heal/HealCascade.java`
- Modify: `src/main/java/delivery/heal/HealResult.java` (javadoc: tier `retry` is allowed)
- Create: `src/test/java/delivery/heal/HealNamedRetryTest.java`

**Interfaces:**
- Extend the existing full `heal(...)` so current callers compile:

```java
public HealResult heal(..., List<ProvenStep> spent) {
    return heal(..., spent, List.of(), null);
}

public HealResult heal(
        String tcId,
        StepIntentBinder.IntentLine intent,
        String slimHtml,
        byte[] pngOrNull,
        String failureReason,
        Path screenshotPathOrNull,
        boolean tryOllama,
        List<String> priorStepSummaries,
        boolean allowInvent,
        List<ProvenStep> spent,
        List<FailedLocator> failed,
        CandidateLivenessProbe probe)
```

If `CandidateLivenessProbe` does not exist yet, add `failed` in this task and add `probe` in Task 7. Old overloads delegate with `List.of()` / `null`.

After extract + spent filter:

```java
candidates = StepIntentBinder.withoutFailedLocators(candidates, failed);
```

Append banned locators onto `reason` before Ollama/Cursor:

```text
Banned locators (do not pick the same control):
- css: a[href='/upstream'] → zero size
```

**Deterministic retry (before Ollama):**

```java
List<DomCandidate> named = StepIntentBinder.retainDistinctiveMatches(intent, candidates);
if (named.isEmpty() && intent.kind() == CLICK && intentRequiresNamedActionControl(intent.text())) {
    named = fallbackNamedActionMatches(intent, candidates);
}
if (!named.isEmpty()) {
    DomCandidate pick = authoring.shortlistForIntent(intent, named, 1).get(0);
    List<ProvenStep> steps = authoring.stepsPreferringCandidate(tcId, intent, candidates, pick.id(), false);
    if (validHealSteps(intent, candidates, steps, false, spentSteps)) {
        return HealResult.success(steps, "retry");
    }
}
// existing Ollama → Cursor → invent
```

When `named.size()==1` this **skips Ollama**. When `named.size()>1`, still try the top-ranked named row first (`retry`); only if `validHealSteps` rejects it continue to Ollama.

Phrase intents should already be bound in layer 1; if heal is invoked anyway, `stepsPreferringCandidate` already emits body xpath — keep that.

- [ ] **Step 1: Write the failing test**

Use banner+button HTML. Fake Ollama/Cursor that would pick the banner. Pass `failed` = the banner css. Assert heal returns the button and `tierUsed=retry`, and that Ollama was **not** called.

```java
@Test
public void retriesNamedButtonWithoutCallingOllama() {
    String html = """
            <body>
              <a href="/upstream">banner</a>
              <button onclick="addItem()">Add Item</button>
            </body>
            """;
    AtomicInteger ollama = new AtomicInteger();
    LocalLlmClient llm = new LocalLlmClient("http://127.0.0.1:9", "x") {
        @Override public String completeJson(String system, String user) {
            ollama.incrementAndGet();
            return "{\"candidateId\":\"c1\"}";
        }
        @Override public String completeJson(String system, String user, byte[] imagePng) {
            return completeJson(system, user);
        }
    };
    HealCascade cascade = new HealCascade(new AuthoringService(llm, new LocatorValidator()),
            new CursorHealClient(false, "unused", 1));
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Click the Add Item button");
    HealResult result = cascade.heal("TC", intent, html, null, "zero size", null, true,
            List.of(), true, List.of(),
            List.of(new FailedLocator("css", "a[href='/upstream']", "zero size")));
    Assert.assertTrue(result.ok(), result.reason());
    Assert.assertEquals(result.tierUsed(), "retry");
    Assert.assertEquals(ollama.get(), 0);
    Assert.assertTrue(result.steps().get(0).locatorValue().toLowerCase().contains("add item"));
}
```

Adjust the `heal` overload to match the signature you add.

- [ ] **Step 2: Run `HealNamedRetryTest` — expect FAIL**

- [ ] **Step 3: Implement extract filter + named retry + banned text**

- [ ] **Step 4: Re-run `HealNamedRetryTest` and `HealCascadeTest` — expect PASS**

Existing `HealCascadeTest` cases must still call Ollama when there is **no** unused named row (e.g. submit-button HTML with no failed ledger). If named retry would steal those cases, only run named retry when `failed` is non-empty **or** a liveness probe dropped at least one row. That keeps today’s Ollama unit tests intact while still covering “we already burned a locator”.

---

### Task 7: Live liveness probe

**Files:**
- Create: `src/main/java/delivery/heal/CandidateLiveness.java`
- Create: `src/main/java/delivery/heal/CandidateLivenessProbe.java`
- Create: `src/main/java/delivery/job/SeleniumCandidateLivenessProbe.java`
- Create: `src/test/java/delivery/heal/HealLivenessFilterTest.java`
- Modify: `src/main/java/delivery/heal/HealCascade.java` (filter shortlist/named pool)
- Modify: `src/main/java/delivery/job/ProvePhase.java` (construct probe)

**Interfaces:**

```java
package delivery.heal;

public record CandidateLiveness(boolean displayed, boolean enabled, int width, int height) {
    public boolean interactable() {
        return displayed && enabled && width > 0 && height > 0;
    }
}

@FunctionalInterface
public interface CandidateLivenessProbe {
    CandidateLiveness probe(DomCandidate candidate);
}
```

`SeleniumCandidateLivenessProbe` uses `SelectorParser.toBy` + `ContextSearch.find`. On miss or exception, return `new CandidateLiveness(false, false, 0, 0)` — do not throw. Read `getSize()` for width/height.

In `HealCascade`, if `probe != null`, drop candidates where `!probe.probe(c).interactable()` before named retry / shortlist. If probe is null (unit tests without a driver), skip the filter.

- [ ] **Step 1: Write the failing test** with a fake probe that marks the banner as 0×0 and the button as 80×20. No failed ledger. Assert heal picks the button (`retry`) and does not call Ollama.

- [ ] **Step 2: Run — expect FAIL** (banner still ranked first without a probe filter).

- [ ] **Step 3: Implement records, Selenium probe, HealCascade filter. ProvePhase passes `new SeleniumCandidateLivenessProbe(driverFactory)` into every `heal(...)` in `attemptIntentWithRetry` and the batch-heal path.

- [ ] **Step 4: Re-run `HealLivenessFilterTest` + `HealCascadeTest` + `HealNamedRetryTest` — expect PASS**

---

### Task 8: ProvePhase failed-locator collection

**Files:**
- Modify: `src/main/java/delivery/job/ProvePhase.java` (`attemptIntentWithRetry`)
- Create: `src/test/java/delivery/job/FailedLocatorCollectionTest.java`

Extract a package-visible helper (on `ProvePhase` or `FailedLocator`):

```java
static List<FailedLocator> recordFailures(List<ProvenStep> attempted, String executeError)
```

Logic: for each attempted step in the failed batch, add `new FailedLocator(strategy, value, trimmedError)`. Dedup by strategy+value. Cap error summary at 200 chars.

In `attemptIntentWithRetry`, keep `List<FailedLocator> failedThisIntent = new ArrayList<>()` and pass it to every `healCascade.heal`. After a failed `execution.execute`, append `recordFailures(stepBatch, lastReason)` (and healed retry batches).

- [ ] **Step 1: Unit-test `recordFailures`** with one click step and a zero-size error string.

- [ ] **Step 2: Run — expect FAIL** (helper missing).

- [ ] **Step 3: Implement helper + wire heal call sites in `ProvePhase` to pass `failedThisIntent` + liveness probe.

- [ ] **Step 4: Re-run that test — expect PASS**

Do not parse hostnames out of the error string.

---

### Task 9: Verification

- [ ] **Step 1: Run the new tests together**

```text
mvn "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21" "-Dtest=AccessibleNameCaptionTest,PhraseAssertBindTest,SameControlFingerprintTest,HayContainsTokenTest,WithoutFailedLocatorsTest,HealNamedRetryTest,HealLivenessFilterTest,FailedLocatorCollectionTest" test
```

Expected: all PASS.

- [ ] **Step 2: Run the full unit suite**

```text
mvn "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21" test
```

Expected: existing suite green. If `pom` compiler 24 fails on this JDK, keep the 21 overrides.

- [ ] **Step 3: Grep production code for site leakage**

Search `src/main/java` for `github`, `heroku`, `the-internet`, `tourdedave`, `elementalselenium`. Expected: no matches added by this work.

- [ ] **Step 4: Do not commit** unless the user asks.

---

## Spec coverage

| Spec item | Task |
|-----------|------|
| Caption-only adjacent name | 1 |
| Generic fixtures, no site denylist | 1–8, Global Constraints |
| Phrase assert → body xpath | 2 |
| Same-control id/css/xpath | 3 |
| Word-ish tokens | 4 |
| Failed locator ledger | 5, 8 |
| Named retry before Ollama | 6 |
| Live size/displayed filter | 7 |
| Invent / login / AgentRouter untouched | (explicit non-goals) |

## Self-review

- No TBD placeholders.
- `heal` overloads: Task 6 adds `failed`; Task 7 adds `probe`; old overloads delegate.
- Tier name locked as `retry`.
- Phrase parser not duplicated.
- Named retry gated on failed ledger or liveness drops so existing Ollama unit tests keep calling Ollama on a clean first heal.
