# Candidate Scorer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bind chooses a locator from a scored DOM shortlist before Ollama or vision, updates the catalog only after a live click proves it, measures that in `bind-quality.json` plus the fixture gate, and on a TIE sends a scored pick card (not invent).

**Architecture:** Extract `CandidateScorer` + `BindDecision` from the token-overlap soup in `StepIntentBinder.scoreCandidates`. Copy Playwright-style stability bands from `LocatorRanker` as integers (do not call `LocatorRanker`). Bind, heal shortlist, and catalog preference all call the scorer. Fingerprint ignores class-hash churn; vision may not grow the catalog until execute PASS. Asserts still do not heal.

**Tech Stack:** Java 21, Jsoup, TestNG, existing `DomCandidateExtractor` / `StepIntentBinder` / `PageLocatorCatalog` / `HealCascade` / `ProvePhase`.

## Global Constraints

- Site-agnostic: no AxisPay / the-internet hostnames in production code. Fixtures are synthetic HTML.
- Keel owns locators; models only pick `candidateId` from the shortlist.
- Do not invent locators in this pack. Do not add heal invent. Do not go vision-first.
- ASSERT_VISIBLE never enters HealCascade (`ProvePhase.mayHeal` stays false).
- Portal templates, codegen, and `LocatorPipeline` / `LocatorRanker.rank(WebDriver, …)` stay out of the bind path.
- TDD: no production code before a failing TestNG test. Watch it fail.
- Run tests with `mvn -pl keel-portal "-Dtest=ClassName"` (PowerShell splits unquoted commas).
- After each task: commit as `rsayed <rsayed@axisapp.com>`, no `Co-authored-by: Cursor` (use `git commit-tree` if `git commit` adds one). Do not push. Do not commit `META-INF/`, `target/`, or the portal DB.
- Do not start a live Execute/Automate job (credits). `CandidateScorerFixtureTest` is the gate before any AxisPay spend.
- No Claude / Anthropic models.

## File map

| File | Role |
|------|------|
| `delivery.authoring.CandidateScore` | Per-candidate ints: stability, uniqueness, intentMatch, roleFit, labelDistance, total |
| `delivery.authoring.BindDecision` | `CLEAR` / `TIE` / `EMPTY` + winner + shortlist |
| `delivery.authoring.CandidateScorer` | `score` + `decide`; constants `MARGIN=12`, `MIN_TOTAL=15`, `SHORTLIST_MAX=5` |
| `delivery.authoring.BindQualityMetrics` | Job counters + `BIND_QUALITY` log line + `bind-quality.json` |
| `delivery.authoring.HealPickPayload` | TIE pick card: scored table, whyTied, quotedName, capped neighborhood |
| `DomCandidateExtractor` | Role+name emit; skip generated-class CSS |
| `StepIntentBinder` | `scoreCandidates` delegates to scorer; AMBIGUOUS uses `BindDecision` |
| `AuthoringService.shortlistForIntent` | Top 5 from scorer |
| `HealCascade` | Ollama/Cursor **pick** only on TIE shortlist (max 5); invent stays a later call |
| `AuthoringService.healIntentWithOllama` | Scored pick prompt; `candidateId` JSON only; no Thought; no invent |
| `PageLocatorCatalog` | Fingerprint without nav text / class hash; pending vision; `commitAfterProof` |
| `ProvePhase` | Commit catalog after execute PASS; increment metrics |
| Tests under `src/test/java/delivery/authoring/` | One class per task |
| `src/test/resources/locator-fixtures/scorer-sidebar.html` | Static Support sidebar |

`delivery.snapshot.LocatorRanker` is the **reference for band order only**. Do not import it from authoring tests.

---

### Task 1: Score record + stability bands

**Files:**
- Create: `src/main/java/delivery/authoring/CandidateScore.java`
- Create: `src/main/java/delivery/authoring/CandidateScorer.java`
- Create: `src/test/java/delivery/authoring/CandidateScorerTest.java`

**Interfaces:**
- Produces: `CandidateScore(DomCandidate candidate, int stability, int uniqueness, int intentMatch, int roleFit, int labelDistance)` with `int total()` = sum
- Produces: `CandidateScorer.stability(DomCandidate c)` → 0–50
- Produces: `MARGIN=12`, `MIN_TOTAL=15`, `SHORTLIST_MAX=5`
- Consumes: `PreferredHooksStore.current()`, `GeneratedIdDetector.looksGenerated`, `DomCandidateExtractor.isTestHookAttr` / strategy names

- [ ] **Step 1: Write the failing test**

```java
package delivery.authoring;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CandidateScorerTest {

    @Test
    public void preferredHookOutranksId() {
        DomCandidate hook = new DomCandidate("c1", "css",
                "button[data-axis-test-id='save-hook']", "button", "Save");
        DomCandidate id = new DomCandidate("c2", "id", "btn-save", "button", "Save");
        Assert.assertTrue(CandidateScorer.stability(hook) > CandidateScorer.stability(id),
                hook + " vs " + id);
    }

    @Test
    public void testIdOutranksRawId() {
        DomCandidate testid = new DomCandidate("c1", "data-testid", "save", "button", "Save");
        DomCandidate id = new DomCandidate("c2", "id", "btn-save", "button", "Save");
        Assert.assertTrue(CandidateScorer.stability(testid) > CandidateScorer.stability(id));
    }

    @Test
    public void generatedIdScoresZero() {
        DomCandidate ember = new DomCandidate("c1", "id", "ember1234", "button", "Refresh");
        Assert.assertEquals(CandidateScorer.stability(ember), 0);
    }

    @Test
    public void textXpathBelowStableId() {
        DomCandidate id = new DomCandidate("c1", "id", "save-changes", "button", "Save");
        DomCandidate xp = new DomCandidate("c2", "xpath",
                "//button[normalize-space()='Save']", "button", "Save");
        Assert.assertTrue(CandidateScorer.stability(id) > CandidateScorer.stability(xp));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl keel-portal "-Dtest=CandidateScorerTest" test`

