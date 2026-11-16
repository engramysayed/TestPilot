# Vision grounding (Layer 1.5 / heal 2.5) — design

**Date:** 2026-08-17  
**Status:** Approved  
**Scope:** Pluggable vision as a **candidate generator** folded into existing TestPilot bind + heal. Not a Midscene rewrite. Not coordinate-click automation.

## Problem

Layer 1 (slim HTML → `DomCandidateExtractor` → `StepIntentBinder`) is strong when the DOM has labels, ids, and distinctive text. It is weak when the control is icon-only, CSS-painted, a soup of `<div>`s, or visually obvious but semantically poor.

Today’s “vision” in heal is screenshot + shortlist **candidateId pick**. It does not return bounding boxes or map pixels to a live node via `elementFromPoint`.

GPT-style Midscene/UI-TARS stacks assume an old TestPilot (text → Gemini JSON). We already have DOM extract, scoring, `LocatorValidator`, Selenium prove, and heal cascade. Vision must plug into that, not replace it.

## Goals

- One **master flag** turns all vision grounding on or off.
- When on, vision may help **CLICK, TYPE, SELECT, VERIFY** — but only when Layer 1 is weak or heal needs a spatial hint.
- Vision proposes **where**; existing DOM table + live `elementFromPoint` decide **what**; Selenium **executes**; LLM/heal **reasons** when those disagree.
- Off-screen targets: DOM + `scrollIntoView` first; viewport **sweep** only while vision is actually running.
- Provider-pluggable (Qwen-VL / UI-TARS / later). No product dependency on Gemini.

## Non-goals

- Vision-first on every step.
- Default `Actions.moveByOffset` clicks.
- New parallel packages that reimplement `DomCandidateExtractor`, `IntentKind`, `LocatorValidator`, or the Orchestrator.
- Application locator memory MVP (IR / locator-map already persist proven locators).
- Visual-only assertions as the honesty gate (DOM/body text remain primary; vision may corroborate later).

## Locked decisions

| Topic | Decision |
|-------|----------|
| Placement | Combine **A + B**: same engine, two call sites |
| Master flag | `delivery.vision.grounding.enabled` (default **false**) |
| Action kinds | All of CLICK, TYPE, SELECT, VERIFY **eligible** |
| When to call VLM | Layer 1 **weak or failed**, or heal widen — never when Layer 1 has a clear winner |
| Authority | Vision = candidates only. DOM verifies. Selenium executes. |
| Coordinates | `elementFromPoint(center)` → node → existing locator strategies. Offset-click is last-resort fallback, feature-gated, logged. |
| Scroll | In-DOM off-screen → bind + existing `scrollIntoView`. Vision sweep only if vision was invoked. |
| Sweep cap | **8** viewport pages (0.8 × `innerHeight` each). Configurable later; 8 is the lock. |
| Heal | Replace/extend vision-widen: bbox → ground to `DomCandidate` / candidateId, then same heal ranks |
| LLM | Unchanged role: AMBIGUOUS / heal pick among ids; invent still last hope |

---

## Architecture

```
Excel intent
    → Layer 1 DOM bind (unchanged default)
    → clear winner? → LocatorValidator → Selenium (scrollIntoView on act)
    → weak/fail AND flag on?
         → VisionGroundingProvider (screenshot + step text)
         → VisualCandidate[] (bbox + confidence + description)
         → optional viewport sweep (cap 8)
         → elementFromPoint(center) [+ iframe document]
         → match/add DomCandidate
         → same binder + LocatorValidator → Selenium
    → still fail?
         → HealCascade (failed locators, named retry, Ollama/Cursor)
         → if flag on: vision pass A (same provider) to ground/widen
         → invent last hope
```

**Layer 1.5** = weak first bind. **Layer 2.5** = heal spatial widen. Shared: provider, bbox DTO, coordinate mapper, matcher, logging.

Existing pieces to **reuse**: `PageSnapshot` / screenshot bytes, `HtmlSlimmer`, `DomCandidateExtractor`, `StepIntentBinder`, `LocatorValidator`, `ElementsHandler.scrollToElement`, `FramesHandler` / `ContextSearch`, `HealCascade`, `LocalLlmClient` (or a dedicated HTTP provider — not Gemini).

