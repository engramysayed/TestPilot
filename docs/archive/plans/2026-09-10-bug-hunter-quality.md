# Bug Hunter Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Bug Hunter so the planner gets a structured page map (with full slim fallback), compounding coverage memory, grounded actions, stuck stop, then Phase 2 strategies + oracles + smarter finish.

**Architecture:** New builders (`HuntPageMapBuilder`, `HuntCoverageMap`, `HuntActionGuard`, later `HuntStrategySequencer` + `HuntOracle`) feed `LiveHuntService` and `OllamaHuntPlanner`. Remove the 32k DOM head truncate. Config `delivery.hunt.dom-mode=auto|map|slim`. Phase 1 ships map/ground/coverage/stuck; Phase 2 ships playbook/oracles/`COMPLETE`.

**Tech Stack:** Java 21, Spring Boot, Jsoup/`DomCandidateExtractor`, Selenium, TestNG, Thymeleaf

**Spec:** `docs/superpowers/specs/2026-09-10-bug-hunter-quality-design.md`  
**Extends:** `docs/superpowers/specs/2026-09-09-bug-hunter-design.md`

## Global Constraints

- No Claude / Anthropic models
- No auto-merge of candidate scenarios into the library workbook
- Screenshot remains **one per cycle at cycle start** (before actions)
- Action cap default 5; wait default 5000ms when AI omits `ms` (already shipped — do not regress)
- Page map control cap **100**; thin map = **fewer than 8** controls
- Phase 1 stuck = hard stop `STUCK` (Phase 2 may advance strategy instead)
- Do not commit unless the user explicitly asks in that session (plan commit steps are optional gates)

---

## File structure (locked)

| File | Responsibility |
|------|----------------|
| `delivery/hunt/HuntPageMap.java` | Immutable page map model + `toPromptMd()` / JSON helpers |
| `delivery/hunt/HuntPageMapBuilder.java` | slim HTML + url/title → `HuntPageMap` |
| `delivery/hunt/HuntCoverageMap.java` | append-only coverage file + fail streaks + `forPrompt()` |
| `delivery/hunt/HuntActionGuard.java` | grounded locator check vs map/slim |
| `delivery/hunt/HuntDomMode.java` | enum `MAP` / `SLIM` / `AUTO` + parse |
| `delivery/hunt/HuntStrategySequencer.java` | Phase 2 playbook modes |
| `delivery/hunt/HuntOracle.java` | Phase 2 post-action bug drafts |
| Modify `HuntPlanner.Context` | add pageMapMd, includeSlimDom, coverageMd, strategyHint, domMode |
| Modify `OllamaHuntPlanner` | new prompt sections; **delete** `DOM_PROMPT_MAX_CHARS` truncate |
| Modify `LiveHuntService` | wire map/coverage/guard/stuck/(P2) |
| Modify `HuntActionExecutor` | call guard before locator actions |
| Modify `HuntRequest` / controller / UI | `domMode`; Phase 2 `strategiesEnabled` |
| Modify `HuntPackWriter` / SUMMARY | coverage, grounded rejects, strategies, stop reasons |
| Tests under `src/test/java/delivery/hunt/` | unit + extend `HuntCoreTest` / `HuntApiTest` |

---

# Phase 1 — Context, memory, grounding

### Task 1: Page map model + builder

**Files:**
- Create: `src/main/java/delivery/hunt/HuntPageMap.java`
- Create: `src/main/java/delivery/hunt/HuntPageMapBuilder.java`
- Test: `src/test/java/delivery/hunt/HuntPageMapTest.java`

**Interfaces:**
- Consumes: `delivery.authoring.DomCandidateExtractor.extract(html, 100)`, Jsoup for headings/alerts
- Produces: `HuntPageMap` with `url`, `title`, `headings`, `alerts`, `controls`, `dialogs`, `isThin()`, `toPromptMd()`, `toJsonObject()`, `controlCount()`

- [ ] **Step 1: Write the failing test**