Expected: FAIL compile (`CandidateScorer` does not exist) or assertion fail.

- [ ] **Step 3: Write minimal implementation**

```java
public record CandidateScore(
        DomCandidate candidate,
        int stability,
        int uniqueness,
        int intentMatch,
        int roleFit,
        int labelDistance
) {
    public int total() {
        return stability + uniqueness + intentMatch + roleFit + labelDistance;
    }
}

public final class CandidateScorer {
    public static final int MARGIN = 12;
    public static final int MIN_TOTAL = 15;
    public static final int SHORTLIST_MAX = 5;

    private CandidateScorer() {}

    public static int stability(DomCandidate c) {
        if (c == null) return 0;
        String s = c.strategy() == null ? "" : c.strategy().toLowerCase(Locale.ROOT);
        String v = c.value() == null ? "" : c.value();
        if (PreferredHooksStore.matches(v, PreferredHooksStore.current())
                || PreferredHooksStore.matches(s, PreferredHooksStore.current())) {
            return 50;
        }
        if (("css".equals(s) || "cssselector".equals(s) || "xpath".equals(s))
                && (v.contains("data-axis-test-id") || containsPreferredHook(v))) {
            return 50;
        }
        if ("data-testid".equals(s) || "data-test".equals(s) || "data-qa".equals(s) || "testid".equals(s)) {
            return 45;
        }
        if (v.contains("data-testid") || v.contains("data-test=") || v.contains("data-qa=")) {
            return 45;
        }
        if (v.contains("[role=") && (v.contains("aria-label") || v.contains("name="))) {
            return 40;
        }
        if (v.contains("aria-label") || s.contains("aria")) {
            return 36;
        }
        if ("name".equals(s)) {
            return 30;
        }
        if ("id".equals(s)) {
            return GeneratedIdDetector.looksGenerated(v) ? 0 : 24;
        }
        if ("css".equals(s) || "cssselector".equals(s)) {
            if (v.contains(":nth-")) return 2;
            return 12;
        }
        if ("xpath".equals(s)) {
            if (v.matches(".*\\[\\d+\\].*")) return 2;
            return 8;
        }
        return 0;
    }
}
```

Keep `containsPreferredHook` iterating `PreferredHooksStore.current()`.

- [ ] **Step 4: Run tests and make sure they pass**

Run: `mvn -pl keel-portal "-Dtest=CandidateScorerTest" test`

Expected: Tests run: 4, Failures: 0.

- [ ] **Step 5: Commit**

```
git add src/main/java/delivery/authoring/CandidateScore.java src/main/java/delivery/authoring/CandidateScorer.java src/test/java/delivery/authoring/CandidateScorerTest.java
```

Message: `Rank test ids above raw ids, and give generated ids no stability.`

---

### Task 2: Full score + BindDecision

**Files:**
- Create: `src/main/java/delivery/authoring/BindDecision.java`
- Modify: `src/main/java/delivery/authoring/CandidateScorer.java`
- Modify: `src/test/java/delivery/authoring/CandidateScorerTest.java`

**Interfaces:**
- Consumes: Task 1 `stability`, `CandidateScore.total()`
- Produces: `CandidateScorer.score(IntentLine intent, List<DomCandidate> candidates)` → `List<CandidateScore>` best-first
- Produces: `CandidateScorer.decide(List<CandidateScore> scores)` → `BindDecision`
- Produces: `BindDecision.Kind { CLEAR, TIE, EMPTY }`
- Same-control twins (`StepIntentBinder` locator fingerprint): treat as one runner-up skip. Duplicate the twin check **by calling** existing `StepIntentBinder` package-visible helpers if they are already `static` and usable; otherwise copy the “same id / css vs xpath for same node” rule into a `CandidateScorer.sameControl` that matches `NearTieAmbiguousTest.fingerprintTwins_notAmbiguous`.

- [ ] **Step 1: Write the failing tests** (append to `CandidateScorerTest`)

```java
@Test
public void quotedNameBeatsSharedActionToken() {
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
    List<DomCandidate> cs = List.of(
            new DomCandidate("c1", "css", "span[data-axis-test-id='side-nav-support-submenu-button']",
                    "span", "Support"),
            new DomCandidate("c2", "css", "a[data-axis-test-id='side-nav-home-link']",
                    "a", "Home")
    );
    BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
    Assert.assertEquals(d.kind(), BindDecision.Kind.CLEAR);
    Assert.assertTrue(d.winner().value().contains("support"));
}

@Test
public void twinSameNameIsTie() {
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Click Settings");
    List<DomCandidate> cs = List.of(
            new DomCandidate("c1", "id", "settings-primary", "button", "Settings"),
            new DomCandidate("c2", "id", "settings-advanced", "button", "Settings")
    );
    BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
    Assert.assertEquals(d.kind(), BindDecision.Kind.TIE);
    Assert.assertEquals(d.shortlist().size(), 2);
    Assert.assertNull(d.winner());
}

@Test
public void locatorTwinsAreClear() {
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Click Save");
    List<DomCandidate> cs = List.of(
            new DomCandidate("c1", "css", "#save-btn", "button", "Save"),
            new DomCandidate("c2", "xpath", "//button[@id='save-btn']", "button", "Save")
    );
    BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
    Assert.assertEquals(d.kind(), BindDecision.Kind.CLEAR, d.toString());
}

@Test
public void emptyWhenNothingMatches() {
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Click Warp Drive");
    List<DomCandidate> cs = List.of(
            new DomCandidate("c1", "id", "cancel", "button", "Cancel")
    );
    BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
    Assert.assertEquals(d.kind(), BindDecision.Kind.EMPTY);
}
```