---

## Feature flags

| Key | Default | Meaning |
|-----|---------|---------|
| `delivery.vision.grounding.enabled` | `false` | Master: Layer 1.5 + heal bbox grounding. Off = current behavior. |
| `delivery.vision.provider` | `qwen` | `qwen` \| `uitars` \| (later `openai`). Implementation names, not site logic. |
| `delivery.vision.model` | (provider default) | Model id for the chosen provider (e.g. local Ollama tag). |
| `delivery.heal.vision-widen.enabled` | `true` | Existing shortlist widen. When master flag is on, widen **uses bbox grounding** instead of screenshot-only candidateId pick. When master is off, widen stays as today (screenshot pick from HTML shortlist). |

v1: if master is false, do not call `VisionGroundingProvider` at all.

---

## When Layer 1 is “weak” (Layer 1.5 trigger)

Invoke vision **once** per intent attempt when the flag is on and **any** of:

- No scored candidates, or best score **&lt; 2** (existing weak-match reject).
- Distinctive-token / named-action miss (existing CLICK honesty reject).
- `AMBIGUOUS:` near-tie of **different** controls (fingerprint).
- Empty interactive table after extract (poor DOM).

**Do not** invoke when Layer 1 produced a validated `ProvenStep` with a clear winner (named-action resolved, unique id/name/data-test, exact unique label, score gap honest).

Eligible intents: `CLICK`, `TYPE_FIELD` / type-user-pass, `select`, `ASSERT_VISIBLE` / textContains VERIFY. Skip pure `NAVIGATE` / browser actions (no target widget).

---

## Provider interface

```text
VisionGroundingProvider
  VisionAnalysisResult analyze(byte[] screenshotPng, StepIntentBinder.IntentLine intent)
```

`VisionAnalysisResult`: `found`, `List<VisualCandidate>`, optional error/unavailable.

`VisualCandidate`: `description`, `BoundingBox {x,y,width,height}` in **screenshot CSS pixels** (document the coordinate space: viewport CSS pixels, origin top-left of the captured view), `confidence` 0..1.

Providers: `QwenVisionProvider` (first concrete, local), `UiTarsVisionProvider` (stub or second). Fail closed: empty candidates + log; never throw into prove without catch → treat as vision unavailable → continue DOM/heal.

**Output is structured JSON only.** No MCP/chat prose (same rule as cursor-heal).

Do **not** send raw full HTML to the VLM. Optional tiny hint: intent text + maybe top-N candidate **labels** (not locators) if the provider benefits; default v1 = screenshot + intent text only.

---

## Coordinate → DOM

1. `centerX = x + width/2`, `centerY = y + height/2` in the same space as the screenshot.
2. Account for devicePixelRatio if screenshot is bitmap-scaled vs CSS (`innerWidth` vs image width).
3. `document.elementFromPoint(cssX, cssY)` in the current document; if iframe, try default content then listed frames (`ContextSearch` / `FramesHandler`).
4. Walk up from the hit node to the nearest interactive (`button, a, input, select, textarea, [role=button|link|textbox|combobox|checkbox|radio]`).
5. Map that element onto the **existing** `DomCandidate` table (id/fingerprint/strategy+value). If the node is live but missing from the table, **add one candidate** using the same extractor rules (`id` → `data-test*` → `name` → attr CSS/xpath) then `LocatorValidator`.
6. Re-run bind preferring that candidateId (existing `bindPreferring` / `stepsPreferringCandidate`).

**Forbidden as default:** `moveByOffset`. Allowed only if grounding produced a bbox, `elementFromPoint` returned null **and** no DOM candidate, flag on, and a nested fallback flag (v1: **omit offset-click**; leave TODO for a later flag). v1 failure = not found → heal.

---

## Confidence (configurable, not one magic formula)

Keep three numbers in logs and `StepAssessment`-like fields on the proven/heal rationale (no need for a new user-facing type in v1 if IR already has rationale + healTier):

