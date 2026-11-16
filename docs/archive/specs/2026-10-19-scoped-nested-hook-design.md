# Scoped nested preferred hook

**Date:** 2026-10-19  
**Status:** Implemented (portal authoring + Execute; rebuild jar + Ctrl+F5 to pick up)  
**Plan:** `docs/superpowers/plans/2026-10-19-scoped-nested-hook.md`  
**Handoff:** `docs/history/handoffs/2026-10/2026-10-19-scoped-nested-hook.md`

## Problem

`NestedPreferredHook` only rewrites a wrapper xpath to inner-hook CSS when `tag[attr='value']` matches **exactly one node in the whole slim document**. After a submenu opens, Ant Design clones the item links into a popup. Support’s trigger hook stays unique (`span[data-axis-test-id=…]`). Bill Payment Settlement’s inner `a[data-axis-test-id='side-nav-bill-payment-settlement-link']` exists twice, so Keel keeps `//li[normalize-space(.)='Bill Payment Settlement']`. The click still passes; the preferred hook is never the locator.

## Goal

When the bound locator uniquely matches **one** row, click the **inner preferred hook of that row**. A second copy of the same hook outside that row (Ant popup) must not block the rewrite and must not become bare CSS that hits two links.

## Non-goals

- Treating two different same-name controls (twin Settings buttons) as one control.
- Counting uniqueness by `display:none` / `ant-menu-hidden` class lists.
- Bare CSS `a[data-axis-test-id='…']` when that selector matches two nodes.
- Leftover Expected quoting, OutcomeKind, Admin review-batch, browser type.
- Healing asserts. Execute writing the library.

## Rule

Same entry point: `NestedPreferredHook.rewrite` (author bind, vision xpath, remembered click/type/clear/select).

1. Resolve the bound locator against the slim (or live) DOM. If it does not match **exactly one** node, skip.
2. On that node and its descendants, collect preferred/test-hook attributes whose value is a trustworthy identifier (unchanged).
3. **Document-unique CSS:** if exactly one collected `tag[attr='value']` matches one node in the **whole document**, rewrite to that CSS (today’s Support path).
4. **Row-unique hook, document duplicate:** if exactly one collected hook matches one node **inside the unique row** but that CSS matches more nodes in the document, and that hook node is a **descendant** of the row (not the row itself):
   - xpath row → `{originalXpath}//{tag}[@{attr}='{value}']`
   - css row → `{originalCss} {tag}[{attr}='{value}']`
   - Keep the rewrite only when the scoped locator still matches **exactly one** node.
5. If the unique row **is** the hook node and CSS is not document-unique, keep the original locator (it already uniquely identified this copy).
6. Zero or two-or-more different hooks unique **inside** the row → keep the original locator.

## Examples

| Slim HTML | Bound locator | Result |
|-----------|---------------|--------|
| One `li` with one inner hook | `//li[normalize-space(.)='Bill Payment Settlement']` | `css` `a[data-axis-test-id='side-nav-bill-payment-settlement-link']` |
| Unique `li` plus a second hook copy **outside** that `li` | same xpath | `xpath` `//li[normalize-space(.)='Bill Payment Settlement']//a[@data-axis-test-id='side-nav-bill-payment-settlement-link']` |
| Two different hooks inside one `li` | same xpath | original xpath |
| Two `li`s with the same label | same xpath (0 or 2 matches) | original xpath (skip) |

## Tests

- Unique inner hook still becomes CSS (`rewrite_innerUniqueHook_becomesCss`).
- Duplicate hook **outside** the unique row becomes the scoped xpath (replace `rewrite_duplicateHook_keepsXpath`).
- Two different hooks **inside** the same row keep xpath.
- Remembered wrapper xpath with an outside copy becomes the same scoped locator before execute.

## Product copy

Guide / PRODUCT: unique-in-page CSS as today; a second copy **outside** the unique row becomes a scoped inner-hook locator, not a keep-xpath miss.