Scoring rules to implement (ints):

- `uniqueness`: label (ignore case, trim) shared with 0 others → +15; 1 other → 0; 2+ others → −10. Locator twins of the same node do not count as others.
- `intentMatch`: quoted visible name equals label (ignore case) → 40; else token overlap on label × 6 (cap 30); plus overlap on value × 2 (cap 10). Use the same tokenizer idea as `StepIntentBinder` (split `[^a-z0-9]+`, drop length &lt; 3). Quoted name: reuse `StepIntentBinder.quotedVisibleName` if accessible; else copy the quote regex.
- `roleFit`: CLICK / CLICK_LOGIN on `button`, `a`, `menuitem`, `summary`, or role in locator → 10; `input`/`select`/`textarea` for TYPE_* → 10; CLICK on `div`/`span`/`li` without menuitem → 2; else 4.
- `labelDistance`: label equals quoted or full distinctive tokens → 10; label contains a distinctive token → 6; only `value` contains it → 2; else 0.

`decide`:

- empty or best `total() < MIN_TOTAL` → EMPTY, shortlist empty
- else skip runner-ups that `sameControl` with best
- if no remaining runner-up or `best.total() - runner.total() >= MARGIN` → CLEAR, shortlist of 1
- else TIE, shortlist distinct controls up to `SHORTLIST_MAX` whose total is within MARGIN of best

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl keel-portal "-Dtest=CandidateScorerTest" test`

Expected: FAIL (`score` / `decide` missing).

- [ ] **Step 3: Implement `score` and `decide`**

Do not wire `StepIntentBinder` yet.

- [ ] **Step 4: Run tests**

Run: `mvn -pl keel-portal "-Dtest=CandidateScorerTest" test`

Expected: all pass.

- [ ] **Step 5: Commit**

Message: `Score uniqueness and intent, and only call a winner when the margin is clear.`

---

### Task 3: Binder uses the scorer

**Files:**
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` (`scoreCandidates` ~552, near-tie ~344–346, `rankedCandidates` ~450)
- Modify: `src/test/java/delivery/authoring/NearTieAmbiguousTest.java` only if a message string changes; behavior must stay

**Interfaces:**
- Consumes: `CandidateScorer.score`, `CandidateScorer.decide`
- Produces: `scoreCandidates` returns `List<Scored>` in scorer order (`Scored.score` = `CandidateScore.total()`)
- CLEAR → existing ProvenStep path
- TIE without `tieBreak` preferred id → `AMBIGUOUS:KIND:` + shortlist ids (same prefix as today)
- EMPTY → existing `No DOM candidate` / `Weak candidate match` (EMPTY covers both)

Keep submit / ordinal / named-action gates **after** scored list exists. If those gates pick a control, they still win (today’s `namedActionResolved` skips near-tie). Optionally add their bonuses into scorer later; do not delete the gates in this task.

Replace `NEAR_TIE_SCORE_WINDOW` usage in the generic near-tie block with `decide`. Leave the constant in the class for one release as `@Deprecated` pointing at `CandidateScorer.MARGIN`, or delete it if nothing else references it (update this test class).

- [ ] **Step 1: Write / extend a failing test**

Add to `CandidateScorerTest` or `NearTieAmbiguousTest`:

```java
@Test
public void bindSingle_usesHookNotTextXpath() {
    List<DomCandidate> cs = List.of(
            new DomCandidate("c1", "xpath", "//button[normalize-space()='Save']", "button", "Save"),
            new DomCandidate("c2", "css", "button[data-axis-test-id='save-hook']", "button", "Save")
    );
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Click Save");
    StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC", cs, List.of());
    Assert.assertTrue(r.ok(), r.rejectReason());
    Assert.assertTrue(r.steps().get(0).locatorValue().contains("save-hook"),
            r.steps().get(0).locatorValue());
}
```

- [ ] **Step 2: Run** `mvn -pl keel-portal "-Dtest=NearTieAmbiguousTest,CandidateScorerTest" test`

Expected: new bind test FAIL (xpath may still win on token overlap + old `strategyRank/10`).

- [ ] **Step 3: Delegate `scoreCandidates`**

```java
private static List<Scored> scoreCandidates(
        IntentLine intent, List<DomCandidate> candidates, OrdinalControl ordinal) {
    List<CandidateScore> scored = CandidateScorer.score(intent, candidates);
    List<Scored> out = new ArrayList<>();
    for (CandidateScore s : scored) {
        int total = s.total();
        if (ordinal != null && matchesOrdinalCandidate(s.candidate(), ordinal)) {
            total += 20;
        }
        out.add(new Scored(s.candidate(), total));
    }
    out.sort(Comparator.comparingInt(Scored::score).reversed());
    return out;
}
```

Replace the near-tie block (`runnerUp.score >= best.score - NEAR_TIE_SCORE_WINDOW`) with:

```java
BindDecision decision = CandidateScorer.decide(
        scored.stream().map(s -> new CandidateScore(s.candidate(), s.score(), 0, 0, 0, 0)).toList());
```

