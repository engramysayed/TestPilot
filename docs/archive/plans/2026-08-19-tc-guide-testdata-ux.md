# TC guide TestData UX — Implementation Plan

> **For agentic workers:** Use executing-plans in this session. Do **not** commit unless the user asks.

**Goal:** Document TestData / VisualAssertion on `/tc-guide` and surface a compact strip on `/upload` that deep-links to those rules.

**Architecture:** Content + CSS only. Extend existing `tc-guide.html` sections and `upload.html` promo; add guide CSS in `portal.css`. No Java/API changes.

**Tech Stack:** Thymeleaf HTML, `portal.css`, existing guide visual language.

## Global Constraints

- Match converter behavior from `2026-08-18-testdata-locator-memory-design.md`
- Preserve existing guide layout / portal tokens; no purple-on-white redesign
- Honor `prefers-reduced-motion`
- Do not commit unless asked

---

### Task 1: Update `/tc-guide` content + visuals

**Files:**
- Modify: `src/main/resources/templates/tc-guide.html`
- Modify: `src/main/resources/static/css/portal.css`

- [x] Extend sheet mock with VisualAssertion + TestData optional headers and sample cells
- [x] Document both in legend bullets
- [x] Add `#testdata` section: Steps|TestData alignment + avoid/prefer
- [x] Add `#visual` short note
- [x] TOC links; AI tip mentions new headers
- [x] CSS for alignment grid + optional motion (reduced-motion safe)

### Task 2: Upload compact strip

**Files:**
- Modify: `src/main/resources/templates/upload.html`
- Modify: `src/main/resources/static/css/portal.css` (if strip needs styles)

- [x] Upgrade `guide-promo` with mini legend, three rule chips, CTA to `/tc-guide#testdata`

### Task 3: Smoke-check

- [x] Confirm templates render (no Thymeleaf syntax errors); optional local portal glance if running
  - Static check: `tc-guide.html` / `upload.html` class hooks match `portal.css`; Thymeleaf remains fragment-only (`pageHead`, `sidebar`). Portal not running in this session — open `/tc-guide` and `/upload` locally to glance layout.
