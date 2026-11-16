# Keel visual reskin — release readiness

## Deploy checklist

Ship these paths together; partial deploy shows legacy chrome or broken search/KPIs.

| Asset / area | Path |
|--------------|------|
| Reskin overlay CSS | `src/main/resources/static/css/keel-reskin.css` |
| Global search JS | `src/main/resources/static/js/portal-nav.js` |
| Search API | `PortalNavSearchController` |
| Card KPI fields | `ProjectSummaryService` + `ProjectController` |
| Templates + `fragments.html` | All `src/main/resources/templates/*.html` touched in reskin |

## Automated gates

- Portal MVC batch (includes `KeelStaticAssetsTest`, `ProjectsMvcTest`, `KeelVisualReskinPhase*`, `ProjectListKpiMvcTest`, `PortalNavSearchMvcTest`)
- Optional human/visual: `docs/design/keel-preview/visual-signoff.ps1`

## CSS architecture

- **`portal.css`** — base layout and components (unchanged ownership).
- **`keel-reskin.css`** — Keel mock tokens and chrome; always loaded **after** `portal.css`.
- Single load path: `fragments.html` → `portalStyles` (used by `pageHead` and `authPageHead`).

## Admin invites

- **`GET /admin/invites`** redirects to **`/admin/users`** (invite create/list UI lives on the users page).
- Legacy **`admin-invites.html`** template removed.

## Automate route

- **`GET /automate`** renders **`upload.html`** (not a separate `automate.html` hub).