```java
package delivery.hunt;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HuntPageMapTest {
    @Test
    public void buildsControlsAndDetectsThin() {
        String html = """
                <html><body>
                  <h1>Login</h1>
                  <div role="alert">Bad password</div>
                  <input id="user" name="user" />
                  <button id="go">Sign in</button>
                </body></html>
                """;
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/login", "Login", html);
        Assert.assertEquals(map.url(), "https://ex/login");
        Assert.assertTrue(map.controlCount() >= 2);
        // Threshold: controlCount() < 8 ⇒ thin. This fixture has ~2 controls.
        Assert.assertTrue(map.isThin());
        Assert.assertTrue(map.toPromptMd().contains("Login"));
        Assert.assertTrue(map.toPromptMd().contains("Bad password")
                || map.alerts().stream().anyMatch(a -> a.contains("Bad")));
    }

    @Test
    public void emptyBodyIsThin() {
        HuntPageMap map = HuntPageMapBuilder.build("https://ex/", "x", "<html><body></body></html>");
        Assert.assertTrue(map.isThin());
        Assert.assertEquals(map.controlCount(), 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q "-Dtest=HuntPageMapTest" test`  
Expected: FAIL (classes missing)

- [ ] **Step 3: Implement `HuntPageMap` + `HuntPageMapBuilder`**

```java
// HuntPageMap.java — record with lists; isThin() => controlCount() < 8
// HuntPageMapBuilder.build(url, title, slimHtml):
//   DomCandidateExtractor.extract(slimHtml, 100)
//   Jsoup: h1-h3 texts (max 8), [role=alert] + [aria-invalid=true] snippets (max 10)
//   dialogs: [role=dialog] aria-label/title (max 5)
//   toPromptMd(): markdown sections URL/Title/Headings/Alerts/Controls (strategy:value — label)
```

Lock constants on builder: `CONTROL_CAP = 100`, `THIN_CONTROL_THRESHOLD = 8`.

- [ ] **Step 4: Run tests — expect PASS**

Run: `mvn -q "-Dtest=HuntPageMapTest" test`

- [ ] **Step 5: Commit (only if user asked)**

```bash
git add src/main/java/delivery/hunt/HuntPageMap.java src/main/java/delivery/hunt/HuntPageMapBuilder.java src/test/java/delivery/hunt/HuntPageMapTest.java
git commit -m "feat(hunt): page map builder for planner context"
```

---

### Task 2: Dom mode enum + planner prompt (map primary, no 32k cut)

**Files:**
- Create: `src/main/java/delivery/hunt/HuntDomMode.java`
- Modify: `src/main/java/delivery/hunt/HuntPlanner.java` — extend `Context`
- Modify: `src/main/java/delivery/hunt/OllamaHuntPlanner.java` — prompt assembly
- Modify: `src/main/java/delivery/hunt/HuntRequest.java` — `domMode` field default `auto`
- Test: extend `src/test/java/delivery/hunt/HuntCoreTest.java`

**Interfaces:**
- Consumes: `HuntPageMap.toPromptMd()`, `HuntDomMode`
- Produces: updated `HuntPlanner.Context` fields below; `OllamaHuntPlanner.buildUserPrompt` includes page map; slim only when required

New `Context` fields (append; keep existing order stable where possible):

```java
record Context(
    String briefMd,
    int cycleIndex,
    int cycleCeiling,
    int scenarioCap,
    int scenariosEmitted,
    int actionCapPerCycle,
    String slimDom,
    Path screenshotPath,
    List<Map<String, Object>> networkFailures,
    String stepsJournalMd,
    String plannerMode,
    // Phase 1 additions:
    String pageMapMd,
    boolean includeSlimDom,
    String coverageMd,
    String strategyHint,   // Phase 1: "" or "mode=explore"
    String domMode         // "auto"|"map"|"slim"
) {}
```

All existing call sites must compile — pass `""`, `false`, `""`, `"mode=explore"`, `"auto"` until later tasks fill them.

