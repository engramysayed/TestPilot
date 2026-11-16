# Vision Grounding (Layer 1.5 / Heal 2.5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pluggable vision candidate generator that maps screenshot bboxes to live DOM nodes via `elementFromPoint`, used only when Layer 1 bind is weak or heal needs a spatial widen — never as execution authority and never as a Midscene rewrite.

**Architecture:** Keep prove → Layer 1 bind → Selenium. When `delivery.vision.grounding.enabled=true` and bind is weak/failed, `ProvePhase` (has WebDriver) runs `VisionGroundingEngine`: provider → bbox → CSS-pixel map → `elementFromPoint` → match/add `DomCandidate` → `stepsPreferringCandidate`. Same engine on heal widen. Offset-click is out of v1. Off-screen in-DOM controls stay Layer 1 + existing `scrollIntoView`; viewport sweep (cap 8) runs only while vision is already invoked.

**Tech Stack:** Java 21, Selenium `JavascriptExecutor`, existing `LocalLlmClient` (Ollama `/api/chat` + `images`), TestNG, `DomCandidateExtractor` / `StepIntentBinder` / `LocatorValidator` / `HealCascade`.

## Global Constraints

- Site-agnostic: no domain hardcoding.
- Vision is a candidate generator, not execution authority.
- Master flag `delivery.vision.grounding.enabled` default **false**; if false, never call `VisionGroundingProvider`.
- No Gemini / no cloud vendor on the delivery vision path (`LocalLlmClient.looksLikeCloudVendor`).
- No `moveByOffset` in v1.
- Do not reimplement `DomCandidateExtractor`, `IntentKind`, or `LocatorValidator`.
- Reuse `ElementsHandler.scrollToElement` for in-DOM off-screen controls (no vision required).
- Viewport sweep cap **8**, `scrollBy(0, floor(innerHeight * 0.8))`, **8 total per intent**.
- Do not commit unless the user explicitly asks.
- Spec: `docs/superpowers/specs/2026-08-17-vision-grounding-design.md`.

**Hook location (locked):** Layer 1.5 runs in `ProvePhase.attemptIntentWithRetry` after `authorIntent(..., allowVisionHeal=false)` fails and **before** `HealCascade.heal`. `authorIntent` has no WebDriver, so it cannot `elementFromPoint`.

---

## File map

| File | Role |
|------|------|
| `src/main/java/delivery/vision/BoundingBox.java` | Viewport CSS-pixel box |
| `src/main/java/delivery/vision/VisualCandidate.java` | description, box, confidence |
| `src/main/java/delivery/vision/VisionAnalysisResult.java` | found, candidates, error |
| `src/main/java/delivery/vision/VisionGroundingProvider.java` | `analyze(png, intent)` |
| `src/main/java/delivery/vision/VisionGroundingConfig.java` | flags + factory |
| `src/main/java/delivery/vision/FakeVisionGroundingProvider.java` | Tests / empty default |
| `src/main/java/delivery/vision/CoordinateMapper.java` | bbox center + bitmap→CSS |
| `src/main/java/delivery/vision/GroundingBrowser.java` | Port over WebDriver |
| `src/main/java/delivery/vision/GroundedNode.java` | Hit node attrs |
| `src/main/java/delivery/vision/ElementGrounder.java` | fromPoint → candidateId |
| `src/main/java/delivery/vision/ViewportSweep.java` | Cap-8 analyze loop |
| `src/main/java/delivery/vision/VisionGroundingEngine.java` | Orchestrate 1.5 |
| `src/main/java/delivery/vision/QwenVisionProvider.java` | Local Ollama JSON |
| `src/main/java/delivery/vision/UiTarsVisionProvider.java` | Stub: unavailable |
| `src/main/java/delivery/vision/SeleniumGroundingBrowser.java` | JS + screenshot |
| `src/main/java/delivery/job/ProvePhase.java` | Call engine before heal |
| `src/main/java/delivery/heal/HealCascade.java` | Bbox widen when master on |
| `src/main/resources/application.properties` | Flag comments |

---

### Task 1: DTOs, provider interface, config, fake

**Files:**
- Create: `src/main/java/delivery/vision/BoundingBox.java`
- Create: `src/main/java/delivery/vision/VisualCandidate.java`
- Create: `src/main/java/delivery/vision/VisionAnalysisResult.java`
- Create: `src/main/java/delivery/vision/VisionGroundingProvider.java`
- Create: `src/main/java/delivery/vision/VisionGroundingConfig.java`
- Create: `src/main/java/delivery/vision/FakeVisionGroundingProvider.java`
- Create: `src/test/java/delivery/vision/VisionGroundingConfigTest.java`
- Modify: `src/main/resources/application.properties` (comment block only)

