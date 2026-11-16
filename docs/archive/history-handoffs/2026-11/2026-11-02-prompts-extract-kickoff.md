# Kickoff — Extract AI prompts to editable files (2026-11-02)

**Tip base:** c93aa6d  
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot`  
**Inventory source:** Leader complete locate (static/prompts + inline Java/Node). DomDiffAssertSuggestor / NlLocatorCache = **not** LLM prompts — skip.  
**Constraints:** no push/merge/force-push without user OK; Keel ≠ Sonic; serialize Maven; handoffs under `docs/handoff/`. Parallel OK with guide+pricing (credits-only).

## Goals
1. Property **`keel.prompts.dir`** — absolute override folder the user can point at for editable prompt files.
2. Classpath fallback **`src/main/resources/prompts/`** (NOT under `static/` — secret/system prompts must not be world-readable).
3. Shared **`PromptCatalog`** loader: resolve `dir/id` then classpath; cache; optional hot-reload when dir override is set.
4. One file per intent (`.md` or `.txt`); migrate call sites to load by id.
5. Keep **`src/main/resources/static/prompts/`** only for **user-facing Guide / Generate paste assets** (already there). Do not move secret system prompts into static.

## Existing (leave / reference)
```
src/main/resources/static/prompts/
  keel-tc-generate-from-stories-to-csv.txt
  keel-tc-generate-from-stories-to-json.txt
  keel-tc-rewrite-to-csv.txt
  keel-testing-techniques.txt
  keel-capability-manifest.txt
  + CSV templates
```
Guide public paste may keep reading these. New system prompts go under non-static `resources/prompts/`.

## Target layout (classpath + override mirror)
```
src/main/resources/prompts/
  heal/
    pick.md
    ground-rank.md
    invent.md
    solve.md
    safety.md
  hunt/
    ollama-system.md
  vision/
    vision-template.md
    ui-tars.md
    qwen.md
    design-compare.md
  grounding/
    locator-policy.md
  authoring/
    authoring-service.md
    authoring-review.md
    heal-pick-payload.md
  revise/
    final-revise.md
  codegen/
    test-method-naming.md
```
Ids = relative path without extension (e.g. `heal/pick`). Override folder mirrors the same relative paths.

## Migrate order
1. **heal.mjs ↔ Java duplicates** (`tools/cursor-heal/heal.mjs` + `FreeInventHealer` / `HealPromptSafety` / related heal Java) — single source of truth in files; both Node and Java load same ids where possible.
2. **`OllamaHuntPlanner` SYSTEM**
3. **Vision** — `VisionPromptTemplate`, `UiTarsPrompt`, `QwenVisionProvider`, `DesignCompareGate`
4. **`LocatorPolicy`**
5. **Rest** — `AuthoringService`, `HealPickPayload`, `AuthoringReviewService`, `FinalRevisePhase`, `TestMethodNaming`

## Assignments

### AE (primary — most call sites + catalog core)
- Add package `delivery.prompts.PromptCatalog` (or agreed name) usable from Java modules in this tree.
- Create `src/main/resources/prompts/` tree; extract text from inline strings **verbatim** (no prompt rewriting).
- Wire heal Java + document how `heal.mjs` reads override/classpath (env `KEEL_PROMPTS_DIR` mirroring `keel.prompts.dir`, or file path passed from portal — pick one and document in handoff).
- Migrate steps 1–4 above.
- Handoff: `docs/history/handoffs/2026-11/2026-11-02-prompts-extract-ae.md`

### Backend
- Spring property `keel.prompts.dir` (application.yml/properties + optional env) bound into PromptCatalog.
- Migrate `AuthoringReviewService` (portal).
- Confirm Generate/Guide still use **static/prompts** for public assets only; no secret prompts added under static.
- Coordinate with AE on bean/wiring if catalog is Spring-managed vs static helper.
- Handoff: `docs/history/handoffs/2026-11/2026-11-02-prompts-extract-backend.md`

### Codegen
- Migrate `FinalRevisePhase`, `TestMethodNaming` to PromptCatalog ids under `revise/` and `codegen/`.
- Handoff: `docs/history/handoffs/2026-11/2026-11-02-prompts-extract-codegen.md`

## Definition of done
- Override dir works when set; unset → classpath files.
- No secret system prompts only under `static/prompts`.
- Call sites above load via catalog (no large inline system strings left for migrated intents).
- Tests still compile/pass for touched modules (serialize Maven; don’t overlap portal rebuild with QA reactors).
- Owner handoffs list every new prompt id + file path.
- Maestro portal jar refresh after GREEN; report Leader. **No push.**

## Out of scope
- Changing prompt wording / model behavior (extract only).
- DomDiffAssertSuggestor / NlLocatorCache.
- Dollar pricing (separate credits-only guide+pricing wave).
