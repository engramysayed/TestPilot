# Controlled local acceptance — expected outcomes (recorded before execution)

**Label:** controlled local acceptance fixture. **Not** representative third-party application validation. **Not** Medusa. Launch remains **HOLD**. Candidate `2721e6d` is unchanged.

Recorded **before** Keel jobs, Chrome walks, or ZIP replay. Shop origin will be an ephemeral `http://127.0.0.1:<port>/` served by a JDK `HttpServer` with **in-memory** users/carts/orders. Synthetic data only. Payment is **Pay later (simulated)** — no Stripe, no cards, no AgentRouter, no final revise.

Shop directory (outside this git repo): `D:\priv\testpilot\keel-local-acceptance\`

## Synthetic data

| Kind | Value |
|---|---|
| Seeded customer | `buyer.a@example.test` / `SyntheticPass_A1!` |
| New register | `buyer.new@example.test` / `SyntheticPass_N1!` / name `Buyer New` |
| Invalid password | `WrongPass_xxx` |
| Duplicate email | `buyer.a@example.test` |
| Address | `1 Test Pilot Lane`, city `Testville`, zip `00000` |
| Jacket oracle | Trail Jacket, size **Medium**, color **Blue** |
| Second product | Canvas Tote (no variants) |
| Simulated pay | Button **Pay later (simulated)** — never a card form |
| First order id | `KLA-1001` (monotonic in this process) |

## Challenges built into the fixture (oracles)

| Challenge | Where | What a correct bind must do |
|---|---|---|
| Cookie overlay | First paint of a fresh browser | Click **Accept cookies** (`#accept-cookies`) before header actions. Overlay covers the page. |
| Repeated labels | Cart **Remove**; two **Add to cart** (one hidden) | Remove the **second** Remove. Do not bind the `aria-hidden` decoy Add. |
| Hidden controls | Product **Add to cart** decoy (`aria-hidden`, `display:none`) | Slimmer/extractor must ignore it. Visible add is `#add-visible`. |
| Custom dropdowns | Size / Color are `role=combobox` + `role=listbox`, **not** `<select>` | Open Size, choose **Medium**; open Color, choose **Blue**. |
| Async updates | Add-to-cart spinner ~800ms; stock badge refreshes ~1000ms | Wait for **Added** / cart count; do not pass on the spinner text. |
| Overlay (session) | After **Expire session now** | `#session-overlay` shows **Session expired — sign in again**. Account identity is gone. |
| Delayed errors | Invalid login ~1500ms; blank checkout address ~1200ms | Error text appears **after** the delay. Stay on the same page. |
| Unlabeled glyph | Checkout `#pay-glyph` — empty `<button>`, no accessible name | DOM bind is expected to **miss or weakly bind**. UI-TARS must be **invoked** for `TC_VIS_01`. A DOM-only PASS of other cases does **not** validate grounding. |

## Case pack (Keel IDs must match `TC_[A-Z0-9_]+`)

| ID | Title | CallBefore | Steps (intent) | Expected result (business, not “page changed”) |
|---|---|---|---|---|
| TC_ACC_01 | Create account | — | Accept cookies → **Register** → type name/email/password → Click **Create account** | Header shows `buyer.new@example.test`. Flash **Account created**. |
| TC_ACC_02 | Login | — | Accept cookies → **Open /login.html** → type seeded email/password → Click **Sign in now** | Header shows `buyer.a@example.test`. Login form is gone. |
| TC_ACC_03 | Invalid login | — | Accept cookies → Sign in → seeded email + wrong password → Click **Sign in now** | After delay, **Invalid email or password** is visible. Header does **not** show the buyer email. Still on login. |
| TC_ACC_04 | Duplicate register | — | Accept cookies → Register → type seeded email → Click **Create account** | After delay, **Email already registered** is visible. Not authenticated as a new user. |
| TC_VAR_01 | Jacket Medium Blue | TC_ACC_02 | Open Trail Jacket → Size Medium → Color Blue → Click Add to cart | Cart has **Trail Jacket** line with **Medium** and **Blue**. Not Small/Red. Not the hidden Add. |
| TC_CART_01 | Add two products | TC_ACC_02 | Add jacket (Medium/Blue) and tote | Cart shows **two** lines: Trail Jacket and Canvas Tote. Count **2**. |
| TC_CART_02 | Change tote quantity | TC_CART_01 | On the tote line, set quantity to **2** | Tote qty is **2**. Jacket qty remains **1**. |
| TC_CART_03 | Remove second Remove | TC_CART_01 | Click the **second** Remove | One line remains (the first). Count **1**. |
| TC_CHK_01 | Checkout required address | TC_CART_01 | Open checkout → leave address empty → Click Place order | After delay, **Address is required**. URL still checkout. No order id. |
| TC_CHK_02 | Simulated payment order | TC_CART_01 | Checkout with full address → Click **Pay later (simulated)** | Confirmation shows **Order KLA-** and `buyer.a@example.test`. No card fields. |
| TC_ORD_01 | Order on account | TC_CHK_02 | Open Account | Account lists the same **Order KLA-** id, not only a generic account heading. |
| TC_SES_01 | Session expiry overlay | TC_ACC_02 | Click Expire session now | Overlay **Session expired — sign in again**. Header does not show the email. |
| TC_VIS_01 | Unlabeled pay glyph | TC_CART_01 | Open checkout → Click the unmarked payment glyph | **UI-TARS must run** (log `VISION_GROUND` and/or Ollama `ui-tars`). Oracle control is the visible empty bag button, not decoy text. Success is optional; **invocation is required** to score grounding. |

