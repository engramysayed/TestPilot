# Hunt Knowledge Pack guidance (AE) — 2026-11-02

**Status:** Live-applied on TestPilot (pack-guided Hunt). Backend HTTP pack APIs may still be landing; AE uses `KnowledgePackReader` so HTTP can plug without a second FS store.

**Parent contract:** `docs/handoff/2026-11-02-knowledge-pack-contract.md` (and Maestro interim lock).

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
| `delivery/knowledge/NoOpKnowledgePackReader` | Production bean until HTTP |
| `delivery/knowledge/HttpKnowledgePackReader` | Stub for Backend GET plug-in (returns empty today) |
| `delivery/knowledge/FixtureKnowledgePackReader` | Tests only |
| `delivery/portal/config/KnowledgePackConfig` | `@Bean KnowledgePackReader` → NoOp |
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