- Vision confidence (provider)
- DOM match confidence (token/fingerprint/spatial overlap of bbox vs `getBoundingClientRect`)
- Execution / locator validity (validator + unique find + displayed/enabled)

v1 **gate**: execute from vision path only if locator validates to exactly one element and liveness probe says displayed+enabled (existing `CandidateLiveness`). Do not ship a weighted formula as architecture; a simple `min(vision, 1.0 if validated else 0)` is enough. Formula remains swappable.

Disabled/not displayed after grounding → `NOT_EXECUTABLE` / reject (do not click a disabled Checkout).

---

## Scroll and long pages

**Case 1 — In HTML, below the fold**  
Layer 1 binds. Execute uses existing `ElementsHandler.scrollToElement` (`scrollIntoView`). Vision is **not** required.

**Case 2 — Vision already invoked (weak DOM or heal)** and target not in current screenshot  

Viewport sweep:

1. Capture screenshot of current viewport (same WebDriver session).
2. `analyze(...)`.
3. If no usable candidate: `window.scrollBy(0, Math.floor(innerHeight * 0.8))` (or the primary scrollable overflow container if `document.scrollingElement` is not enough — v1: `scrollingElement` / `documentElement`).
4. Repeat until hit or **8** steps.
5. On hit: ground with `elementFromPoint`, then execute (which may `scrollIntoView` again).

Do not sweep when Layer 1 already bound. Do not infinite-scroll. Iframes: after switching into a frame, sweep that frame’s document independently (cap still 8 per frame attempt, or 8 total per intent — **lock: 8 total per intent** to bound time).

---

## Heal integration (pass A)

When `delivery.vision.grounding.enabled=true`:

- If distinctive shortlist is empty / vision-widen would fire: run bbox provider on current screenshot (optional short sweep if first shot empty).
- Grounded node → candidateId in the **existing** table (or add validated candidate).
- Then Ollama/Cursor pick among ids **including** the grounded one; still **no invented locators** on the shortlist path.
- Invent remains last hope.

When master flag is false: keep today’s screenshot + `visionHealRules` candidateId pick.

Heal tier: grounded bbox success may still label `vision` (already in `healTierRank`).

---

## Logging (every vision-touched step)

Log: raw Excel intent, action kind, vision candidates (bbox + confidence), DOM matched id/attrs/visible/enabled, grounding hit/miss, locator strategy/value/validation, final READY/AMBIGUOUS/BLOCKED, execute SUCCESS/FAILURE, sweep index if any. No API keys. No full screenshot bytes in logs (path only).

---

## Testing (v1 minimum)

Unit/integration without a live VLM: fake `VisionGroundingProvider` returning known bboxes.

- Fake bbox over a fixture button → `elementFromPoint` mock or small HTML page → `By.id`.
- Weak Layer 1 + flag on → provider called; strong Layer 1 → provider **not** called.
- Flag off → provider never called.
- Disabled button grounded → not executed.
- Sweep: first two screenshots empty, third hits (fake provider keyed by scroll Y).
- Heal: empty distinctive pool + flag on → grounded candidateId used.
- Provider throws / empty → fall through to DOM/heal, no crash.

Live VLM tests optional behind env; not required for merge.

---

## Implementation order (incremental)

1. DTOs + `VisionGroundingProvider` + no-op/fake + master flag.  
2. Coordinate mapper + `elementFromPoint` JS + candidate match/add + `LocatorValidator`.  
3. Layer 1.5 hook in `authorIntent` / ProvePhase when weak.  
4. Viewport sweep (cap 8).  
5. One real provider (Qwen local).  
6. HealCascade bbox widen when master on.  
7. Structured logs.  
8. UI-TARS provider stub or second impl.

Do not start at Orchestrator rewrite or memory store.

## Spec self-review

- [x] No TBD for implementers on flags, triggers, cap, authority split.  
- [x] Offset-click explicitly out of v1.  
- [x] Reuse Layer 1 / heal; no duplicate extractor.  
- [x] Scroll cases split (DOM vs vision sweep).  
- [x] Gemini not in provider list.
