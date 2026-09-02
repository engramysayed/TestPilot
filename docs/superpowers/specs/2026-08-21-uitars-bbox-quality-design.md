# UI-TARS bbox & assert quality — design

**Date:** 2026-08-21  
**Status:** Approved (product owner: yes)  
**Related:** `docs/superpowers/specs/2026-08-17-vision-grounding-design.md`,  
`docs/superpowers/plans/2026-08-21-delivery-polish-uitars-scorecard.md`

## Problem

Keel defaults vision to **ui-tars**, but `UiTarsVisionProvider` reuses **Qwen** prompts and JSON parsing. Live smoke (`UiTarsLiveSmokeTest`) showed:

| Symptom | Observed |
|---------|----------|
| Grounding | `found=true` with **bbox 1×1 @ (0,0)**, conf **0.5** |
| Description | Whole-page essay instead of “Login button” |
| Assert | Empty body → **UNCERTAIN** (honesty gate OK; still useless) |

Root causes (combined):

1. UI-TARS is action/point-oriented; Qwen-style `{bbox}` JSON is a poor native fit.  
2. Shared parser **coerces** `width/height ≤ 0` to `1×1`, turning “no box” into a fake point.  
3. Assert prompt/path does not force concrete observation/evidence when the model returns empty JSON.

## Goals (success = option C)

On a clear public page (e.g. the-internet Login):

1. **Tight bbox** — center maps via `elementFromPoint` to an interactive control matching the intent (e.g. Login).  
2. **Honest confidence** — when `found=true`, confidence ≥ **0.7**; when not visible, `found=false` / empty candidates (no junk 0.5 + 1×1).  
3. **Solid assert** — on a clear login page, assert returns PASS or FAIL with **non-empty** observation and evidence that pass `VisionAssertionGate` honesty (no placeholders, min length).

## Non-goals

- Fine-tuning or replacing the Ollama model weights.  
- Changing Qwen provider behavior except shared parse helpers that both can call safely.  
- Vision-first on every step (Layer 1 DOM bind remains primary).  
- Meta/Facebook live re-prove.  
- Portal UI changes.

## Locked decisions

| Topic | Decision |
|-------|----------|
| Strategy | **Native adapter + JSON fallback + quality gates** |
| Provider | Changes live in `UiTarsVisionProvider` (+ small shared parse/gate utilities) |
| Qwen | Unchanged prompts; may share stricter bbox validation if non-breaking |
| Contract out | Still `VisionAnalysisResult` / `VisualCandidate` / `BoundingBox` for heal |
| Fail closed | Reject junk before heal uses a hit; never invent a 1×1 from missing size |
| Assert | UiTars-specific assert prompt + **one** empty-body retry; keep `VisionAssertionGate` |

## Architecture

```text
Screenshot + Intent
        │
        ▼
UiTarsVisionProvider
  ├─ UiTars grounding prompt (locate ONE control)
  ├─ Ollama /api/chat
  ├─ UiTarsResponseParser
  │     ├─ try native (point / click / start_box / end_box / …)
  │     └─ else JSON candidates (same schema as today)
  └─ VisionBboxQualityGate.filter(candidates, imageW, imageH)
        │
        ▼
VisionAnalysisResult  →  ViewportSweep / ElementGrounder / heal (unchanged)

Assert path:
  UiTars assert prompt → parse → if empty UNCERTAIN, one nudge retry
  → VisionAssertionGate.honestyCheck (existing)
```

## 1. UiTars grounding prompts

Separate from `VisionPromptTemplate` (keep Qwen on v1 template).

**Must:**

- Task: locate the **single interactive control** named by the step.  
- Output: prefer JSON our stack understands **or** a documented native click/point form.  
- Forbid: page-level captions, “describe the screenshot”, XPath/CSS.  
- Bbox: CSS pixels, top-left origin of the **image**; width/height must be real widget size (not 0, not full screenshot).  
- Confidence: 0.7–0.99 when found; omit junk defaults.

**User message:** reuse structured ACTION/TARGET (from `VisionPromptTemplate.userPrompt`) so intent kind stays clear.

## 2. Native response adapter

`UiTarsResponseParser` (new) accepts, in order:

