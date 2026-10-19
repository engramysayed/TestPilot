# Authoring verb aliases (Owner list)

**Date:** 2026-10-19  
**Status:** Implemented  
**Plan:** `docs/superpowers/plans/2026-10-19-authoring-verb-aliases.md`  
**Handoff:** `docs/handoff/2026-10-19-authoring-verb-aliases.md`

## Problem

Excel steps use verbs Keel does not classify (`Observe`, `Ensure`, …). Those lines are skipped. Adding a verb today requires a code change. The Owner wants a list of what Keel already treats as click / type / assert, plus the ability to add more words onto those same kinds.

## Goal

1. **Read-only built-ins** on Admin: every classify verb Keel ships, grouped by how it is treated. Cannot edit or delete.
2. **Owner aliases:** add a word and pick **Click**, **Type**, **Assert visible**, or **Ignore**. Next Execute/Automate/Generate review bind uses it. No new action kinds.

## Non-goals

- New kinds (pick-any-row-with-status, hover, drag, “observe screenshot”).
- Remapping login / username / password / submit-login heuristics.
- Remapping named-click token lists (`INTENT_ACTION_VERBS` such as add/edit/submit used to match a control after the line is already a Click).
- Leftover Expected filler quoting, OutcomeKind quote-wins-first, nested-hook uniqueness.
- Project- or member-level lists. Members do not edit this.
- Changing `api-keys.local.bat`.

## Surface

Owner **Admin → LLM & API keys** (same `requireHeadAdmin` gate), new **Authoring verbs** block under browser settings.

### Built-in (read-only table)

Shown as word → treat as. Not checkboxes. Not dropdowns.

**Click** — `click`, `press`, `tap`, `open`, `go to`, `add ` (as in “add the …”).

**Type** — `enter`, `type`, `fill`, `input`, `select ` (line starts with select), `choose`, `pick `, leave/keep empty, leave blank, do not fill / don't fill, skip the field.

**Assert visible** — `confirm`, `verify`, `shown`, `visible`, `see `, `displays`, `is shown`, `are visible`. Expected leftover also treats unquoted `displayed`, `expands`, `section`, or a quoted phrase, as an outcome (existing `looksLikeOutcome`; not aliases).

**Ignore (skip the line)** — `post-login…`, `do not …` at the start of a step (existing).

**Code-only specials (listed, not remappable):** username/email + type/fill on a login line → type user; password + type/fill → type password; login/sign in + click/press/button → click login; explicit `capture from` / `compare captured` / `assert signed-out` lines.

### Owner aliases (editable)

Table of `{ word, treatAs }` plus Add row.

| Field | Rule |
|-------|------|
| Word | One token, letters only, length 3–40, stored lowercase. Unique among aliases. |
| Treat as | Dropdown: `click` / `type` / `assert` / `ignore` |

Save rejects a word that collides with a **built-in classify token** (built-in wins; show an error). Max **50** aliases.

Example: `observe` → `assert` makes `Observe the Agent User Details page is displayed` a visible wait instead of a skipped step.

## Apply

Persist in `delivery-store/llm-settings.json` as `authoringVerbAliases: [{ "word": "observe", "treatAs": "assert" }, …]` (not a secret).

`StepIntentBinder.classify` / `looksLikeAssert` / `isActionStep`:

1. Existing built-in and login/password/explicit rules first (unchanged).
2. If still unclassified, whole-word match against aliases (word boundary, case-insensitive).
3. `assert` → `ASSERT_VISIBLE` (and `looksLikeAssert` true so Expected leftover with that verb is an outcome).
4. `click` → `CLICK`. `type` → `TYPE_FIELD`. `ignore` → skip (same as unclassified).
5. A click word on the same line still beats an assert alias (`looksLikeClickAction` unchanged).

Execute, Automate, Generate, Record bind, and remembered rewrite all go through `classify`. No JVM restart after Save (read aliases from `LlmSettingsService` / a process-wide holder, same pattern as preferred hooks).

## Tests (intent)

- Built-in `click` still CLICK when an alias tries to override it (save rejected or classify ignores alias).
- `Observe the rates module is displayed` with alias observe→assert → `ASSERT_VISIBLE`.
- No alias: Observe line still skipped.
- `ensure` → ignore: `Ensure the page loaded` skipped even if it contains “page”.
- Alias `tap` rejected (built-in click token).
- Member GET/PUT of aliases → 403.

## Known limits

- Aliases do not teach “click any grid row with this status.” Quote a unique on-screen name, or the step stays AMBIGUOUS.
- `"Pending Edit"` is an exact label match, not a status filter.
- Default install **seeds** one editable alias: `observe` → `assert`. The Owner may delete or change it. Built-ins stay read-only.
