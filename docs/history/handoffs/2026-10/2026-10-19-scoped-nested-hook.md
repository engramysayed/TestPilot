# 2026-10-19 — Scoped nested preferred hook

**Status:** Implemented on main. Rebuild the portal jar (`rebuild-portal.cmd`), `start-portal.cmd`, then Ctrl+F5 before trusting `:8080`.

Spec: `docs/superpowers/specs/2026-10-19-scoped-nested-hook-design.md`  
Plan: `docs/superpowers/plans/2026-10-19-scoped-nested-hook.md`

## What shipped

After bind / vision / remembered rewrite, `NestedPreferredHook` still turns a unique inner preferred hook into CSS when that CSS matches one node in the whole document.

When the bound locator uniquely matches **one** row and that row contains **one** preferred hook that Ant (or similar) also cloned **outside** the row, Keel no longer keeps the wrapper xpath. It rewrites to a **scoped** locator so the click target is the inner hook of that unique row:

`//li[normalize-space(.)='Bill Payment Settlement']//a[@data-axis-test-id='side-nav-bill-payment-settlement-link']`

Bare CSS that would hit both copies is not used. Two different hooks inside one row keep the original locator. Twin Settings stay AMBIGUOUS (bound locator is not exactly one node).

## How to use

Same as nested hook: project **Settings** preferred hooks (e.g. `data-axis-test-id`). No new UI.

## Known defects

- Live jar not updated until rebuild + restart + Ctrl+F5.
- If the unique row **is** the hook node and CSS is not document-unique, the original locator is kept (already uniquely identified that copy).
- Two `li`s with the same label still skip rewrite.
- Admin review-batch size and browser type remain spec-only (`docs/superpowers/specs/2026-10-19-admin-review-batch-and-browser-design.md`).
