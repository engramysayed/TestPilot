# FE — Confirm credit add-ons (no Draft UI) — 2026-11-02

**Tree:** `D:\Work\projects\Keel\keel\TestPilot` (Keel LIVE; not Sonic)  
**Tip at edit start:** `294291ac32c00af4a0f172630e9b0a91cb0e607e` (`294291a`) — verified with `git rev-parse HEAD` before edit.  
**Portal PID (kickoff):** 22104  
**Constraints:** credits only (no USD); no push; FE templates only.

**User confirmed credits:** `VISUAL_DIFF=5`, `SUITES_MONITOR=10`, `HEAL_REVIEW=5`.

## Backup
`_backup\backup-pre-credits-addons-confirm-20261102-213327\`
- `pricing.html`
- `guide.html`

## Files changed
1. `src\main\resources\templates\pricing.html`
   - Removed Draft badge CSS (`.pricing-draft-badge`, `.pricing-draft-row`), `th:with isDraft` / `th:classappend`, Draft spans, `(draft)` notes, and provisional/draft copy.
   - Table is a plain `th:each="row : ${prices}"` like core services (label / unit / detail / cost).
   - Kept credits-only language + “What uses which credits” mapping; note Owner edits all rows on Admin → Prices.
   - GFK: no Draft UI at all (preferred) — any seeded GFK row renders as a normal row.
2. `src\main\resources\templates\guide.html`
   - Dropped draft/provisional wording for `HEAL_REVIEW`, `VISUAL_DIFF`, `SUITES_MONITOR`, GFK add-on credits, and pricing FAQ bullets.
   - Kept credits model (Generate / Execute / Automate / Hunt + Owner Admin → Prices).
   - Left non-pricing “draft” verbs (e.g. “drafts cases”, “drafted cases”, nav “Packs → draft cases”).

## Verify
1. After Maestro portal jar refresh (Backend handoff first if still open): open `/pricing`.
2. Expect: no Draft badge, no yellow draft-row styling, no “(draft)” beside costs.
3. Confirmed add-ons appear as normal table rows when seeded (`VISUAL_DIFF` / `SUITES_MONITOR` / `HEAL_REVIEW`).
4. Copy stays credits-only; Owner edit note points at Admin → Prices.
5. Spot-check guide Credits callouts for heal / visual / suites / GFK — no “draft credits” language.

## Next
**Maestro:** portal jar rebuild/refresh after Backend confirm slice lands; smoke `/pricing` + `/admin/pricing`.  
Do not push. Do not ping Maestro from this FE handoff (report via Leader/parent only).

## Out of scope
- Job debit / `CreditsBillingKind` wiring  
- Seed cost changes (Backend owns ensureAddon 5/10/5)  
- Sonic tree