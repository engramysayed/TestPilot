# UI-TARS / Submit polish — execution scorecard (2026-08-21)

Results from executing `docs/superpowers/plans/2026-08-21-delivery-polish-uitars-emit.md` Tasks 1–4, plus the live follow-up.

## Task 1 — UI-TARS honesty (DONE)

| Check | Result |
|-------|--------|
| SauceDemo-shaped placeholder PASS → UNCERTAIN | PASS (`VisionAssertionGateTest`) |
| `evaluate()` via Fake provider applies honesty gate | PASS |
| ProvePhase demotes placeholder UNCERTAIN | PASS |
| UiTars uses shared `ASSERT_SYSTEM_PROMPT` | PASS |
| Prompt bans `what is visible` / `why` echoes | PASS |

## Task 2A — Submit bind Facebook-shaped (DONE)

Unit tests lock Sign-up **button** over bare Sign-up **anchor**.

## Task 2B — Facebook Submit live rebind (DONE — evening follow-up)

| Item | Result |
|------|--------|
| Landed | `https://www.facebook.com/reg/` (110 candidates) |
| Bind | `//div[contains(normalize-space(.),'Submit')]…` |
| IR | 4 click steps patched (`TC_FB_REG_02`–`05`), rationale `live-rebind-submit` |
| Package | Regenerated → `click_Submit_Button()` (was `click_Sign_Up_Button`) |
| Notes | `2026-08-21-facebook-submit-rebind-notes.md` |

**Caveat:** Live control is a **div** containing “Submit”, not `<button type=submit>`. Still correct vs Excel wording and better than Sign-up link.

## Task 3 — Smell before write (DONE)

Verify runs before FreeMarker emit.

## Task 4 — Heal stress + live UI-TARS

| Mode | Result |
|------|--------|
| Fixture (`UiTarsHealStressTest`) | Vision tier fires; grounds control (Fake stand-in) |
| Live Ollama (`UiTarsLiveSmokeTest`) | Model responds; analyze ~11s found=true but **weak 1×1 bbox**; assert **UNCERTAIN**/empty — honesty gate OK (no false PASS) |
| Notes | `2026-08-21-uitars-live-smoke-notes.md` |

**Verdict:** Pipeline + honesty are solid. Live ui-tars **bbox/assert quality** still needs prompt/model work before trusting heal in production.

## Still optional

- Commit when you ask
- Separate plan: ui-tars bbox quality tuning
