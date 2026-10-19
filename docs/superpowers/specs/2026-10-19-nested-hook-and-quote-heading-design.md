# Nested preferred hook + leftover Expected quoting

**Date:** 2026-10-19  
**Status:** Implemented (portal authoring + Execute; rebuild jar + Ctrl+F5 to pick up)  
**Plan:** `docs/superpowers/plans/2026-10-19-nested-hook-and-quote-heading.md`  
**Handoff:** `docs/handoff/2026-10-19-nested-hook-and-quote-heading.md`  
**Live miss:** Execute `exec_e0d85b6abce0` TC_3 — click Bill Payment Settlement used `//li[normalize-space(.)='Bill Payment Settlement']` while the inner link had `data-axis-test-id="side-nav-bill-payment-settlement-link"`; leftover Expected `the Upload Transactions list area is visible` became `AMBIGUOUS:ASSERT_VISIBLE` though the card heading **Upload Transactions List** was on screen.

## Problem

Ant Design menu rows are a wrapper `<li role="menuitem">` plus an inner `<a data-axis-test-id="…">`. Bind treats those as two named controls (`a` vs `li`/`menuitem` are not `locatorTwins`), so a quoted click goes AMBIGUOUS and vision `elementFromPoint` lands on the `li`. Vision’s hardcoded `data-testid` / `data-test` / `data-qa` list never reads the site preferred hook on the child. The click still works; memory stores xpath.

Leftover Expected that names a heading without quotes is `ASSERT_VISIBLE`, not a text wait. Pre-run review does not see the live page. Vision is not eligible on asserts. Asserts never heal.

## Goal

1. **Nested hook (approach A).** After a named click binds to a row, look inside that node. If **exactly one unique** preferred hook exists on the node or a descendant, rewrite to that CSS. Otherwise keep the xpath.
2. **Vision uses the same preferred-hook list** (site `PreferredHooksStore`, e.g. `data-axis-test-id`), on the observed node or one unique child.
3. **Review + Execute quoting (option C).** Filter filler words, quote leftover distinctive names in review, then Execute rewrites to the live heading only when **exactly one** visible heading/card title contains those filtered words.

## Non-goals

- Treating two different same-name controls (twin Settings buttons) as one control.
- Healing asserts, rewriting locators after an assert fail, or changing OutcomeKind quote-wins-first.
- Writing the library from Execute (runtime quote correction is for that run only).
- Sending screenshots into pre-run review.
- Review “learning” quotes as free LLM reasoning.
- Admin review-batch size, remaining-cap counter, `rebuild-portal.cmd` (separate unwritten spec).
- Midscene, Claude/Anthropic, charging review, `api-keys.local.bat`.

## Nested preferred hook

Approach **A** (chosen): look inside after the named row is chosen. Do **not** drop wrapper xpath at extract time (approach B). Do **not** rely on vision to recover (approach C).

### When

After bind (or vision ground) produces a click/type locator, before execute.

### Rule

1. Resolve the chosen locator against the slim DOM. If it does not match exactly one element, skip.
2. On that element and its descendants, collect attributes from `PreferredHooksStore.current()` (and built-in test-hook attrs: `data-*` containing `test`, plus `data-qa`) whose value is a usable identifier.
3. Keep a candidate only when `tag[attr='value']` matches **exactly one node in the whole document** (not merely unique inside the subtree). A duplicated Ant menu copy must not become CSS that hits two links.
4. **One** surviving hook → rewrite the step to `css` `tag[attr='value']`. **Zero or two-or-more** → keep the original locator (usually the `li` xpath).
5. Do not walk outside the chosen node (no parent, no siblings). Two Settings buttons stay AMBIGUOUS.

### Vision

`SeleniumGroundingBrowser` `elementFromPoint` today:

- Reads preferred attrs only on the observed node.
- `dataTest` is only `data-testid` / `data-test` / `data-qa`.

Change:

- Pass `PreferredHooksStore.current()` (already argument `[2]`).
- If the observed node has no unique preferred/test hook, look at descendants with the same uniqueness rule as bind.
- Named-text walk may still select the `li`; the child-hook step then rewrites to the inner `<a>` CSS.
- `LocatorRanker.TEST_ID_ATTRS` and `ProveWiring.currentScore` must count preferred hooks the same way (`data-axis-test-id` is a test id, not generic CSS).

