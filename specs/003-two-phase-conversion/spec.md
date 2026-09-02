# Feature Specification: Two-Phase Conversion (Prove IR → Emit)

**Feature Branch**: `003-two-phase-conversion`

**Created**: 2026-08-06

**Status**: Implemented (engine + portal job history)

**Input**: Mission review roadmap — separate Phase 1 (parse/execute/validate/retry, durable IR drafts) from Phase 2 (page clustering, CodeWriter, locator map, ZIP). Portal surfaces step-level progress and job history/re-download.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Phase-1 IR with retry and blocker (Priority: P1)

During conversion, each Excel case is proven live. A failed step retries up to 2 times with a fresh DOM bind. After retries fail, that step is the blocker; remaining steps in the case are not invented. A durable IR draft is written per TC.

**Independent Test**: Unit/IR round-trip + ProvePhase retry constant; live NEW job writes `delivery-work/.../ir/*.json`.

### User Story 2 - Phase-2 emit with page clustering (Priority: P1)

After all IR drafts exist, Phase 2 clusters locators into page classes from URL paths, writes POM + tests (including partial TODOs with proven steps), writes `docs/locator-map.json`, stores locator map + IR under the project store, and packs the ZIP.

**Independent Test**: PageClusterer + LocatorMapBuilder + EmitPhase mapping tests; live job produces clustered page classes and locator-map.

### User Story 3 - Portal step progress and job history (Priority: P1)

While Phase 1 runs, status message shows TC/step/retry. Users can open `/jobs` to list past jobs and re-download completed ZIPs.

**Independent Test**: Open `/jobs` after a completed job; status page shows Phase1 messages during run.

## Requirements *(mandatory)*

- **FR-001**: Conversion MUST run Phase 1 (prove) then Phase 2 (emit); codegen MUST NOT run mid-prove for the suite.
- **FR-002**: Each TC MUST persist an IR draft under the job work folder `ir/{tcId}.json`.
- **FR-003**: Bind/execute failures MUST retry at most twice (3 attempts total) with re-slim HTML.
- **FR-004**: On blocker, remaining intents in that TC MUST NOT execute; draft status PARTIAL or TODO.
- **FR-005**: Phase 2 MUST cluster page names from URL path (not a single god `Page` when URL differs).
- **FR-006**: System MUST persist `locator-map.json` on the project store and in the ZIP `docs/`.
- **FR-007**: Portal MUST list owned jobs and allow re-download of completed packages.
- **FR-008**: Progress messages MUST include Phase1 step/retry context when available.

## Success Criteria

- IR drafts survive for every TC in a completed job folder.
- Partial cases keep proven steps in TodoTest with STOPPED comment.
- Distinct checkout/cart/inventory URLs produce distinct page class stems.
- Job history page lists completed jobs with download links.
