# Phase 2 interim API / disk contract — Backend lock for AE/FE (2026-11-02)

**Tip base:** 83b9077 (impl in flight)  
**Status:** INTERIM LOCK — AE capture into these paths; Backend APIs will serve them. Do not invent a second store.

## 1. Failure evidence (screen + network + console)

### Disk (under existing execute evidence root)

`
{projectRoot}/execute-runs/{jobId}/evidence/{tcFolder}/failure/{stepIndex}/
  meta.json
  screen.png          # required when capture succeeded
  network.json        # HAR-like or CDP network events (array or {entries:[...]})
  console.json        # browser console messages array
`

- {tcFolder} = same folder ProvePhase / EvidencePaths already uses for that TC occurrence (prefer storage-key / __occ_N layout already on disk).
- {stepIndex} = 0-based intent/step index that failed (string decimal, no padding).
- If a blob is missing, omit the file; still write meta.json.

### meta.json fields

| Field | Type | Notes |
|-------|------|--------|
| jobId | string | execute-run id |
| 	cId | string | portal TC id (e.g. TC_1) |
| stepIndex | int | failed step |
| ailedAt | ISO-8601 | |
| 
eason | string | failure summary |
| pageUrl | string | optional |
| hasScreen | bool | |
| hasNetwork | bool | |
| hasConsole | bool | |
| source | string | prove \| execute |

### Portal APIs (Backend will publish)

| Method | Path |
|--------|------|
| GET | /api/execute-runs/{jobId}/failures |
| GET | /api/execute-runs/{jobId}/failures/{tcId} |
| GET | /api/execute-runs/{jobId}/failures/{tcId}/{stepIndex} |
| GET | /api/execute-runs/{jobId}/failures/{tcId}/{stepIndex}/screen |
| GET | /api/execute-runs/{jobId}/failures/{tcId}/{stepIndex}/network |
| GET | /api/execute-runs/{jobId}/failures/{tcId}/{stepIndex}/console |

Auth: same as existing execute-run evidence (project operable / job readable).

**AE write rule:** on failed execute/prove step, write the ailure/{stepIndex}/ tree above (do not replace historical screenshots outside that folder).

---

## 2. Heal proposals (AE emits only — no silent Java rewrite)

### Disk

`
{projectRoot}/heal-proposals/{proposalId}.json
`

### Proposal JSON fields

| Field | Type | Notes |
|-------|------|--------|
| proposalId | string | UUID |
| projectId | string | |
| jobId | string | source run |
| 	cId | string | |
| stepIndex | int | |
| pageClass | string | **existing** generated page FQN/simple name — required; never invent a new page class |
| locatorBefore | object | {strategy,value} failed locator |
| locatorAfter | object | proposed {strategy,value} |
| patchKind | string | locator (v1); later may add step |
| 	ier | string | heal tier used |
| 
eason | string | |
| status | string | pending \| ccepted \| 
ejected |
| createdAt | ISO-8601 | |
| decidedAt | ISO-8601 \| null | |
| evidenceRel | string \| null | optional path under evidence/ |

### Portal APIs

| Method | Path | Body |
|--------|------|------|
| GET | /api/projects/{projectId}/heal-proposals?status=pending | |
| GET | /api/projects/{projectId}/heal-proposals/{proposalId} | |
| POST | /api/projects/{projectId}/heal-proposals | AE create (or engine writes disk; POST optional) |
| POST | /api/projects/{projectId}/heal-proposals/{proposalId}/accept | apply to **existing** generated Java only |
| POST | /api/projects/{projectId}/heal-proposals/{proposalId}/reject | mark rejected; no code change |

**Accept rule:** patch locator in the named existing page class / proven step mapping only. If page class file missing → 409, do not create a new class.

**AE rule:** emit proposal JSON (status=pending) only; never rewrite Java.

---

## 3. Flaky tags

### Disk

`
{projectRoot}/flaky-tags.json
`

Array of:

| Field | Type | Notes |
|-------|------|--------|
| 	agId | string | UUID |
| 	cId | string | |
| stepIndex | int \| null | null = whole case |
| 	ag | string | v1 fixed laky (custom tags = P3) |
| 
eason | string | |
| sourceJobId | string \| null | |
| signal | string | AE hint: 	imeout \| intermittent \| heal_retry \| manual \| other |
| createdAt | ISO-8601 | |
| clearedAt | ISO-8601 \| null | |

### Portal APIs

| Method | Path |
|--------|------|
| GET | /api/projects/{projectId}/flaky-tags |
| POST | /api/projects/{projectId}/flaky-tags body {tcId, stepIndex?, reason?, sourceJobId?, signal?} |
| DELETE | /api/projects/{projectId}/flaky-tags/{tagId} | soft-clear (clearedAt) or hard delete |

**AE:** may POST or append disk entries with signal set; Backend is SoT for list/clear.

---

## Out of scope here
Codegen W2 emit, Postman, schedules/alerts (P3 spike).