`PageLocatorCatalog.preferStable` already prefers `data-axis-test-id` CSS when that candidate is in the catalog. Nested collapse still runs after bind so a wrapper xpath can become the child hook when that CSS is unique in the document. If HtmlSlimmer marked the hook document-ambiguous, uniqueness fails and the xpath stays.

## Leftover Expected quoting

Filler **never** inside quotes (case-insensitive whole words):

`area`, `button`, `label`, `title`, `page`, `section`, `field`, `menu`, `item`, `list`, `visible`, `shown`, `displayed`.

### Review (deterministic pass after the model)

Runs in `AuthoringReviewService` on `cases[]` after the LLM (Keel or Cursor). Prompt may teach the same rule; the pass is the source of truth so gemma cannot invent `'Upload Transactions list area'`.

For each leftover Expected clause that is not already a quoted phrase:

1. Strip filler words.
2. Remaining distinctive name must have at least two tokens **or** one capitalized proper name of length ≥ 3. Otherwise leave unchanged.
3. If Steps or title already quote a string whose filtered tokens equal that remainder, reuse that quote exactly (`'Bill Payment Settlement'`).
4. Else wrap the filtered leftover (`'Upload Transactions'`).
5. Do not quote only filler. Do not add new cases.

Apply the same wording in `tools/cursor-heal/heal.mjs` authoring-review prompt so Cursor sidecar stays aligned; portal still runs the Java pass.

### Execute (before assert bind, not heal)

When an Expected clause is unquoted leftover / page-shown wording **or** a quoted leftover that may be shorter than the live heading:

1. Filter as above → token set (e.g. `upload`, `transactions`).
2. Collect visible heading/card titles: `h1`–`h3`, `[role=heading]`, `.ant-card-head-title`, `[class*=card-head-title]`, `[class*=card-title]` — displayed, non-empty, trimmed.
3. Match = title contains every filtered token (case-insensitive).
4. **Exactly one** match → rewrite that clause to a quoted text wait on the **live** title (`'Upload Transactions List'`). OutcomeKind then `QUOTED_TEXT` / `textContains`.
5. **Zero or two-or-more** → do not rewrite; today’s `ASSERT_VISIBLE` / AMBIGUOUS stands.
6. Do not write the library. Asserts still never heal. Do not call vision for this rewrite.

## Data flow

```text
named click bind
  → NestedPreferredHook.rewrite(step, slimHtml, preferredHooks)
  → execute click
  → leftover Expected
       review already quoted filtered leftover
       → LiveHeadingQuote.maybeRewrite(clause, liveHtml)
            1 heading → quoted live title
            else unchanged
       → OutcomeKind.classify → bind assert
```

## Tests (TDD)

- `li` + inner `a[data-axis-test-id='side-nav-bill-payment-settlement-link']`, unique hook → click `'Bill Payment Settlement'` binds CSS hook, not `li` xpath.
- Same HTML with **two** identical `data-axis-test-id` values in the document → keep xpath.
- Two `button` Settings with different ids → still TIE / AMBIGUOUS.
- Vision grounder fixture: observed `li` with child preferred hook → CSS on the child.
- Review pass: `the Upload Transactions list area is visible` → `'Upload Transactions'` (not `list`/`area`).
- Review pass: Expected leftover tokens already quoted in Steps → reuse that quote.
- Execute heading: one `h1`/`card-title` `Upload Transactions List` + leftover tokens upload+transactions → `'Upload Transactions List'`.
- Execute heading: two titles containing those tokens → unchanged.

## Files (planning)

| Unit | Responsibility |
|------|----------------|
| `NestedPreferredHook` (new) | Look inside chosen locator; unique document hook → CSS |
| `StepIntentBinder` / authoring after bind | Call rewrite on click/type steps |
| `SeleniumGroundingBrowser` / `ElementGrounder` | Preferred hooks + unique child |
| `LocatorRanker` / `ProveWiring.currentScore` | Preferred hook scores as test id |
| `ExpectedQuoteFilter` + review post-pass (new) | Filler strip + leftover quotes |
| `AuthoringReviewService` + `heal.mjs` | Prompt + Java pass |
| `LiveHeadingQuote` (new) | Unique live heading rewrite before assert bind |
| `ProvePhase` | Call heading quote on leftover Expected |

## Known defects this spec does not fix

- Pre-run review still cannot see the live heading; Execute supplies the exact string when uniqueness holds.
- If the card title is not one of the listed heading selectors, Execute will not rewrite (zero matches).
