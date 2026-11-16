# Kickoff — Slice B: prompts Owner UI + VERSION (2026-11-02)

**Status:** ASSIGNED Backend + Frontend  
**Base tip:** `301a3fc` (Slice A addon debit CLOSED)  
**Portal LIVE:** PID `17288` on `:8080` (verify before rebuild)  
**No push.** No secrets. Keel only. Serialize Maven (one reactor; coordinate jar with Maestro).  
**Depends on:** prompts extract (PromptCatalog + `resources/prompts/` + `keel.prompts.dir`) already GREEN.

## Goal

1. **Owner / Admin prompts UI** — list prompt ids; show active source (classpath vs override); view/edit; save into override dir; show diff vs classpath.  
2. **Prompt pack VERSION** — `prompts/VERSION` (or git-describe fallback) stamped into **Generate** and **Heal** job evidence so runs are reproducible.

## Backend DoD

- Admin-authenticated API (mirror `AdminLlmSettings` / pricing auth):
  - `GET` list known ids (from classpath inventory under `prompts/` + any override-only files)
  - `GET` one id → `{ id, source: classpath|override, text, classpathText? }`
  - `PUT/POST` save text → writes under absolute `keel.prompts.dir` (create dirs); refuse if override dir unset/blank with clear 400
  - `GET` diff vs classpath for one id (unified or line-oriented JSON)
- When override dir unset: list/get still work from classpath; save explains how to set `keel.prompts.dir` / `KEEL_PROMPTS_DIR`.
- Add `src/main/resources/prompts/VERSION` (semver or date-sha short, e.g. `2026.11.02` or `1`); `PromptCatalog` (or small helper) exposes `version()` reading override VERSION first then classpath.
- Stamp version into Generate + Heal job evidence (JSON/meta field e.g. `promptsVersion` / `promptPackVersion`) at job start or evidence write — find existing evidence writers and extend; do not invent a second evidence format.
- Tests: list non-empty; get classpath; save+reload from temp override dir; version() resolves; evidence stamp assertion if feasible.
- Handoff: `docs/history/handoffs/2026-11/2026-11-02-prompts-owner-ui-backend.md`
- Backup under `_backup/` before edits.

## Frontend DoD

- New admin page (e.g. `admin-prompts.html`) matching existing admin chrome (`admin-pricing`, `admin-llm-settings`).
- List ids with source badge (classpath / override).
- Detail: editor + Save (to override) + Diff vs classpath panel.
- Clear empty-state when `keel.prompts.dir` not configured (show Backend’s message).
- Sidebar / admin nav link: **Prompts** (Owner/Admin only — same gate as other admin pages).
- Plain-English copy; credits-only product voice.
- Handoff: `docs/history/handoffs/2026-11/2026-11-02-prompts-owner-ui-fe.md`
- Backup before template edits.

## Coordination

- Backend lands API contract first (or same-day with stub shapes in kickoff). FE may mock briefly but should wire live endpoints before GREEN.
- Maestro owns portal jar after both GREEN (or after Backend if FE is templates-only live-apply).
- AE: only if Backend finds heal evidence owned outside portal — then ping Maestro; do not self-start AE.

## Out of scope

- Hot-reload changes beyond existing override re-read
- Push
- Changing prompt text content wholesale (UI to edit is enough)
- Slice A debit changes

## Report

Each specialist: GREEN + tip + handoff. Maestro: jar refresh, smoke admin prompts + pricing, report Leader tip/PID.