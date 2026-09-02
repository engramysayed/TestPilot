# Facebook Login Generate Fix — Tasks

**Plan:** `docs/superpowers/plans/2026-08-28-facebook-login-generate-fix.md`

**Fixture US:** `src/test/resources/generate/facebook-login-negative-us.txt` (create in T004)

## Phase 0 — Confirm

- [x] T001 Reproduce failure: paste fixture US → Generate → capture jobId + message before/after fail → write notes file
- [x] T002 Classify root cause (H1 quality gate / H2 prompt / H3 Ollama / H4 parse / H5 UX-only)

## Phase 1 — Fail loud

- [x] T003 [P] Create `GenerateBatchJobRunnerFailureTest.java` (expect last story error in exception)
- [x] T004 Implement `lastFailure` in `GenerateBatchJobRunner.java` + wire through `GenerateBatchWorker.java`
- [x] T005 Update `status.html` to show full batch failure reason for GENERATE_BATCH

## Phase 2 — Prompt alignment

- [x] T006 [P] Add failing assertions to `GenerateJsonPromptResourceTest.java` + `GeneratePromptResourceTest.java`
- [x] T007 Fix combined-field wording in `keel-tc-generate-from-stories-to-json.txt` (remove Email-field contradiction)
- [x] T008 Mirror prompt fixes in `keel-tc-generate-from-stories-to-csv.txt` + add malformed-input example
- [x] T009 Run prompt resource tests until green

## Phase 3 — Golden regression

- [x] T010 Create `facebook-login-negative-us.txt` + `facebook-login-negative-golden.json` fixtures
- [x] T011 Create `FacebookLoginNegativeGenerateTest.java` — gate pass + coverage groups + stub generateStory
- [x] T012 Add bad-output regression (standalone Phone field → QUALITY_GATE)
- [x] T013 Run `FacebookLoginNegativeGenerateTest` until green

## Phase 4 — Optional gate tweak

- [x] T014 Skipped — golden tests pass without gate changes

## Phase 5 — Live verify

- [ ] T015 Live Generate with fixture US → COMPLETED, ≥8 TCs, all 4 scenario groups present
- [ ] T016 Record live results in notes file; loop prompt tweak if gate still fails after retry

## Phase 6 — Done

- [x] T017 Run full test bundle (see plan Task 7 command)
- [ ] T018 User replaces Facebook `latest.xlsx` via UI Generate

**MVP (diagnose + UX):** T001–T005  
**Full fix:** T001–T013 + T015–T018  
**Stretch:** T014 if needed
