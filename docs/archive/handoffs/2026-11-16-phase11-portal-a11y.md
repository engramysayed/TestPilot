# Portal UX Phase 11 — QA + accessibility

**Date:** 2026-11-16  
**Branch:** `ux-overhaul`

## Shipped

| Area | Change |
|------|--------|
| **Skip link** | First focusable control in sidebar; targets `#keel-main-content` (`keel-portal-a11y.js` sets `id` on `main.shell`). |
| **Focus rings** | `:focus-visible` outline on sidebar links, nav chevrons, top bar, `?` help, Guide TOC/cards (`keel-portal-a11y.css`). |
| **Feature help** | Keyboard **Enter/Space** opens/closes; `aria-expanded` + `aria-controls`; popover `role="dialog"`; **Escape** returns focus to `?`. |
| **Nav groups** | `aria-expanded` / `aria-controls` synced with open state in `keel-sidebar-ia.js`. |
| **Contrast / discovery** | `?` buttons at 50% opacity (not hover-only); light theme ring color tweak. |
| **Motion** | `prefers-reduced-motion` disables KPI pulse and sidebar transitions. |
| **Landmarks** | Sidebar `aria-label`; glossary table `aria-label`. |

## Manual QA checklist

1. **Tab** from page load — skip link appears, activates, focus moves to main heading area.
2. Tab through sidebar — every link and **?** shows a visible focus ring; **Space** on **?** opens help; **Escape** closes and refocuses **?**.
3. Open **TCs Generation** / **Test suites** / **History** chevrons — screen reader / devtools show `aria-expanded` toggling.
4. **Light + dark theme** — focus ring visible on sidebar and Guide TOC.
5. OS **reduce motion** on — dashboard live KPI dot does not pulse.
6. Guide **Feature glossary** table — announced with label “Sidebar features…”.

## Files

- `static/css/keel-portal-a11y.css` (new)
- `static/js/keel-portal-a11y.js` (new)
- `static/js/keel-feature-help.js`, `keel-sidebar-ia.js`
- `templates/fragments.html`, `guide.html`
- `messages.properties` (`a11y.*`)

## Not in scope

- Full WCAG audit of every admin page.
- Automated axe CI gate (optional follow-up).
- Auth/login pages do not load sidebar skip link (sidebar absent); login `main` still gets `id` if script added to auth head later.

## UX overhaul status

Phases **0–11** complete for the scoped portal IA work. Further work = defects from user review or new features outside this plan.