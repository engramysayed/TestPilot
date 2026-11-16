# List-row first + extra Treat as Implementation Plan

> **For agentic workers:** Implement task-by-task with TDD. This session implements in-tree on main.

**Goal:** First matching list/grid row wins for shared-status clicks; Treat as gains Clear / Select / Drag / Drop / Hover / Double-click / Right-click mapped to real Execute actions.

**Architecture:** `ListRowFirstBind` picks the first visible matching `tr`/`[role=row]` from slim HTML and a catalog candidate inside it. `AuthoringService` passes HTML into `StepIntentBinder.bindSingle`. New `IntentKind`s for drag/hover/double/right; clear stays TYPE_FIELD emitting `clear`. `actionExecute` / `ElementsHandler` run hover, doubleClick, rightClick; drag already has `dragDrop`.

**Tech Stack:** Java 21, Jsoup, TestNG, Selenium Actions, Thymeleaf Admin dropdown.

## Global Constraints

- One Java page class per page (unchanged).
- No Claude models.
- Do not edit `api-keys.local.bat`.
- Product credits stay ON.
- Alias `[[` must not appear in Admin LLM script.

---

## Tasks

### Task 1: List-row first bind

- Test: `ListRowFirstBindTest` with two Pending Edit rows; unquoted status step binds first name link; quoted other name binds that row; two Login buttons do not use this path.
- `ListRowFirstBind.java` + `bindSingle(..., slimHtml)` + `AuthoringService` passes HTML.
- Run `ListRowFirstBindTest`.

### Task 2: Treat as clear / select / drag / hover / doubleclick / rightclick

- Tests in `AuthoringVerbAliasesTest` + drag bind test.
- Expand `TREAT_AS`, `classify`, `IntentKind`, `isLeaveOrKeepEmptyStep` for clear alias, drag parse, Admin dropdown (no `[[`).
- Execute: map `drag` → `dragDrop`; add hover / doubleClick / rightClick.
- Run alias + API tests.

### Task 3: Guide + changelog + commit

- Guide verbs section, PRODUCT, handoff, CHANGELOG, RESUME.
- Verify tests. Commit as rsayed, no Cursor trailer, no push.
