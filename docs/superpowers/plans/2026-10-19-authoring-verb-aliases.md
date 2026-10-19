# Authoring verb aliases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Admin shows built-in classify verbs read-only; Owner adds aliases onto Click / Type / Assert visible / Ignore; `observe` is seeded as assert.

**Architecture:** `AuthoringVerbAliases` owns built-in tokens, validation, ThreadLocal+process current list (like preferred hooks). `classify` / `looksLikeAssert` consult aliases only after built-ins. `LlmSettingsService` persists `authoringVerbAliases` and publishes on Save.

**Tech Stack:** Java 21, TestNG, existing Admin LLM page.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-19-authoring-verb-aliases-design.md`
- TDD. Author `rsayed`. No Co-authored-by. Do not push.
- Do not change leftover quoting, OutcomeKind, nested hooks, login heuristics, named-click INTENT_ACTION_VERBS.

## File map

| File | Role |
|------|------|
| `delivery.authoring.AuthoringVerbAliases` | Built-ins, parse, activate, classify leftover |
| `StepIntentBinder` | After built-ins, apply alias |
| `LlmSettingsService` | Persist + publish |
| `admin-llm-settings.html` | Read-only table + alias editor |
| Tests | Classify, save reject tap, seed observe |

---

### Task 1: AuthoringVerbAliases + classify

- [x] Failing tests then implement catalog, seed observe, activate, classify observe→assert, tap rejected, built-in click wins, ensure→ignore
- [x] Wire `StepIntentBinder.classify` / `looksLikeAssert`

### Task 2: Persist + Admin UI + docs

- [x] llm-settings.json + GET builtInVerbs + aliases
- [x] Admin tables
- [x] PRODUCT, Guide, CHANGELOG, handoff
