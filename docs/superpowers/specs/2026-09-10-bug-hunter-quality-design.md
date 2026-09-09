# Bug Hunter Quality — design (page map + strategies)

**Date:** 2026-09-10  
**Status:** Implemented (Phase 1 + Phase 2)  
**Extends:** `docs/superpowers/specs/2026-09-09-bug-hunter-design.md`  
**Job kind:** `HUNT` (unchanged)

## Goal

Make Bug Hunter reliable on real SPAs (e.g. AxisPay Ops) by:

1. **Phase 1 — Context that doesn’t lie:** structured **page map (B)** as the planner’s primary DOM signal, with **full slim HTML (A)** as fallback when the map is too thin; drop the useless mid-prompt 32k head truncate.
2. **Phase 1 — Memory & grounding:** compounding **coverage map** + existing steps journal; reject invented locators before execute; stop when stuck.
3. **Phase 2 — Hunter smarts:** seeded **hunt strategies**, richer **oracles**, smarter **finish** rules, cleaner pack fields.

Phase 3 (two-pass DOM neighborhoods) is out of scope unless Phase 1 still blows context on AxisPay.

## Non-goals

- Auto-merge candidate scenarios into the library
- Full HAR / performance profiling
- Multi-browser matrix in one hunt
- Replacing Automate prove or Execute
- Two-pass / region-only DOM (Phase 3 later)
- Changing the strict planner JSON contract keys (`decision`, `rationale`, `actions`, `bugs`, `scenarios`) except documented additive fields below

---

## Phase 1 — Context, memory, grounding

### 1.1 Page map (B) — primary planner DOM

Each cycle, after capture:

1. Keep writing full slim HTML to `cycles/cycle-NN/dom-slim.txt` via existing `HtmlSlimmer.slim(..., 80000)` (evidence + fallback source).
2. Build a **page map** from that slim HTML (reuse `DomCandidateExtractor` + light page metadata).
3. Write `cycles/cycle-NN/page-map.json` (and a compact `page-map.md` for humans).
4. Put the **compact map text** into the planner prompt under `## Page map` — **not** a raw HTML dump by default.

**Page map contents (minimum):**

| Field | Source |
|-------|--------|
| `url` | `driver.getCurrentUrl()` |
| `title` | `driver.getTitle()` |
| `headings` | Up to ~8 visible `h1–h3` texts |
| `alerts` | `[role=alert]`, `.toast`, validation/`aria-invalid` summaries (best-effort selectors) |
| `controls[]` | From `DomCandidateExtractor` (cap **100**): role/tag, accessible/name hint, best locator strategy+value, optional spent flag |
| `dialogs` | Open `[role=dialog]` / modal titles if detectable |

**Prompt size rule:** cap by **control count** (and short string fields), never by chopping mid-tag HTML. If the rendered map markdown exceeds ~24k chars, drop lowest-priority controls first (keep alerts + headings + first N controls).

### 1.2 Full slim fallback (A) — no 32k cut

Config: `delivery.hunt.dom-mode` = `map` | `slim` | `auto` (default **`auto`**).

| Mode | Planner receives |
|------|------------------|
| `map` | Page map only (plus journal/coverage/network/screenshot) |
| `slim` | Full `dom-slim` text as today (≤80k from HtmlSlimmer), **no second 32k substring truncate** |
| `auto` | Page map; if map is **thin**, also attach full slim |

**Thin map** (auto → attach slim): fewer than **8** interactive controls **or** empty body after slim.  
Remove `OllamaHuntPlanner.DOM_PROMPT_MAX_CHARS` head truncation entirely.

### 1.3 Steps journal (already shipped — keep)

- Pack root `steps-journal.md`: actions + results only (no prior DOM/screenshots).
- Included in every planner prompt under `## Steps journal`.
- Journal prompt window may truncate **oldest** lines if huge; full file stays on disk.

### 1.4 Coverage map (new)

Pack root `coverage-map.md` (and optional `coverage-map.json`), updated after every cycle:

- URLs visited (deduped, ordered)
- Screen fingerprints: `title` + primary heading (when available)
- Controls exercised: locator or name + action type + last status
- Strategies completed (Phase 2 fills this; Phase 1 may leave `strategy=explore`)
- Failure streaks: `(actionType, locatorKey) → consecutive fail count`

Planner prompt section: `## Coverage` (compact markdown from the same file).

