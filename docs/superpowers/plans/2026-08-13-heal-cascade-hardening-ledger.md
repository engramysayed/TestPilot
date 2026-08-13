# Heal cascade hardening — progress ledger

**Branch:** `feat/heal-cascade-hardening`  
**Plan:** `docs/superpowers/plans/2026-08-13-heal-cascade-hardening.md`  
**Locks (user 2026-08-13):**
- Last-hope invent: Cursor default; optional AgentRouter via config
- No Gemini
- Invent prompts must be self-contained: photo + step + HTML + prior steps (no session memory)
- Fix plan bug list (B1–B14)

| Task | Status | Notes |
|------|--------|-------|
| 0 Plan lock | done | invent provider + AgentRouter optional invent |
| 1–4 Core heal | done | commit `89b6d8d` via implementer |
| 5 Revise/emit + bugfixes | done | screenshot cap, emit counts, revise risk rank |
| 6 Verify | done | focused heal tests pass |

## Commits
- `89b6d8d` Harden heal recovery before honest fallback
- (follow-up) Harden invent prompts and revise awareness of heal tiers
