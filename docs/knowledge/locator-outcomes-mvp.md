# Locator outcome store — OR-scorer + memory-FirstOf MVP

> **Date landed:** 2026-11-20. **Status:** MVP-M shipped (bag reorder + nlCached FirstOf shape + memory-side candidate persistence). Memory hits now emit FirstOf out of the box.
>
> Companion rule: [`FRAMEWORK_RULES.md` → KEEL-R-FIRSTOF](../FRAMEWORK_RULES.md).

## 1. Why

Keel generates a page like:

```java
protected final By loginButton = FirstOf.of("LoginPage", "loginButton",
        By.id("login-btn"),                       // primary
        By.cssSelector("button[data-test='x']"),   // fallback 1
        By.xpath("//button[normalize-space()='Login']")); // fallback 2
```

The three locators are picked by a stability tier at bind time (preferred-hook > test-id > id > …). After a few hundred runs, we learn which slot actually wins. Before this MVP, that learning was thrown away between runs: no slot history, no cross-run adjustment, and `nlCached` memory-hit fields had zero fallback (one rename → straight to healing).

MVP goal: **learn from every FirstOf resolution and reorder the slots on the next codegen** — same stability policy at bind, win-ratio reordering at codegen. Plus: give memory-hit fields the same FirstOf safety net so `nlCached` is no longer a single-By bypass.

## 2. What ships now

### Framework-template side (customer's generated project)

- [`FirstOf.java`](../../customer-framework-template/src/main/java/project/utils/locators/FirstOf.java)
  - New overload: `FirstOf.of(String pageClass, String fieldName, By... bys)` — codegen emits this.
  - Legacy overload: `FirstOf.of(String fieldName, By... bys)` kept so already-generated projects still compile.
  - On every `findElements`, logs one `WIN` for the winning slot and one `MISS` per earlier tried-and-missed slot. Never throws from logging.
  - Legacy fallback-win log (human-readable warn + legacy JSONL event) is preserved.

- [`KeelResults.java`](../../customer-framework-template/src/main/java/project/utils/reports/KeelResults.java)
  - New constant `LOCATOR_OUTCOMES = "locator-outcomes.jsonl"`.
  - `reset()` does **not** touch this file — it accumulates across runs on purpose.

- [`Page.java.ftl`](../../customer-framework-template/templates/Page.java.ftl)
  - Bag path emits the new signature: `FirstOf.of("<PageClass>", "<field>", …)`. Memory-hit fields flow through this same branch (their `ProvenStep.locatorCandidates()` is attached by `DomainLocatorMemory.recall`, which `PageAccumulator.allocateField` turns into a bag).
  - NL-cached path (W3 explicit NL steps) still emits a single `By.…` since `NlLocatorCache.CachedBy` carries no bag.

### Portal side (Spring, `delivery/*`)

- [`LocatorOutcomeStore.java`](../../src/main/java/delivery/store/LocatorOutcomeStore.java) (NEW)
  - Reads `<projectRoot>/test-output/keel-results/locator-outcomes.jsonl`.
  - Aggregates `(page, field, target)` → `{wins, misses}`.
  - `rankedIndices(page, field, targetSigs)` returns the input indices reordered by Laplace-smoothed win ratio, stable for ties. Unseen locators score 0.5 (same as 0W/0L after smoothing).
  - Absent / unreadable file → empty `Aggregate` → identity ordering, zero diff on re-emit.
  - `targetSig(strategy, value)` builds `"By.<strategy>: <value>"` so codegen target strings match Selenium's `By.toString()` which is what FirstOf logs.

- [`CodeWriter.java`](../../src/main/java/delivery/codegen/CodeWriter.java)
  - `reorderFieldsByStore(pageClass, fields, aggregate)` — static helper, called once per emit, right before `pageTpl.process`.
  - Rebuilds each FieldModel with a non-empty bag using the store's ordering; identity permutations return the original FieldModel unchanged (keeps re-emit diffs clean when nothing changed).

- [`DomainLocatorMemory.java`](../../src/main/java/delivery/store/DomainLocatorMemory.java) (memory-side enrichment)
  - Persists snapshot fallbacks alongside the proven primary. A new `candidates: [{strategy, value}]` JSON array per entry carries up to `LocatorCandidate.MAX_FALLBACKS` locators pulled from the proved step's `locatorCandidates()` at `remember()` time (the proven primary is skipped — it already lives in the top-level `strategy` / `locator` fields).
  - `recall()` builds `List<LocatorCandidate>` from the persisted fallbacks and attaches them via `ProvenStep.withLocatorCandidates(...)`, so a memory-hit step flows into `PageAccumulator.allocateField` with a non-empty bag, which in turn makes the codegen template emit `FirstOf.of(...)` for that field — closing the zero-fallback brittleness §1 named.
  - Entries saved before this change (no `candidates` array) still load correctly and recall as single-By (bag empty); forward and backward compatible. `recallFieldAssertion` is unchanged — assertions never feed FirstOf.

### Vision

- [`UiTarsVisionProvider.shouldRetryGrounding`](../../src/main/java/delivery/vision/UiTarsVisionProvider.java) — generalized from the hardcoded `login|submit` gate to any CLICK-ish `IntentKind` (CLICK, CLICK_LOGIN, HOVER, DOUBLE_CLICK, RIGHT_CLICK). Retry nudge switched to `retryNudgeFor(intent)` which still emits the Login/Submit-specific wording when the intent names them, else the quoted control name, else a generic "the control the instruction names, not an input field" nudge.

### Tests

