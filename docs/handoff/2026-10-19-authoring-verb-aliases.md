# 2026-10-19 — Authoring verb aliases

**Status:** Spec approved for planning. Not implemented.

Spec: `docs/superpowers/specs/2026-10-19-authoring-verb-aliases-design.md`

## What was decided

- Admin → LLM & API keys shows **built-in** classify verbs as a **read-only** table (click / type / assert / ignore / code-only specials).
- Owner **adds aliases** onto those same four treats: Click, Type, Assert visible, Ignore. No new action kinds. Built-in words cannot be remapped.
- Seed one deletable alias: `observe` → assert.
- Login/password/named-click token lists stay in code.

## How to use (once built)

Owner opens LLM & API keys, sees built-ins, adds e.g. `ensure` → Assert visible, Save. Next run classifies those words. Rebuild jar + Ctrl+F5 after implementation.

## Known defects (today, before the work)

- `Observe` steps are skipped. No Admin list yet.
