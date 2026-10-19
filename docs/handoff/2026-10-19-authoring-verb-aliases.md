# 2026-10-19 — Authoring verb aliases

**Status:** Implemented on main.

Spec: `docs/superpowers/specs/2026-10-19-authoring-verb-aliases-design.md`  
Plan: `docs/superpowers/plans/2026-10-19-authoring-verb-aliases.md`

## What shipped

- Admin → LLM & API keys shows **built-in** classify verbs as a **read-only** table (click / type / assert / ignore / code-only specials).
- Owner **adds aliases** onto those same four treats: Click, Type, Assert visible, Ignore. No new action kinds. Built-in words cannot be remapped (Save returns 400).
- Seed one deletable alias: `observe` → assert. Missing `authoringVerbAliases` in `llm-settings.json` seeds it; an empty list means the Owner deleted it.
- `StepIntentBinder.classify` / `looksLikeAssert` apply aliases after built-ins (whole-word). `ignore` skips the line. A built-in click word on the same line still beats an assert alias.
- Login/password/named-click token lists stay in code.

## How to use

1. Rebuild the portal jar, restart, Ctrl+F5.
2. Owner opens **Admin → LLM & API keys**, **Authoring verbs**.
3. Built-ins stay as listed. Add e.g. `ensure` → Assert visible, or change/delete `observe`. Save.
4. Next Execute / Automate / Generate review / Record bind uses the list (no extra JVM restart after Save on a live process).

## Known defects

- Aliases do not teach “click any grid row with this status.” Quote a unique on-screen name, or the step stays AMBIGUOUS.
- `"Pending Edit"` is an exact label match, not a status filter.
- Live Admin UI was not exercised in the browser this pass (login wall / jar not restarted here). API tests cover GET/PUT, tap collision, member 403.
