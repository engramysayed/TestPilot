# Layer-1 Bind Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the Important (and highest-impact Minor) defects in DOM extract → score → winner binding so emitted locators stay correct under apostrophes, caption layouts, label collisions, fragile ordinals, and aggressive near-ties.

**Architecture:** Keep the deterministic Layer-1 pipeline. Share one xpath-literal helper across extractor and binder. Tighten AccessibleName sibling walk and label uniqueness. Prefer safer indexed strategies. Widen near-tie handling slightly and stop first-id AMBIGUOUS fallback from silently picking wrong. Optionally apply failed-locator exclusion on first bind within a TC.

**Tech Stack:** Java 21, Jsoup, TestNG, existing `DomCandidateExtractor` / `AccessibleName` / `StepIntentBinder` / `ProvePhase`.

## Global Constraints

- Site-agnostic: no domain or the-internet hardcoding.
- Security > maintainability > performance (HelloGroup).
- Do not invent locators in Layer 1 (extract + score only).
- Prefer shared helpers over duplicated escape/contains logic.
- Do not commit unless the user explicitly asks.
- Review source: Layer-1 audit canvas / conversation findings (2026-08-17).

## Impact order (why this sequence)

| Priority | Issue | Why first |
|----------|-------|-----------|
| P1 | XPath apostrophe strip | Silent wrong/broken locators; one shared helper fixes extract + assert asymmetry |
| P2 | Caption sibling walk aborts on icons | Wrong/empty AccessibleName → wrong winner scores |
| P3 | Label `:contains` uniqueness | Skips good label-anchored xpath for common field names |
| P4 | Document-global `(//tag)[n]` | Fragile anonymous-field locators |
| P5 | ±1 AMBIGUOUS + first-id fallback | Wrong winners / needless heal |
| P6 | Failed locators only in heal | Same bad locator can win again in-TC |
| P7 | Checkbox `nth-of-type` CSS | Mis-count risk (xpath already preferred) |

---

## File map

| File | Role |
|------|------|
| `src/main/java/delivery/authoring/XpathLiterals.java` (new) | Shared `escapeXpathLiteral` / quote-safe concat |
| `DomCandidateExtractor.java` | Use shared escape; safer label uniqueness; safer indexed selectors; drop or demote bad CSS ordinals |
| `AccessibleName.java` | Skip non-caption siblings instead of aborting |
| `StepIntentBinder.java` | Delegate escape; near-tie policy; `withoutFailedLocators` already here |
| `AuthoringService.java` | Stop silent first-id AMBIGUOUS fallback (or only when score gap ≥ threshold) |
| `ProvePhase.java` | Pass failed locators into `authorIntent` bind path when available |
| Tests under `src/test/java/delivery/authoring/` | One focused test class/task |

---

### Task 1: Shared xpath literal escape (P1)

**Files:**
- Create: `src/main/java/delivery/authoring/XpathLiterals.java`
- Modify: `src/main/java/delivery/authoring/DomCandidateExtractor.java` (`escape`)
- Modify: `src/main/java/delivery/authoring/StepIntentBinder.java` (`escapeXpathLiteral` → delegate)
- Create: `src/test/java/delivery/authoring/XpathLiteralsTest.java`
- Modify/create: `src/test/java/delivery/authoring/DomCandidateExtractorApostropheTest.java`

**Interfaces:**
- Produces: `XpathLiterals.quote(String text)` → xpath string literal or `concat(...)`
- Consumes: extractor text/attr xpath builders; binder phrase xpath

- [ ] **Step 1: Failing tests**

```java
@Test
public void quoteWrapsPlainText() {
    Assert.assertEquals(XpathLiterals.quote("Login"), "'Login'");
}

@Test
public void quoteUsesConcatForApostrophe() {
    String q = XpathLiterals.quote("It's gone");
    Assert.assertTrue(q.startsWith("concat("), q);
    Assert.assertTrue(q.contains("\"'\""), q);
}

@Test
public void extractorKeepsApostropheInButtonTextXpath() {
    String html = "<html><body><button>It's gone</button></body></html>";
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    boolean found = cs.stream().anyMatch(c ->
            "xpath".equalsIgnoreCase(c.strategy())
                    && c.value() != null
                    && c.value().contains("concat(")
                    && c.value().toLowerCase().contains("gone"));
    Assert.assertTrue(found, cs.toString());
}
```

