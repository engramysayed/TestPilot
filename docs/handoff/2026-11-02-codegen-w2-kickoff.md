# Codegen W2 kickoff — 2026-11-02

**Assigned by:** Maestro (user pick via Leader)  
**Base tip:** `83b9077` (live `D:\Work\projects\Keel\keel\TestPilot`)  
**Hold:** P2 / P3 / W3 until Leader says otherwise

## Scope (DoD)

1. **Semantic locator ladder** — prefer role → label → testid → then CSS (honor W1 preferred-hook-first; do not regress empty-hooks proven primary).
2. **Pick-locator playground** — uniqueness-aware pick (reject ambiguous / non-unique candidates).
3. **Merge re-generate** — re-emit into the existing page class (no `LoginPage2` / duplicate page classes).
4. **Trimmed ARIA / region asserts** — emit lean ARIA/region assertions where appropriate.
5. **Still one Java class per page** (locators + actions + asserts together) + `isLoaded()` from W1.

## Constraints

- Live-apply on Ramy; **backup before apply** under `_backup\`.
- Codegen-only paths (emit / PageAccumulator / templates / Assertion / focused tests).
- **No push**, no merge to main without user OK; prefer no commit unless Maestro asks.
- Serialize Maven on live tree (one reactor at a time) — portal may be running; do not overlap portal rebuilds.
- Patch + SHA256 + handoff under `docs/handoff/` when GREEN.
- Sample evidence under `docs/handoff/samples/` if useful.

## Verify (Codegen)

Focused Surefire covering W1 suites **plus** new W2 tests; report run/fail/error/skip + BUILD. Include before/after emit snippets for ladder, uniqueness reject, merge-into-existing-class, ARIA assert.

## QA

After Codegen claims GREEN: re-gate same focused tests; write `docs/handoff/2026-11-02-qa-gates-codegen-w2.md`.
