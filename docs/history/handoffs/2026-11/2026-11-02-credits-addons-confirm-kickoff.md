# Kickoff — Confirm credit add-ons + admin-editable (2026-11-02)

**User confirmed** (via Leader): VISUAL_DIFF=5, SUITES_MONITOR=10, HEAL_REVIEW=5 credits.

**Tip base:** current HEAD (~294291a / portal PID 22104).  
**Constraints:** credits only (no USD); no push without user OK; Keel ≠ Sonic; serialize Maven; job debit for these codes stays **unwired** unless already end-to-end without debit (admin display/edit is enough).

## Already true
- `/admin/pricing` loops all `ServicePrice` rows — add-ons already editable once seeded.
- Seeds exist via `ensureAddon` at 5 / 10 / 5.

## Backend
1. Treat add-ons as **confirmed** ServicePrice rows (not draft): update comments + `detail()` covers — remove “Draft credit amount…” wording; keep plain-English covers + “Owner can edit on Admin → Prices” if useful without saying Draft.
2. Ensure seed costs remain 5 / 10 / 5; `ensureAddon` still insert-only-if-missing (do not overwrite owner edits on re-run).
3. If DB rows still have old draft cover text, refresh only when covers still match the old canned draft string (same pattern as refreshLegacyCovers).
4. Do **not** wire into `CreditsBillingKind` / job debit unless already safe without a JobKind.
5. Handoff: `docs/history/handoffs/2026-11/2026-11-02-credits-addons-confirm-backend.md`

## FE
1. `/pricing` (`pricing.html`): remove Draft badge, draft-row CSS, “(draft)” notes, and copy that calls these provisional/draft.
2. Present VISUAL_DIFF / SUITES_MONITOR / HEAL_REVIEW like core services; note Owner edits credits on Admin → Prices.
3. Guide credits/pricing callouts: drop “draft” language for these three if present.
4. Handoff: `docs/history/handoffs/2026-11/2026-11-02-credits-addons-confirm-fe.md`

## Done
Maestro portal jar refresh + smoke `/pricing` + `/admin/pricing`. Report Leader. Parallel with prompts extract finish.
