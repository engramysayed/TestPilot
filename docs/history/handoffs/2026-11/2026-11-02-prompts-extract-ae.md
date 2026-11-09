# Handoff - prompts-extract AE (2026-11-02)

**Status:** GREEN
**Specialist:** AE (PRIMARY owner for PromptCatalog + most call-site migrates)
**Tip base at kickoff:** `c93aa6d` → working tip before AE commit: `fc3faba` (guide+pricing + Codegen bootstrap on disk)
**Live tree:** `D:\Work\projects\Keel\keel\TestPilot`
**Backup:** `_backup/prompts-extract-ae-20261102-212528/`
**Author:** rsayed <rsayed@axisapp.com> — **no push**

## PromptCatalog (subsumed Codegen bootstrap)

FQN: `delivery.prompts.PromptCatalog` — **extended in place** (no second API).

| Contract | Detail |
|----------|--------|
| `get(id)` | Load by id (`heal/pick` …) |
| `format(id, Map)` | `{{name}}` placeholder fill (AE) |
| `setOverrideDir(Path)` | Absolute path; Spring/tests; wins over prop/env; `null` clears |
| `overrideDir()` | Optional: setter → prop `keel.prompts.dir` → env `KEEL_PROMPTS_DIR` |
| `PROP_DIR` / `ENV_DIR` | `keel.prompts.dir` / `KEEL_PROMPTS_DIR` |
| Resolve | Override dir (absolute) `.md` then `.txt`, else classpath `prompts/<id>.md|.txt` |
| Cache | Classpath cached; override **re-reads each call** (hot-reload) |
| Fail closed | Missing override file throws (no silent classpath fallback) |

Backend ACK: Spring may `System.setProperty(PROP_DIR, …)` and/or call `setOverrideDir`; AE documents both.

## New / owned prompt ids + files

### AE migrated call sites

| Id | File | Call site(s) |
|----|------|----------------|
| `heal/pick` | `prompts/heal/pick.md` | `tools/cursor-heal/heal.mjs` |
| `heal/ground-rank` | `prompts/heal/ground-rank.md` | heal.mjs |
| `heal/ground-rank-batch` | `prompts/heal/ground-rank-batch.md` | heal.mjs |
| `heal/invent` | `prompts/heal/invent.md` | heal.mjs |
| `heal/solve` | `prompts/heal/solve.md` | heal.mjs |
| `heal/safety` | `prompts/heal/safety.md` | `HealPromptSafety` + heal.mjs fallback |
| `heal/hunt-cursor` | `prompts/heal/hunt-cursor.md` | heal.mjs hunt mode (Cursor wording) |
| `hunt/ollama-system` | `prompts/hunt/ollama-system.md` | `OllamaHuntPlanner` (+ appends `grounding/hunt-planner`) |
| `vision/vision-template` | `prompts/vision/vision-template.md` | `VisionPromptTemplate.systemPrompt` |
| `vision/vision-few-shot` | `prompts/vision/vision-few-shot.md` | `VisionPromptTemplate.fewShotBlock` |
| `vision/ui-tars` | `prompts/vision/ui-tars.md` | `UiTarsPrompt.groundingSystem` |
| `vision/ui-tars-assert` | `prompts/vision/ui-tars-assert.md` | `UiTarsPrompt.assertSystem` |
| `vision/ui-tars-assert-retry` | `prompts/vision/ui-tars-assert-retry.md` | `UiTarsPrompt.assertRetryNudge` |
| `vision/qwen` | `prompts/vision/qwen.md` | `QwenVisionProvider` assert system |
| `vision/qwen-assert-retry` | `prompts/vision/qwen-assert-retry.md` | Qwen assert retry nudge |
| `vision/design-compare` | `prompts/vision/design-compare.md` | `DesignCompareGate` |
| `grounding/locator-policy` | `prompts/grounding/locator-policy.md` | `LocatorPolicy.promptRules` |
| `grounding/login-prelude` | `prompts/grounding/login-prelude.md` | `LocatorPolicy.loginPreludeRules` |
| `grounding/vision-heal` | `prompts/grounding/vision-heal.md` | `LocatorPolicy.visionHealRules` |
| `grounding/hunt-planner` | `prompts/grounding/hunt-planner.md` | `LocatorPolicy.huntPlannerRules` |
| `grounding/free-invent` | `prompts/grounding/free-invent.md` | `LocatorPolicy.freeInventRules` / FreeInventHealer AgentRouter |
| `authoring/heal-pick-payload` | `prompts/authoring/heal-pick-payload.md` | `HealPickPayload.ollamaSystem` |
| `authoring/authoring-service` | `prompts/authoring/authoring-service.md` | `AuthoringService` ambiguous shortlist system |
| `authoring/authoring-review` | `prompts/authoring/authoring-review.md` | **file only** — Backend owns call-site |
| `authoring/authoring-review-cursor` | `prompts/authoring/authoring-review-cursor.md` | heal.mjs authoring-review mode |

### Codegen (already on disk; included so catalog tree is complete)

| Id | File | Call site |
|----|------|-----------|
| `revise/final-revise` | `prompts/revise/final-revise.md` | `FinalRevisePhase` (Codegen migrated) |
| `codegen/test-method-naming` | `prompts/codegen/test-method-naming.md` | `TestMethodNaming` (Codegen migrated) |

## Override how-to

1. Set absolute folder mirroring classpath relative paths, e.g. `<dir>/heal/pick.md`.
2. Java: system property `keel.prompts.dir` **or** env `KEEL_PROMPTS_DIR` **or** `PromptCatalog.setOverrideDir(Path)` (setter wins).
3. Node heal.mjs: env `KEEL_PROMPTS_DIR` first, else `src/main/resources/prompts` relative to repo / cwd.
4. Unset → classpath (Java) / repo resources (Node).

## heal.mjs contract

- Loads templates via `loadPrompt` / `formatPrompt` (`{{placeholders}}`).
- Modes: pick, groundRank, groundRankBatch, invent, solve, authoring-review → `authoring/authoring-review-cursor`, hunt → `heal/hunt-cursor`.
- Safety: prefers `req.safetyBlock` from Java; else builds from `heal/safety` (same template as `HealPromptSafety`).
- Literal `${TARGET_*}` tokens in hunt prompt text preserved for the model.

## Leftovers

| Owner | Item |
|-------|------|
| **Backend** | Spring bind `keel.prompts.dir`; migrate `AuthoringReviewService` → `PromptCatalog.get("authoring/authoring-review")` |
| **Codegen** | Call sites already migrated (`FinalRevisePhase`, `TestMethodNaming`) — no further AE action |
| **Skip** | DomDiffAssertSuggestor, NlLocatorCache; `static/prompts/` Guide paste assets untouched |

## Gate

```text
mvn -B -pl keel-portal -am -Dsurefire.failIfNoSpecifiedTests=false \
  "-Dtest=PromptCatalogTest,HealPromptSafetyTest,UiTarsPromptParityTest,HealPickPayloadTest,FinalRevisePhaseTest,TestMethodNamingTest" test
-> Tests run: 23, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS
(Africa/Cairo ~21:27+)
```

No full `package` / portal jar refresh (portal LIVE on :8080). Maestro owns refresh after overall GREEN.

## Constraints honored

- Extract only — no prompt wording / model behavior changes intended
- No secret prompts under `static/prompts`
- No push / no Co-authored-by Cursor
- Parallel guide+pricing portal files not fought

## Next

- Backend: property bind + AuthoringReviewService migrate + handoff
- Maestro: portal jar refresh after prompts-extract GREEN
