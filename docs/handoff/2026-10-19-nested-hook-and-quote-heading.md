# 2026-10-19 — Nested preferred hook + leftover Expected quoting

**Status:** Implemented (Tasks 1–7 on main). Rebuild the portal jar and Ctrl+F5 before trusting a server already on `:8080`.

Plan: `docs/superpowers/plans/2026-10-19-nested-hook-and-quote-heading.md`

## What shipped

- **Named click/type bind:** after bind picks a wrapper row, look **inside** that node. **One unique** preferred hook in the **whole document** (project Settings, e.g. `data-axis-test-id`) → rewrite to that CSS. Zero or two-or-more → keep xpath. Twin Settings buttons stay AMBIGUOUS.
- **Vision:** `PreferredHooksStore` site hooks on the observed node or one unique child; ranker scores those hooks like test ids.
- **Review cases:** deterministic pass after the model strips filler from leftover Expected and quotes distinctive names. **Accept & run** still saves the library. Pre-run review still does not see the live page.
- **Execute:** before assert bind (not heal), **LiveHeadingQuote** may rewrite a quoted leftover to the **live** heading/card title when **exactly one** visible title contains the filtered tokens. **OutcomeKind** quote-wins-first unchanged. Asserts never heal. Execute does not write the library.

## How to use

1. Rebuild and restart the portal, then Ctrl+F5.
2. Configure preferred hooks under project **Settings** (e.g. `data-axis-test-id`).
3. Optional **Review cases before run** on Execute or Automate — check leftover Expected rows; Accept saves quotes into the library, then the run starts.
4. On Execute, a leftover such as `'Upload Transactions'` may become a wait on `'Upload Transactions List'` only when that heading is unique on screen.

## Known defects

- Live jar not updated until rebuild + restart + Ctrl+F5.
- Pre-run review cannot see the live heading (Execute supplies the exact string when uniqueness holds).
- Headings outside the supported selectors (`h1`–`h3`, role=heading, common card-title classes) are not upgraded (zero matches).
- Asserts never heal; Execute does not persist runtime quote fixes to the library.
- Twin Settings (or any duplicate same-name controls) stay AMBIGUOUS.
- Admin review-batch size, remaining-cap counter, and `rebuild-portal.cmd` remain a separate unwritten spec.

## Spec

`docs/superpowers/specs/2026-10-19-nested-hook-and-quote-heading-design.md`
