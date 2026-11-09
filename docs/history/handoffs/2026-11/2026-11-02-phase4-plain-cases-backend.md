# Phase 4A — Plain-English cases backend (2026-11-02)

**Status:** GREEN (store + REST + execute wire + tests committed)  
**Bot:** Backend (live-apply on Ramy)  
**Base tip:** `8c486cc` (portal already live)  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\plain-cases-20261102-1756`  
**Time (Africa/Cairo):** 2026-11-02 ~18:10+

## FE contract matched

| Method | Path | Body / notes |
|--------|------|----------------|
| `GET` | `/api/projects/{projectId}/plain-cases` | List `[{id,caseId,plainCaseId,title,lines,createdAt,updatedAt}, …]` |
| `GET` | `/api/projects/{projectId}/plain-cases/{caseId}` | Same object |
| `POST` | `/api/projects/{projectId}/plain-cases` | `{ "title"?: string, "lines": string[] }` → **201** with ids |
| `PATCH` | `/api/projects/{projectId}/plain-cases/{caseId}` | `{ "title"?, "lines"? }` |
| `POST` | `/api/projects/{projectId}/plain-cases/{caseId}/execute` | Optional `credentialProfile`, `runner=private` → **202** `{jobId,status,plainCaseId,source}` |

Auth: same as KnowledgePack / DesignReference — `ProjectAccess.denyUnlessReadable` (GET) / `denyUnlessOperable` (mutate+execute); execute also `permissionEnforcer.requireFeatureJob(..., "EXECUTE")`.

## Persist layout

`{projectDiskRoot}/plain-cases/{id}.json`

```json
{
  "id": "pc_…",
  "title": "Login smoke",
  "lines": ["click Login", "expect Welcome"],
  "createdAt": "…ISO…",
  "updatedAt": "…ISO…"
}
```

## Execute entrypoint (exact)

**Preferred:** `POST /api/projects/{projectId}/plain-cases/{caseId}/execute`

Flow:

1. Load `plain-cases/{id}.json`
2. `PlainCaseService.toManualTestCase` — non-`expect*` lines → numbered **Steps**; `expect …` lines → numbered **ExpectedResult** (prefix stripped); `keelPath=EXECUTE`; workbook `tcId` = `TC_` + uppercased plain id (Excel gate)
3. `ManualTcExcelWriter.write` → temp workbook under `java.io.tmpdir/delivery-uploads/{projectId}/plain-cases/`
4. Same quality-gate + surface guard + `ExecuteWorker.submit` path as `POST …/execute-runs` (no multipart change to that API)

FE “Open in Execute” can later call this execute endpoint (or open `/execute` and pick the case). Multipart `execute-runs` unchanged.

## Files

- `src/main/java/delivery/portal/service/PlainCaseDiskStore.java` (NEW)
- `src/main/java/delivery/portal/service/PlainCaseService.java` (NEW)
- `src/main/java/delivery/portal/api/PlainCaseController.java` (NEW)
- `src/test/java/delivery/portal/service/PlainCaseDiskStoreTest.java` (NEW)
- `src/test/java/delivery/portal/api/PlainCaseApiTest.java` (NEW)
- `docs/history/handoffs/2026-11/2026-11-02-phase4-plain-cases-backend.md` (this file)

**Not touched:** Visual*, HuntWorker, Generate, META-INF/, portal DB, api-keys.local.bat, `execute-runs` multipart signature.

## Verify

```bat
mvn -q -Dtest=PlainCaseDiskStoreTest,PlainCaseApiTest test
```

Expect disk unit tests green always; API test needs Spring context (same pattern as KnowledgePackApiTest).

## Portal restart

Maestro owns `:8080` rebuild. After this commit lands on tip, restart portal JAR (no `mvn clean` required if compile/`target/classes` pick-up is used; otherwise rebuild without deleting locked jar). Hard-refresh FE (`Ctrl+F5`) so Save stops showing “Waiting on Backend”.

## Next

- FE: after Save, optionally call `POST …/plain-cases/{id}/execute` or list cases on Execute page.
- Do not invent Postman/API Execute surfaces.