- [ ] **Step 1: Failing tests for prompt behavior**

```java
@Test
public void promptUsesPageMapAndSkipsSlimWhenRichAuto() {
    HuntPlanner.Context ctx = new HuntPlanner.Context(
            "brief", 1, 8, 5, 0, 5,
            "<body>" + "x".repeat(40_000) + "</body>",
            null, List.of(), "", "ollama",
            "## Page map\ncontrols: 20\n",
            false,
            "",
            "mode=explore",
            "auto");
    String p = OllamaHuntPlanner.buildUserPrompt(ctx);
    Assert.assertTrue(p.contains("## Page map"));
    Assert.assertFalse(p.contains("## Slim DOM"));
    Assert.assertFalse(p.contains("…(DOM truncated"));
}

@Test
public void promptIncludesFullSlimWhenRequested() {
    String slim = "<body><button id='a'>A</button></body>";
    HuntPlanner.Context ctx = new HuntPlanner.Context(
            "b", 1, 8, 5, 0, 5, slim, null, List.of(), "", "ollama",
            "map", true, "", "mode=explore", "slim");
    String p = OllamaHuntPlanner.buildUserPrompt(ctx);
    Assert.assertTrue(p.contains("## Slim DOM"));
    Assert.assertTrue(p.contains("id='a'") || p.contains("id=\"a\"") || p.contains(slim));
    Assert.assertFalse(p.contains("DOM truncated"));
}
```

- [ ] **Step 2: Run — expect FAIL or compile errors on Context arity**

- [ ] **Step 3: Implement**

`HuntDomMode.parse(String)` → MAP/SLIM/AUTO; invalid → AUTO.

`OllamaHuntPlanner`:
- Delete `DOM_PROMPT_MAX_CHARS` and substring truncate.
- Prompt order per spec: Caps → Strategy → Brief → Journal → Coverage → Network → Page map → Slim (if `includeSlimDom`) → Screenshot note.
- System prompt: mention page map grounding + actionCap + wait 5000 default.

`HuntRequest`: `domMode` string, normalize to `auto|map|slim`.

Helper (can live on `HuntDomMode` or `LiveHuntService`):

```java
static boolean shouldIncludeSlim(HuntDomMode mode, HuntPageMap map) {
    return mode == HuntDomMode.SLIM
            || (mode == HuntDomMode.AUTO && (map == null || map.isThin()));
}
```

- [ ] **Step 4: Fix all Context constructors** (`LiveHuntService`, `DryRunHuntService`, `CursorHuntPlanner` path, tests)

- [ ] **Step 5: Run** `mvn -q "-Dtest=HuntCoreTest,HuntPageMapTest" test` — PASS

- [ ] **Step 6: Commit (optional)** `feat(hunt): page-map planner prompt and remove 32k DOM cut`

---

### Task 3: Coverage map

**Files:**
- Create: `src/main/java/delivery/hunt/HuntCoverageMap.java`
- Test: `src/test/java/delivery/hunt/HuntCoverageMapTest.java`

**Interfaces:**
- Consumes: cycle url/title/heading, action log rows, optional strategy name
- Produces: `coverage-map.md` (+ optional json), `forPrompt()`, `failStreak(type, locatorKey)`, `recordActions(...)`, `noteVisit(url, title, heading)`, `shouldStopStuck()` when streak ≥ 2

- [ ] **Step 1: Failing test**

