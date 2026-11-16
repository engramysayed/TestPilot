# Generate Excel Quality Fixes — Tasks

**Plan:** `docs/superpowers/plans/2026-08-27-generate-excel-quality-fixes.md`

## Phase 1 — Semantic rules

- [x] T001 Create `GenerateAuthoringRules.java` with step/testData line alignment helpers
- [x] T002 [P] Create `GenerateAuthoringRulesTest.java` with TC_02/TC_03/TC_04 regression cases (tests fail first)
- [x] T003 Implement rules in `GenerateAuthoringRules.java` until tests pass

## Phase 2 — Gate integration

- [x] T004 Add `validate(cases, baseUrl)` to `GenerateQualityGate.java`
- [x] T005 Pass `project.getBaseUrl()` from `TcGenerateService.generateStory`
- [x] T006 Extend `buildQualityRetryUserMessage` semantic hints
- [x] T007 Update `GenerateQualityGateTest.java` for baseUrl facebook rule

## Phase 3 — Prompts

- [x] T008 Update `keel-tc-generate-from-stories-to-json.txt` (login, empty, assert rules + example)
- [x] T009 [P] Update `keel-tc-generate-from-stories-to-csv.txt` (mirror JSON rules)
- [x] T010 Create `GenerateJsonPromptResourceTest.java`
- [x] T011 Extend `GeneratePromptResourceTest.java` for new CSV phrases

## Phase 4 — Verify

- [x] T012 Run full test bundle and confirm green

**MVP:** T001–T005 (gate blocks bad Excel before save).  
**Full:** T001–T012 including prompt alignment.
