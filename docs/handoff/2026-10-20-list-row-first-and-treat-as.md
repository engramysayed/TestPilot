# 2026-10-20 — List-row-first click + extra Treat as kinds

**Status:** Implemented on main.

Spec: `docs/superpowers/specs/2026-10-20-list-row-first-and-treat-as-design.md`  
Plan: `docs/superpowers/plans/2026-10-20-list-row-first-and-treat-as.md`

## What shipped

- **ListRowFirstBind:** for `CLICK` intents, when slim HTML has matching table/grid rows and the step names a shared cell (`with … status` or quoted badge text), pick the **first visible** row and bind the row’s preferred action control (name link), not the status cell. Rationale tag `listRowFirst`. Quoted unique visible names still use normal bind. Two page-level Login buttons do not use this path.
- **Treat as extensions:** Admin and `AuthoringVerbAliases` accept select, clear, drag, drop, hover, doubleclick, rightclick. Built-ins include clear the field, drag/drop, hover, double/right click. Classify → `IntentKind` DRAG/HOVER/DOUBLE_CLICK/RIGHT_CLICK; clear aliases → TYPE_FIELD with `clear` action via `isLeaveOrKeepEmptyStep`. Execute runs hover/doubleClick/rightClick/dragDrop.
- **Guide** `/guide#verbs` updated for new verbs and list-row-first behavior.

## How to use

1. Rebuild jar (`rebuild-portal.cmd`), restart (`start-portal.cmd`), Ctrl+F5.
2. Write grid steps with status filters knowing the **first** twin row is used, or quote the person name.
3. Owner may add aliases e.g. `wipe` → Clear on Admin → LLM & API keys.
4. Drag steps: `Drag the Ticket card onto the Done column` (two named controls).

## Known defects / limits

- Pre-run **review** only sees workbook text — it cannot see the live grid; quote important names and asserts in the TC.
- File upload and drag-and-drop file upload remain unsupported.
- Pre-existing `StepIntentBinderTest` failures on main were not in scope for this pass.