Do **not** reconstruct broken scores. Better: `decide` from the original `CandidateScorer.score` list **before** ordinal bump, then apply ordinal as today on that list. Keep a local `List<CandidateScore> raw = CandidateScorer.score(...)` in `bindSingle` and call `CandidateScorer.decide(raw)` for the honesty gate when `!namedActionResolved`.

If TIE and `tieBreak` contains an id on the shortlist, pick that (today’s preferOnTie).

- [ ] **Step 4: Run** `mvn -pl keel-portal "-Dtest=NearTieAmbiguousTest,CandidateScorerTest,SubmitVsSignUpBindTest,LabelUniquenessTest,AccessibleNameButtonContentsTest" test`

Expected: PASS. If a pre-existing bind test fails, fix the scorer weights — do not weaken `NearTieAmbiguousTest`.

- [ ] **Step 5: Commit**

Message: `Bind with the scored shortlist, and keep twin controls ambiguous.`

---

### Task 4: Stronger extractor candidates

**Files:**
- Modify: `src/main/java/delivery/authoring/DomCandidateExtractor.java`
- Create: `src/test/java/delivery/authoring/DomCandidateExtractorStabilityTest.java`

**Interfaces:**
- Consumes: `GeneratedIdDetector.looksGenerated`
- Produces: unique role+name locator (`css` `button[aria-label='Save']` or `[role='button'][aria-label='Save']`) when no test hook / stable id already emitted for that node
- Produces: `uniqueAttrSelector` must not return a CSS that is only `.jss*` / `.css-*` / `.svelte-*` / hashed class (`GeneratedIdDetector.looksGenerated` on the class token)

- [ ] **Step 1: Failing tests**

```java
@Test
public void roleAndAriaLabelEmittedWhenNoTestId() {
    String html = "<html><body><div role=\"button\" aria-label=\"Export csv\">X</div></body></html>";
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    Assert.assertTrue(cs.stream().anyMatch(c ->
            c.value().contains("role") && c.value().toLowerCase().contains("export")),
            cs.toString());
}

@Test
public void hashedClassIsNotTheLocator() {
    String html = "<html><body><button class=\"css-1ab2cde primary\">Go</button></body></html>";
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    Assert.assertFalse(cs.stream().anyMatch(c ->
            "css".equals(c.strategy()) && c.value().contains("css-1ab2cde")),
            cs.toString());
}

@Test
public void generatedIdNotEmittedAsIdStrategy() {
    String html = "<html><body><button id=\"ember1234\" aria-label=\"Refresh\">R</button></body></html>";
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    Assert.assertFalse(cs.stream().anyMatch(c -> "id".equals(c.strategy()) && "ember1234".equals(c.value())),
            cs.toString());
}
```

The third test may already pass (`usableIdentifier`). Keep it as a regression.

- [ ] **Step 2: Run** `mvn -pl keel-portal "-Dtest=DomCandidateExtractorStabilityTest" test`

Expected: first and/or second FAIL.

- [ ] **Step 3: Implement**

In the interactive fallback loop, before `uniqueAttrSelector`, if `el.hasAttr("aria-label")` and (`role` present or native button/a/input): emit `css` role+name when `uniqueAttribute`. Skip generated class tokens in `uniqueAttrSelector` / class CSS builder.

- [ ] **Step 4: Run** `mvn -pl keel-portal "-Dtest=DomCandidateExtractorStabilityTest,DomCandidateExtractorTest,GeneratedIdDetectorTest,PageLocatorCatalogTest" test`

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `Emit role and name locators, and skip hashed CSS classes.`

---

### Task 5: Catalog fingerprint + proof write-back

**Files:**
- Modify: `src/main/java/delivery/authoring/PageLocatorCatalog.java`
- Modify: `src/test/java/delivery/authoring/PageLocatorCatalogTest.java`

**Interfaces:**
- `fingerprint(url, html)`: URL + interactive count + sorted test-hook `attr=value` + sorted test-hooks **inside** `nav, aside, [role=navigation]` (not `nav.text()`)
- `adoptOrPreferStable`: if related catalog row exists, return it; **else** store on a pending list, return the vision candidate, **do not** append to `candidates`
- `commitAfterProof(DomCandidate c)`: if `c` is pending or already catalogued, append to `candidates` if missing
- `pendingCount()` for tests

- [ ] **Step 1: Failing tests** (append to `PageLocatorCatalogTest`)

```java
@Test
public void classHashChurnDoesNotRebuild() {
    PageLocatorCatalog catalog = new PageLocatorCatalog();
    catalog.ensure("https://app.test/home", PAGE);
    int gen = catalog.generation();
    String churn = PAGE.replace("<h1>Home</h1>", "<h1 class=\"css-xyz99\">Home</h1>");
    catalog.ensure("https://app.test/home", churn);
    Assert.assertEquals(catalog.generation(), gen);
}

@Test
public void visionDoesNotGrowCatalogUntilProof() {
    PageLocatorCatalog catalog = new PageLocatorCatalog();
    catalog.ensure("https://app.test/home", PAGE);
    int before = catalog.candidates().size();
    DomCandidate visionNew = new DomCandidate(
            "v2", "css", "button[data-axis-test-id='toast-close']", "button", "Close");
    catalog.adoptOrPreferStable(visionNew);
    Assert.assertEquals(catalog.candidates().size(), before);
    catalog.commitAfterProof(visionNew);
    Assert.assertTrue(catalog.candidates().size() > before);
}
```

Change existing `visionHitIsAdoptedWhenCatalogDoesNotHaveIt` to match proof gating (split: adopt does not grow; commit does).

