# Keel portal redesign, domain store admin, ui-tars vision

**Date:** 2026-08-21  
**Status:** Approved  
**Product name:** **Keel** (replaces “TestPilot Delivery” in user-facing UI)  
**Priority:** A → B → C

## Locked decisions

| Topic | Decision |
|---|---|
| Product name | **Keel** — short, memorable; logo + all chrome |
| Feel | Serious enterprise; **brighter** palette (still professional, not playful) |
| Scope UX | **Whole portal** (login, shell, dashboard, projects, upload, jobs, status, TC guide, account, admin) |
| Login layout | **2:1 split** — ~2/3 form, ~1/3 brand panel with logo |
| Copy | Remove deep tech jargon from customer-facing text; plain language |
| Motion | Intentional UI motion; honor `prefers-reduced-motion` |
| Admin domain tools | Admin-only page: domain dropdown, stats, delete |
| Delete semantics | Deleting a domain removes **the entire domain folder and everything inside** (memory, maps, projects, frameworks, zips). Requires type-to-confirm |
| Store layout | `delivery-store/<domain>/<projectId>/…` — domain folders; each new project is a subfolder |
| Vision (C) | Prefer **`ui-tars`** (fits 4 GB A2000); keep Qwen as configurable fallback |
| Out of scope | Full StepAssessment / ambiguity LLMPlanner redesign; cloud VLMs; renaming Java packages / Maven artifact (UI brand only unless trivial) |

## A — Brand + whole-portal UX

### Logo
- SVG mark + wordmark “Keel”
- Concept: stable keel / guiding line under a simple craft or path — enterprise, not cartoon
- Assets: `static/img/keel-logo.svg`, favicon, sidebar mark, login panel

### Visual system
- Brighter enterprise tokens: lighter panels, clearer accent (teal/blue), stronger contrast for muted text
- Avoid: purple-on-white, cream+terracotta, newspaper broadsheet, excess glow/pills
- Typography: keep distinctive display + body (may refine fonts; no Inter/Roboto/Arial-only stack)
- Motion: sidebar/nav fade, panel reveal, subtle brand panel animation on login; reduced-motion = static

### Login (2:1)
- Grid: form column (~2) | brand column (~1)
- Brand: logo, short tagline (“Manual cases → proven automation”), atmosphere gradient/pattern
- Form: email, password, Sign in — plain errors (“Wrong email or password”)

### Portal chrome
- Sidebar brand → Keel logo + name
- Nav labels stay human (Dashboard, Projects, Upload, Guide, Jobs, Admin…)
- Soften status/upload/guide copy that currently sounds like engineer notes

### Pages in scope
All Thymeleaf templates under `src/main/resources/templates/` + `portal.css` (+ small JS if needed).

## B — Domain store layout + admin delete

### Layout
```
delivery-store/
  <domain>/                 # e.g. saucedemo-com
    <projectId>/            # each portal/CLI project for that domain
      domain-locator-memory.json
      locator-map.json
      framework/
      versions/
      …
  portal-db.*               # stays at store root (not under a domain)
```

- New jobs resolve/create `storeRoot/domain/projectId`
- Migration: on first use of admin domains API or store helper, optionally relocate flat legacy roots that look like domain projects under `domain/projectId` (document carefully; don’t move H2 DB files)

### Admin UI (`/admin/domains`) — ROLE_ADMIN only
1. Dropdown of known domains (scan `delivery-store` children that are domain dirs)
2. On select, show:
   - Project count under domain
   - Locator memory entry count (sum or primary file)
   - Locator-map presence / size
   - Last modified time of domain folder (or latest project)
3. **Delete domain** button → modal: type domain name to confirm → DELETE API removes **entire** `delivery-store/<domain>/` recursively
4. List in sidebar under Admin next to Users

### API
- `GET /api/admin/domains` — list + summary stats
- `GET /api/admin/domains/{domain}` — detail
- `DELETE /api/admin/domains/{domain}` — full wipe (admin only); reject path traversal

## C — Vision provider default / switch

- Document and set recommended local run: `delivery.vision.provider=uitars` (model from config / `ui-tars`)
- Keep `qwen` path intact
- No architecture rewrite; prompt template from prior plan stays

## Success criteria

- Login shows Keel logo in 2:1 layout; sidebar branded Keel
- Portal copy noticeably less jargon; brighter enterprise look across main pages
- Admin can list domains, see counts, delete a domain folder entirely with confirm
- New store paths nest under domain; existing runs still findable after migration helper
- ui-tars selectable/documented for 4 GB GPU

## Non-goals

- Rewriting conversion engine internals beyond store path helpers
- Making vision “perfect”
- Public self-signup or non-admin domain delete
