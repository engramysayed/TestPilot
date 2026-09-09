# Bug Hunter — Phase 3: two-pass DOM neighborhoods (deferred)

> **Gate:** Implement **only if** live hunts (AxisPay or similar SPA) still blow planner context or produce weak actions **after** Phase 1–2 map + slim auto **and** after Phase 1–2 gaps that affect signal quality.  
> Until then, leave this plan untouched.

**Goal:** Shrink per-cycle LLM context by splitting planning into (1) target selection from the page map + screenshot + journal, then (2) a second call with **only HTML neighborhoods** around chosen controls — not the full slim DOM.

**Status:** Draft — deferred  
**Parent spec:** `docs/superpowers/specs/2026-09-10-bug-hunter-quality-design.md` § Phase 3  
**Depends on:** Shipped quality plan + preferably gaps plan P0 network/oracle fixes  
**Gaps plan:** `docs/superpowers/plans/2026-09-10-bug-hunter-phase12-gaps.md`

## Global Constraints

- No Claude / Anthropic models
- Same action allowlist and grounding rules as Phase 1–2
- Prefer **one screenshot at cycle start** still (reuse for both passes)
- Cap total planner latency (two LLM calls); fail closed to current single-pass map path if pass-1 fails
- Config flag: `delivery.hunt.dom-mode=twopass` or `delivery.hunt.twopass-dom=true` (default **off**)

---

## When to build (evidence checklist)

Start Phase 3 only when ≥1 is true on a real hunt:

1. Auto mode attaches slim often (thin maps) and slim is still huge / model timeouts.
2. Rich maps (100 controls) + brief + journal still truncate useful signal or confuse the model.
3. Actions repeatedly miss nearby context (e.g. need sibling text not in map labels).

If none apply after gaps work: **do not build** Phase 3.

---

## Design (locked intent)

### Pass 1 — Select

**Inputs:** brief, caps, strategy, journal, coverage, **page map only** (no slim), network, screenshot.  
**Output JSON (additive keys, still one object):**

```json
{
  "decision": "continue|finish",
  "rationale": "...",
  "focus": [
    {"locatorStrategy": "id", "locatorValue": "submit", "reason": "primary CTA"}
  ],
  "actions": [],
  "bugs": [],
  "scenarios": []
}
```

- `focus[]` max **5** (align with action cap).
- If `decision=finish`, skip Pass 2.
- If `focus` empty and continue → fall back to single-pass behavior (map ± slim as today).

### Pass 2 — Act

**Inputs:** same memory sections + **neighborhood HTML only** for each focus locator (N chars of outerHTML around match, parent form, or dialog — cap e.g. 8k total).  
**Output:** normal `actions[]` / bugs / scenarios (grounded against neighborhood ∪ map).

### Neighborhood extractor

New `HuntDomNeighborhoods.extract(slimHtml, focusList, maxTotalChars)`:

- Find element by strategy/value in slim HTML (Jsoup / existing presence helpers).
- Include: element outerHTML + nearest `form` ancestor or `role=dialog` ancestor if present.
- Deduplicate overlapping nodes.
- Truncate lowest-priority focus first if over budget.

### Wiring

- `OllamaHuntPlanner` / Cursor path: if twopass enabled, call Pass 1 then Pass 2; write evidence:
  - `cycles/cycle-NN/planner-pass1-prompt.txt` / `planner-pass1-response.txt`
  - `cycles/cycle-NN/planner-pass2-prompt.txt` / `planner-pass2-response.txt`
  - `cycles/cycle-NN/neighborhoods.html` (or `.txt`)
- Keep writing full `dom-slim.txt` + `page-map.*` for humans.
- Grounding: guard against map ∪ neighborhood HTML.

### UI / config

- Advanced: DOM mode option **Two-pass** or checkbox “Two-pass DOM (experimental)”.
- Default remains `auto`.

---

## Tasks (when un-deferred)

### Task 1: Neighborhood extractor + tests

**Files:** Create `HuntDomNeighborhoods.java`, `HuntDomNeighborhoodsTest.java`

- [ ] Extract around id/css/xpath-ish values present in slim
- [ ] Cap total chars; drop extras
- [ ] Unit tests with fixture HTML

### Task 2: Pass-1 / Pass-2 planner API

**Files:** `OllamaHuntPlanner.java`, `CursorHuntPlanner.java`, `HuntPlanner.Context` (flags), `LocalLlmClient` usage

- [ ] Schema docs in system prompt for `focus[]`
- [ ] Parse focus; build Pass-2 user prompt
- [ ] Fallback to single-pass on empty focus / parse failure

### Task 3: LiveHuntService + evidence + config/UI

**Files:** `LiveHuntService.java`, `HuntRequest`, `HuntController`, `bug-hunter.html`, `application.properties`, `HuntPackWriter` SUMMARY line

- [ ] Toggle twopass
- [ ] Write pass1/pass2 evidence files
- [ ] SUMMARY: `domMode=twopass` when used

### Task 4: Verification

- [ ] Unit + dry-run path with twopass stub (fake planner returning focus)
- [ ] Live smoke: one AxisPay hunt comparing auto vs twopass token size / action quality (note in hunt pack SUMMARY or ops note)

---

## Non-goals

- Multi-screenshot mid-cycle vision loops
- Full accessibility tree dump
- Replacing page map (map remains Pass-1 primary)

## Rollback

If twopass worsens quality: set config off; single-pass `auto` remains the default forever unless flipped.
