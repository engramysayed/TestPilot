# Plain-English UI copy pass (2026-11-02)

**Status:** GREEN  
**Bot:** Frontend (executor on Ramy)  
**Tip SHA (dirty tree OK):** started ~`e9ee274`; handoff tip `ce4bc2c` (`docs(portal): phase3 interim API contract…`) + uncommitted template/JS copy edits  
**Backup:** `_backup\backup-pre-plain-english-ui-20261102-194543` (includes suites.html + fragments.html added mid-pass after P3 landed)  
**Time (Africa/Cairo):** 2026-11-02 ~19:55+  
**Jar refresh needed:** **yes** (Thymeleaf templates + static JS under `static/js/`) — Maestro owns portal rebuild/restart. No push. No Maven from this bot.

## Goal

Simplify complicated technical words in portal **UI strings only** (templates, inline JS user-visible strings, sidebar labels). Everyday English. Did **not** rename API paths, JSON keys, Java, DB, CSS class selectors, element ids JS binds to, or data-* attributes. Product feature names kept where useful with a short plain phrase nearby.

## Files touched

### Templates
1. `src/main/resources/templates/execute.html`
2. `src/main/resources/templates/evidence.html`
3. `src/main/resources/templates/generate.html`
4. `src/main/resources/templates/generate-from-knowledge.html`
5. `src/main/resources/templates/bug-hunter.html`
6. `src/main/resources/templates/upload.html`
7. `src/main/resources/templates/record.html`
8. `src/main/resources/templates/packages.html`
9. `src/main/resources/templates/status.html`
10. `src/main/resources/templates/project-detail.html`
11. `src/main/resources/templates/projects.html`
12. `src/main/resources/templates/guide.html`
13. `src/main/resources/templates/tc-guide.html`
14. `src/main/resources/templates/suites.html` (after P3 GREEN)
15. `src/main/resources/templates/fragments.html` (sidebar labels after P3 GREEN)

### Static JS (user-visible label maps / empty strings)
16. `src/main/resources/static/js/keel-labels.js`
17. `src/main/resources/static/js/keel-mobile-results.js`

### Scanned, little/no jargon left (no material edit)
- `plain-cases.html`, `jobs.html`, `dashboard.html` — already everyday English for high-traffic copy
- `portal.js`, `keel-confirm.js`, `portal-nav.js`, `keel-credits.js` — no material jargon user strings beyond approval wording already plain

### Intentionally not rewritten
- `PortalUiController.java` (P3 owns wiring; copy-only pass skipped Java)
- `keel-sidebar-ia.js` (paths/permissions only; no user-facing glossary strings worth changing)
- Phase 3 kickoff / contract handoff files

## Before → after examples (≥8)

| # | File | Before | After |
|---|------|--------|-------|
| 1 | execute.html | DOM and vision — find bugs without codegen first | page structure and screen vision — find bugs before building automation code |
| 2 | execute.html | Approve baseline | Approve saved good screenshot |
| 3 | execute.html | Flaky (retry tile) | Sometimes fails (title: sometimes fails for no clear reason) |
| 4 | execute.html | Healed | Fixed broken steps |
| 5 | evidence.html | Heal proposals / Failure replay | Fix suggestions (heal) / What happened |
| 6 | evidence.html | Flaky chip | Sometimes fails / Mark as sometimes fails |
| 7 | generate-from-knowledge.html | Knowledge packs / Ingest… / Upload TCs | Case packs (test knowledge files) / Add from… / Upload cases |
| 8 | bug-hunter.html | DOM mode / Full slim DOM | Page scan mode / Full slim page scan |
| 9 | record.html | each click kept a locator | each click saved how we find that control on the page |
| 10 | packages.html | leftover prove IR / Prove cleanup | leftover proven-case drafts / Proven-case cleanup |
| 11 | keel-labels.js | FLAKY: 'Flaky'; HEALED: 'Fixed itself' | Sometimes fails / Fixed broken steps |
| 12 | suites.html | Create suite / No suites yet / Run suite now | Create test pack / No test packs yet / Run test pack now |
| 13 | fragments.html | Suites / Visual diffs / From knowledge | Suites (test packs) / Screenshot diffs / From knowledge packs |

## Deferred / out of scope this pass
- Server-side API error message strings (Backend owns if any user-facing)
- Excel column header names that must match workbook contract (`VisualAssertion` kept in sheet mockups; surrounding prose says “visual check”)
- Further Admin pages (pricing/users/roles/llm) — not in priority list
- Sonic / Postman

## Verify notes
1. Diff backup vs live: API path sets and element `id=` sets unchanged on suites/evidence/execute (spot-checked).
2. CSRF header `X-Keel-Requested-With` unchanged.
3. After Maestro jar refresh: hard refresh **Ctrl+F5**.
4. Spot-check pages: `/execute` (screenshot diffs + sometimes-fails tiles), `/evidence` (fix suggestions / what happened), `/generate-from-knowledge` (case packs), `/suites` (test packs wording), sidebar Suites / Screenshot diffs.
5. No portal restart by this bot. No push.

## READY path
Portal copy reads plainly on high-traffic Features pages + Suites + sidebar; Maestro refreshes jar so templates/JS ship.
