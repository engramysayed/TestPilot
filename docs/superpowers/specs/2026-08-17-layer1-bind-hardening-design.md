# Layer-1 bind hardening design

**Date:** 2026-08-17  
**Status:** Approved for planning (from audit findings)  
**Plan:** `docs/superpowers/plans/2026-08-17-layer1-bind-hardening.md`

## Problem

Static review of DOM extract → score → winner found Important defects: apostrophe stripping in xpath, caption walks aborting on icons, substring label uniqueness, document-global ordinals, aggressive AMBIGUOUS fallback, and failed-locator exclusion only in heal.

## Goals

- Correct xpath literals for apostrophes everywhere Layer 1 builds xpath.
- AccessibleName finds captions past decorative siblings.
- Label-anchored strategies use exact label text uniqueness.
- Indexed form-control locators are type-scoped; no `nth-of-type` CSS for checkbox/radio.
- AMBIGUOUS honesty preserved; no silent first-id winner on LLM failure.
- In-TC bind respects failed-locator ledger like heal.

## Non-goals

- Changing heal LLM prompts or invent tier.
- Widening near-tie window beyond ±1 without a separate decision.
- Site-specific locator rules.