1. **Canonical JSON** — `{found, candidates:[{description,bbox,confidence}]}`  
2. **Native-ish shapes** (best-effort, site-agnostic), e.g.:
   - `{x,y}` or `{click:[x,y]}` / `point` → bbox as small pad around point **only if** model did not give width/height (pad ≤ ~24px; still must pass quality gate)  
   - `start_box` / `end_box` or `[x1,y1,x2,y2]` (absolute or normalized 0–1 / 0–1000) → convert to `BoundingBox`  
3. Else → `unavailable` / empty found=false

Do **not** coerce missing width/height to 1×1 for JSON candidates that claim a box object with zeros — **drop** that candidate.

## 3. Quality gate (`VisionBboxQualityGate`)

Applied inside UiTars provider **before** returning analysis (and reusable from sweep if needed).

Reject a candidate when any of:

| Rule | Rationale |
|------|-----------|
| `width < 8` or `height < 8` (after native pad) | Live failure mode was 1×1 |
| Area ratio vs screenshot &gt; ~15% **or** height ratio &gt; ~25% | Whole-page / form-card regions (align with existing `ViewportSweep.plausibleWidgetBox`) |
| `found=true` and `confidence < 0.7` | Option C honesty |
| Description length &gt; ~120 chars **and** looks like a page summary (heuristic: many commas / “page” / “containing”) | Essay-as-description |

If all candidates rejected → `found=false`, empty list (heal continues DOM/retry).

## 4. Assert path

- UiTars `ASSERT_SYSTEM_PROMPT` variant: same honesty bans as Qwen (`what is visible` / `why` / angle brackets) **plus** “observation and evidence must be concrete visible UI facts; never leave them blank when status is PASS or FAIL.”  
- Parse via existing `VisionAssertionParser`.  
- If result is UNCERTAIN with blank observation/evidence **and** no parse error → **one** retry with a short nudge user message (“Return the full JSON object with non-empty observation and evidence”).  
- Always run `VisionAssertionGate.honestyCheck` at the prove boundary (already wired).

## 5. Shared parse cleanup (careful)

Today `QwenVisionProvider.parseResponse` turns `w/h ≤ 0` into `1×1`. That hides model failure.

- Extract shared JSON candidate parsing used by both providers **or** only change behavior behind UiTars parser.  
- **Preferred:** UiTars uses new parser; Qwen keeps current coercion until a separate task (avoid regressing Qwen mid-flight).  
- Document that 1×1 coercion is a known Qwen quirk to remove later.

## 6. Proof / tests

| Test | Expectation |
|------|-------------|
| Unit: native point / box arrays → `BoundingBox` | Correct geometry |
| Unit: JSON with w=h=0 | Candidate dropped, not 1×1 |
| Unit: page-essay description + tiny box | Gate rejects |
| Unit: good JSON | Passes gate |
| Unit: empty assert → one retry then still uncertain | Retry invoked once |
| Live: `UiTarsLiveSmokeTest` (skip if no model) | Grounding: `elementFromPoint` hits interactive Login-related control; assert: non-empty observation/evidence and not placeholder PASS |

## 7. Config

No new required flags. Optional later:

- `delivery.vision.uitars.min-confidence` (default 0.7)  
- `delivery.vision.uitars.assert-retry` (default true)

Defaults match option C without portal changes.

## File map (implementation)

| File | Role |
|------|------|
| `UiTarsVisionProvider.java` | UiTars prompts; call parser + gate; assert retry |
| `UiTarsResponseParser.java` (new) | Native + JSON parse |
| `VisionBboxQualityGate.java` (new) | Reject junk candidates |
| `UiTarsPrompt.java` or constants in provider | Grounding + assert system strings |
| Tests under `src/test/java/delivery/vision/` | Unit + strengthen live smoke |
| Qwen | Untouched unless extracting a shared helper without behavior change |

## Risks

| Risk | Mitigation |
|------|------------|
| Ollama UI-TARS build ignores native schema | JSON fallback + gate |
| Pad-around-point still misses small icons | Gate + sweep; DOM Layer 1 still primary |
| Assert still empty after retry | Stay UNCERTAIN; never fake PASS |
| Over-reject good large controls (tables) | Gate thresholds aligned with existing `plausibleWidgetBox` |

## Self-review

- No TBD placeholders.  
- Goals match approved option C.  
- Does not contradict vision grounding design (vision = candidates; DOM verifies).  
- Scope fits one implementation plan.

## Approval

Product owner approved design verbally (**yes**, 2026-08-21). Spec written for review before implementation plan.