Keep `reusesCatalogUntilDomFingerprintChanges` (KYC hook still rebuilds).

- [ ] **Step 2: Run** `mvn -pl keel-portal "-Dtest=PageLocatorCatalogTest" test`

Expected: FAIL on churn and/or adopt-grows.

- [ ] **Step 3: Implement fingerprint + pending list**

Replace nav text hash:

```java
List<String> navHooks = new ArrayList<>();
for (Element el : doc.select("nav, aside, [role=navigation]")) {
    for (Attribute attr : el.getAllElements().stream() /* flatten */ ...) {
        if (DomCandidateExtractor.isTestHookAttr(attr.getKey()) && usable) {
            navHooks.add(attr.getKey().toLowerCase() + "=" + attr.getValue());
        }
    }
}
navHooks.sort(null);
```

- [ ] **Step 4: Run** `mvn -pl keel-portal "-Dtest=PageLocatorCatalogTest" test`

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `Fingerprint stable hooks only, and wait for a live click before catalog write-back.`

---

### Task 6: Ollama only on TIE; shortlist max 5

**Files:**
- Modify: `src/main/java/delivery/authoring/AuthoringService.java` (`shortlistForIntent` limit default 12 → `CandidateScorer.SHORTLIST_MAX`)
- Modify: `src/main/java/delivery/heal/HealCascade.java` (Ollama block ~355–370; `shortlistForIntent(..., 12)` → 5)
- Create: `src/test/java/delivery/authoring/BindDecisionHealPolicyTest.java`

**Interfaces:**
- Consumes: `BindDecision.Kind`
- Produces: `AuthoringService.shortlistForIntent` uses `CandidateScorer.score` then `decide`; CLEAR → list of 1; TIE → `decision.shortlist()`; EMPTY → empty list (do not fall back to first 5 of the raw pool when distinctive tokens were required)
- HealCascade: if `shortlist.size()==1` and not widened, **skip Ollama** and use named retry / that one row (deterministic). If size 2–5, Ollama may pick. If empty, vision/invent path unchanged.
- ASSERT_VISIBLE: existing `mayHeal` false — add a test that a TIE assert still does not call heal (reuse `AssertHealPolicyTest` if it already covers this)

- [ ] **Step 1: Failing test**

```java
@Test
public void shortlistClearIsSingleWinner() {
    AuthoringService svc = new AuthoringService(null); // use the constructor tests already use
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
    List<DomCandidate> cs = List.of(
            new DomCandidate("c1", "css", "span[data-axis-test-id='side-nav-support-submenu-button']",
                    "span", "Support"),
            new DomCandidate("c2", "id", "home", "a", "Home")
    );
    List<DomCandidate> sl = svc.shortlistForIntent(intent, cs, 12);
    Assert.assertEquals(sl.size(), 1);
    Assert.assertTrue(sl.get(0).value().contains("support"));
}

@Test
public void shortlistTieCapsAtFive() {
    AuthoringService svc = new AuthoringService(null);
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Click Delete");
    List<DomCandidate> cs = new ArrayList<>();
    for (int i = 1; i <= 8; i++) {
        cs.add(new DomCandidate("c" + i, "id", "del-" + i, "button", "Delete"));
    }
    List<DomCandidate> sl = svc.shortlistForIntent(intent, cs, 12);
    Assert.assertTrue(sl.size() <= CandidateScorer.SHORTLIST_MAX, String.valueOf(sl.size()));
    Assert.assertTrue(sl.size() >= 2);
}
```

Look up `AuthoringService` constructors in existing tests (`AuthoringServiceTest`) and match them. If the service requires collaborators, call `StepIntentBinder.rankedCandidates` / a new `AuthoringService` package method `shortlistFromDecision` that is static-friendly.

Prefer adding `CandidateScorer.shortlist(IntentLine, List<DomCandidate>)` that returns the decision shortlist, and make `AuthoringService.shortlistForIntent` one call. Then the test does not need to construct `AuthoringService`.

```java
public static List<DomCandidate> shortlist(IntentLine intent, List<DomCandidate> candidates) {
    return decide(score(intent, candidates)).shortlist();
}
```

- [ ] **Step 2: Run** `mvn -pl keel-portal "-Dtest=BindDecisionHealPolicyTest,AssertHealPolicyTest" test`

Expected: FAIL until `shortlist` exists / AuthoringService still returns 12.

- [ ] **Step 3: Implement**

HealCascade: `int cap = CandidateScorer.SHORTLIST_MAX;` replace 12 and 16 widen only for vision widen path (keep widen at 16 — that path is “DOM empty of distinctive tokens”). Normal shortlist cap 5.

Skip Ollama when `shortlist.size() == 1`.

- [ ] **Step 4: Run** `mvn -pl keel-portal "-Dtest=BindDecisionHealPolicyTest,AssertHealPolicyTest,NearTieAmbiguousTest,CandidateScorerTest" test`

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `Ask Ollama only when the shortlist is tied, and cap it at five rows.`

---

### Task 7: Fixture false-bind gate + metrics

This class is the **gate before any live AxisPay Execute**. Expand until Support, Save, Refresh, Delete twin, and Settings twin all have an expected kind + locator.