- [ ] **Step 2: Run RED**

```powershell
mvn "-Dtest=XpathLiteralsTest,DomCandidateExtractorApostropheTest" "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21" test
```

- [ ] **Step 3: Implement**

Move `StepIntentBinder.escapeXpathLiteral` body into `XpathLiterals.quote`.  
`DomCandidateExtractor.escape` must **not** strip `'`; for values used inside xpath string literals call `XpathLiterals.quote` (or quote only the literal portion). For CSS attribute selectors, keep CSS-safe escaping (double-quote attrs or escape) — do not put `concat()` into CSS.

Rule:
- XPath attribute/text predicates → `XpathLiterals.quote(v)`
- CSS selectors → keep single-quoted attrs but escape `'` as `\'` or switch to double quotes; never strip characters that change meaning.

- [ ] **Step 4: Delegate binder** `escapeXpathLiteral` → `XpathLiterals.quote`

- [ ] **Step 5: GREEN + optional commit**

---

### Task 2: AccessibleName skip non-caption siblings (P2)

**Files:**
- Modify: `src/main/java/delivery/authoring/AccessibleName.java` (`siblingText`)
- Create: `src/test/java/delivery/authoring/AccessibleNameIconSiblingTest.java`

**Interfaces:**
- Consumes: existing `isCaptionSibling`
- Produces: caption found after skipping icon/`br`/empty elements within hop limit

- [ ] **Step 1: Failing test**

```java
@Test
public void skipsIconBetweenInputAndCaption() {
    String html = "<html><body><label><input id='x'/>"
            + "<i class='icon'></i><span>Username</span></label></body></html>";
    // Or sibling layout outside label if that is the intended pattern:
    // <div><input id='x'/><i></i><span>Username</span></div>
    org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
    org.jsoup.nodes.Element input = doc.getElementById("x");
    Assert.assertEquals(AccessibleName.of(input), "Username");
}
```

Use the DOM shape that currently fails (icon sibling abort). Prefer the sibling layout the review cited: `input → i → span`.

- [ ] **Step 2: RED**

- [ ] **Step 3: Implement**

In `siblingText`, when node is an Element that is **not** a caption sibling, **continue** to next sibling (still within `hops < 3`) instead of `return ""`. Only return empty when hops exhausted.

Do not treat interactive siblings as skippable forever — if sibling is interactive (`a, button, input, …`), stop (avoid stealing another control’s name).

- [ ] **Step 4: GREEN**

---

### Task 3: Exact label uniqueness (P3)

**Files:**
- Modify: `DomCandidateExtractor.countLabelsContaining` → rename/replace with exact-text uniqueness
- Create: `src/test/java/delivery/authoring/LabelUniquenessTest.java`

- [ ] **Step 1: Failing test**

```java
@Test
public void shortNameDoesNotBlockDistinctFirstNameLabel() {
    String html = "<html><body>"
            + "<label>Name<input id='n'/></label>"
            + "<label>First Name<input id='fn'/></label>"
            + "</body></html>";
    List<DomCandidate> cs = DomCandidateExtractor.extract(html);
    // Expect a label-anchored xpath for First Name (unique exact label text)
    boolean anchored = cs.stream().anyMatch(c ->
            "xpath".equalsIgnoreCase(c.strategy())
                    && c.value() != null
                    && c.value().contains("First Name")
                    && c.value().contains("label"));
    Assert.assertTrue(anchored, cs.toString());
}
```

- [ ] **Step 2: RED**

- [ ] **Step 3: Implement**

Replace Jsoup `label:contains(name)` count with exact normalized text equality:

```java
private static int countLabelsWithExactName(Document doc, String name) {
    String want = name == null ? "" : name.trim();
    int n = 0;
    for (Element label : doc.select("label")) {
        String t = label.ownText().isBlank() ? label.text() : label.ownText();
        if (want.equalsIgnoreCase(t == null ? "" : t.trim())) {
            n++;
        }
    }
    return n;
}
```

Use this for the uniqueness gate before emitting label-anchored xpath. Keep site-agnostic.

- [ ] **Step 4: GREEN**

---

