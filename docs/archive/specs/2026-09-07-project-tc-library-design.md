# Project TC library + run selection (option B)

## Goal
Users keep generated TCs on the project, edit/delete them later, and on Execute/Automate select one or more library TCs and optionally mix an upload (upload wins on `TC_ID`).

## Design
- Library = generated workbook (`generated/latest.xlsx` + csv/meta).
- Project **Test cases** tab: section **Generated library** (CRUD) plus existing Automate IR section.
- Execute/Automate: source = upload | full library | selected library rows; upload may accompany library selection (mix).
- Mix: library rows (selected or all) ∪ upload rows; same `TC_ID` → upload wins.
- No version history in v1.

## APIs
- `GET .../generated-workbook/cases` → `{ cases: [row...] }`
- `DELETE .../generated-workbook/cases` body `{ tcIds: [...] }`
- Existing `PUT .../cases/{tcId}` for edit
- Job create: `tcIds` multi + optional `excel` with `useGenerated`

## Non-goals
LLM Wiki, branching, Claude models.
