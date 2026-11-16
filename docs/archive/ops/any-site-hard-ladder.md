# Any-site hard ladder (site-agnostic stress pack)

**File:** [`src/test/resources/delivery/any-site-hard-ladder.xlsx`](../../src/test/resources/delivery/any-site-hard-ladder.xlsx)

This is **not** SauceDemo-specific. Steps use `[[PLACEHOLDERS]]` you replace with **labels/text from the live UI** of whatever site you point the job at.

## How to use

1. Copy the xlsx (keep the original as template).
2. Search-replace every `[[...]]` with real wording from the app (button text, product names, error strings).
3. Job settings:
   - **Base URL** = that app’s URL  
   - **Username / password** = TARGET creds (used for login prelude + `${TARGET_*}` emit)  
   - Mode **NEW**
4. Run via portal upload or CLI (`docs/ops/local-live-ollama.md`).
5. Read `AUTOMATION_SCORE.md` + `ir/*.json` — farthest = highest `TC_HARD_0N` that is honestly PASSED (or honest TODO on negative/ambiguous).

Optional: set `delivery.honesty-demote=true` so thin PASSes demote.

## Ladder (difficulty)

| TC | Stress | What “far” means |
|----|--------|------------------|
| **01** | Valid login + landmark | Bind + login prelude + assert |
| **02** | Wrong password + error text | Honesty (must not fake PASS into app) |
| **03** | Nav + section landmark | Multi-page URL stamping |
| **04** | Two **different** named items | Distinctive-token binder (no silent swap) |
| **05** | 3-field form + success text | TYPE_FIELD + submit + textContains |
| **06** | Visible → action → **notVisible** | State assert codegen/prove |
| **07** | Long E2E (18 steps) | Ceiling: multi-page + names + form + finish |
| **08** | Vague “main button” | Heal (Ollama/Cursor) or honest TODO |

Suggested order: run **01 → 02 → 04 → 07** first; add 03/05/06/08 when those are green.

## Placeholder cheat sheet

| Token | Fill with |
|-------|-----------|
| `[[VALID_USERNAME]]` / `[[VALID_PASSWORD]]` | Same as job TARGET (or leave and rely on prelude for 01/07 login) |
| `[[WRONG_PASSWORD]]` | Password that is **not** TARGET |
| `[[AUTH_LANDMARK_TEXT]]` | Text only visible when logged in |
| `[[LOGIN_ERROR_TEXT]]` | Exact/partial error string on failed login |
| `[[ITEM_A]]` / `[[ITEM_B]]` | Two distinct names (products, rows, menu items) |
| `[[ITEM_A_ACTION]]` | e.g. `Add`, `Open`, `Select` |
| `[[FIELD_N_LABEL]]` / `[[FIELD_N_VALUE]]` | Form label wording + value to type |
| `[[FORM_SUBMIT_BUTTON]]` / `[[FINISH_OR_CONFIRM_BUTTON]]` | Visible button text |
| `[[FLOW_SUCCESS_TEXT]]` | Final success message |

## Regenerate template

```bat
python scripts/gen_any_site_hard_ladder.py
```
