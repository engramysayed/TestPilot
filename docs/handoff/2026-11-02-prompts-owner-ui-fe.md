# FE handoff — prompts Owner UI (2026-11-02)

**Status:** GREEN (Frontend Slice B)  
**Tree:** `D:\Work\projects\Keel\keel\TestPilot` (Keel only; not Sonic)  
**Base tip:** `301a3fc`  
**Tip (this FE):** see `git log -1` / commit that added this handoff (no self-referential SHA in blob).  
**No push / no merge.** Credits-only voice. Plain-English copy.

## What shipped

Owner/Admin **Prompts** page: list prompt ids with classpath/override source badge, view/edit textarea, **Save to override**, **Diff vs classpath**, empty-state banner when override dir is unset (shows Backend message / 400 text).

### Files

| Path | Change |
|------|--------|
| `src/main/resources/templates/fragments.html` | Admin nav: **Prompts** link (`th:if="${headAdmin}"`, `navActive` `prompts`, href `/admin/prompts`) next to LLM & API keys |
| `src/main/java/delivery/portal/web/PortalUiController.java` | `pageTitleForNav`: `case "prompts" -> "Prompts"`; `@GetMapping("/admin/prompts")` → `requireHeadPage()` + `addNav(..., "prompts")` + `"admin-prompts"` |
| `src/main/resources/templates/admin-prompts.html` | **New** — JS-driven page matching admin-llm-settings / admin-pricing chrome |

### Backup

`_backup\backup-pre-prompts-owner-ui-20261102-224715\`  
- `fragments.html`  
- `PortalUiController.java`  
- `admin-prompts.html-created` (post-create note copy)

## API wiring (provisional — Backend handoff not present at FE GREEN)

Backend handoff `docs/handoff/2026-11-02-prompts-owner-ui-backend.md` was **absent**. No `AdminPrompts*` / `PromptAdmin*` controller under `src/main/java`. Wired parent-confirmed provisional contract:

| Method | Path | Expected shape |
|--------|------|----------------|
| GET | `/api/admin/prompts` | **Array** `{ id, source: "classpath"\|"override" }[]` (also tolerates `{ items: [...] }` / `prompts` + optional `overrideConfigured` / `message`) |
| GET | `/api/admin/prompts/{id}` | `{ id, source, text, classpathText? }` |
| PUT | `/api/admin/prompts/{id}` | body `{ text }` → save override; **400** + message if dir unset (banner shows message) |
| GET | `/api/admin/prompts/{id}/diff` | `{ unified? }` / `{ lines? }` / `{ diff? }` / plain text — rendered as-is |

List/get expected to work from classpath when override unset. **promptsVersion** stamp is Backend-only — no FE work.

CSRF/header pattern (same as admin-llm-settings):

```
HDR = { Accept: 'application/json', Content-Type: 'application/json', 'X-Keel-Requested-With': 'Keel' }
fetch(..., { credentials: 'same-origin', headers: HDR })
```

Stub-tolerant UI: clear errors for 401 / 403 / 404 / 501; override-not-configured banner from list message or save 400.

**If Backend lands with different paths/shapes:** retarget this page + update this handoff before calling FE GREEN again.

## How to verify

1. Portal jar includes these templates/controller (Maestro refresh after Backend GREEN). Live PID `17288` `:8080` was pre-slice; templates will not appear until jar/restart.
2. Sign in as Owner / head admin.
3. Open `/admin/prompts` (also Admin sidebar **Prompts**).
4. Expect list of prompt ids + source badges (or clear 404/501 if Backend API not in jar yet).
5. Select one → editor fills; **Diff vs classpath** shows Backend payload; **Save to override** either saves or shows Backend 400 / unset-dir message in banner.
6. Non-owner → forbidden (same as other head-admin pages).

## Next

- **Maestro:** portal jar after Backend GREEN (serialize Maven).
- Do **not** push. Do **not** ping Maestro from FE (parent/Leader coordinates).