**Interfaces:**
- Produces:
  - `record BoundingBox(int x, int y, int width, int height)`
  - `record VisualCandidate(String description, BoundingBox boundingBox, double confidence)`
  - `record VisionAnalysisResult(boolean found, List<VisualCandidate> candidates, String error)` with factory `empty()`, `unavailable(String error)`, `of(List<VisualCandidate>)`
  - `interface VisionGroundingProvider { VisionAnalysisResult analyze(byte[] screenshotPng, StepIntentBinder.IntentLine intent); }`
  - `VisionGroundingConfig.enabled()`, `providerId()`, `model()`, `createProvider()` → Fake when disabled or unknown; `qwen`/`uitars` wired in later tasks (for now unknown → Fake empty)
- Consumes: `StepIntentBinder.IntentLine`

- [ ] **Step 1: Failing tests**

```java
@Test
public void disabledByDefault() {
    System.clearProperty("delivery.vision.grounding.enabled");
    Assert.assertFalse(VisionGroundingConfig.enabled());
}

@Test
public void enabledWhenTrue() {
    System.setProperty("delivery.vision.grounding.enabled", "true");
    try {
        Assert.assertTrue(VisionGroundingConfig.enabled());
    } finally {
        System.clearProperty("delivery.vision.grounding.enabled");
    }
}

@Test
public void fakeReturnsEmpty() {
    VisionAnalysisResult r = new FakeVisionGroundingProvider()
            .analyze(new byte[] {1}, new StepIntentBinder.IntentLine(
                    StepIntentBinder.IntentKind.CLICK, "Click Login"));
    Assert.assertFalse(r.found());
    Assert.assertTrue(r.candidates().isEmpty());
}
```

- [ ] **Step 2: Run RED**

```powershell
mvn "-Dtest=VisionGroundingConfigTest" "-Dmaven.compiler.source=21" "-Dmaven.compiler.target=21" test
```

- [ ] **Step 3: Implement**

`VisionGroundingConfig.enabled()`: system property `delivery.vision.grounding.enabled`, else `PropertyReader.getProperty`, else `false`. True only for `"true"` (ignore case).

`providerId()`: `delivery.vision.provider` default `"qwen"`.

`model()`: `delivery.vision.model` default `""` (provider supplies later).

`createProvider()`: if `!enabled()` return `FakeVisionGroundingProvider`. Else switch on providerId: default Fake until Task 6/8 register Qwen/UiTars.

`FakeVisionGroundingProvider.analyze`: catch nothing; return `VisionAnalysisResult.empty()`. Null png/intent still empty, no NPE.

`application.properties`:

```
# Vision grounding (Layer 1.5 + heal bbox). Off = DOM bind only.
# delivery.vision.grounding.enabled=false
# delivery.vision.provider=qwen
# delivery.vision.model=
```

- [ ] **Step 4: GREEN**

- [ ] **Step 5: Optional commit** — `feat: vision grounding config and provider interface`

---

### Task 2: CoordinateMapper (CSS pixels)

**Files:**
- Create: `src/main/java/delivery/vision/CoordinateMapper.java`
- Create: `src/test/java/delivery/vision/CoordinateMapperTest.java`

**Interfaces:**
- Produces:
  - `record CssPoint(double x, double y)`
  - `CoordinateMapper.center(BoundingBox box)` → `CssPoint` at `x+width/2`, `y+height/2`
  - `CoordinateMapper.toCss(CssPoint imagePoint, int imageWidth, int imageHeight, int viewportWidth, int viewportHeight)` — scale image pixels to CSS when bitmap ≠ viewport (DPR). If any dimension ≤ 0, return imagePoint unchanged.

- [ ] **Step 1: Failing tests**

```java
@Test
public void centerOfBox() {
    CssPoint p = CoordinateMapper.center(new BoundingBox(400, 500, 120, 40));
    Assert.assertEquals(p.x(), 460.0, 0.01);
    Assert.assertEquals(p.y(), 520.0, 0.01);
}

@Test
public void scalesBitmapToViewport() {
    CssPoint img = new CssPoint(200, 100);
    CssPoint css = CoordinateMapper.toCss(img, 2000, 1000, 1000, 500);
    Assert.assertEquals(css.x(), 100.0, 0.01);
    Assert.assertEquals(css.y(), 50.0, 0.01);
}
```

