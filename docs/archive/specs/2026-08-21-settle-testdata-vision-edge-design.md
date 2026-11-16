# SauceDemo follow-ups — settle wait, TestData, vision prompt, Edge prefs

**Date:** 2026-08-21  
**Status:** Approved (user: 5s default wait; full combined scope)  
**Goal:** Fix customer TestData loss on heal/auto-fill, stop stale DOM binds after actions, upgrade Qwen vision *contract* (not rebuild), and mirror Chrome password-bubble prefs on Edge.

## Context (evidence)

SauceDemo `saucedemo-com-20260821-025316` **PASSED** after Chrome password-manager prefs. Remaining issues from that run and analysis:

1. Six steps needed `HEAL_RETRY` after page changes — first bind used stale HTML; vision aborted (low-conf / implausible bbox); cheap retry then succeeded.
2. Checkout typed invented values (`Merna` / `Brekke` / `73475`) instead of Excel TestData (`John` / `Doe` / `12345`).
3. Vision architecture already matches the intended design; the **prompt/input** to Qwen is too thin (`Intent: …` + short JSON schema).
4. `EdgeFactory` still lacks the same password-manager prefs as `ChromeFactory`.

## Locked decisions

| Topic | Decision |
|---|---|
| Post-action wait | Static sleep after each executed action (click / type / select / navigate), then **re-snapshot HTML** before binding the next intent |
| Wait default | **5000 ms** |
| Wait config | Env / system property `DELIVERY_POST_ACTION_WAIT_MS` (override default). `0` disables sleep but still re-snapshots when implemented in the prove path |
| TestData | Concrete Excel line always wins over invent on first bind, heal retry, and auto-fill. Invent only when that line is blank |
| RequiredControlFiller | Must not invent over customer TestData when filling before Continue / Checkout / similar |
| Vision scope | **Gap-fill** existing `delivery.vision` stack — configurable prompt template, structured ACTION/TARGET/constraints, few-shots, better logging. No rebuild; no default coordinate click |
| Edge | Same prefs as Chrome: credentials / password manager / leak detection / autofill off |
| Out of scope | Swapping `qwen2.5vl:3b` for a larger model; full GPT StepAssessment / ambiguity LLMPlanner redesign; regenerating Facebook/Amazon workbooks |

## Workstreams

### 1. Edge password bubble

Mirror `ChromeFactory` experimental prefs in `EdgeFactory` (+ package-visible `buildOptions()` + unit test like `ChromeFactoryOptionsTest`).

### 2. TestData integrity

- Failing tests proving heal / `stepsPreferringCandidate` / `RequiredControlFiller` respect `IntentLine.testData()`.
- Align TestData lines with Steps on read (preserve blank lines; pad or keep trailing blanks so line N maps to step N).
- `DummyValueInventor`: never invent when column line is concrete; heal and auto-fill must pass column values through.
- Auto-fill before submit-like clicks must prefer matching TestData for empty fields when available (pass intent corpus or per-field values from the TC).

### 3. Post-action settle

- After successful execute of an action in prove (`TcExecutionService` / `ProvePhase` — one clear choke point), sleep `DELIVERY_POST_ACTION_WAIT_MS` (default 5000).
- Always take a fresh slim HTML snapshot for the **next** intent bind (even if wait is 0).
- Log wait duration once per action at INFO (short).

### 4. Vision prompt / contract upgrade

Keep: `VisionGroundingProvider`, bbox → `CoordinateMapper` → `elementFromPoint` → locator, implausible/low-conf guards, no `moveByOffset` default.

Add / change:

- External or configurable prompt template (versioned, e.g. `v1`) with the approved grounding instructions (locate only; no XPath; spatial relationships; multi-candidate JSON).
- User payload: ACTION, TARGET (from intent), optional SEMANTIC CONSTRAINTS (role/text hints when known), not raw DOM.
- Optional few-shot block (configurable / empty-ok).
- Defensive JSON parse (already partial) + clear VISION logs: step, model, bbox, conf, grounding outcome.
- Do **not** remove existing abort guards in this plan.

## Success criteria

- Unit tests: Edge prefs present; TestData survives heal and auto-fill; wait property reads default 5000 and override; vision provider builds structured user prompt from template.
- Re-run SauceDemo checkout Excel: typed checkout names/zip match TestData; fewer or no `HEAL_RETRY` solely due to stale DOM; password bubble stays suppressed on Chrome/Edge.
- No site hardcoding; no coordinate-click default; no commit unless user asks.

## Non-goals

- Making Qwen “perfect” or removing heal cascade.
- Changing portal UX / TC guide for this work (already updated earlier).