- [`LocatorOutcomeStoreTest.java`](../../src/test/java/delivery/store/LocatorOutcomeStoreTest.java) — covers absent file, malformed-line resilience, learned-fallback promotion, unseen-fallback placement vs a known loser, and `targetSig` ↔ Selenium toString parity.

## 3. Data flow

```
customer-framework-template (customer's project after Automate emit)
  └─ runs test → project.utils.locators.FirstOf.findElements
       └─ appends {page, field, slot, outcome: WIN|MISS, target} to
          <projectRoot>/test-output/keel-results/locator-outcomes.jsonl   (append-only)

portal (Spring, delivery.job.EmitPhase on next Automate emit)
  └─ CodeWriter.writeVerified(projectRoot, …)
       └─ LocatorOutcomeStore.read(projectRoot)
            └─ reorderFieldsByStore(pageClass, page.fields(), aggregate)
                 └─ new FieldModel (primary + bag permuted)
       └─ pageTpl.process(model, buf)
```

### Path assumption and how to override it

The reorder path hardcodes `<projectRoot>/test-output/keel-results/locator-outcomes.jsonl`. The writer (`FirstOf` in the generated project) uses the test-run JVM's `user.dir`; the reader (`LocatorOutcomeStore.read` called from `CodeWriter.writeVerified`) uses the `projectRoot` that the portal hands to codegen. Those are the same path **only when the test-runner JVM's working directory is the emitted project root** — this is the user's stated "all on the same server, client only accesses the portal" deployment model.

If a deployment breaks that assumption (sidecar runs tests in a different CWD, writer and reader on different disks, writer sharded across hosts):

- the writer sees nothing in its own CWD, drops `locator-outcomes.jsonl` elsewhere, and the reader's `read(projectRoot)` finds an empty file — the reorder silently no-ops;
- override with `-Dkeel.results.dir=<path>` on the test-run JVM so the writer lands the JSONL under the project root the portal reads;
- on the reader side, `LocatorOutcomeStore.readFile(Path)` is the escape hatch — point it at the file the writer actually produced (e.g. a shared-storage mount) instead of resolving `<projectRoot>/test-output/keel-results/...`.

No warning is logged when the file is missing — that path is also the first-run legitimate case where no outcomes exist yet. Diagnose with the §7 PowerShell snippet to verify the file is landing where the portal looks.

## 4. Ranking formula

For each `(page, field)` and each candidate locator `t`:

```
ratio(t) = (wins(t) + 1) / (wins(t) + misses(t) + 2)
```

Laplace (+1/+2) smoothing means:
- **First run after store starts** — every locator scores 0.5 → sort is a no-op → identical to pre-MVP output.
- **Unseen locator vs known loser** — unseen (0.5) beats a locator with `wins≪misses`.
- **Unseen locator vs known winner** — the known winner stays on top until unseen one proves itself.

Tiebreak is the incoming stability-tier order (stable sort).

## 5. Backward compatibility

- Projects generated before this MVP still compile: legacy `FirstOf.of(label, bys…)` signature is intact; slot outcomes from those projects log under `page=""`.
- `locator-events.jsonl` (per-run, CI-oriented) is unchanged. The new slot-level data lives in a separate file (`locator-outcomes.jsonl`) that `KeelResults.reset()` does not clear.
- `domain-locator-memory.json`: the new optional `candidates` array per entry is written only when non-empty. Old files (no `candidates` field) load cleanly and recall as single-By until the next successful prove rewrites the entry with fallbacks. No migration script needed.
- First-run codegen with no prior outcomes = identity reorder = diff-free.

## 6. Open follow-ups

- **~~ProvePhase memory enrichment~~** — **DONE in this MVP.** Fallbacks are captured at `remember()` time by reading the proved step's `locatorCandidates()` and persisted to the memory file alongside the primary; `recall()` replays them. No live DOM poke needed on recall — the snapshot taken when the memory was first validated is the fallback source. A longer-term upgrade (fresh snapshot top-K on every recall) is still possible but costs a DOM query per memory hit, which would eat memory's main benefit (fast replay).
- **Portal UI surface.** Expose per-slot wins/misses on the Locator Memory page so the user can see which slots are rotting. Reuses `LocatorMemoryService` + the new store.
- **Compaction / retention.** `locator-outcomes.jsonl` is append-only and unbounded. A daily/weekly compaction job that rewrites it as per-target totals keeps it small. Not needed at MVP scale (a day of runs is <100KB).
- **Validator rule for KEEL-R-FIRSTOF.** Flag any hand-written `FirstOf.of("<label>", ...)` in generated Pages that lacks the pageClass argument. Deferred because codegen is the sole writer today and regenerating a project upgrades every field in one pass — the rule catches nothing until a project ships hand-written Pages that call FirstOf. Add the rule when either (a) the framework starts accepting hand-authored Pages alongside generated ones, or (b) a migration starts touching Pages generated by codegen older than this MVP without re-emitting.
- **Memory row overwrite on slot-1 promotion.** When OR-scorer promotes a non-primary slot ahead of the memory locator across many runs, the memory row itself should be overwritten with the new winner. Not in this MVP — needs a decision rule (how much better, over how many runs).

## 7. How to check it locally

```powershell
# Build (unit tests only)
mvn -pl . -am test -Dtest=LocatorOutcomeStoreTest
```

```powershell
# Peek at what a running test wrote
Get-Content .\test-output\keel-results\locator-outcomes.jsonl | Select-Object -Last 20
```

```powershell
# Clear the store (e.g. to re-baseline after a page redesign)
Remove-Item .\test-output\keel-results\locator-outcomes.jsonl
```