- [ ] **Step 2: RED** then implement then GREEN.

---

### Task 3: ElementGrounder (fromPoint → candidate)

**Files:**
- Create: `src/main/java/delivery/vision/GroundedNode.java`
- Create: `src/main/java/delivery/vision/GroundingBrowser.java`
- Create: `src/main/java/delivery/vision/ElementGrounder.java`
- Create: `src/test/java/delivery/vision/ElementGrounderTest.java`

**Interfaces:**
- Consumes: `DomCandidate` list, `LocatorValidator`, `CoordinateMapper`, `VisualCandidate`
- Produces:
  - `record GroundedNode(String tag, String id, String name, String dataTest, String ariaLabel, String role, boolean displayed, boolean enabled, String outerFingerprint)`
  - `interface GroundingBrowser { GroundedNode elementFromPoint(double cssX, double cssY); ViewportSize viewportSize(); int screenshotWidth(); int screenshotHeight(); }` — screenshot dims used by mapper; Task 5 adds scroll/screenshot. For Task 3, `GroundingBrowser` may only include `elementFromPoint`. Keep the interface in one file and add methods in Task 5 rather than splitting.
  - `ElementGrounder.ground(List<DomCandidate> table, VisualCandidate visual, GroundingBrowser browser, int imageW, int imageH, int viewW, int viewH)` → `Optional<String> candidateId`
  - `ElementGrounder.addOrMatch(...)` if match fails: build locator via id → data-test → name → css `[aria-label=...]` using `XpathLiterals`/CSS-safe attrs; `LocatorValidator.validate`; if valid, append `new DomCandidate("cV1", strategy, value, tag, label)` to a **mutable copy** of the table (return a small result type).

**Result type:**

```java
public record GroundingHit(String candidateId, List<DomCandidate> table, boolean added) {}
```

`ElementGrounder.groundToHit(...)` returns `Optional<GroundingHit>`.

Matching: first `table` row whose `value` equals node `id` (strategy id) or `dataTest` in css/xpath, or tag+label equals `ariaLabel`. If none, add candidate.

- [ ] **Step 1: Failing test**

```java
@Test
public void matchesExistingIdCandidate() {
    List<DomCandidate> table = List.of(
            new DomCandidate("c1", "id", "loginBtn", "button", "Login"));
    VisualCandidate v = new VisualCandidate("Login", new BoundingBox(0, 0, 10, 10), 0.9);
    GroundingBrowser browser = (x, y) -> new GroundedNode(
            "button", "loginBtn", "", "", "Login", "button", true, true, "button#loginBtn");
    Optional<GroundingHit> hit = ElementGrounder.groundToHit(
            table, v, browser, 10, 10, 10, 10);
    Assert.assertTrue(hit.isPresent());
    Assert.assertEquals(hit.get().candidateId(), "c1");
    Assert.assertFalse(hit.get().added());
}

@Test
public void addsValidatedCandidateWhenMissing() {
    VisualCandidate v = new VisualCandidate("Go", new BoundingBox(0, 0, 10, 10), 0.9);
    GroundingBrowser browser = (x, y) -> new GroundedNode(
            "button", "goBtn", "", "", "", "button", true, true, "button#goBtn");
    Optional<GroundingHit> hit = ElementGrounder.groundToHit(
            List.of(), v, browser, 10, 10, 10, 10);
    Assert.assertTrue(hit.isPresent());
    Assert.assertTrue(hit.get().added());
    Assert.assertEquals(hit.get().table().get(0).strategy(), "id");
    Assert.assertEquals(hit.get().table().get(0).value(), "goBtn");
}

@Test
public void rejectsDisabledNode() {
    VisualCandidate v = new VisualCandidate("Checkout", new BoundingBox(0, 0, 10, 10), 0.9);
    GroundingBrowser browser = (x, y) -> new GroundedNode(
            "button", "co", "", "", "", "button", true, false, "button#co");
    Assert.assertTrue(ElementGrounder.groundToHit(List.of(), v, browser, 10, 10, 10, 10).isEmpty());
}
```

If `GroundingBrowser` cannot be a lambda after Task 5 extra methods, use a test fake class `RecordingGroundingBrowser` from Task 3 onward.

- [ ] **Step 2–4: TDD implement.** Null `elementFromPoint` → empty Optional. `displayed=false` or `enabled=false` → empty (NOT_EXECUTABLE). New ids: `cV` + index (`cV1`).

