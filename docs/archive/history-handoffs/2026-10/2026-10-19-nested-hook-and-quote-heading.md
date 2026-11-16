# 2026-10-19 — Nested preferred hook + leftover Expected quoting

**Status:** Implemented (Tasks 1–7 on main), plus the 2026-10-19 post-review fix wave. Rebuild the portal jar and Ctrl+F5 before trusting a server already on `:8080`.

Plan: `docs/superpowers/plans/2026-10-19-nested-hook-and-quote-heading.md`

## What shipped

- **Named click/type bind:** after bind picks a wrapper row, look **inside** that node. **One unique** preferred hook in the **whole document** (project Settings, e.g. `data-axis-test-id`) → rewrite to that CSS. Zero or two-or-more → keep xpath. Twin Settings buttons stay AMBIGUOUS.
- **Vision:** `PreferredHooksStore` site hooks on the observed node or one unique child; ranker scores those hooks like test ids.
- **Review cases:** deterministic pass after the model strips filler from leftover Expected and quotes distinctive names. **Accept & run** still saves the library. Pre-run review still does not see the live page.
- **Execute:** before assert bind (not heal), **LiveHeadingQuote** may rewrite **unquoted** leftover Expected (filler stripped) or a quoted leftover to the **live** heading/card title when **exactly one** visible title contains the filtered tokens. **OutcomeKind** quote-wins-first unchanged. Asserts never heal. Execute does not write the library.

## How to use

1. `rebuild-portal.cmd`, then `start-portal.cmd`, then Ctrl+F5.
2. Configure preferred hooks under project **Settings** (e.g. `data-axis-test-id`).
3. Optional **Review cases before run** on Execute or Automate — check leftover Expected rows; Accept saves quotes into the library, then the run starts.
4. On Execute, an unquoted leftover such as `the Upload Transactions list area is visible`, or a shorter quote such as `'Upload Transactions'`, may become a wait on `'Upload Transactions List'` only when that heading is unique on screen.

## Follow-up (same day)

- Execute strips the Confirm / `Confirm the text "…" is visible` wrapper before matching a live heading, including each clause of a compound expected check.
- A remembered click, type, clear, or select xpath is rewritten with `NestedPreferredHook` against live HTML before execute (single step and precision batch).
- Vision keeps the hit node’s own unique hook when a child also has one.
- Review writes dash markers back onto Expected rows and replaces an already-quoted filler phrase. Distinctive filler-free quotes stay.

## Known defects

- Live jar not updated until rebuild + restart + Ctrl+F5.
- Pre-run review cannot see the live heading (Execute supplies the exact string when uniqueness holds).
- Headings outside the supported selectors (`h1`–`h3`, role=heading, common card-title classes) are not upgraded (zero matches).
- Asserts never heal; Execute does not persist runtime quote fixes to the library.
- Twin Settings (or any duplicate same-name controls) stay AMBIGUOUS.
- Admin review-batch size and browser type remain spec-only. `rebuild-portal.cmd` is in the repo root (package only; it does not start the portal).
- **Follow-up shipped:** a second copy of the inner hook **outside** the unique row now becomes a scoped inner-hook locator. See `docs/history/handoffs/2026-10/2026-10-19-scoped-nested-hook.md`.

## Spec

`docs/superpowers/specs/2026-10-19-nested-hook-and-quote-heading-design.md`
