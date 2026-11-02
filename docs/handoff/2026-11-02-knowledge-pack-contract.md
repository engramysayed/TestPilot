# Knowledge pack contract (interim) — 2026-11-02

**Status:** DRAFT contract locked with Maestro; **not on live tip `b41c774` yet**. Backend cloud agent implementing now; live-apply + handoff follow. AE/FE may wire against this shape.

**Single store:** project store under the portal store root (same project tree as TC library / generated workbook). No second DB table for AE to invent. Packs are markdown + sidecar metadata on disk.

## KnowledgePack (JSON / DTO)

| Field | Type | Notes |
|-------|------|--------|
| packId | string | Stable id within project |
| projectId | string | Project key |
| title | string | Display name |
| source | enum | `LIBRARY` \| `UPLOAD_TC` \| `UPLOAD_MD` |
| batchSize | int | User-set; presets Ollama 20–40, stronger 50–100; always overridable |
| revision | int | Bumps on update/replace |
| markdown | string | Full pack body (GET by id always includes this) |
| markdownPath | string | Relative path under project store (optional on list) |
| createdAt | string | ISO-8601 |
| updatedAt | string | ISO-8601 |

## APIs (FE + AE)

Base: `/api/projects/{projectId}/knowledge-packs`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/projects/{projectId}/knowledge-packs` | List packs (metadata; may omit full markdown) |
| GET | `/api/projects/{projectId}/knowledge-packs/{packId}` | Metadata **+** markdown |
| POST | ingest endpoints (library pick / raw TC upload / `.md` upload) | Create/update packs with `batchSize` |

Exact ingest route names ship with the Backend handoff once live-applied.

## Out of scope

- Changing current Generate behavior
- Postman / API Execute
- Sonic device farm
- Hunt invent-extras (AE owns)

Copied from box contract by Maestro for live handoff tree.
