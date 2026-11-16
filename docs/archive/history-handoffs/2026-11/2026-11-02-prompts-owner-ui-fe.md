# FE handoff — prompts Owner UI (2026-11-02)

**Status:** GREEN (Frontend Slice B + API retarget to Backend final)  
**Tree:** `D:\Work\projects\Keel\keel\TestPilot` (Keel only; not Sonic)  
**Base tip (page ship):** `301a3fc`  
**Tip before this retarget:** `8d7fe9773646b0e4f42beb1a04d6795c3d708dab` (`8d7fe97` — docs/handoff recorded Backend tip SHA)  
**No push / no merge.** Credits-only voice. Plain-English copy.  
**Do not ping Maestro/Backend from FE** (parent/Leader coordinates).

## What shipped

Owner/Admin **Prompts** page: list prompt ids with classpath/override source badge, view/edit textarea, **Save to override**, **Diff vs classpath**, empty-state banner when override dir is unset (shows Backend message / 400 text).

### Files

| Path | Change |
|------|--------|
| `src/main/resources/templates/fragments.html` | Admin nav: **Prompts** link (`th:if="${headAdmin}"`, `navActive` `prompts`, href `/admin/prompts`) next to LLM & API keys |
| `src/main/java/delivery/portal/web/PortalUiController.java` | `pageTitleForNav`: `case "prompts" -> "Prompts"`; `@GetMapping("/admin/prompts")` → `requireHeadPage()` + `addNav(..., "prompts")` + `"admin-prompts"` |
| `src/main/resources/templates/admin-prompts.html` | JS-driven page; **retargeted 2026-11-02** to Backend GREEN final contract (nested-id path helper + polish) |

### Backup

`_backup\backup-pre-prompts-owner-ui-20261102-224715\`  
- `fragments.html`  
- `PortalUiController.java`  
- `admin-prompts.html-created` (post-create note copy)

`_backup\backup-pre-prompts-api-retarget-20261102-225740\`  
- `admin-prompts.html` (pre-retarget copy)

## API wiring (Backend-confirmed final)

Backend handoff `docs/history/handoffs/2026-11/2026-11-02-prompts-owner-ui-backend.md` is **GREEN / final** (not provisional). FE matches:

| Method | Path | Shape |
|--------|------|-------|
| GET | `/api/admin/prompts` | `{ items:[{id,source}], promptsVersion, overrideDirConfigured }` |
| GET | `/api/admin/prompts/{id}` | `{ id, source, text, … }` — **id may contain `/`** (e.g. `heal/pick`) |
| PUT | `/api/admin/prompts/{id}` | body `{ text }` → save override; **400** `{ error:"PROMPTS_DIR_UNSET", message }` when dir unset |
| GET | `/api/admin/prompts/{id}/diff` | `{ id, source, identical, unified, lines:[{op,classpath,override}] }` |

### Nested-id path fix (required)

Backend controller uses `/**` + restPath. Encoding the whole id with `encodeURIComponent` turns `/` into `%2F` and breaks nested ids. FE helper encodes **each segment** and keeps `/`:

```js
function promptApiPath(id, suffix) {
  var parts = String(id).split('/').map(encodeURIComponent).join('/');
  return '/api/admin/prompts/' + parts + (suffix ? suffix : '');
}
```

Used for get, put, and diff (`promptApiPath(id, '/diff')`).

### FE polish (same retarget)

1. List load: if `body.promptsVersion` present, muted list hint includes `Prompt pack version: …`.
2. Save 400: when `body.error === 'PROMPTS_DIR_UNSET'`, prefer `body.message` for error + banner (not the raw code alone).
3. Diff: `identical === true` → short “Same as classpath”; else prefer `unified`, else format `lines` as `op | classpath | override`.
4. `normalizeListPayload` still accepts wrapped `{ items, overrideDirConfigured }` (primary) and legacy array / `prompts` / `overrideConfigured` aliases.

CSRF/header pattern (same as admin-llm-settings):

```
HDR = { Accept: 'application/json', Content-Type: 'application/json', 'X-Keel-Requested-With': 'Keel' }
fetch(..., { credentials: 'same-origin', headers: HDR })
```

Stub-tolerant UI: clear errors for 401 / 403 / 404 / 501; override-not-configured banner from list message or save 400.

## How to verify

1. Portal jar includes these templates/controller (Maestro refresh after Backend GREEN). Templates will not appear until jar/restart.
2. Sign in as Owner / head admin.
3. Open `/admin/prompts` (also Admin sidebar **Prompts**).
4. Expect list of prompt ids + source badges; if Backend sends `promptsVersion`, muted “Prompt pack version: …” on the list hint.
5. Select a nested id (e.g. `heal/pick`) → editor loads (URL must keep `/` between segments, not `%2F`).
6. **Diff vs classpath** → “Same as classpath” when identical, else unified or lines.
7. **Save to override** with dir unset → banner/error uses Backend `message` for `PROMPTS_DIR_UNSET`.
8. Non-owner → forbidden (same as other head-admin pages).

## Next

- **Maestro:** portal jar refresh when coordinating (serialize Maven).
- Do **not** push. Do **not** ping Maestro/Backend from FE.
