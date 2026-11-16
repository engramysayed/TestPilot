# Slice E — Suites TC picker (Cursor)

Slice / owner / observed-at (Africa/Cairo): **E** / Cursor / **2026-11-07 ~16:08**

HEAD before: `43922ae00839d5d2dc149c6aa1beea323efe4c2f`  
HEAD after: *(uncommitted local — slice E hunks only; no push)*

Baseline dirty/untracked preserved; backup references (no secret contents):
- `_backup/backup-pre-slice-e-20261107/suites.html`, `SuiteController.java`
- Unchanged pre-existing mods: `application.properties`, `admin-prompts.html`, prompts handoff kickoffs

Discovery answers (paths + symbols):
- UI: `src/main/resources/templates/suites.html`
- APIs: `SuiteController`, `SuiteService`, `SuiteDiskStore`
- Library adapter: `SuiteTestCaseLibraryService` + `GET /api/projects/{projectId}/test-case-library`
- Run: `SuiteController.startRun` → `JobWorkbookResolver`

Proposed vs implemented:
- Unified library GET added (plan allowed thin adapter if list missing for picker).
- Plain cases listed with `executableInSuite: false`; suite run still workbook-only (existing AE path).

Changed paths (slice E boundary):
- `src/main/java/delivery/portal/service/SuiteTestCaseLibraryService.java` (NEW)
- `src/main/java/delivery/portal/api/SuiteController.java`
- `src/main/resources/templates/suites.html`
- `src/test/java/delivery/portal/service/SuiteTestCaseLibraryServiceTest.java` (NEW)
- `src/test/java/delivery/portal/api/SuiteSliceEApiTest.java` (NEW)
- `src/test/java/delivery/portal/service/SuiteDiskStoreTest.java` (dedupe test)
- `docs/knowledge/contracts/E-contract-map.md` (NEW)

Contracts:
- `GET .../test-case-library?q=&cursor=&limit=` → `{ items[{id,title,source,executableInSuite}], nextCursor, total }`
- Suite create/update unchanged: `{ tcIds: string[] }`

Fixtures: synthetic projects in H2 + `./target/test-delivery-store-suite-slice-e`; TC_1..TC_3

Commands:
```text
mvn -pl keel-portal -Dtest=SuiteDiskStoreTest,SuiteTestCaseLibraryServiceTest,SuiteSliceEApiTest test
```
Java 21, Maven 3.x, `delivery.dry-run=true`, portal **not** started.

Results:
- SuiteDiskStoreTest — PASS (incl. `normalizeTcIds_dedupesPreservesOrder`)
- SuiteTestCaseLibraryServiceTest — PASS (2 tests)
- SuiteSliceEApiTest — PASS (4 tests); exit code 0

Offline gate: **PASS**

HOME live gate: **PASS (smoke)** — portal on :8080; `GET /suites` → 200 (2026-11-07 ~16:24). Full picker Ctrl+F5 + pick/save/run on real project library: **not recorded** this session.

Release/shipped: **NO**

Pricing/refund changes: **NONE**

Rollback: restore `suites.html` + `SuiteController.java` from `_backup/backup-pre-slice-e-20261107/`; remove new service/tests; keep `suites.json` data.

Next slice allowed: **F** offline work may proceed after Leader review; schedule worker remains design-only until F.

Known defects:
- Picker has no automated browser/UI test in this slice (API + disk only).
- Plain-case IDs can be saved but cannot run via suite Run now (by design until workbook mapping exists).
