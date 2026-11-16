# UI-TARS bbox & assert quality — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.  
> **Do not commit unless the user asks.**

**Goal:** Make `UiTarsVisionProvider` return heal-usable grounding (tight bbox, honest confidence) and non-empty honest visual asserts, via native+JSON parsing and quality gates — without changing Qwen behavior.

**Architecture:** Add `UiTarsPrompt`, `UiTarsResponseParser` (canonical JSON + native point/box), and `VisionBboxQualityGate`; wire them only in `UiTarsVisionProvider`. Assert path gets a UiTars-specific prompt and one empty-body retry. Prove/heal contracts (`VisionAnalysisResult`, `ViewportSweep`) stay unchanged.

**Tech Stack:** Java 21, TestNG, Ollama `ui-tars`, existing `LocalLlmClient` / `VisionAssertionParser` / `VisionAssertionGate`.

**Spec:** `docs/superpowers/specs/2026-08-21-uitars-bbox-quality-design.md`

## Global Constraints

- Strategy: native adapter + JSON fallback + quality gates (option C).
- Never coerce missing/zero bbox size to fake `1×1` on the UiTars path.
- When `found=true`, keep only candidates with confidence ≥ **0.7** (after gate).
- Reject page-essay descriptions and region-sized boxes (align with `ViewportSweep.plausibleWidgetBox` ratios).
- Qwen prompts/parser coercion: **leave as-is** this plan.
- Do not commit unless asked.
- Compile/test: `mvn -q "-Dmaven.compiler.release=21" -Dtest=… test`

---

## File map

| File | Responsibility |
|------|----------------|
| Create: `src/main/java/delivery/vision/UiTarsPrompt.java` | Grounding + assert system prompts |
| Create: `src/main/java/delivery/vision/UiTarsResponseParser.java` | Parse model text → `VisionAnalysisResult` |
| Create: `src/main/java/delivery/vision/VisionBboxQualityGate.java` | Filter junk candidates |
| Modify: `src/main/java/delivery/vision/UiTarsVisionProvider.java` | Use new prompt/parser/gate; assert retry |
| Create: `src/test/java/delivery/vision/UiTarsResponseParserTest.java` | Native + JSON parse cases |
| Create: `src/test/java/delivery/vision/VisionBboxQualityGateTest.java` | Gate reject/accept |
| Create: `src/test/java/delivery/vision/UiTarsVisionProviderTest.java` | Assert retry + wiring with Fake LLM if needed |
| Modify: `src/test/java/delivery/vision/UiTarsLiveSmokeTest.java` | Stronger success criteria (skip if no model) |
| Modify: `src/test/java/delivery/vision/UiTarsPromptParityTest.java` | Point at UiTars prompts, not only Qwen share |

---

### Task 1: `VisionBboxQualityGate`

**Files:**
- Create: `src/main/java/delivery/vision/VisionBboxQualityGate.java`
- Create: `src/test/java/delivery/vision/VisionBboxQualityGateTest.java`

**Interfaces:**
- Consumes: `List<VisualCandidate>`, optional image width/height (screenshot pixels)
- Produces: `List<VisualCandidate> filter(List<VisualCandidate> in, int imageW, int imageH)` — possibly empty
- Constants: `MIN_SIDE_PX = 8`, `MIN_CONFIDENCE = 0.7`, max height ratio `0.25`, max area ratio `0.15`, essay length `120`

- [ ] **Step 1: Write failing tests**