**Files:**
- Create: `src/test/resources/locator-fixtures/scorer-sidebar.html`
- Create: `src/main/java/delivery/authoring/BindQualityMetrics.java`
- Create: `src/test/java/delivery/authoring/CandidateScorerFixtureTest.java`
- Create: `src/test/java/delivery/authoring/BindQualityMetricsTest.java`
- Modify: `src/main/java/delivery/job/ProvePhase.java` (increment counters; log line; write `bind-quality.json` — Task 8 also wires catalog commit; either task may add the write if the file is missing)
- Modify: `src/main/java/delivery/authoring/PageLocatorCatalog.java` (`ensure` already logs rebuild; increment metrics there **or** return whether it rebuilt and let ProvePhase count)

**Interfaces:**
- `BindQualityMetrics` mutable ints: `bindClear`, `bindTie`, `bindEmpty`, `catalogHit`, `catalogRebuild`, `ollamaAsked`, `visionAsked`, `healEntered`, `falseBind`
- `void add(BindDecision.Kind kind)`
- `String summaryLine()` → `BIND_QUALITY clear=.. tie=.. empty=.. catalogHit=.. catalogRebuild=.. ollama=.. vision=.. heal=..`
- `void writeJson(Path file)` → `{"clear":N,"tie":N,"empty":N,"catalogHit":N,"catalogRebuild":N,"ollama":N,"vision":N,"heal":N}` (omit `falseBind` in production JSON)
- Thread-confined per prove run (instance on `ProvePhase`, not static)

Fixture HTML (`scorer-sidebar.html`):

```html
<!doctype html><html><body>
<nav aria-label="Main">
  <span data-axis-test-id="side-nav-support-submenu-button">Support</span>
  <a data-axis-test-id="side-nav-home-link">Home</a>
  <a data-axis-test-id="side-nav-bulk-charge-back-link">Bulk Charge Back</a>
</nav>
<button id="ember1234" aria-label="Refresh">R</button>
<button data-axis-test-id="save-hook" id="btn-save" data-testid="save">Save</button>
<button id="del-1">Delete</button>
<button id="del-2">Delete</button>
<button id="settings-primary">Settings</button>
<button id="settings-advanced">Settings</button>
</body></html>
```

- [ ] **Step 1: Failing fixture tests**

```java
@Test
public void supportClick_isClearHook() {
    String html = Files.readString(Path.of("src/test/resources/locator-fixtures/scorer-sidebar.html"));
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
    BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
    Assert.assertEquals(d.kind(), BindDecision.Kind.CLEAR, d.toString());
    Assert.assertTrue(d.winner().value().contains("side-nav-support-submenu-button"),
            d.winner().toString());
}

@Test
public void saveClick_prefersHookOverId() {
    String html = Files.readString(Path.of("src/test/resources/locator-fixtures/scorer-sidebar.html"));
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Save");
    StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC_01", cs, List.of());
    Assert.assertTrue(r.ok(), r.rejectReason());
    Assert.assertTrue(r.steps().get(0).locatorValue().contains("save-hook")
            || "data-testid".equals(r.steps().get(0).locatorStrategy()),
            r.steps().get(0).locatorStrategy() + "=" + r.steps().get(0).locatorValue());
}

@Test
public void emberId_isNotTheRefreshLocator() {
    String html = Files.readString(Path.of("src/test/resources/locator-fixtures/scorer-sidebar.html"));
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Refresh");
    StepIntentBinder.BindResult r = StepIntentBinder.bindSingle(intent, "TC_01", cs, List.of());
    Assert.assertTrue(r.ok(), r.rejectReason());
    Assert.assertFalse("ember1234".equals(r.steps().get(0).locatorValue()));
}

@Test
public void twoDeletes_areTied() {
    BindDecision d = decide("Click Delete");
    Assert.assertEquals(d.kind(), BindDecision.Kind.TIE, d.toString());
}

@Test
public void twoSettings_areTied() {
    BindDecision d = decide("Click Settings");
    Assert.assertEquals(d.kind(), BindDecision.Kind.TIE, d.toString());
    Assert.assertTrue(d.shortlist().size() >= 2);
}

private static BindDecision decide(String step) {
    String html = readFixture();
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    var intent = new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, step);
    return CandidateScorer.decide(CandidateScorer.score(intent, cs));
}
```

Read the HTML via classpath (`getResource("/locator-fixtures/scorer-sidebar.html")`) not `Path.of("src/test/...")` so tests work from `keel-portal` cwd. Helper `readFixture()` wraps that.

Add `BindQualityMetricsTest.writeJson_roundTripsCounts` that writes a temp file and asserts the JSON keys `clear`, `tie`, `empty`, `ollama`, `vision`, `heal`.

- [ ] **Step 2: Run** `mvn -pl keel-portal "-Dtest=CandidateScorerFixtureTest" test`

Expected: FAIL until extractor+scorer+binder agree on the fixture.

- [ ] **Step 3: Implement metrics class; wire ProvePhase**

On bind: `metrics.add(decision.kind())`. On `pageCatalog.ensure`: if generation bumped, `catalogRebuild++` else `catalogHit++`. When HealCascade is entered, `healEntered++`. When Ollama branch runs, `ollamaAsked++`. When `VisionProveHook.tryLayer15` is attempted, `visionAsked++`. After the job (or TC if that is the only hook), `LogsManager.info(metrics.summaryLine())` and `metrics.writeJson(evidenceDir.resolve("bind-quality.json"))` when `evidenceDir` is non-null.

Do not set `falseBind` in production (no oracle). Fixture tests fail the method when kind or locator is wrong — that *is* the false-bind gate.

- [ ] **Step 4: Run** `mvn -pl keel-portal "-Dtest=CandidateScorerFixtureTest,BindQualityMetricsTest" test`

This pair must stay green before anyone starts a live AxisPay run.

