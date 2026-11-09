# Slice E — contract map (baseline discovery)

**Observed at:** 2026-11-07 Africa/Cairo  
**TREE:** `D:\Work\projects\Keel\keel\TestPilot`  
**HEAD:** `43922ae00839d5d2dc149c6aa1beea323efe4c2f` (matches S3; rechecked locally)

## Baseline preservation

Modified (preserved, not staged): `docs/history/handoffs/2026-11/2026-11-02-prompts-owner-ui-fe.md`, `src/main/resources/application.properties`, `src/main/resources/templates/admin-prompts.html`

Untracked (preserved): `BOOT-INF/`, `_backup/`, `_scratch/`, integration/, and handoff kickoffs listed in S3.

## UI route

| Path | Symbol | Role |
|------|--------|------|
| `/suites` | `PortalUiController` `@GetMapping("/suites")` | Renders `suites.html` |

## Suite storage & APIs

| Concern | Path / symbol |
|---------|----------------|
| SoT | `{projectRoot}/suites.json` via `SuiteDiskStore.FILE_NAME` |
| Service | `delivery.portal.service.SuiteService` |
| REST | `delivery.portal.api.SuiteController` `@RequestMapping("/api/projects/{projectId}")` |
| tcIds normalize | `SuiteDiskStore.normalizeTcIds` — trim, dedupe (LinkedHashSet), preserve first-seen order |
| Run consumer | `SuiteController.startRun` → `JobWorkbookResolver.resolve(workbooks, projectId, true, tcIds, null)` |
| Empty run | `BAD_REQUEST` / `"Suite has no tcIds to run"` when `tcIds` empty |

### Verified suite routes

- `GET /suites`, `GET /suites/{suiteId}`, `POST /suites`, `PUT /suites/{suiteId}`
- `POST /suites/{suiteId}/runs` — body `{trigger?, credentialProfile?, runner?}` → 202 `{jobId, status, suiteId, trigger, source}`

## Library sources (picker)

| Source | Route | ID field | Title | Executable in suite run |
|--------|-------|----------|-------|-------------------------|
| Generated workbook | `GET /api/projects/{projectId}/generated-workbook/cases` → `GeneratedWorkbookController.listCases` | `tcId` | `title` | Yes (`JobWorkbookResolver`) |
| Plain cases | `GET /api/projects/{projectId}/plain-cases` → `PlainCaseController.list` | `caseId` / `id` | `title` | No (separate `POST .../plain-cases/{id}/execute` path) |

**Slice E addition (proposed → implemented in this slice):**

- `GET /api/projects/{projectId}/test-case-library?q=&cursor=&limit=` → merged read-only adapter with `items[{id,title,source,executableInSuite}]`, `nextCursor`.

## Policies (Slice E)

| Topic | Decision |
|-------|----------|
| Missing/deleted TC IDs on save | Retain IDs in `tcIds`; show as unresolved in UI |
| Save validation | No hard block on unknown IDs (disk store unchanged) |
| Run | Disable UI when selection empty or any ID not `executableInSuite`; BE仍然 400 empty / resolver errors for unknown workbook IDs |
| Cross-project | `ProjectAccess.denyUnlessReadable` / `getOwnedProject` on all routes |
| Schedule / debit | Unchanged in E |

## Tests (planned)

- Unit: `SuiteTestCaseLibraryServiceTest`, `SuiteDiskStoreTest` dedupe `[A,A,B]`
- Integration: `SuiteSliceEApiTest` — library, save/reload `tcIds`, empty run 400, run with workbook subset (dry-run)

## Gates

| Gate | Status |
|------|--------|
| E offline | PASS/FAIL recorded in slice handoff after `mvn test` |
| HOME live picker/smoke | **PENDING** — portal left OFF |