```java
@Test
public void rejectsTinyOneByOne() {
    var c = new VisualCandidate("Login", new BoundingBox(0, 0, 1, 1), 0.9);
    Assert.assertTrue(VisionBboxQualityGate.filter(List.of(c), 800, 600).isEmpty());
}

@Test
public void rejectsLowConfidenceEvenIfBoxOk() {
    var c = new VisualCandidate("Login", new BoundingBox(10, 10, 80, 30), 0.5);
    Assert.assertTrue(VisionBboxQualityGate.filter(List.of(c), 800, 600).isEmpty());
}

@Test
public void rejectsPageEssayDescription() {
    String essay = "Login page containing a username input field, password input field, and a login button, "
            + "with a message indicating the login page is for logging into the secure area";
    var c = new VisualCandidate(essay, new BoundingBox(10, 10, 80, 30), 0.9);
    Assert.assertTrue(VisionBboxQualityGate.filter(List.of(c), 800, 600).isEmpty());
}

@Test
public void keepsTightHighConfWidget() {
    var c = new VisualCandidate("Login button", new BoundingBox(100, 200, 90, 36), 0.85);
    Assert.assertEquals(VisionBboxQualityGate.filter(List.of(c), 800, 600).size(), 1);
}
```

- [ ] **Step 2: Run** `-Dtest=VisionBboxQualityGateTest` — expect FAIL (class missing)

- [ ] **Step 3: Implement gate** — reuse ratio logic consistent with `ViewportSweep.plausibleWidgetBox`; essay heuristic: length > 120 and (contains `"containing"` or `"page"` with 2+ commas)

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit only if asked**

---

### Task 2: `UiTarsResponseParser` (JSON + native)

**Files:**
- Create: `src/main/java/delivery/vision/UiTarsResponseParser.java`
- Create: `src/test/java/delivery/vision/UiTarsResponseParserTest.java`

**Interfaces:**
- Consumes: raw model string (may include markdown fences)
- Produces: `VisionAnalysisResult parse(String raw)`
- Order: extract JSON object → try `candidates[]` (drop w/h ≤ 0; **no** 1×1 coerce) → else native point/box fields → else empty/unavailable

- [ ] **Step 1: Failing tests**

```java
@Test
public void parsesCanonicalCandidate() { /* found + bbox 100x40 conf 0.9 */ }

@Test
public void dropsZeroSizeBboxInsteadOfOneByOne() {
    // JSON bbox width=0 height=0 → candidates empty / found false
}

@Test
public void parsesPointAsPaddedBox() {
    // {"x":50,"y":60,"confidence":0.9,"description":"Login"} → width/height in 8..24
}

@Test
public void parsesStartEndBoxNormalized() {
    // start_box/end_box or [x1,y1,x2,y2] on 0–1000 scale → absolute bbox
}
```

- [ ] **Step 2: Run tests — RED**