```java
@Test
public void tracksVisitAndStuckStreak() throws Exception {
    Path root = Files.createTempDirectory("hunt-cov");
    HuntCoverageMap cov = new HuntCoverageMap(root);
    cov.noteVisit("https://ex/a", "A", "Heading A");
    cov.recordActions(List.of(Map.of(
            "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
    cov.recordActions(List.of(Map.of(
            "type", "click", "status", "fail", "locator", "#go", "reason", "missing")));
    Assert.assertTrue(cov.shouldStopStuck());
    Assert.assertTrue(cov.forPrompt().contains("https://ex/a"));
    Assert.assertTrue(Files.isRegularFile(root.resolve("coverage-map.md")));
}
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement**

```java
public final class HuntCoverageMap {
  public static final String FILE_NAME = "coverage-map.md";
  public static final int STUCK_FAIL_STREAK = 2;
  public static final int PROMPT_MAX_CHARS = 12_000;
  // noteVisit, recordActions (bump streak on fail for click|type|clear|assert_visible; reset on ok)
  // locatorKey = locator or locatorStrategy+":"+locatorValue
  // shouldStopStuck() when any streak >= 2
  // forPrompt() truncate oldest if needed
}
```

Also append completed strategy names when Phase 2 calls `markStrategyDone(String)` (stub method in Phase 1 that appends a line; sequencer uses it in Phase 2).

- [ ] **Step 4: PASS** `mvn -q "-Dtest=HuntCoverageMapTest" test`

- [ ] **Step 5: Commit (optional)** `feat(hunt): coverage map for compounding memory`

---

### Task 4: Grounded action guard

**Files:**
- Create: `src/main/java/delivery/hunt/HuntActionGuard.java`
- Modify: `src/main/java/delivery/hunt/HuntActionExecutor.java`
- Test: `src/test/java/delivery/hunt/HuntActionGuardTest.java` (+ extend actionCap test in `HuntCoreTest`)

**Interfaces:**
- Consumes: current `HuntPageMap` (nullable), current `slimHtml`, action map
- Produces: `Optional<String> rejectReason(action)` — empty if allowed

Rules:
- Types requiring ground: `click`, `type`, `clear`, `assert_visible`
- Allowed if locator value matches any control in map (`strategy`+`value` or raw `locator` equals `value` / appears in `strategy:value`) **OR** `HtmlLocatorPresence.present(strategy, value, slimHtml)`
- If strategy blank and locator looks like `#id` / `id=...`, treat as css/id best-effort presence in slim
- `navigate`, `wait`, `assert_text` → always allowed (structure validation still in executor)

- [ ] **Step 1: Failing test**

```java
@Test
public void rejectsInventedLocator() {
    HuntPageMap map = HuntPageMapBuilder.build("https://ex/", "t",
            "<body><button id='real'>Go</button></body>");
    HuntActionGuard g = new HuntActionGuard(map, "<body><button id='real'>Go</button></body>");
    Assert.assertTrue(g.rejectReason(Map.of("type", "click", "locator", "#nope")).isPresent());
    Assert.assertTrue(g.rejectReason(Map.of(
            "type", "click", "locatorStrategy", "id", "locatorValue", "real")).isEmpty());
}
```

- [ ] **Step 2: Implement guard**

- [ ] **Step 3: Wire executor**

```java
// HuntActionExecutor fields: HuntActionGuard guard (nullable)
// setGuard(HuntActionGuard) or constructor overload
// at start of executeOne, after type resolved, if guard != null:
//   Optional<String> why = guard.rejectReason(action);
//   if (why.isPresent()) { row status rejected reason=ungrounded_locator: ...; return row; }
```

- [ ] **Step 4: Tests PASS** including existing `actionCapSkipsExtras`

- [ ] **Step 5: Commit (optional)** `feat(hunt): reject ungrounded hunter actions`

---

### Task 5: Wire Phase 1 into LiveHuntService + dry-run + pack + UI/config

**Files:**
- Modify: `src/main/java/delivery/hunt/LiveHuntService.java`
- Modify: `src/main/java/delivery/hunt/DryRunHuntService.java`
- Modify: `src/main/java/delivery/hunt/HuntPackWriter.java`
- Modify: `src/main/java/delivery/hunt/HuntController.java` (+ body DTO)
- Modify: `src/main/resources/templates/bug-hunter.html`
- Modify: `src/main/resources/application.properties` — `delivery.hunt.dom-mode=auto`
- Modify: wherever properties are read for delivery (e.g. `HuntWorker` / factory) — pass default domMode into request if blank
- Test: `HuntCoreTest` / `HuntApiTest` dry-run still green; add assertion pack can mention coverage when present