- [ ] **Step 5: Optional commit**

---

### Task 4: Weak-bind detector + VisionGroundingEngine (no live VLM)

**Files:**
- Create: `src/main/java/delivery/vision/VisionTriggers.java`
- Create: `src/main/java/delivery/vision/VisionGroundingEngine.java`
- Create: `src/test/java/delivery/vision/VisionTriggersTest.java`
- Create: `src/test/java/delivery/vision/VisionGroundingEngineTest.java`

**Interfaces:**
- Consumes: Task 1–3, `AuthoringService.stepsPreferringCandidate`, `StepIntentBinder.IntentLine`
- Produces:
  - `VisionTriggers.isEligible(IntentLine intent)` — true for `CLICK`, `CLICK_LOGIN`, `TYPE_FIELD`, `TYPE_USER`, `TYPE_PASS`, `ASSERT_VISIBLE`
  - `VisionTriggers.isWeakBind(List<ProvenStep> stepBatch)` — true if empty, any `!validated()`, or rationale starts with `AMBIGUOUS:` or contains `Weak candidate match` or `No DOM candidate` or `No distinctive-token` or `No action control matching`
  - `VisionGroundingEngine.tryGround(String tcId, IntentLine intent, List<DomCandidate> table, VisionGroundingProvider provider, GroundingBrowser browser, byte[] png, AuthoringService authoring)` → `List<ProvenStep>` empty if skip/fail

Engine algorithm:
1. If `!VisionGroundingConfig.enabled()` return empty.
2. If `!isEligible(intent)` return empty.
3. Call `provider.analyze(png, intent)` inside try/catch; on throw log + return empty.
4. If `!found` or candidates empty return empty.
5. Sort candidates by confidence descending; try each until `groundToHit` present.
6. `authoring.stepsPreferringCandidate(tcId, intent, hit.table(), hit.candidateId(), true)` (relaxed distinctive — spatial ground is the widen).
7. Return steps if all validated; else empty.

- [ ] **Step 1: Tests**

```java
@Test
public void strongBindNotWeak() {
    ProvenStep ok = new ProvenStep("T", "P", "elementAction", "click",
            "id", "loginBtn", "", "", "", true, "ok");
    Assert.assertFalse(VisionTriggers.isWeakBind(List.of(ok)));
}

@Test
public void ambiguousIsWeak() {
    ProvenStep bad = new ProvenStep("T", "P", "elementAction", "click",
            "", "", "", "", "", false, "AMBIGUOUS:CLICK:c1,c2");
    Assert.assertTrue(VisionTriggers.isWeakBind(List.of(bad)));
}

@Test
public void engineSkipsWhenDisabled() {
    System.clearProperty("delivery.vision.grounding.enabled");
    VisionGroundingEngine engine = new VisionGroundingEngine();
    List<ProvenStep> out = engine.tryGround("T",
            new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click Login"),
            List.of(), new FakeVisionGroundingProvider(), null, new byte[0], null);
    Assert.assertTrue(out.isEmpty());
}

@Test
public void engineBindsWhenFakeHitsId() {
    System.setProperty("delivery.vision.grounding.enabled", "true");
    try {
        FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
                List.of(new VisualCandidate("Login", new BoundingBox(0, 0, 10, 10), 0.95)));
        // Fake must support constructor with canned candidates — add this in Task 1 if missing
        ...
    } finally {
        System.clearProperty("delivery.vision.grounding.enabled");
    }
}
```

Add `FakeVisionGroundingProvider(List<VisualCandidate> canned)` in this task if Task 1 only had empty fake.

Use a real `AuthoringService` with table containing `c1` id=loginBtn and `stepsPreferringCandidate` — existing unit pattern from `AuthoringServiceTest`.

- [ ] **Step 2–4: Implement + GREEN**

---

### Task 5: ViewportSweep (cap 8)

**Files:**
- Modify: `src/main/java/delivery/vision/GroundingBrowser.java` — add `byte[] screenshotPng(); ViewportMetrics metrics(); void scrollViewport();`  
  `record ViewportMetrics(int innerWidth, int innerHeight, int screenshotWidth, int screenshotHeight)`
- Create: `src/main/java/delivery/vision/ViewportSweep.java`
- Create: `src/test/java/delivery/vision/ViewportSweepTest.java`
- Modify: `VisionGroundingEngine` to use sweep instead of single analyze

