# Vision role split + Qwen assert prompt — design

**Date:** 2026-08-21  
**Status:** Approved (product: approach A, models = caller-configured / skip if missing)  
**Related:** `docs/superpowers/specs/2026-08-21-uitars-bbox-quality-design.md`

## Problem

Today `delivery.vision.provider` selects **one** LLM for both grounding (`analyze`) and visual asserts (`assertVisual`). UI-TARS is strong at click/point grounding and weak at JSON assert judging; Qwen is a better assert fit. `createAssertionProvider()` already exists but still calls the same `createLlmProvider()`.

Qwen’s assert prompt already bans some placeholders but still **teaches** `confidence:0.0` and `<angle-bracket>` schema examples, which models echo.

## Goals

1. **Split providers:** grounding vs assert configurable independently.  
2. **Defaults:** grounding → `uitars` / `ui-tars`; assert → `qwen` / `qwen2.5vl:3b` (overridable).  
3. **Legacy:** if only `delivery.vision.provider` / `.model` is set, both paths fall back to that (no surprise break).  
4. **Harden Qwen assert prompt:** no `0.0` / angle-bracket examples; require concrete observation/evidence and honest confidence.  
5. **Live proof:** smoke that uses UI-TARS for Login ground + Qwen for assert when both are available; **skip cleanly** if a required model/base URL is missing (caller configures models — option C).

## Non-goals

- Fine-tuning (Qwen or UI-TARS).  
- Porting UI-TARS native `Action: click(start_box=…)` prompts onto Qwen grounding.  
- DOM post-click validation (follow-up plan).  
- Facebook / Meta live re-prove.  
- Changing UI-TARS grounding path beyond using the split config.

## Locked decisions

| Topic | Decision |
|-------|----------|
| Approach | **A** — separate grounding vs assert config; harden Qwen assert; live scorecard |
| Models | **C** — defaults in code; operators override via properties; tests **SkipException** if Ollama/model missing |
| Qwen grounding prompts | Unchanged this pass (keep `VisionPromptTemplate` JSON bbox) |
| Honesty gate | Keep `VisionAssertionGate`; Qwen assert must be able to PASS it on a clear login page |

## Architecture

```text
Heal / ViewportSweep / analyze
        → VisionGroundingConfig.createProvider()
        → grounding.provider (default uitars)

Prove / VisionAssertionGate.evaluate
        → VisionGroundingConfig.createAssertionProvider()
        → assert.provider (default qwen)

Legacy delivery.vision.provider / .model
        → fallback when grounding.* / assert.* unset
```

### Config keys

| Key | Role | Default |
|-----|------|---------|
| `delivery.vision.grounding.provider` | analyze/heal | `uitars` (else legacy `delivery.vision.provider`) |
| `delivery.vision.grounding.model` | grounding model | `ui-tars` (else legacy model) |
| `delivery.vision.assert.provider` | assertVisual | `qwen` (else legacy provider) |
| `delivery.vision.assert.model` | assert model | `qwen2.5vl:3b` (else legacy model if provider matches, else qwen default) |
| `delivery.vision.provider` / `.model` | legacy fallback | unchanged |

`providerId()` / `model()` remain as **legacy getters** (grounding-oriented) for logs that already call them; assertion evidence should log assert provider/model when available.

### Qwen assert prompt changes

- Schema example confidence: **`0.9`**, not `0.0`.  
- Observation/evidence descriptions: plain English requirements, **no** `<…>` placeholders in the schema line.  
- Keep existing bans on Figma, locators, “what is visible” / “why”.  
- Optional: one empty-body retry on Qwen assert (same idea as UI-TARS) — **include** if cheap; otherwise honesty gate alone.

### Tests / live

- Unit: config resolves split vs legacy; assertion path uses assert provider.  
- Unit: Qwen assert prompt does not contain `"confidence":0.0` or angle-bracket schema slots.  
- Live (skip if missing):  
  - UI-TARS analyze → Login control (`elementFromPoint`).  
  - Qwen assert → non-placeholder observation/evidence; gated status not placeholder-PASS.  
- Notes file under `docs/superpowers/plans/` (best-effort).

## Success criteria

1. With split defaults, heal uses UI-TARS and asserts use Qwen without setting a single shared provider.  
2. Legacy single-provider mode still works.  
3. Qwen assert prompt unit tests green; live smoke skips or passes under option C.  
4. No change to UI-TARS native grounding behavior beyond config wiring.