**Purpose:** stop re-probing the same dead control / same URL loop without relying on thin one-line cycle summaries.

### 1.5 Grounded actions

Before execute (inside `HuntActionExecutor` or a small `HuntActionGuard`):

- For `click` / `type` / `clear` / `assert_visible`: resolve locator; require it to appear in the **current cycle page map** **or** pass `HtmlLocatorPresence` against current slim HTML.
- Reject with `status=rejected`, `reason=ungrounded_locator` — do not crash the job.
- `navigate`, `wait`, `assert_text` (page-level text) are not map-bound the same way; `assert_text` stays page-source / visible-text based.

Planner system prompt must say: only emit locators that appear in the page map (or slim when attached).

### 1.6 Stop when stuck (Phase 1)

In addition to `finish` and `cycleCeiling`:

- If the same `(type, locatorKey)` fails **twice in a row** across actions/cycles → set `stopReason=STUCK` (or force planner `finish` next with rationale “stuck on …”) and end the hunt after writing pack.
- Optional soft path: mark that control spent in coverage and continue if cycles remain — **v1 hard-stop is simpler and preferred** unless strategy mode (Phase 2) wants to advance playbook instead.

**Phase 1 choice:** on double fail of same grounded action → `stopReason=STUCK` and end. Phase 2 may override to “advance strategy” when a strategy sequencer is active.

### 1.7 Evidence (keep + extend)

Per cycle (existing + new):

| Path | Role |
|------|------|
| `dom-slim.txt` | Full slim evidence |
| `page-map.json` / `page-map.md` | Structured map |
| `screenshot.png` | Cycle-start shot (unchanged) |
| `planner-prompt.txt` / `planner-response.txt` | Exact I/O |
| `actions-log.json` | Executed / rejected |
| `network-failures.json` | CDP best-effort |

Pack root: `steps-journal.md`, `coverage-map.md`, `brief.md`, `SUMMARY.md`, bug/scenario reports.

Screenshot timing unchanged: **one shot at cycle start**, before actions.

---

## Phase 2 — Strategies, oracles, finish, pack polish

### 2.1 What “hunt strategies” means

A **server-side playbook**, not a second model. Seeded from selected library TCs + optional user story.

Default sequence (configurable later; v1 fixed order):

1. `happy` — exercise the primary flow once (aligned to selected TC intents).
2. `empty` — submit / continue with required fields blank or cleared.
3. `boundary` — max-length / special-char / unicode probes on visible inputs.
4. `abuse` — double-submit, rapid repeat click, browser Back after a success signal.
5. `session` — only if credential profile present: mid-flow logout / stale session probe (best-effort; skip if unsupported).
6. `invent` — prefer emitting candidate scenarios; may still log bugs.

Each cycle prompt includes:

```text
## Hunt strategy
mode=<name>
goal=<one-line instruction for this mode>
completedModes=[...]
```

The planner still chooses concrete allowlisted actions; the mode **constrains intent**. Server may reject actions that clearly violate the mode (soft: log warning; hard reject only for obvious cases e.g. `happy` mode inventing 5 unrelated navigations — keep soft in v1).

When Phase 2 is active, **double-fail** advances to the **next strategy** instead of immediate `STUCK`, unless already on the last mode — then `STUCK` or allow `finish`.

### 2.2 Oracle quality

After the action batch (and/or at next cycle start before planning), collect signals:

| Signal | Behavior |
|--------|----------|
| Network 4xx/5xx / net::ERR_* | Already captured; auto-draft bug if planner emitted none |
| `[role=alert]`, toast-like nodes, `aria-invalid` | Snapshot texts into cycle `oracle.json`; failed expectation → bug draft |
| Blank main / empty critical region | Heuristic: main landmark text length below threshold after navigate |
| Unexpected URL / error path | Compare to coverage; flag redirects to login/error when not intended |

Bug drafts must include:

- `title`, `severity`, `expected`, `actual`
- `repro` = slice of steps journal for this hunt (last N successful steps + failing step)
- evidence paths (cycle screenshot + cycle dir)

### 2.3 Stop when useful

Stop when any of:

| Condition | `stopReason` |
|-----------|--------------|
| Planner `finish` | `FINISH` |
| Cycle ceiling | `CYCLE_CAP` |
| Stuck (see Phase 1/2 rules) | `STUCK` |
| Invent budget met **and** strategies exhausted **and** at least one bug or explicit planner finish | `COMPLETE` |
| Job cancel | existing cancel path |