- [ ] **Step 4b: Run the regression set** `mvn -pl keel-portal "-Dtest=CandidateScorerFixtureTest,BindQualityMetricsTest,CandidateScorerTest,NearTieAmbiguousTest,PageLocatorCatalogTest,AssertHealPolicyTest,BindDecisionHealPolicyTest" test`

Expected: PASS.

- [ ] **Step 5: Commit**

Message: `Measure bind decisions on a frozen sidebar fixture.`

---

### Task 8: ProvePhase catalog commit after proof + docs

**Files:**
- Modify: `src/main/java/delivery/job/ProvePhase.java` (after successful `attempt.ok()`, before/with `rememberSuccessfulLocator`, `pageCatalog.commitAfterProof` on each proven step’s locator; call `metrics.writeJson` if Task 7 did not already hook the evidence dir)
- Modify: vision path that calls `stabilizeSteps` — must not grow catalog (Task 5 already); still rewrite xpath → hook
- Modify: `docs/handoff/locator-and-split-roadmap.md` (P0/P1 now “scorer shipped / in progress”)
- Modify: `docs/ENGINEERING_HANDOFF.md` (link spec+plan)
- Modify: `docs/PRODUCT.md` one line under Execute: bind prefers test-id / role+name; Ollama only on a close shortlist
- Modify: `CHANGELOG.md` dated section when this task’s code is in (not for the plan-only commit)

**Interfaces:**
- Consumes: `PageLocatorCatalog.commitAfterProof`
- Produces: after execute PASS, catalog contains the winning locator

- [ ] **Step 1: Failing unit test** if `commitAfterProof` is not yet called from a small helper you can test without Spring. Prefer extracting:

```java
static void commitProvenSteps(PageLocatorCatalog catalog, List<ProvenStep> steps) {
    if (catalog == null || steps == null) return;
    for (ProvenStep step : steps) {
        catalog.commitAfterProof(new DomCandidate(
                "p", step.locatorStrategy(), step.locatorValue(), "", step.rationale()));
    }
}
```

Test: catalog size grows only after `commitProvenSteps`.

- [ ] **Step 2: Run** `mvn -pl keel-portal "-Dtest=PageLocatorCatalogTest,CandidateScorerFixtureTest" test`

- [ ] **Step 3: Call helper from ProvePhase next to `rememberSuccessfulLocator`**

Vision `stabilizeSteps` stays prefer-stable. Do not `commitAfterProof` on vision until execute PASS (the helper on the success path does that).

- [ ] **Step 4: Run** `mvn -pl keel-portal "-Dtest=PageLocatorCatalogTest,CandidateScorerFixtureTest,AssertHealPolicyTest,NearTieAmbiguousTest,CandidateScorerTest,BindDecisionHealPolicyTest,DomCandidateExtractorStabilityTest,GeneratedWorkbookRevisionFlowTest" test`

`GeneratedWorkbookRevisionFlowTest` is unrelated — skip it. Keep the authoring list.

- [ ] **Step 5: Commit**

Message: `Commit catalog rows after a live click, and document the scorer pack.`

---

### Task 9: TIE pick payload (Ollama trim + Cursor pick v2)

Do this after Tasks 1–6 so `BindDecision` and scored shortlists exist. EMPTY still goes vision, not this card.

**Files:**
- Create: `src/main/java/delivery/authoring/HealPickPayload.java`
- Create: `src/test/java/delivery/authoring/HealPickPayloadTest.java`
- Modify: `src/main/java/delivery/authoring/AuthoringService.java` (`healIntentWithOllama` system + user strings)
- Modify: `src/main/java/delivery/heal/HealCascade.java` (TIE: `cursor.pickCandidateId`, not `solve`; pass payload; do not `acceptWrittenLocator` on that pick)
- Modify: `src/main/java/delivery/heal/CursorHealClient.java` (`pickCandidateId` JSON keys `quotedName`, `whyTied`, `scoredShortlist`, `neighborhood`; leave `slimHtmlExcerpt` empty on this path)
- Modify: `src/test/java/delivery/heal/CursorHealClientTest.java` or add `CursorHealPickPayloadTest` if a test already stubs invoke — only if an existing test asserts request keys; otherwise unit-test `HealPickPayload` JSON shape only

**Interfaces:**
- Consumes: `BindDecision` (TIE), `List<CandidateScore>`, original HTML, `StepIntentBinder.quotedVisibleName`
- Produces:

```java
public record HealPickPayload(
        String quotedName,
        String whyTied,
        String scoredTable,
        String neighborhood
) {
    public static final int NEIGHBORHOOD_MAX = 1500;

    public static HealPickPayload from(
            StepIntentBinder.IntentLine intent,
            BindDecision decision,
            String html);

    public String ollamaSystem();
    public String ollamaUser(String tcId, String failureReason);
}
```

`whyTied` example: `TIE margin=3 best=c1=41 runner=c2=38 same-label=Settings`

`scoredTable` one line per shortlist row, max 5:

`c1 | css | button[data-axis-test-id='save-hook'] | button | Save | total=64 | stability=50 | uniqueness=15 | intentMatch=40 | roleFit=10 | labelDistance=10`

`neighborhood`: Jsoup — for each shortlist locator, find the element, take `parent().outerHtml()` (or the node if no parent), concatenate unique snippets, truncate to `NEIGHBORHOOD_MAX`. Not `ObservationBudget.clip(slimHtml)`.

`ollamaSystem()`:

```
Pick exactly one candidateId from the scored shortlist.
Reply with JSON only: {"candidateId":"<id from shortlist>"}.
Do not invent ids. Do not return locators. Do not write a Thought. Do not invent steps.
```

