# P0-03 release benchmark

Controlled fixtures for first-release result trust (F00), Update reuse (P1-02/P1-03), prerequisite replay (P1-04), and sensitive-field sanitation (P2-04). Serve `pages/` as static files. Do not point this benchmark at a customer website.

**Status:** fixture definitions recorded before runs. First recorded execution: [run-2026-09-16.md](run-2026-09-16.md). Proof coverage, assertion correctness, replay success, and stability are separate metrics — do not roll them into one pass rate.

**Serve locally:** from this directory, `python -m http.server 8765 --directory pages` then use base URL `http://127.0.0.1:8765`.

## Pages

| File | Role |
|------|------|
| `login.html` | Username/password login; wrong credentials show `Invalid credentials`; success navigates to home. Password is a sensitive field (`data-sensitive="credential"`). |
| `home.html` | Heading `Dashboard` (deliberately **not** "Admin Home"). Shared landing used by several cases. |
| `shop.html` | Catalog; Add widget is a link to `cart-full.html` (non-login prerequisite for checkout). |
| `cart.html` | Empty cart (`Cart is empty`) used by the empty-checkout path. |
| `cart-full.html` | Cart with one item; checkout control goes to `pay.html`. |
| `checkout.html` | Empty-cart checkout (`Cannot checkout`). |
| `pay.html` | Ready-to-pay page; Place order goes to `confirmed.html`. |
| `confirmed.html` | `Order confirmed`. |

## Datasets

`datasets/cases.csv` is the authored workbook stand-in. Expected outcomes live in `expected-outcomes.md` and must not be edited to match a failing run.

## Sequences

See `sequences.md` for NEW → downloaded replay → UPDATE → downloaded replay → repeated UPDATE, including concurrent same-host jobs (those remain expected **fail** until P2-02).
