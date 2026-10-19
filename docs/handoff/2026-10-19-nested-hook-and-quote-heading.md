# 2026-10-19 — Nested preferred hook + leftover Expected quoting

**Status:** Spec approved in chat. Not implemented. Plan not written yet.

## What was decided

- Named click may bind the wrapper `li` xpath. After that, look **inside** the chosen node. **One unique** preferred hook (document-wide CSS) on the node or a child → use that CSS. Zero or two-or-more → keep xpath. Twin Settings buttons stay AMBIGUOUS.
- Vision reads site preferred hooks (`PreferredHooksStore`, e.g. `data-axis-test-id`), not only `data-testid` / `data-test` / `data-qa`, including a unique child.
- Review (deterministic pass after the model) strips filler (`area`, `button`, `label`, `title`, `page`, `section`, `field`, `menu`, `item`, `list`, `visible`, `shown`, `displayed`) and quotes leftover distinctive names. Execute rewrites to the live heading only when **exactly one** visible heading/card title contains those tokens. Zero or two+ → leave unchanged. Asserts still never heal. Execute does not write the library.

## How to use (once built)

Rebuild the portal jar after implementation. Preferred hooks stay in project Settings. Review leftover Expected; Accept still saves the library. Execute supplies the exact on-screen heading when uniqueness holds.

## Known defects (today, before the work)

- Live `exec_e0d85b6abce0` TC_3 click used `li` xpath despite inner `data-axis-test-id`. Last assert `AMBIGUOUS:ASSERT_VISIBLE` with heading **Upload Transactions List** on screen.
- Pre-run review cannot see the live heading (intentional; Execute corrects when unique).

## Spec

`docs/superpowers/specs/2026-10-19-nested-hook-and-quote-heading-design.md`