- [ ] **Step 1: Failing tests**

```java
@Test
public void scoredTableIncludesTotals() {
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Click Settings");
    List<DomCandidate> cs = List.of(
            new DomCandidate("c1", "id", "settings-primary", "button", "Settings"),
            new DomCandidate("c2", "id", "settings-advanced", "button", "Settings")
    );
    BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
    HealPickPayload p = HealPickPayload.from(intent, d, "<html></html>");
    Assert.assertTrue(p.whyTied().startsWith("TIE"), p.whyTied());
    Assert.assertTrue(p.scoredTable().contains("c1") && p.scoredTable().contains("total="),
            p.scoredTable());
    Assert.assertFalse(p.ollamaSystem().toLowerCase().contains("invent"));
    Assert.assertFalse(p.ollamaSystem().toLowerCase().contains("thought"));
}

@Test
public void neighborhoodIsParentNotWholePage() {
    String html = """
            <html><body>
            <nav><span data-axis-test-id="side-nav-support-submenu-button">Support</span>
            <a data-axis-test-id="side-nav-home-link">Home</a></nav>
            <main id="huge">""" + "x".repeat(4000) + """
            </main></body></html>
            """;
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Clicks \"Support\" in the sidebar");
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, cs));
    HealPickPayload p = HealPickPayload.from(intent, d, html);
    Assert.assertTrue(p.neighborhood().length() <= HealPickPayload.NEIGHBORHOOD_MAX + 80,
            String.valueOf(p.neighborhood().length()));
    Assert.assertFalse(p.neighborhood().contains("x".repeat(200)));
}

@Test
public void emptyDecisionHasNoNeighborhoodCard() {
    var intent = new StepIntentBinder.IntentLine(
            StepIntentBinder.IntentKind.CLICK, "Click Warp Drive");
    BindDecision d = CandidateScorer.decide(CandidateScorer.score(intent, List.of(
            new DomCandidate("c1", "id", "cancel", "button", "Cancel"))));
    Assert.assertEquals(d.kind(), BindDecision.Kind.EMPTY);
    HealPickPayload p = HealPickPayload.from(intent, d, "<html><body>lots</body></html>");
    Assert.assertEquals(p.scoredTable(), "");
    Assert.assertEquals(p.neighborhood(), "");
}
```

- [ ] **Step 2: Run** `mvn -pl keel-portal "-Dtest=HealPickPayloadTest" test`

Expected: FAIL (`HealPickPayload` missing).

- [ ] **Step 3: Implement payload + wire prompts**

`AuthoringService.healIntentWithOllama`: if caller passes a `HealPickPayload`, use `payload.ollamaSystem()` / `ollamaUser`; else build payload from the shortlist scores (re-score via `CandidateScorer.score` filtered to shortlist ids).

`HealCascade` after building the TIE shortlist:

```java
BindDecision decision = CandidateScorer.decide(CandidateScorer.score(intent, distinctivePool));
if (decision.kind() == BindDecision.Kind.TIE) {
    HealPickPayload payload = HealPickPayload.from(intent, decision, slimHtml);
    // Ollama pick (existing tryOllama gate)
    // Cursor: cursor.pickCandidateId(..., payload) — not solve
}
```

Add `CursorHealClient.pickCandidateId` overload that puts:

- `scoredShortlist` = `payload.scoredTable()`
- `whyTied` = `payload.whyTied()`
- `quotedName` = `payload.quotedName()`
- `neighborhood` = `payload.neighborhood()`
- `slimHtmlExcerpt` = `""`

Do not run `acceptWrittenLocator` on a successful or failed **pick**. Invent remains `tryInvent` after pick+vision miss.

- [ ] **Step 4: Run** `mvn -pl keel-portal "-Dtest=HealPickPayloadTest,BindDecisionHealPolicyTest,AssertHealPolicyTest,CandidateScorerFixtureTest" test`

Expected: PASS. Existing Ollama tests that look for `Thought` or `formatTable` must be updated to the new system string.

- [ ] **Step 5: Commit**

Message: `Send a scored pick card on a tie, and keep invent off that call.`

---

## Self-review


**Spec coverage**

| Spec requirement | Task |
|------------------|------|
| Better candidates (testid / aria / role+name; drop generated id / hashed class) | 1, 4 |
| Scored shortlist top 3–5; Ollama only if close; vision if empty/still tied | 2, 3, 6 |
| Fingerprint ignores class churn | 5 |
| Write-back only on proof; asserts do not heal | 5, 6, 8 |
| Fixture false-bind gate (Support / Save / Refresh / Delete / Settings) | 7 |
| `bind-quality.json` + log line | 7, 8 |
| Cursor/Ollama TIE pick card; no invent on that call | 9 |
| Portal/codegen out | Global constraints |
| No Midscene / invent / fatter prompts | Global constraints + Task 9 |
| Do not mix in-run catalog with DomainLocatorMemory redeploy survival | Spec + Task 5 |

**Not in this plan (explicit):** pixel/ancestor distance, slim-HTML keep-nav, iframe/shadow/canvas, Precision `groundRank` neighborhood, `LocatorPipeline` prove wiring, live AxisPay Execute, portal bind-quality UI.

**Placeholder scan:** none.

**Type consistency:** `CandidateScore`, `BindDecision`, `CandidateScorer.score/decide/shortlist/stability`, `MARGIN/MIN_TOTAL/SHORTLIST_MAX`, `commitAfterProof`, `BindQualityMetrics.writeJson`, `HealPickPayload` — used with the same names in later tasks.
