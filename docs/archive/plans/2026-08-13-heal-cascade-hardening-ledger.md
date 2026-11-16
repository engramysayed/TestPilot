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
- `088f419` Harden invent prompts and revise awareness of heal tiers
- (follow-up) Close invent wrong-entity hole, make vision widen reachable, cap invent per TC

## Post-review fixes (2026-08-13)
1. **Invent wrong-entity guard** — invented click locators must carry the intent's distinctive
   tokens, otherwise the heal is rejected (previously invent bypassed `validHealSteps` entirely).
2. **Vision widen reachable** — widened picks skip the token rule (it is what emptied the pool)
   but must land on an interactive control; `AuthoringService` gained a `relaxDistinctive`
   force-bind path so the binder no longer rejects them. Tier is `vision` for both providers.
3. **Invent budget** — `delivery.heal.invent.max-per-tc` (default 2) caps invent calls per TC
   across all heal invocations.
4. Tier rank corrected to invent > vision > cursor > ollama.

## Known open issue (pre-existing, not from this work)
`CheckboxBindAndAssertTest.fullCaseBindsDistinctCheckboxes` fails: ordinal intents such as
"Click checkbox 2 to uncheck it" do not bind. `StepIntentBinder` was never touched by this branch.