### Task 4: Safer indexed selectors (P4)

**Files:**
- Modify: `DomCandidateExtractor.indexedSelector` / `emitIndexedInputs`
- Create: `src/test/java/delivery/authoring/IndexedSelectorScopeTest.java`

- [ ] **Step 1: Failing test**

```java
@Test
public void indexedInputIsScopedToTypeNotAllTags() {
    // Already true for emitIndexedInputs — assert xpath is (//input[@type='checkbox'])[n]
    // Add case: anonymous text inputs — indexedSelector must not use (//div)[n]
    // Prefer (//input)[n] only among inputs OR prefer label/css over global tag index when possible
}
```

Concrete acceptance:

1. Stop emitting `css` `nth-of-type` for checkboxes/radios (Task 7 can fold here): only xpath document-order index.
2. For `indexedSelector(tag)`: if tag is a form control, index among `input`/`select`/`textarea` of same tag **and** same `type` when present: `(//input[@type='text'])[n]` rather than `(//input)[n]` when type exists.
3. Never emit indexed xpath for non-control tags (`div`, `span`, `section`).

- [ ] **Step 2–4: TDD implement GREEN**

---

### Task 5: Near-tie policy + AMBIGUOUS fallback (P5)

**Files:**
- Modify: `StepIntentBinder.java` (near-tie threshold)
- Modify: `AuthoringService.java` (AMBIGUOUS resolution fallback)
- Create: `src/test/java/delivery/authoring/NearTieAmbiguousTest.java`

**Policy (locked):**

1. Keep ±1 near-tie → `AMBIGUOUS` when two **different** controls (fingerprint) are within 1 point (honesty).
2. Change AuthoringService fallback: if LLM cannot pick, **do not** bindPreferring first id. Return reject / leave for heal instead of silent wrong winner.
3. Optional soften: if `best.score - runnerUp.score >= 2`, never AMBIGUOUS (already implied). If scores equal and strategies differ only by fingerprint twins, already handled.

- [ ] **Step 1: Failing test** — mock/stub path that previously took first AMBIGUOUS id must now leave unbound or return clear reject reason starting with `AMBIGUOUS:` without inventing a step.

- [ ] **Step 2–4: Implement + GREEN**

Do **not** widen the ±1 window without a separate user decision — honesty gate stays.

---

### Task 6: Failed locators on in-TC bind (P6)

**Files:**
- Modify: `AuthoringService.authorIntent` (or ProvePhase call site) to accept `List<FailedLocator>`
- Modify: `ProvePhase` to pass accumulating failed list into each bind (same list heal already uses)
- Modify: `WithoutFailedLocatorsTest` or add `FailedLocatorBindPathTest`

- [ ] **Step 1: Failing test** — after a failed locator is recorded, next `bindSingle` / `authorIntent` for a later intent must not reselect the same css+xpath twin.

- [ ] **Step 2: Wire**

```java
candidates = StepIntentBinder.withoutFailedLocators(candidates, failedLocators);
```

on the **first-bind** path inside the TC loop (after extract, before bind), not only in `HealCascade`.

- [ ] **Step 3: GREEN**

---

### Task 7: Drop checkbox nth-of-type CSS (P7)

**Files:**
- Modify: `DomCandidateExtractor.emitIndexedInputs`
- Extend: `CheckboxBindAndAssertTest` or `IndexedSelectorScopeTest`

- [ ] **Step 1:** Assert extract for two checkboxes emits xpath ordinals and **does not** emit `nth-of-type` css candidates.

- [ ] **Step 2:** Remove css put in `emitIndexedInputs`.

- [ ] **Step 3: GREEN**

---

## Spec coverage self-review

| Finding | Task |
|---------|------|
| Escape strip vs escapeXpathLiteral conflict | Task 1 |
| Caption sibling abort on icon | Task 2 |
| Label `:contains` uniqueness | Task 3 |
| Global `(//tag)[n]` | Task 4 |
| ±1 AMBIGUOUS + first-id fallback | Task 5 |
| Failed locators heal-only | Task 6 |
| nth-of-type CSS | Task 4/7 |

Placeholder scan: none. Commit steps optional per user rule.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-17-layer1-bind-hardening.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — execute in this session with checkpoints  

Which approach?
