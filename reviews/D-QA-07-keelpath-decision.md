# D-QA-07 Decision — GeneratedWorkbookKeelPathUpdateTest (2026-10-14)

**Chosen default:** Product behavior is intentional — `updateKeelPaths` commits with source=`KEEL_PATH_EDIT` (see `GeneratedWorkbookService.updateKeelPaths` → `saveFromCases(..., "KEEL_PATH_EDIT", sourceRef, model)`).

**QA action:** Update test to expect `KEEL_PATH_EDIT` while asserting `sourceRef` + `model` are preserved. Remove from known-failure allowlist.

**Rejected alternative:** Change producer to keep source=`GENERATE` — would erase audit trail of path edits.
