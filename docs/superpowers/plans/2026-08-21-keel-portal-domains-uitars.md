# Keel portal + domain admin + ui-tars — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Do **not** commit unless the user asks.

**Goal:** Rebrand the portal as **Keel** with a brighter enterprise UX and 2:1 login, add admin full-domain store wipe, nest store under `domain/projectId`, and document/switch vision to **ui-tars** on capable local GPUs.

**Architecture:** UI/CSS/brand assets first; then store path helpers + admin API/page; then vision config default/docs. No conversion algorithm rewrite.

**Tech Stack:** Thymeleaf, `portal.css`, Spring Security (`ROLE_ADMIN`), existing `ProjectStore` / delivery-store, Ollama `ui-tars`.

## Global Constraints

- Product name in UI: **Keel**
- Delete domain = **entire** `delivery-store/<domain>/` tree (type-to-confirm)
- Store: `delivery-store/<domain>/<projectId>/`
- Vision C: prefer `uitars`; keep `qwen` available
- Honor `prefers-reduced-motion`
- Spec: `docs/superpowers/specs/2026-08-21-keel-portal-domains-uitars-design.md`
- Do not commit unless asked

## File map

| Area | Files |
|---|---|
| Brand | `static/img/keel-logo.svg`, favicon, `fragments.html`, `login.html`, titles |
| UX | `portal.css`, all `templates/*.html`, TC guide copy soften |
| Store | `ProjectStore.java`, job/CLI path resolution, optional migrator |
| Admin | `AdminDomainsController`, `admin-domains.html`, sidebar admin link |
| Vision | `VisionGroundingConfig` defaults/docs; portal/README note |

---

### Task 1: Logo + favicon assets

**Files:**
- Create: `src/main/resources/static/img/keel-logo.svg`
- Create: `src/main/resources/static/img/favicon.svg` (or .ico)

- [ ] Design SVG mark + wordmark “Keel” (enterprise keel/path motif; works on light brand panel and dark sidebar)
- [ ] Add favicon link in `pageHead` fragment
- [ ] Visual check in browser or static open — mark readable at 24px and 120px

---

### Task 2: Login 2:1 + Keel brand panel

**Files:**
- Modify: `src/main/resources/templates/login.html`
- Modify: `src/main/resources/static/css/portal.css`

- [ ] Rebuild login as split layout (~2 form / ~1 brand)
- [ ] Brand panel: logo, “Keel”, short plain tagline
- [ ] Brighter enterprise colors on login; form errors stay plain language
- [ ] Mobile: stack brand above or below form (readable)
- [ ] Reduced-motion: no looping animation

---

### Task 3: Shell + whole-portal rebrand + brighter theme

**Files:**
- Modify: `fragments.html` (brand, titles “— Keel”)
- Modify: `portal.css` tokens (brighter panels/accent/muted)
- Modify: dashboard, projects, upload, jobs, status, tc-guide, account, admin-users, invite templates — replace TestPilot Delivery strings; soften jargon

- [ ] Sidebar logo + “Keel”
- [ ] Update page `<title>` pattern to `… — Keel`
- [ ] Soften Upload / Status / TC guide customer-facing tech phrases
- [ ] Motion: subtle nav/panel transitions; respect reduced-motion
- [ ] Smoke: open login + dashboard + upload + tc-guide visually

---

### Task 4: Store layout `domain/projectId`

**Files:**
- Modify: `delivery/store/ProjectStore.java` (+ callers that assume flat `storeRoot/projectId`)
- Create: `delivery/store/DomainStorePaths.java` (normalize host → folder; resolve paths)
- Tests: `DomainStorePathsTest`, `ProjectStoreDomainLayoutTest`

- [ ] TDD: `saucedemo.com` → `saucedemo-com`; resolve `store/domain/projectId`
- [ ] Update write/load/delete project to nested path
- [ ] Keep `portal-db.*` at store root
- [ ] Migration helper: detect legacy flat domain-like dirs; move into `domain/projectId` when safe (log actions)
- [ ] Unit tests GREEN

---

### Task 5: Admin domains API + UI (full folder wipe)

**Files:**
- Create: `AdminDomainsController` `/api/admin/domains`
- Create: `admin-domains.html` + JS
- Modify: `PortalUiController` `/admin/domains`
- Modify: sidebar admin nav
- Tests: API security (non-admin 403); delete rejects `..`; delete removes tree

- [ ] `GET` list domains with projectCount, memoryEntries, lastModified
- [ ] `GET /{domain}` detail
- [ ] `DELETE /{domain}` recursive delete of `storeRoot/domain` only after sanitized name match
- [ ] UI: dropdown → stats panel → Delete → type domain name to confirm
- [ ] Admin-only; no customer role access

---

### Task 6: Vision ui-tars switch (C)

**Files:**
- Modify: config docs / `README` or portal help snippet
- Optionally default `delivery.vision.provider` when unset → `uitars` **or** document explicit flag (prefer explicit to avoid surprise on machines without the model)

- [ ] Decide: document-only vs soft-default to `uitars` when model present
- [ ] Verify `UiTarsVisionProvider.fromConfig()` uses installed `ui-tars`
- [ ] Short note in Keel Upload or admin: “Vision model: ui-tars recommended on 4 GB GPUs”

---

### Task 7: Verification

- [ ] Unit tests for store paths + admin delete
- [ ] Manual: login 2:1, sidebar Keel, admin domains list/delete confirm on a **throwaway** domain folder
- [ ] Optional: one conversion with `-Ddelivery.vision.provider=uitars`
- [ ] Report evidence; no commit unless asked

---

## Notes

- Destructive delete is intentional — confirm UI must be unmistakable.
- Do not delete H2 `portal-db` when wiping a domain.
- Frontend design rules: one composition on login first viewport; brand-first on brand panel; no purple-on-white / cream-terracotta defaults.
