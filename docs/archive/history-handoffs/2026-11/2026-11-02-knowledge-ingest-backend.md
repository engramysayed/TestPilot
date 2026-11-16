# Knowledge ingest + GFK create-run (Backend Phase 1) — 2026-11-02

**Status:** LIVE-APPLIED on Ramy tip  
**Bot:** Backend (executor; cloud agents exhausted — local apply)  
**Tip base:** `b422366` (AE Hunt KnowledgePack guidance)  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\knowledge-ingest-20261102-1735`

## Schema (disk, single store)

Under `PortalStore.projectDiskRoot(projectId)`:

```
knowledge-packs/{packId}/
  pack.md          # markdown body (source of truth)
  pack.json        # metadata sidecar

generate-from-knowledge/runs/{runId}.json
```

### pack.json fields

| Field | Type | Notes |
|-------|------|--------|
| packId | string | `kp_` + 12 hex |
| projectId | string | Project key |
| title | string | Display name |
| source | enum | `LIBRARY` \| `UPLOAD_TC` \| `UPLOAD_MD` |
| batchSize | int | Clamped 1–500; default 30 |
| revision | int | Starts at 1; bumps on PUT/PATCH |
| markdownPath | string | Relative e.g. `knowledge-packs/{id}/pack.md` |
| createdAt | string | ISO-8601 Instant |
| updatedAt | string | ISO-8601 Instant |

HTTP responses reuse AE `delivery.knowledge.KnowledgePack` field names (plus empty `markdown` on list).

### Run JSON fields

| Field | Type | Notes |
|-------|------|--------|
| runId | string | `gfk_` + 12 hex |
| projectId | string | |
| packIds | string[] | |
| status | enum | `QUEUED` \| `RUNNING` \| `SUCCEEDED` \| `FAILED` \| `CANCELLED` |
| createdAt / updatedAt | string | ISO-8601 |
| title / batchSize | optional | Echoed when provided |
| note | string | Phase 1 stub explanation |

Phase 1 stub: create persists then marks **SUCCEEDED** with note *"Phase 1 stub: run recorded; agent walk not executed yet."*

## API table

Base packs: `/api/projects/{projectId}/knowledge-packs`

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/projects/{projectId}/knowledge-packs` | readable | List (metadata; `markdown` empty) |
| GET | `/api/projects/{projectId}/knowledge-packs/{packId}` | readable | Metadata + full markdown |
| POST | `.../knowledge-packs/ingest/library` | operable | JSON library pick → markdown |
| POST | `.../knowledge-packs/ingest/upload-tc` | operable | Multipart TC/xlsx/csv → markdown |
| POST | `.../knowledge-packs/ingest/upload-md` | operable | Multipart `.md` |
| PUT/PATCH | `.../knowledge-packs/{packId}` | operable | Update title/batchSize/markdown (revision++) |

GFK runs (FE-locked):

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/projects/{projectId}/generate-from-knowledge/runs` | operable | Create run stub |
| GET | `/api/projects/{projectId}/generate-from-knowledge/runs/{runId}` | readable | Get run |

## JSON examples

### Upload MD (multipart)

`POST /api/projects/{projectId}/knowledge-packs/ingest/upload-md`  
fields: `file` (.md), optional `title`, optional `batchSize`

**201**
```json
{
  "packId": "kp_abc123def456",
  "projectId": "proj_…",
  "title": "Sample MD Pack",
  "source": "UPLOAD_MD",
  "batchSize": 40,
  "revision": 1,
  "markdown": "# Pack body\n\n…",
  "markdownPath": "knowledge-packs/kp_abc123def456/pack.md",
  "createdAt": "2026-11-02T15:40:00Z",
  "updatedAt": "2026-11-02T15:40:00Z"
}
```

### Library ingest

`POST .../ingest/library`
```json
{ "tcIds": ["TC_001", "TC_002"], "title": "Checkout pack", "batchSize": 30 }
```

### Create GFK run

`POST /api/projects/{projectId}/generate-from-knowledge/runs`
```json
{ "packIds": ["kp_abc123def456"], "batchSize": 30, "title": "optional" }
```

**201**
```json
{
  "runId": "gfk_aabbccddee01",
  "projectId": "proj_…",
  "packIds": ["kp_abc123def456"],
  "status": "SUCCEEDED",
  "createdAt": "…",
  "updatedAt": "…",
  "batchSize": 30,
  "title": "optional",
  "note": "Phase 1 stub: run recorded; agent walk not executed yet."
}
```

## Files added

- `src/main/java/delivery/portal/service/KnowledgePackDiskStore.java`
- `src/main/java/delivery/portal/service/KnowledgePackService.java`
- `src/main/java/delivery/portal/service/GenerateFromKnowledgeRunStore.java`
- `src/main/java/delivery/portal/api/KnowledgePackController.java`
- `src/main/java/delivery/portal/api/GenerateFromKnowledgeController.java`
- `src/test/java/delivery/portal/api/KnowledgePackApiTest.java`
- `src/test/java/delivery/portal/api/GenerateFromKnowledgeRunApiTest.java`

**Not touched:** Generate endpoints, HuntWorker.java, META-INF/, target/, portal DB, api-keys.local.bat.

## Verify

```bat
cd /d D:\Work\projects\Keel\keel\TestPilot
mvn -pl keel-portal -am -Dtest=KnowledgePackApiTest,GenerateFromKnowledgeRunApiTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Manual: hard-refresh `/generate-from-knowledge`, pin project, upload/list packs, Start run → expect 201 + SUCCEEDED poll.

## HuntWorker compile note

Pre-existing dirty-tree risk in `HuntWorker.java` (effectively-final lambda) may break a full `mvn compile`. Isolate with `-Dtest=…` as above; do **not** edit HuntWorker in this slice.

## Portal restart

**Yes — portal needs restart** (or rebuild) to pick up new `@RestController` classes. Static FE already polls these paths. Do not kill unless ops asks; schedule restart after commit.

## Next

1. AE: swap `KnowledgePackConfig` / `HttpKnowledgePackReader` to real HTTP against these GET paths.
2. FE: verify live HTTP (list packs + create-run); drop "Waiting on Backend" empty-state.
3. Later: real agent walk for GFK runs (leave QUEUED→RUNNING→terminal).