**Interfaces:**
- Produces: `ViewportSweep.run(provider, intent, browser, grounderCallback)`  
  Loop `i = 0 .. 7` (8 attempts): screenshot → analyze → if any candidate with confidence ≥ 0 and `elementFromPoint` non-null interactive enabled displayed, return that `VisualCandidate` + metrics; else `scrollViewport()` which must execute `scrollingElement.scrollBy(0, Math.floor(innerHeight * 0.8))`. Stop early on hit. After 8 analyzes, empty.

- [ ] **Step 1: Fake browser keyed by scroll count**

```java
@Test
public void hitsOnThirdScreen() {
    CountingBrowser browser = new CountingBrowser(2); // first 2 fromPoint null, third hits
    FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(
            List.of(new VisualCandidate("X", new BoundingBox(1, 1, 2, 2), 0.9)));
    Optional<VisualCandidate> hit = ViewportSweep.find(fake,
            new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click X"),
            browser);
    Assert.assertTrue(hit.isPresent());
    Assert.assertEquals(browser.scrolls(), 2);
}

@Test
public void stopsAtEight() {
    CountingBrowser browser = new CountingBrowser(99);
    FakeVisionGroundingProvider fake = new FakeVisionGroundingProvider(List.of());
    Assert.assertTrue(ViewportSweep.find(fake,
            new StepIntentBinder.IntentLine(StepIntentBinder.IntentKind.CLICK, "Click X"),
            browser).isEmpty());
    Assert.assertEquals(browser.analyzes() /* or screenshots */, 8);
}
```

`CountingBrowser`: `scrolls` increments on `scrollViewport`; `elementFromPoint` returns node only when `scrolls >= missCount`. Empty analyze still scrolls.

- [ ] **Step 2–4: Implement.** Engine calls `ViewportSweep` then `ElementGrounder`. Empty analyze still counts toward 8.

---

### Task 6: QwenVisionProvider (local Ollama)

**Files:**
- Create: `src/main/java/delivery/vision/QwenVisionProvider.java`
- Create: `src/test/java/delivery/vision/QwenVisionProviderTest.java`
- Modify: `VisionGroundingConfig.createProvider()` — `qwen` → `QwenVisionProvider`

**Interfaces:**
- Consumes: `LocalLlmClient.completeJson(system, user, png)`
- Produces: parse JSON `{ "found": true, "candidates": [ { "description", "bbox": {x,y,width,height}, "confidence" } ] }`
- Constructor: `(LocalLlmClient client)` for tests; production: base URL from `delivery.llm-base-url` / existing llm config, model from `delivery.vision.model` or default `qwen3-vl` (string only — if missing, analyze returns unavailable, no throw)

System prompt (verbatim in class):

```
Return JSON only. No markdown. Schema:
{"found":true,"candidates":[{"description":"string","bbox":{"x":0,"y":0,"width":0,"height":0},"confidence":0.0}]}
bbox is the target widget in the screenshot, CSS pixels, origin top-left of the image.
If not visible, {"found":false,"candidates":[]}.
```

User: `"Intent: " + intent.kind() + "\nText: " + intent.text()`

- [ ] **Step 1: Parse tests with stub client** (anonymous `LocalLlmClient` subclass or package-visible parse method `QwenVisionProvider.parseResponse(String json)` static for unit tests without HTTP)

```java
@Test
public void parseTwoCandidates() {
    VisionAnalysisResult r = QwenVisionProvider.parseResponse(
            "{\"found\":true,\"candidates\":[{\"description\":\"Login\",\"bbox\":{\"x\":1,\"y\":2,\"width\":3,\"height\":4},\"confidence\":0.96}]}");
    Assert.assertTrue(r.found());
    Assert.assertEquals(r.candidates().get(0).boundingBox().x(), 1);
}

@Test
public void parseGarbageIsUnavailable() {
    VisionAnalysisResult r = QwenVisionProvider.parseResponse("not json");
    Assert.assertFalse(r.found());
    Assert.assertNotNull(r.error());
}
```

- [ ] **Step 2–4:** `analyze` try/catch IOException → `unavailable`. Never send full HTML.

- [ ] **Step 5: Optional commit**

---

### Task 7: SeleniumGroundingBrowser + ProvePhase Layer 1.5 + Heal bbox + logs

**Files:**
- Create: `src/main/java/delivery/vision/SeleniumGroundingBrowser.java`
- Modify: `src/main/java/delivery/job/ProvePhase.java` (`attemptIntentWithRetry`)
- Modify: `src/main/java/delivery/heal/HealCascade.java`
- Create: `src/test/java/delivery/vision/VisionProveHookTest.java` (unit: extract trigger+engine with fakes; do not boot Spring)
- Create: `src/test/java/delivery/heal/HealVisionBboxWidenTest.java`