- [ ] **Step 3: Implement parser** — strip ``` fences; `indexOf('{')`…`lastIndexOf('}')` like `VisionAssertionParser`; support:
  - `candidates[].bbox.{x,y,width,height}`
  - `point` / `click` array / top-level `x`,`y`
  - `bbox` as `[x1,y1,x2,y2]` absolute or if all ≤ 1.0 treat as 0–1 of image (need optional imageW/H args — pass 0,0 means absolute-only; or overload `parse(raw, imageW, imageH)`)

**Prefer:** `parse(String raw, int imageW, int imageH)` so normalized coords work.

- [ ] **Step 4: Tests PASS**

- [ ] **Step 5: Commit only if asked**

---

### Task 3: `UiTarsPrompt` + wire grounding in provider

**Files:**
- Create: `src/main/java/delivery/vision/UiTarsPrompt.java`
- Modify: `src/main/java/delivery/vision/UiTarsVisionProvider.java`
- Modify: `src/test/java/delivery/vision/UiTarsPromptParityTest.java`

**Interfaces:**
- `UiTarsPrompt.groundingSystem()` / `assertSystem()`
- `analyze`: `completeJson(UiTarsPrompt.groundingSystem(), VisionPromptTemplate.userPrompt(intent), png)` → parse → filter gate with screenshot dims if known (if unknown, pass png decode dims or `0,0` and skip ratio checks that need size — **prefer** reading PNG IHDR for W/H in provider, or pass metrics later; for analyze without browser, use `0,0` and still apply min side + conf + essay rules)

- [ ] **Step 1: Update parity test** — assert `UiTarsVisionProvider` source uses `UiTarsPrompt` / `UiTarsResponseParser`, not `QwenVisionProvider.SYSTEM_PROMPT` for analyze

- [ ] **Step 2: RED then implement prompts** — locate ONE control; allow JSON or point/box; forbid page essays and placeholders

- [ ] **Step 3: Wire `analyze`**

```java
String raw = client.completeJson(UiTarsPrompt.groundingSystem(), user, screenshotPng);
VisionAnalysisResult parsed = UiTarsResponseParser.parse(raw, imageW, imageH);
List<VisualCandidate> ok = VisionBboxQualityGate.filter(parsed.candidates(), imageW, imageH);
return new VisionAnalysisResult(!ok.isEmpty(), ok, parsed.error());
```

- [ ] **Step 4: Unit test with stub** — if `LocalLlmClient` hard to stub, test parser+gate only; provider wiring covered by source parity + live smoke

- [ ] **Step 5: Commit only if asked**

---

### Task 4: Assert prompt + one empty-body retry

**Files:**
- Modify: `src/main/java/delivery/vision/UiTarsPrompt.java` (`assertSystem`)
- Modify: `src/main/java/delivery/vision/UiTarsVisionProvider.java` (`assertVisual`)
- Create/extend: `src/test/java/delivery/vision/UiTarsVisionProviderTest.java`

**Interfaces:**
- Retry when `VisionAssertionParser` returns UNCERTAIN with blank observation **and** blank evidence (match live failure: empty vision assertion response **or** empty fields)
- Nudge user: `"Return the full JSON with non-empty observation and evidence. Schema unchanged."`
- Max **one** retry

- [ ] **Step 1: Failing test** — Fake/`LocalLlmClient` subclass counting calls: first returns `{}` or `{"status":"UNCERTAIN"}`, second returns full PASS JSON → assert call count 2 and final PASS

(If constructing `UiTarsVisionProvider` with custom client is already supported via public ctor — use it.)

- [ ] **Step 2: Implement assert path with `UiTarsPrompt.assertSystem()`**

- [ ] **Step 3: Tests PASS** — also keep honesty: placeholder PASS still rejected by gate at ProvePhase (existing)

- [ ] **Step 4: Commit only if asked**

---

### Task 5: Strengthen live smoke

**Files:**
- Modify: `src/test/java/delivery/vision/UiTarsLiveSmokeTest.java`

**Interfaces:**
- Still skip if Ollama / `ui-tars` missing
- After analyze: require gate-quality candidate (side ≥ 8, conf ≥ 0.7) **or** fail with clear message
- `elementFromPoint` on bbox center must hit interactive node (reuse smoke helper pattern from `VisionGroundingLiveSmokeTest`)
- Assert: after provider+optional retry, observation length ≥ 20 and evidence length ≥ 8 **unless** status UNCERTAIN with explicit model error; must not be placeholder PASS

- [x] **Step 1: Tighten assertions in live smoke**

- [x] **Step 2: Run** `-Dtest=UiTarsLiveSmokeTest` — PASS or SkipException only

- [x] **Step 3: Run full vision unit suite**  
  `-Dtest=VisionBboxQualityGateTest,UiTarsResponseParserTest,UiTarsVisionProviderTest,UiTarsPromptParityTest,VisionAssertionGateTest`

- [x] **Step 4: Update scorecard note** (optional — skipped; live notes file updated by smoke)

- [x] **Step 5: Commit only if asked**

---

## Verification (definition of done)

1. Unit: zero-size JSON bbox does not become 1×1; native point pads; essay+tiny rejected; good widget kept.  
2. UiTars analyze no longer uses `QwenVisionProvider.SYSTEM_PROMPT`.  
3. Assert empty → exactly one retry.  
4. Live smoke (when model present): interactive Login ground + non-empty assert fields.  
5. Qwen tests / behavior unchanged.

## Self-review

- Spec coverage: prompts, native+JSON, gate, assert retry, live proof — all tasked.  
- No TBD.  
- Qwen isolation respected.  
- Bite-sized TDD tasks.

## Execution handoff

Plan: `docs/superpowers/plans/2026-08-21-uitars-bbox-quality.md`

**Say `start` (or `start Task 1`) to implement.** Prefer subagent-driven-development per task.
