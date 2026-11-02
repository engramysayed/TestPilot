# Hunt Knowledge Pack guidance (AE) — 2026-11-02

**Status:** LIVE-APPLIED HTTP swap — prod `KnowledgePackReader` bean is `HttpKnowledgePackReader.local(KnowledgePackService)` (same-process call to the store behind GET `/api/projects/{projectId}/knowledge-packs[/{packId}]`). No second FS pack store. Missing pack ids still skip (log + continue).
**Tip:** `f8358f3` (feat(hunt): wire KnowledgePackReader to HTTP/local KnowledgePackService). Base was `cfa220e` (Backend ingest) + AE `b422366`.
**Backup:** local branch `backup/hunt-http-reader-20261102` at `cfa220e` (no push).

**HTTP swap (2026-11-02):**
- `KnowledgePackConfig` → `HttpKnowledgePackReader.local(service::list, service::get)`
- `HttpKnowledgePackReader` also supports `http(baseUrl)` for out-of-process; unit tests cover local + mock HTTP + 404 skip
- Flag unchanged: `delivery.hunt.knowledge-packs.enabled`
- Fixture reader remains tests-only
- Portal restart required to pick up bean swap
## Contract (consumed)

| Field | Notes |
|-------|--------|
| packId | e.g. `kp_01JEXAMPLE` |
| projectId | Project key |
| title | Section heading in brief |
| source | `LIBRARY` \| `UPLOAD_TC` \| `UPLOAD_MD` |
| batchSize / revision | Carried on DTO; batchSize not forcing Hunt scenarioCap yet |
| markdown | Full body (GET by id) — planner SoT |
| markdownPath | Relative under project store (e.g. `knowledge-packs/.../pack.md`) |
| createdAt / updatedAt | ISO-8601 |

Hunt job input: optional `knowledgePackIds: string[]`. Empty/missing → **legacy Hunt** (no pack guidance). Missing pack ids are skipped (no hard fail).

## Flag

- `delivery.hunt.knowledge-packs.enabled` (default **true**)
- Nested: `DeliveryPortalProperties.HuntProperties.knowledgePacksEnabled`
- When false, ids on the request are ignored for guidance (still persisted on `request.json`).

## Files

| Path | Role |
|------|------|
| `delivery/knowledge/KnowledgePack*.java` | DTO, source enum, reader, guidance compose |
| `delivery/knowledge/NoOpKnowledgePackReader` | Retained fallback / tests |
| `delivery/knowledge/HttpKnowledgePackReader` | Prod reader (local service transport + `http(baseUrl)`) |
| `delivery/knowledge/FixtureKnowledgePackReader` | Tests only |
| `delivery/portal/config/KnowledgePackConfig` | `@Bean KnowledgePackReader` → `HttpKnowledgePackReader.local` |
| `delivery/hunt/HuntRequest` | `knowledgePackIds` |
| `delivery/hunt/HuntBriefBuilder` | Appends pack guidance section |
| `delivery/hunt/LiveHuntService` / `DryRunHuntService` | Accept `knowledgeGuidanceMd` |
| `delivery/portal/api/HuntController` | `StartHuntBody.knowledgePackIds` |
| `delivery/portal/worker/HuntWorker` | Loads packs when flag+ids; preferred-hooks unchanged |

## Behavior

1. Controller accepts optional `knowledgePackIds`; writes them on durable `request.json`.
2. Worker: if flag on and ids non-empty → `reader.getByIds(projectId, ids)` → `KnowledgePackGuidance.compose` → pass into Live/DryRun brief.
3. Brief feeds planner (`OllamaHuntPlanner` / Cursor) via existing `## Brief` path.
4. **Preferred hooks:** unchanged — `PreferredHooksStore` still activated in `LiveHuntService`; RANK 1 locator preference remains.
5. **Invent-extras:** still optional + evidence-gated (`HuntBugAdmission`, invent plateau / strategy sequencer). Pack text tells planner not to invent bugs without evidence.

## Backend HTTP plug-in

Replace NoOp bean with HTTP reader calling:

- `GET /api/projects/{projectId}/knowledge-packs`
- `GET /api/projects/{projectId}/knowledge-packs/{packId}` (markdown required)

Suggested: implement `HttpKnowledgePackReader` (or new class) and change `KnowledgePackConfig` `@Bean`. Do **not** add a second AE filesystem pack store.

## Tests

- `delivery.knowledge.KnowledgePackGuidanceTest`
- `delivery.hunt.HuntKnowledgePackBriefTest` (pack-guided + empty-ids legacy + dry-run brief)
- `delivery.portal.api.HuntApiTest#startHunt_knowledgePackIds_persistedOnRequestJson`

## Out of scope

Generate-from-knowledge, Postman, Sonic, silent heal, Midscene vision-first, invent second pack store.