**Interfaces:**
- `SeleniumGroundingBrowser(WebDriver driver)` implements `GroundingBrowser`:
  - `screenshotPng()`: `((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES)` catch → empty
  - `metrics()`: JS `return {w: innerWidth, h: innerHeight, dpr: devicePixelRatio}`
  - `elementFromPoint`: JS walk up to interactive tags listed in spec; return null if none
  - `scrollViewport`: JS `const s = document.scrollingElement || document.documentElement; s.scrollBy(0, Math.floor((window.innerHeight||0)*0.8));`

**ProvePhase insert** (after bindFailed computed, before heal):

```java
if (bindFailed && VisionGroundingConfig.enabled()
        && VisionTriggers.isEligible(intent)
        && VisionTriggers.isWeakBind(stepBatch)) {
    List<DomCandidate> table = DomCandidateExtractor.extract(html);
    table = StepIntentBinder.withoutFailedLocators(table, failedThisIntent);
    VisionGroundingEngine engine = new VisionGroundingEngine();
    List<ProvenStep> grounded = engine.tryGround(
            tc.tcId(), intent, table, VisionGroundingConfig.createProvider(),
            new SeleniumGroundingBrowser(driverFactory.get()), healPng, authoring);
    if (!grounded.isEmpty() && grounded.stream().allMatch(ProvenStep::validated)) {
        stepBatch = grounded;
        bindFailed = false;
        healTier = mergeHealTier(healTier, "vision");
        LogsManager.info("VISION_GROUND: READY intent=" + intent.text());
    }
}
```

Then existing heal if still `bindFailed`.

**HealCascade:** when `VisionGroundingConfig.enabled()` and `widened` would apply, before Ollama:
- run `VisionGroundingEngine` / `ElementGrounder` once (sweep cap still 8 total — if ProvePhase already swept, heal may screenshot once only: **lock heal to 1 analyze + optional 8 sweep only if engine used**; simpler lock: heal calls `ViewportSweep` with remaining budget 8 always, acceptable).
- If `GroundingHit`, prepend/ensure that candidate in `shortlist` then existing Ollama/Cursor **candidateId only**.

If master flag false: unchanged `visionHealRules` screenshot pick.

**Logs:** `LogsManager.info` lines: `VISION: candidates=... bbox=... conf=...`, `GROUNDING: hit|miss id=...`, `LOCATOR: strategy= value=`, `FINAL: READY|MISS`. No PNG bytes.

- [ ] **Step 1: Heal test** — inject Fake provider via HealCascade constructor overload `HealCascade(..., VisionGroundingProvider vision)` or static/thread-local is forbidden. Add constructor param `VisionGroundingProvider visionOrNull`. When non-null and config enabled, use it. Test: empty distinctive pool, fake bbox matches `c1`, heal returns success tier `vision`.

- [ ] **Step 2–4: Implement.** Existing `HealCascadeTest` must still pass (null provider → old path).

- [ ] **Step 5: Optional commit**

---

### Task 8: UiTarsVisionProvider stub

**Files:**
- Create: `src/main/java/delivery/vision/UiTarsVisionProvider.java`
- Modify: `VisionGroundingConfig.createProvider()`
- Create: `src/test/java/delivery/vision/VisionGroundingConfigProviderSwitchTest.java`

**Interfaces:**
- `analyze` returns `VisionAnalysisResult.unavailable("uitars provider not configured")` — no HTTP.
- `createProvider()`: `uitars` → stub; `qwen` → Qwen; else Fake.

- [ ] **Step 1–4: Test switch + GREEN**

---

## Spec coverage self-review

| Spec item | Task |
|-----------|------|
| DTOs + interface + fake + master flag | 1 |
| Coordinate CSS / DPR | 2 |
| elementFromPoint + match/add + disabled reject | 3 |
| Weak trigger + engine; skip when strong / flag off | 4 |
| Sweep cap 8 | 5 |
| Qwen local JSON provider | 6 |
| ProvePhase 1.5 + heal bbox + logs + Selenium browser | 7 |
| UI-TARS stub | 8 |
| No offset-click | all (never added) |
| Eligible kinds | `VisionTriggers` Task 4 |
| Off-screen DOM uses existing scroll | no new code; execute path |

Placeholder scan: none. Commit steps optional.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-08-18-vision-grounding.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — this session with checkpoints  

Which approach?
