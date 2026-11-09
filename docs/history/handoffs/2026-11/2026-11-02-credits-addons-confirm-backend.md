# Handoff — Confirm credit add-ons backend (2026-11-02)

**Status:** GREEN  
**Tip base:** 294291a  
**Scope:** Confirm VISUAL_DIFF / SUITES_MONITOR / HEAL_REVIEW as non-draft ServicePrice rows. No job debit.

## Confirmed credits

| Code | Label | Seed cost | Unit | Notes |
|------|-------|-----------|------|-------|
| VISUAL_DIFF | Visual diffs | **5** | Each review | Confirmed; insert-if-missing |
| SUITES_MONITOR | Suites & monitor | **10** | Each scheduled run | Confirmed; insert-if-missing |
| HEAL_REVIEW | Heal review | **5** | Each proposal | Confirmed; insert-if-missing |

- No Draft / draft / provisional wording in `detail()` covers.
- Optional note kept: "Owner can edit on Admin → Prices."
- `ensureAddon` still insert-only-if-missing (does not overwrite owner edits).
- `refreshLegacyCovers` extended for the three old canned **draft** cover strings (exact-match only).
- **Not** wired into `CreditsBillingKind` / job debit.

## Changed files

- `src/main/java/delivery/portal/service/credits/ServicePriceService.java`
- `src/test/java/delivery/portal/service/credits/ServicePriceServiceTest.java`
- Backup: `_backup/credits-addons-confirm-20261102-2133/`

## Verify

```bat
mvn -q -pl keel-portal -Dtest=ServicePriceServiceTest test
```

**Result:** Tests run: 3, Failures: 0, Errors: 0, Skipped: 0.

## Portal refresh

Maestro: rebuild/restart portal jar so Admin → Prices and `/pricing` API list pick up new covers for any DB rows still holding the old draft canned strings. FE owns `pricing.html` badge/copy strip (separate handoff).

## Commit (selective, no push)

```
feat(portal): confirm credit addon covers remove draft
```

user.name=KeelBackend. Staged only ServicePriceService + test + this handoff. Did not stage application.properties or other dirty WIP.
