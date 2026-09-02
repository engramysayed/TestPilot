# Honest bind + heal recovery

**Date:** 2026-08-17  
**Status:** approved (chat), pending implementation plan approval

## Problem

Bind and heal were scoring chrome nodes as the named control because adjacent accessible-name resolution copied a neighboring layout `div`’s entire text onto a sibling link. Text-phrase asserts then hit `AMBIGUOUS` on those chrome rows and never used the body `textContains` xpath. After execute failed, heal ranked the same dead locator first because failed tries were not banned and the real named control was not tried deterministically.

## Non-negotiable

- **Site-agnostic.** No hostnames, brands, product names, known demo URLs, `href` hosts, alt-text of a specific site, or “if this looks like site X” branches in production or tests that encode a real public site.
- Tests use **synthetic layout HTML** (banner link beside a content column, footer after a row, hidden phrase nodes).
- Honesty over false PASS. Layer 3 may still exhaust when the control is not on the page.

## Layer 1 — bind

1. Adjacent name is a **caption**, not a layout block.
2. `"Confirm the text X is visible"` (and quoted / message / heading variants already parsed by `extractAssertTextPhrase`) always binds body `textContains` xpath. No `AMBIGUOUS` on that path.
3. `id`, `tag[id='…']`, and `//*[@id='…']` of the same tag are one control.
4. Token match is word-ish: `element` does not match `elemental`; hyphen compounds still match (`bike` in `bike-light`).

## Layer 2 — Ollama

Pick `candidateId` only. Skip when a single remaining named control exists (deterministic bind). No score rewriting.

## Layer 3 — execute-fail recovery

1. Ledger of failed locators for this intent; drop them and same-control twins.
2. Try remaining named rows in score order **before** Ollama.
3. Optional live probe: drop displayed=false or width/height 0.
4. Then Ollama → Cursor on what remains, with banned locators in the failure text. Invent unchanged (last-hope, gated).

## Out of scope

Login `${TARGET_USERNAME}` rewrite, AgentRouter final revise, invent-provider changes, site-specific chrome denylists.
