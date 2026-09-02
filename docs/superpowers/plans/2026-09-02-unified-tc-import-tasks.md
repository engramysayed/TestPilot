# Unified TC Import — Tasks

**Plan:** `docs/superpowers/plans/2026-09-02-unified-tc-import.md`  
**Spec:** `docs/superpowers/specs/2026-09-02-unified-tc-import-design.md`

## Phase 1 — Core import

- [x] T001 Create `TcImportRepair` + `TcImportRepairTest` (fences, literal `\n`)
- [x] T002 Create `TcImportService` + `TcImportServiceTest` (JSON/CSV → gate → save)
- [x] T003 Add `POST /generate/import` + `GenerateImportApiTest`

## Phase 2 — UI + prompts

- [x] T004 Generate page Paste/Import UI + `GenerateMvcTest` updates
- [x] T005 Rewrite JSON/CSV copy-prompts + prompt resource tests

## Phase 3 — All doors gated

- [x] T006 Route Compare `saveCompared` through import gate
- [x] T007 Gate Automate + Execute Excel upload + API test
- [x] T008 KeelPath blank regression + README subsection

## Phase 4 — Verify

- [x] T009 Full Maven bundle green + manual smoke notes

**MVP:** T001–T005 (paste works end-to-end)  
**Full:** T001–T009