Do **not** force finish solely because invent budget is zero; hunting bugs may continue until strategies/stuck/ceiling.

### 2.4 Human-usable pack polish

- Ensure bug rows carry severity + repro + evidence paths.
- `SUMMARY.md` lists strategies run, coverage URL count, grounded rejection count, stop reason.
- Candidate scenarios remain review-only (no library write).

---

## Architecture (components)

| Component | Responsibility |
|-----------|----------------|
| `HuntPageMapBuilder` | slim HTML + driver meta → page map model + md/json |
| `HuntCoverageMap` | append-only coverage file; fail streaks; forPrompt() |
| `HuntActionGuard` | grounded locator check vs map/slim |
| `HuntOracle` (Phase 2) | post-action signals → bug drafts |
| `HuntStrategySequencer` (Phase 2) | mode pointer; prompt hint; advance on double-fail |
| `OllamaHuntPlanner` / Cursor path | prompt assembly: brief, caps, strategy, journal, coverage, page map (± slim), network, screenshot note |
| `LiveHuntService` | wire capture → map → plan → guard → execute → journal/coverage/oracle → stop rules |
| Config | `delivery.hunt.dom-mode`, existing action/scenario/cycle caps |

### Planner prompt order (stable)

1. Caps (incl. `actionCapPerCycle`)  
2. Hunt strategy (Phase 2; omit or `explore` in Phase 1)  
3. Brief  
4. Steps journal  
5. Coverage  
6. Network failures (this cycle)  
7. Page map (always in `map`/`auto`)  
8. Slim DOM (only if mode `slim` or `auto`+thin)  
9. Screenshot note (+ vision bytes when multimodal)

### Data flow (Phase 1 cycle)

```
capture slim + screenshot + network
  → page-map.json
  → build Context (journal + coverage + map [± slim])
  → write planner-prompt.txt
  → planner JSON
  → write planner-response.txt
  → guard + execute (≤ actionCap; wait default 5s)
  → append journal + coverage
  → stuck check → continue | stop
```

---

## Config & API / UI

| Key | Default | Notes |
|-----|---------|-------|
| `delivery.hunt.dom-mode` | `auto` | `map` \| `slim` \| `auto` |
| `actionCapPerCycle` | 5 | already shipped |
| wait default | 5000 ms | already shipped |

Bug Hunter form (Phase 1): optional DOM mode select (or advanced toggle); defaults to auto.  
Phase 2: optional “strategies” checkbox default on (fixed sequence).

---

## Testing

**Phase 1**

- Page map builder: fixture HTML → controls + alerts; thin page triggers auto slim attach.
- Planner prompt: no 32k truncate; map section present; slim absent when map rich under `auto`.
- Guard: invented locator rejected; map locator accepted.
- Coverage: after two cycles, URLs/controls appear in `forPrompt()`.
- Stuck: two identical fails → `STUCK`.
- Existing `HuntCoreTest` / `HuntApiTest` stay green; dry-run writes map stubs.

**Phase 2**

- Sequencer advances modes on double-fail.
- Oracle promotes network/alert into bug draft with journal repro.
- `COMPLETE` stop when strategies + invent done with ≥1 bug.

---

## Success criteria

**Phase 1**

- Planner no longer receives head-truncated DOM at 32k.
- Default path sends structured page map; thin pages still get full slim.
- Invented locators are rejected with clear action-log reason.
- Coverage + journal appear in prompt; pack includes both files.
- Double identical failure ends with `STUCK` (until Phase 2 overrides).

**Phase 2**

- Hunts follow happy → attack → invent playbook visible in prompt + SUMMARY.
- Oracles create actionable bug drafts when the model stays silent.
- Stop reasons include `COMPLETE` / strategy-aware stuck behavior.
- Pack is review-ready (severity, repro, evidence).

---

## Ship order

1. **Phase 1** implement + verify (map, auto/slim, guard, coverage, stuck, remove 32k cut).  
2. **Phase 2** implement + verify (strategies, oracles, finish, pack polish).  
3. **Phase 3** only if needed: two-pass neighborhoods after AxisPay evidence.

## Open decisions (locked for this spec)

- Phase 1 stuck = hard stop (`STUCK`), not soft skip.  
- Control cap for map = **100**.  
- Thin map threshold = **fewer than 8** controls.  
- Screenshot remains cycle-start only.  
- No auto library merge.