**Live loop changes (each cycle):**

1. Capture slim + screenshot + network (unchanged).
2. `HuntPageMap map = HuntPageMapBuilder.build(driver.getCurrentUrl(), driver.getTitle(), slim)`.
3. Write `page-map.json` + `page-map.md` under cycle dir.
4. `coverage.noteVisit(...)`.
5. `includeSlim = HuntDomMode.shouldIncludeSlim(mode, map)`.
6. `actions.setGuard(new HuntActionGuard(map, slim))`.
7. Build Context with `pageMapMd`, `includeSlimDom`, `coverage.forPrompt()`, strategyHint `"mode=explore"`.
8. Plan → execute → journal + `coverage.recordActions(actionLog)`.
9. If `coverage.shouldStopStuck()` → `stopReason=STUCK`; break.
10. Ensure `coverage-map.md` is under hunt root when pack zips the root (already packs huntRoot files).

`SUMMARY.md`: add lines for `domMode`, `stopReason` (including STUCK), grounded reject count (scan action logs or coverage counter).

UI: select `dom-mode` with options Auto / Map only / Full slim; POST `domMode`.

- [ ] **Step 1: Implement wiring**

- [ ] **Step 2: Dry-run writes stub `page-map.md` + empty coverage section so pack shape matches**

- [ ] **Step 3: Run** `mvn -q "-Dtest=HuntCoreTest,HuntApiTest,HuntPageMapTest,HuntCoverageMapTest,HuntActionGuardTest" test` — PASS

- [ ] **Step 4: Commit (optional)** `feat(hunt): wire page map, coverage, grounding, stuck stop`

---

# Phase 2 — Strategies, oracles, finish

### Task 6: Strategy sequencer

**Files:**
- Create: `src/main/java/delivery/hunt/HuntStrategySequencer.java`
- Test: `src/test/java/delivery/hunt/HuntStrategySequencerTest.java`
- Modify: `HuntRequest` — `strategiesEnabled` default `true`
- Modify: `LiveHuntService` — strategy hint; on double-fail advance mode instead of STUCK when strategies enabled and not last mode

**Modes (fixed order):** `happy`, `empty`, `boundary`, `abuse`, `session`, `invent`

```java
public final class HuntStrategySequencer {
  public record Hint(String mode, String goal, List<String> completed) {}
  public Hint current();
  public String forPrompt(); // ## Hunt strategy\nmode=...\ngoal=...\ncompletedModes=...
  public void advance();
  public boolean isLast();
  public List<String> completed();
}
```

Goals (exact strings):

| mode | goal |
|------|------|
| happy | Exercise the primary happy path once for the selected feature. |
| empty | Probe empty/cleared required fields and submit/continue. |
| boundary | Probe max-length, special characters, or unicode in visible inputs. |
| abuse | Try double-submit, repeat click, or Back after a success signal. |
| session | If logged in, probe stale session / logout mid-flow; else skip via advance. |
| invent | Prefer emitting candidate edge scenarios; still record bugs if found. |

- [ ] **Step 1: Test advance + prompt**

```java
@Test
public void advancesUntilLast() {
    HuntStrategySequencer seq = new HuntStrategySequencer(true);
    Assert.assertEquals(seq.current().mode(), "happy");
    seq.advance();
    Assert.assertEquals(seq.current().mode(), "empty");
    Assert.assertTrue(seq.forPrompt().contains("completedModes"));
}
```

- [ ] **Step 2: Implement + wire**

When `strategiesEnabled` and coverage would stuck:
- if `!seq.isLast()` → `seq.advance(); coverage.markStrategyDone(prev);` clear that locator streak; continue
- else → `stopReason=STUCK`

Skip `session` immediately (call `advance`) when no login username on job.

UI checkbox “Use hunt strategies” default on → `strategiesEnabled`.

