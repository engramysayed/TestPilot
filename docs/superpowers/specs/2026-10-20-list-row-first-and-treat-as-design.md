# List-row first click + extra Treat as kinds

**Date:** 2026-10-20  
**Status:** Implementing  
**Plan:** `docs/superpowers/plans/2026-10-20-list-row-first-and-treat-as.md`

## Problem

1. Grid steps such as `Click on an Agent user with Pending edit status from the list` fail named-action bind: `edit` must sit on the same control as the other tokens. Quoted `"Pending Edit"` hits the badge, not the row. Two matching rows stay AMBIGUOUS. Owner wants the **first matching list row** (index 1) so the flow can run; which twin user does not matter.
2. Treat as is only Click / Type / Assert / Ignore. Owner wants Clear, Drag, Drop, and the other actions Execute can already run, so we do not return later.

## Goals

1. **List-row first:** a Click that names a shared cell in a table/grid (quoted or `with … status`) binds the **first visible matching row’s action control** (name link / preferred hook), not the status chip. One row or many: first row. Heal must not swap to another twin.
2. **Quoted unique name still wins when it is a specific person/control:** `Click on "Sara Ali"` binds Sara’s row even if she is not first Pending Edit.
3. **Duplicate page buttons stay AMBIGUOUS** (two Logins not in a data row).
4. **Treat as** adds kinds Execute already has: Clear, Select, Drag, Drop, Hover, Double-click, Right-click. Aliases map onto those kinds. Built-ins list the new classify words read-only.
5. Review stays text-only. It should keep quoting important names/asserts. It does not pick rows.

## Non-goals

- File upload / drag-and-drop upload (Guide unsupported).
- Pick-any-row-with-status as a new IntentKind name.
- Remapping login/username/password.
- Making Review see live DOM.

## List-row first

Applies only to `CLICK` (not login submit). Needs slim HTML.

**Needle**

- Quoted visible name if present (`"Pending Edit"`, `"Sara Ali"`), else
- `with (.+?) status` (e.g. Pending edit).

No needle → existing bind.

**Matching rows:** `tr`, `[role=row]`, `.ant-table-row` (skip `thead`, skip hidden). Row matches if its visible text contains the needle (case-insensitive, collapsed whitespace).

**Pick:** first matching row in document order. Among catalog candidates whose locator resolves inside that row, prefer `a` / `button` / `[role=button]` whose label is **not** equal to the needle (name link over badge). Else the first interactive in the row.

**Quoted name outside any data row:** do not use this path (existing named click / AMBIGUOUS).

**Rationale** on the proven step includes `listRowFirst`.

## Treat as (Owner dropdown + classify)

| treatAs | Classify | Proven action |
|---|---|---|
| click | CLICK | click |
| type | TYPE_FIELD | type (or select if the control is a dropdown) |
| select | TYPE_FIELD | prefer select |
| clear | TYPE_FIELD | clear (`element.clear`) |
| assert | ASSERT_VISIBLE | assert |
| ignore | skip | — |
| drag | DRAG | drag (source locator; drop in `dropCandidates` / value) |
| drop | DRAG | same as drag |
| hover | HOVER | hover |
| doubleclick | DOUBLE_CLICK | doubleClick |
| rightclick | RIGHT_CLICK | rightClick |

Built-in classify words (read-only, in addition to today’s list): `clear` (field), `drag`, `drop`, `hover`, `double click`, `right click`. `clear` as a **button** still needs Click (`Click Clear`).

Drag Excel: `Drag {source} onto/to {target}` or `Drop {source} on {target}`. Bind two candidates. Execute maps `drag` → existing `dragDrop`. Hover / doubleClick / rightClick use Selenium Actions on one locator.

## Tests (intent)

- Two Pending Edit rows + unquoted status step → first row’s name link, not AMBIGUOUS.
- One Pending Edit row → that row’s name link.
- `Click on "Omar Nabil"` with two Pending rows → Omar’s link.
- Two Login buttons, no table → not list-row-first.
- Alias `wipe` → clear classifies TYPE_FIELD and binds action clear.
- `Drag the Ticket card onto the Done column` binds drag + drop target.
- PUT reject unknown treatAs; accept `clear` / `drag`.

## Known limits

- First = first **visible** matching row in DOM order. Hidden responsive copies stay skipped.
- If every cell in the row is the status text, we still click that row’s first interactive.
- Drag needs two nameable controls on the live page.
