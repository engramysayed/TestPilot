# Bug Hunter — Phase 1–2 gaps (follow-up)

> **For agentic workers:** Use superpowers:executing-plans or subagent-driven-development when building this. Do **not** start until a live hunt on the target app shows these gaps matter, or the user explicitly prioritizes this plan.

**Goal:** Close remaining quality-spec items that were deferred after Phase 1–2 shipped (page map, coverage, grounding, strategies, basic oracles, STUCK/COMPLETE).

**Status:** Draft — backlog  
**Parent spec:** `docs/superpowers/specs/2026-09-10-bug-hunter-quality-design.md`  
**Shipped plan:** `docs/superpowers/plans/2026-09-10-bug-hunter-quality.md`  
**Sibling (only if context still blows):** `docs/superpowers/plans/2026-09-10-bug-hunter-phase3-twopass-dom.md`

## Global Constraints

- No Claude / Anthropic models
- No auto-merge of candidate scenarios into the library
- Screenshot remains one per cycle at cycle start (unless a later design changes this)
- Do not regress action cap (default 5) or wait default (5000 ms)
- Prefer extending `HuntOracle` / pack writers over new parallel pipelines

---

## Gap inventory (from ship review)

| ID | Gap | Priority | Effort |
|----|-----|----------|--------|
| G1 | Promote `net::ERR_*` / `loading_failed` network rows to oracle bug drafts | P0 | S |
| G2 | Blank-main / empty critical-region heuristic → bug draft | P1 | M |
| G3 | Unexpected URL / error-path redirect vs coverage → bug draft | P1 | M |
| G4 | Write `cycles/cycle-NN/oracle.json` snapshot of signals each cycle | P1 | S |
| G5 | Toast-like selectors (e.g. `.toast`) in page-map alerts | P2 | S |
| G6 | Optional `coverage-map.json` beside `coverage-map.md` | P2 | S |
| G7 | SUMMARY: coverage URL count | P2 | S |
| G8 | Dry-run writes stub `planner-prompt.txt` / `planner-response.txt` | P2 | S |
| G9 | API/UI test: `strategiesEnabled=false` round-trip | P2 | S |
| G10 | Live AxisPay (or target) smoke checklist + note in CHANGELOG | P0 (process) | S |

---

### Task 1: Network oracle completeness (G1)

**Files:**
- Modify: `src/main/java/delivery/hunt/HuntOracle.java`
- Modify: `src/main/java/delivery/hunt/HuntNetworkCapture.java` (ensure error kinds are labeled consistently)
- Test: `src/test/java/delivery/hunt/HuntOracleTest.java`

**Done when:**
- Synthetic `loading_failed` / `net::ERR_*` (or equivalent status field already stored) produces a bug draft with journal `repro` when planner bugs for that cycle are empty (same gate as HTTP errors).
- Existing HTTP 4xx/5xx behavior unchanged.

- [ ] Extend oracle network rule beyond `http_error`
- [ ] Unit tests for ERR / loading_failed
- [ ] Run `HuntOracleTest`, `HuntCoreTest`

---

### Task 2: Page-state oracles (G2, G3)

**Files:**
- Modify: `HuntOracle.java` (+ maybe small `HuntPageSignals` helper)
- Modify: `LiveHuntService.java` — pass post-action URL / main-text length / coverage URLs into oracle
- Test: `HuntOracleTest.java`

**Rules (v1 heuristics — tune thresholds in constants):**
- **Blank main:** after actions, if `main` / `[role=main]` / `body` visible text length &lt; `BLANK_MAIN_CHARS` (e.g. 40) and last action was `navigate` or `click` → draft bug `severity=major`.
- **Unexpected URL:** if current URL matches login/error patterns (`(?i)login|signin|error|404|denied`) **and** coverage already had a different non-login URL this hunt **and** strategy is not `session` → draft bug.

- [ ] Implement helpers + wire collect()
- [ ] Unit tests with fixture HTML/URL strings (no browser required)
- [ ] Document thresholds in class javadoc

---

### Task 3: Evidence pack polish (G4–G8)

**Files:**
- Modify: `LiveHuntService.java`, `DryRunHuntService.java`, `HuntPackWriter.java`, `HuntCoverageMap.java` (optional JSON)
- Modify: `HuntPageMapBuilder.java` alert selectors for `.toast`
- Test: `HuntCoreTest`, `HuntApiTest`

**Done when:**
- Each live cycle writes `oracle.json` (signals + whether a draft was emitted).
- Dry-run cycle dir includes stub `planner-prompt.txt` and `planner-response.txt`.
- SUMMARY mentions coverage URL count when coverage file present.
- Optional `coverage-map.json` written at hunt root (same data as md, machine-readable).

- [ ] Implement pack writes
- [ ] Assert ZIP / disk paths in dry-run tests

---

### Task 4: API test + smoke notes (G9–G10)

**Files:**
- Modify: `HuntApiTest.java` — POST with `strategiesEnabled: false`
- Modify: `CHANGELOG.md` or `docs/ops/` short smoke checklist for Bug Hunter live

**Live smoke checklist (human):**
1. `delivery.dry-run=false`, multimodal model (e.g. `gemma4:e2b`)
2. Hunt 1–2 library TCs on AxisPay (or target)
3. Confirm pack has page-map, journal, coverage, prompt/response, screenshot
4. Confirm invented locator rejected in actions-log if model invents one
5. If planner prompt still huge / model ignores map → open **Phase 3** plan

- [ ] API test green
- [ ] Smoke checklist checked into docs

---

## Out of scope (this gaps plan)

- Phase 3 two-pass DOM (separate plan)
- Auto-merge scenarios into library
- Changing screenshot timing to post-action
- New strategy modes beyond the fixed six

## Exit criteria

All P0/P1 gaps closed **or** explicitly wontfix’d in CHANGELOG with reason. P2 optional.