## UPDATE pack (production reuse — not fixture-IR regenerate)

Same engine as DOM NEW (**Keel**). Same base URL and store.

| ID | UPDATE change | Expected reuse |
|---|---|---|
| TC_ACC_02 | Unchanged steps/expected/testdata | **REUSED** if NEW was PASSED |
| TC_CART_02 | Quantity **2 → 3** (steps + expected) | **Re-author** (changed content hash) |
| TC_CART_01 | Unchanged | Reused if PASSED, unless a prerequisite it depends on is authored |
| TC_CART_03 | Unchanged steps | **Re-author** because CallBefore `TC_CART_01` is **also changed** (jacket color oracle **Blue → Red** in UPDATE) |
| Others | Unchanged | Reuse if PASSED and prerequisites unchanged |

UPDATE also changes **TC_CART_01** expected/steps to add the jacket as **Medium / Red** so it is a real prerequisite change that invalidates `TC_CART_03`.

## Modes

| Mode | Engine | Vision grounding | Cursor key | What “pass” means |
|---|---|---|---|---|
| A | Keel | **off** | unset | DOM bind + execute against oracles. Providers used must not require Cursor. |
| B | Keel | **on** (`uitars` / `ui-tars`) | unset | At least `TC_VIS_01` must show a **real** UI-TARS call. DOM-only PASSes elsewhere are not grounding evidence. |
| C | Precision | **off** | **unset** | Keel fallback with visible `PRECISION_FALLBACK` and reason (expect `PROVIDER_UNAVAILABLE`). **Cursor execution not tested.** Do not put credentials in reports. |

## Production pipeline (Mode A)

1. NEW `ConversionJobRunner` (not dry-run).
2. Download/extract ZIP → `mvn -B clean test` against the **still-running** shop.
3. UPDATE `ConversionJobRunner` with `ReuseEligibility` (not zip-replay fixture regenerate).
4. Extract UPDATE ZIP → `mvn -B clean test`.

## Non-goals

No Postgres, Docker, Node, Medusa, Stripe, AgentRouter, final revise, commits, publish, candidate replace, or launch-gate close.

## Pack/fixture adjustments after first-run defects (not a rewrite of the oracles)

Recorded after the first Keel run failed, still before the rerun:

- Quote synthetic passwords in the Excel step text so TYPE_PASS is not replaced by empty `${TARGET_PASSWORD}`.
- Assert with `Confirm the text … is visible` so page phrases are not scored as chrome controls.
- Put the sign-in form on the home page so downloaded ZIP replay can type email without a missing `Open /login.html` codegen navigation.
- Isolate `TC_VIS_01` on `/vision.html` with no Call-before so UI-TARS can run when DOM bind misses.
- Excel order is independents first, then one contiguous Call-before chain (`ACC_02` → cart → checkout → order → session). `ConversionJobRunner` does not re-expand Call-before before each leaf, so a case between login and cart starts a fresh browser (cookie overlay intercepts).
- After each Add to cart, confirm **Added** so ZIP replay waits out the 800ms async POST (codegen clicks do not inherit prove-time settle waits).
- `Click Submit blank address` must not trigger form auto-fill (`looksLikeSubmit` skips blank/empty named clicks).
- Named `Submit …` clicks bind the distinctive button even when AccessibleName falls back to the element id (no `"submit"` in the candidate hay).
- Button and link accessible names include descendant text, so `Click Pay later (simulated)` binds `id="pay-simulated"` without renaming the fixture. Duplicate visible labels still reject as AMBIGUOUS.
