# Phase 1 — Sidebar IA + feature help (2026-11-16)

## Shipped

- Sidebar regrouped: **Design tests** · **Execute & automate** · **History** · **Resources**.
- Renamed nav labels (URLs mostly unchanged): TCs Generation (+ children Multi-model generate, Generate from knowledge), Playback, Test blueprints, Test suites, TCs execution, TCs automation, Bug Hunter under Execute, History (+ Run memory child).
- Friendly redirects: `/playback` → `/record`, `/history` → `/runs`, `/test-blueprints` → `/structured-plans`.
- **`?` feature help** on every main nav item — click opens popover; optional link to Guide (`keel-feature-help.js`, `messages.properties` `feature.help.*`).
- Nav groups remember open/closed in `localStorage` (`keel-sidebar-ia.js`).

## How to verify

1. Restart portal; open any signed-in page.
2. Hover a sidebar row — **`?`** appears; click for one-line help.
3. Toggle TCs Generation / Test suites / History chevrons; refresh — state should persist.
4. Hit `/playback`, `/history`, `/test-blueprints` — should land on record, runs, structured-plans.

## Known gaps (later phases)

- Page **H1 titles** and in-page copy still use old names in places.
- Guide anchors not yet wired per feature (`#projects` etc. may 404 until Guide updated).
- Admin sidebar links have no `?` yet.

## Files

- `templates/fragments.html`, `messages.properties`
- `static/css/keel-feature-help.css`, `keel-sidebar-roles.css`
- `static/js/keel-feature-help.js`, `keel-sidebar-ia.js`
- `PortalUiController.java` (aliases + breadcrumb titles)