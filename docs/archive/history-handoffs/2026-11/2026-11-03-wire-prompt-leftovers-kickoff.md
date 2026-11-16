# Kickoff — Slice D: Wire prompt leftovers (2026-11-03)

**Status:** KICKOFF ONLY — do not implement until Maestro assigns; **depends on Slice C** known-id list / inventory semantics (or at least the same 27 classpath ids + VERSION).  
**Base tip:** `cc502f35006e4dc078345bf7df6d5804bd3e3408` (includes `780b112` Slice B)  
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot` (Ramy)  
**Constraints:** **no push / force-push / merge**; **no portal restart**; **serialize Maven** (avoid package/full test fight); do **not** edit `application.properties` or secrets. Extract/wire only — **no prompt wording rewrites** unless file already matches inline (prefer file text as SoT).

---

## Goal

Re-grep leftover **inline** AI system/assert prompts and alternate loaders **not** yet wired through `PromptCatalog` + `src/main/resources/prompts/`. Migrate call sites; keep `tools/cursor-heal/heal.mjs` paths aligned with catalog ids; leave `src/main/resources/static/prompts/` **public-only**. Report what moved vs intentionally left.

After C: boot inventory should list any **new** ids added here once they land on classpath.

---

## Owners

| Role | Scope |
|------|--------|
| **Backend (PRIMARY)** | Java call-site wires + new prompt files under `resources/prompts/` for portal/vision/hunt/planner leftovers; tests; handoff |
| **AE** | `heal.mjs` path alignment if any new heal/* ids; confirm invent/user-scaffold decisions; parity with Java heal safety |
| **Codegen** | Only if `LLMPlanner` / planner path is owned there — coordinate; default Backend+AE unless Maestro splits |
| **FE** | None |

Do **not** self-assign; Maestro assigns after C (or parallel only if ids frozen).

---

## Depends on Slice C

- Use C’s known-id enumeration (scan or golden 27) as the “already catalogued” set.
- New files added in D must appear in that set after extract.
- Prefer landing D after C’s inventory runner exists so incomplete override fail-fast covers new ids — if Maestro parallelizes, D must still add files under `resources/prompts/` with same id layout.

Kickoff C: `docs/history/handoffs/2026-11/2026-11-03-boot-prompt-inventory-kickoff.md`

---

## Grep seed list (2026-11-03 live tree)

### A) File exists on classpath but call site still inline (wire-only)

| Id / file | Call site | Finding |
|-----------|-----------|---------|
| `vision/design-compare` → `prompts/vision/design-compare.md` | `delivery.vision.DesignCompareGate` | `SYSTEM_PROMPT` still concatenated inline; **unused** `import PromptCatalog` — wire `PromptCatalog.get("vision/design-compare")` (and optional short `USER_PROMPT` stay or file) |
| `vision/qwen` → `prompts/vision/qwen.md` | `delivery.vision.QwenVisionProvider` | `ASSERT_SYSTEM_PROMPT` still inline; grounding already uses `VisionPromptTemplate` → catalog; assert retry uses `vision/qwen-assert-retry` |
| `vision/ui-tars-assert-retry` → `prompts/vision/ui-tars-assert-retry.md` | `delivery.vision.UiTarsPrompt.assertRetryNudge()` | Method returns **inline** string identical to md; should `PromptCatalog.get("vision/ui-tars-assert-retry")` |

### B) Inline AI prompts with **no** catalog file yet (extract + wire)

| Candidate id | Call site | Notes |
|--------------|-----------|-------|
| `hunt/bug-triage` (proposed) | `delivery.hunt.HuntBugTriage.triagePrompt` | System/rules preamble `"You are triaging Bug Hunter findings…"` + JSON schema; keep dynamic Candidates/Journal/Coverage as user/format vars |
| `planner/llm-planner` (proposed) | `llmLayer.LLMPlanner.buildPrompt` | Large ROLE/OBJECTIVE/OUTPUT text block (~lines 64–170); extract system template; keep `%d` maxSteps via `format` / `{{maxSteps}}` |

### C) Dynamic user scaffolds — review; default **leave** unless Backend+AE agree extract

| Site | Why leave / optional |
|------|----------------------|
| `delivery.heal.FreeInventHealer.inventPrompt` | Per-call Excel intent / failure / HTML / screenshot flags — user message scaffolding, not fixed system SoT; system already `LocatorPolicy.freeInventRules()` → `grounding/free-invent` |
| `AuthoringService` user `.formatted(...)` batches | Dynamic shortlist tables — not system SoT |
| `UiTarsPrompt.groundingUser` | Short dynamic “User Instruction: …” line — OK inline |
| `DesignCompareGate` one-line user compare instruction | OK inline if system wired |

### D) Intentionally leave (public / non-LLM / already migrated)

| Path | Reason |
|------|--------|
| `src/main/resources/static/prompts/*` | Guide / Generate paste assets — **public-only**; `TcGenerateService.loadPrompt("static/prompts/…")` alternate classpath loader is intentional |
| `TcGenerateService.loadPrompt` | Do **not** route through PromptCatalog; must not pull secret system prompts into static |
| DomDiffAssertSuggestor / NlLocatorCache | Prior extract kickoff: **not** LLM prompts — skip |
| `tools/cursor-heal/heal.mjs` `loadPrompt` / `KEEL_PROMPTS_DIR` | Already catalog-aligned for heal/authoring-review-cursor/hunt-cursor; touch only if new heal ids or path drift |
| Call sites already on `PromptCatalog.get/format` | LocatorPolicy, HealPromptSafety, AuthoringReviewService, AuthoringService system, HealPickPayload, OllamaHuntPlanner, VisionPromptTemplate, UiTars assert/grounding systems, FinalRevisePhase, TestMethodNaming, etc. |

### E) heal.mjs alignment checklist (AE)

- Roots: `KEEL_PROMPTS_DIR` then `src/main/resources/prompts` (repo-relative / cwd).
- Ids in use: `heal/pick`, `heal/ground-rank`, `heal/ground-rank-batch`, `heal/invent`, `heal/solve`, `heal/safety`, `heal/hunt-cursor`, `authoring/authoring-review-cursor`.
- After D: if any new Node-consumed id appears, add `formatPrompt`/`loadPrompt` usage and document in AE handoff; keep relative paths mirrored under override dir.

---

## Acceptance criteria

1. Grep seed items in **A** wired to existing PromptCatalog ids (no duplicate inline SoT).
2. Items in **B** extracted verbatim into `resources/prompts/` + wired; new ids named in specialist handoff.
3. **C/D** explicitly listed in handoff as left intentional (with one-line reason).
4. `static/prompts` unchanged in purpose (no secret system prompts added).
5. heal.mjs still resolves existing ids; any new shared ids documented.
6. Focused tests GREEN (vision/hunt/planner tests touched + PromptCatalog smoke). Serialize Maven.
7. Handoff: `docs/handoff/2026-11-03-wire-prompt-leftovers.md` — moved vs left table, tip SHA.
8. **No push.** No portal restart unless Maestro schedules post-GREEN refresh.
9. Slice C inventory (when present) still passes; new ids included in classpath scan.

---

## Files likely touched

| Path | Change |
|------|--------|
| `DesignCompareGate.java` | Wire catalog; drop dead inline / unused-import cleanup |
| `QwenVisionProvider.java` | Wire assert system to `vision/qwen` |
| `UiTarsPrompt.java` | Wire assert retry nudge |
| `HuntBugTriage.java` + `prompts/hunt/bug-triage.md` | Extract + format |
| `LLMPlanner.java` + `prompts/planner/llm-planner.md` | Extract + format |
| `tools/cursor-heal/heal.mjs` | Only if AE path/id updates needed |
| Tests under `src/test/java/delivery/vision`, `…/hunt`, prompts | Parity / load tests |
| Specialist handoff under `docs/handoff/` | Required |

---

## Test plan

```text
mvn -B -pl keel-portal -am -Dsurefire.failIfNoSpecifiedTests=false `
  "-Dtest=PromptCatalogTest,UiTarsPromptParityTest,DesignCompareGate*,Qwen*,HuntBugTriage*,PromptsBootInventory*" test
```

(Adjust class names to what exists after wire; do not run full `package` while serialize-Maven holds.)

Manual grep gate after:

```text
# Expect no large SYSTEM_PROMPT leftovers in DesignCompareGate / Qwen assert / UiTars assertRetry
Select-String -Path src\main\java\delivery\vision\*.java -Pattern "SYSTEM_PROMPT\s*="
```

---

## Out of scope

- Boot inventory implementation (Slice C).
- Rewriting model behavior / prompt prose (except trivial whitespace to match file).
- FE / Owner UI changes.
- Push / secrets / `application.properties`.
- DomDiff / NlLocatorCache.

---

## Dirty-tree note

Same as Slice C: ignore local WIP (`application.properties`, `admin-prompts.html`, `BOOT-INF/`, backups). Do not commit foreign files.

---

## Next

Maestro: assign Backend (+ AE for heal.mjs) after C GREEN or with frozen id list; review moved-vs-left table before closing D.