- [ ] **Step 3: Tests PASS**

- [ ] **Step 4: Commit (optional)** `feat(hunt): strategy sequencer playbook`

---

### Task 7: Oracle + COMPLETE stop + pack polish

**Files:**
- Create: `src/main/java/delivery/hunt/HuntOracle.java`
- Modify: `LiveHuntService` — after actions, `oracle.collect(...)` merge bugs
- Modify: `HuntPackWriter` — SUMMARY strategies, oracle bug count, stopReason `COMPLETE`
- Test: `src/test/java/delivery/hunt/HuntOracleTest.java`

**HuntOracle.collect** inputs: cycle index, network failures, optional alert texts from page map refresh, action log, journal repro slice.  
Outputs: list of bug maps with `title`, `severity`, `expected`, `actual`, `repro`, `evidenceHint`.

Rules:
- If network has 4xx/5xx and planner bugs empty → draft bug
- If action `assert_*` failed → draft if not already in planner bugs
- Alerts present after a `fail` action → draft major

**COMPLETE stop:** invent budget remaining == 0 **and** sequencer on last mode completed (or strategies disabled and planner finished attacks) **and** `allBugs.size() >= 1` → can set `stopReason=COMPLETE` when planner also returns finish **or** after last mode ends with bugs. Spec: prefer ending with COMPLETE when strategies exhausted + invent met + ≥1 bug even without planner finish.

Simpler implementable rule:

```text
if (strategiesEnabled && seq.finishedAll()
    && scenariosEmitted >= scenarioCap
    && !allBugs.isEmpty()) stopReason = COMPLETE; break;
```

- [ ] **Step 1: Oracle unit test with synthetic network fail → one bug with repro**

- [ ] **Step 2: Implement oracle + wire + SUMMARY fields**

- [ ] **Step 3: Run full hunt test suite**

```bash
mvn -q "-Dtest=HuntCoreTest,HuntApiTest,HuntPageMapTest,HuntCoverageMapTest,HuntActionGuardTest,HuntStrategySequencerTest,HuntOracleTest" test
```

Expected: PASS

- [ ] **Step 4: Commit (optional)** `feat(hunt): oracles, COMPLETE stop, pack polish`

---

### Task 8: Spec cross-check + manual smoke notes

**Files:**
- Modify: `docs/superpowers/specs/2026-09-10-bug-hunter-quality-design.md` — set Status to `Implemented (Phase 1)` or `Implemented` when both phases done
- Modify: `docs/superpowers/specs/2026-09-09-bug-hunter-design.md` — one-line pointer to quality spec for DOM/memory

- [ ] **Step 1: Checklist vs spec**

Phase 1: map, auto/slim, no 32k, coverage, guard, STUCK, evidence files, UI domMode  
Phase 2: sequencer, oracle, COMPLETE, SUMMARY polish

- [ ] **Step 2: Manual smoke (human):** start dry-run hunt; unzip pack; confirm `page-map.md` / `coverage-map.md` / `planner-prompt.txt` contain map not truncated junk. Live optional with `gemma4:e2b`.

- [ ] **Step 3: Commit docs only if user asks**

---

## Self-review (plan vs spec)

| Spec requirement | Task |
|------------------|------|
| Page map B primary | 1, 2, 5 |
| Slim A fallback / auto thin &lt;8 | 2, 5 |
| Remove 32k truncate | 2 |
| Coverage map | 3, 5 |
| Grounded actions | 4, 5 |
| Phase 1 STUCK hard stop | 5 |
| Evidence page-map + journal | 5 |
| Strategies playbook | 6 |
| Oracles + repro | 7 |
| COMPLETE stop | 7 |
| Pack polish | 5, 7 |
| Phase 3 two-pass | explicitly out of plan |

No TBD placeholders. Context field names consistent across tasks. Guard reason prefix `ungrounded_locator`.

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-10-bug-hunter-quality.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — this session with executing-plans and checkpoints  

Which approach?
