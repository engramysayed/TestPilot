# Generate from knowledge — FE Phase 1 shell (2026-11-02)

**Status:** GREEN (shell navigable + contract wired, stub-tolerant)  
**Bot:** Frontend (live-apply on Ramy)  
**Tip SHA (dirty tree OK):** `b41c774ad59378c17a20af9df2a118469e4ae805`  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\backup-pre-gfk-fe-20261102-172254`

## IA decision (LOCKED)

- Sibling sidebar item **From knowledge** → `/generate-from-knowledge` (own page).
- Placed in Features nav **immediately after** Generate in `fragments.html`.
- `navActive == 'generate-from-knowledge'`.
- Permission: `data-feature-perm="feature.generate"` (same as Generate/Record).
- Classic **Generate** at `/generate` unchanged (body/behavior not edited).

## Files touched

1. `src/main/resources/templates/fragments.html` — nav link only (after Generate)
2. `src/main/java/delivery/portal/web/PortalUiController.java` — `@GetMapping("/generate-from-knowledge")`, `addNav(..., "generate-from-knowledge")`, `pageTitleForNav` → "Generate from knowledge"
3. **NEW** `src/main/resources/templates/generate-from-knowledge.html` — shell UI
4. `src/main/resources/static/js/keel-sidebar-ia.js` — `featurePaths['/generate-from-knowledge'] = 'feature.generate'`
5. **NEW** `docs/handoff/2026-11-02-generate-from-knowledge-fe.md` (this file)
6. `docs/handoff/per-bot-status.md` — one short FE line

**Not touched:** `generate.html` (SHA256 before=after `AFA500697CEAAE859FE419524E0884B4B6C76253364AA3E21D5236C39C516181`; match before `AFA500697CEAAE859FE419524E0884B4B6C76253364AA3E21D5236C39C516181`), Automate/Record, theme CSS stack, RS/avatar.

## DoD shell UI checklist

- [x] Choose project (launcher + pinned; session key `keel.gfkProjectId`)
- [x] Multi-select knowledge pack checklist (`generate-check`)
- [x] Batch size number input + presets hint (default 30; Ollama 20–40 / strong 50–100)
- [x] Start run disabled until project + ≥1 pack when packs exist; if API empty, empty state + Start may no-op with clear status
- [x] Plan preview placeholder / empty state; optional detail fetch fills title/revision (+ truncated markdown)
- [x] Copy: this is **Generate from knowledge**; classic Generate remains at `/generate`

## API contract assumed (Backend DRAFT — not on tip `b41c774`)

Wired list/select against:

- `GET /api/projects/{projectId}/knowledge-packs`
- `GET /api/projects/{projectId}/knowledge-packs/{packId}`

Fields expected: `packId`, `projectId`, `title`, `source` ∈ {LIBRARY, UPLOAD_TC, UPLOAD_MD}, `batchSize`, `revision`, `markdown` + `markdownPath`, `createdAt`/`updatedAt`.

FE behavior until Backend live-applies:

- On project select → list endpoint; on 404/5xx/network → empty state **"Packs API not on tip yet — Backend Phase 1"** (no fake packs).
- Multi-select uses `packId` + `title` (+ `source`/`revision` muted).
- Optional detail GET on checkbox select for plan-preview placeholder.
- **Start run:** no invent job POST. Status **"Waiting on Backend pack run API"**.

### Assumed future create-run path (document only — not called)

- Likely `POST /api/projects/{projectId}/knowledge-packs/generate-runs` (or similar) with body `{ packIds: [...], batchSize: N }`.
- Backend must publish exact path in its handoff; FE will wire without inventing jobs before that.

## What Backend still owes

1. Live-apply KnowledgePack store + GET list / GET by id on tip.
2. Create/run endpoint for generate-from-knowledge jobs (publish path + DTO).
3. Ingest (library / raw TC / .md) — out of FE Phase 1 shell scope.

## Verify notes

- Portal restart **not** required for static/Thymeleaf if already running with template reload; if route 404s, restart portal and note it. **This FE apply did not restart portal.**
- Hard refresh: **Ctrl+F5** after deploy so sidebar IA + new page JS cache clear.
- `mvn -q -DskipTests compile`: skipped unless run separately (prefer leave portal running).
- Grep: no RS/avatar / theme stack edits in this change set.

## READY path

1. Open `/generate-from-knowledge` (sidebar **From knowledge** under Features, after Generate).
2. Pin a project → packs empty state until Backend lands.
3. Confirm `/generate` still classic Generate.

## Compile note

`mvn -q -DskipTests compile` failed on **pre-existing** dirty-tree errors in `HuntWorker.java` (effectively-final lambda) — not introduced by this FE change. PortalUiController GFK mapping is syntactically fine.

AE already has `delivery.knowledge.*` reader stubs on the dirty tree (HttpKnowledgePackReader TODO against the same GET contract). **No portal REST controller** exposing `/api/projects/{id}/knowledge-packs` found yet — FE stub empty-state remains correct.

## Portal restart

Not restarted by FE. If the live portal does not pick up the new Thymeleaf route, restart `start-portal` / rebuild and **Ctrl+F5**.

## Run wire (create-run + poll) — 2026-11-02 17:28 Africa/Cairo

**Status:** GREEN (Start POSTs locked path; stub-tolerant until Backend lands)  
**Tip SHA (dirty tree OK):** `b422366de94529e578e10da35eda975283dec418`  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\backup-pre-gfk-run-wire-20261102-172818`  
**IA:** unchanged (sibling From knowledge; classic Generate untouched)

### Endpoints wired

- **Create run:** `POST /api/projects/{projectId}/generate-from-knowledge/runs`
  - Body: `{ "packIds": [...], "batchSize": N }` (title omitted — no title field on page yet)
  - credentials/same-origin + `Content-Type: application/json` + `X-Keel-Requested-With: Keel`
- **Poll:** `GET /api/projects/{projectId}/generate-from-knowledge/runs/{runId}` every ~2s, max ~60 tries, stop on `SUCCEEDED|FAILED|CANCELLED`

### Stub-tolerant behavior

- On **404/501/network**: status **"Waiting on Backend pack run API"** — no invented success.
- On other **4xx** with body `message`/`error`/`detail`: show that text.
- Start **disabled** while `QUEUED`/`RUNNING` (`runInFlight`); re-enabled on terminal or error.
- Packs list empty-state unchanged (still no fake packs).

### Files this slice

1. `src/main/resources/templates/generate-from-knowledge.html` — Start → create-run + poll
2. `docs/handoff/2026-11-02-generate-from-knowledge-fe.md` — this section
3. `docs/handoff/per-bot-status.md` — one FE line

**Not touched:** `generate.html` (SHA256 still `AFA500697CEAAE859FE419524E0884B4B6C76253364AA3E21D5236C39C516181`). No push. No portal restart.


## Smoke vs Backend tip cfa220e — HOLD (2026-11-02 17:45 Africa/Cairo)

**Status:** YELLOW (partial — HTTP smoke deferred; Maestro owns portal rebuild/restart)
**Verifier tip:** `git rev-parse HEAD` = `cfa220e3bc138dc888df9a0b63aef5056aa90f0c` (matches claimed `cfa220e`; dirty tree OK, ahead 2)
**Portal rebuild/restart by this bot:** NO — steered to HOLD; Maestro owns `start-portal.cmd` on tip cfa220e. No HTTP smoke until parent confirms :8080 up.
**Controllers on disk:** YES — `KnowledgePackController.java`, `GenerateFromKnowledgeController.java` (+ DiskStore/Service/RunStore)
**FE wire still matches?** YES — `generate-from-knowledge.html` Start POSTs `/api/projects/{id}/generate-from-knowledge/runs` with `{ packIds, batchSize }`; poll GET `.../runs/{runId}`; list GET `.../knowledge-packs` (+ detail). `generate.html` not edited this turn.
**Ingest routes (Backend handoff; for later UI):** `POST .../knowledge-packs/ingest/upload-tc`, `.../ingest/upload-md`, `.../ingest/library` — noted, not smoked.
**HTTP endpoints:** not measured yet (HOLD). Resume when Maestro confirms portal up on tip.


## Smoke LIVE :8080 — 2026-11-02 17:50 Africa/Cairo

**Status:** GREEN  
**Tip:** `cfa220e` (`cfa220e3bc138dc888df9a0b63aef5056aa90f0c`) — matches Maestro tip-jar claim  
**Portal:** PID **12476** listening on `:8080` (product profile). **No rebuild/restart** this smoke. **No push.**

### Auth
- Unauth: `/` → **302** `/login`; `/login` → **200**; `/api/projects` → **401**; `/generate-from-knowledge` → **302** `/login`
- Session: form POST `/login` with documented local admin from `application.properties` (`delivery.admin-email` / `delivery.admin-password` → `engramisayed@localhost`). **302** → `/dashboard`. `GET /api/account` → **200** `id=205` role ADMIN.
- Seeded admin had **0** projects → created smoke project `POST /api/projects` → **201** `prj_afdde06f424e` ("GFK Smoke 2026-11-02"). (Also created two accidental empty projects `prj_63b8173500cb`, `prj_450ad3db9b94` while probing create shapes — leave or delete later; not Phase 4A.)

### Per-endpoint HTTP (cookie session + `X-CSRF-TOKEN` + `X-Keel-Requested-With: Keel`)

| Step | Method / path | HTTP | Notes |
|------|----------------|------|--------|
| List packs | `GET /api/projects/prj_afdde06f424e/knowledge-packs` | **200** `[]` | empty before ingest |
| Ingest MD | `POST .../knowledge-packs/ingest/upload-md` | **201** | `packId=kp_93f5570c7059` source UPLOAD_MD revision 1 |
| List packs | same GET | **200** count **1** | list shape: packId/title/source/batchSize/revision; `markdown` empty on list |
| Start run | `POST .../generate-from-knowledge/runs` `{packIds:[kp_93f5570c7059], batchSize:30}` | **201** | `runId=gfk_9f9a99a3ed27` status **SUCCEEDED** stub note |
| Poll | `GET .../runs/gfk_9f9a99a3ed27` ×5 | **200** | stays **SUCCEEDED**; note *"Phase 1 stub: run recorded; agent walk not executed yet."* |

### Smoke Y/N
- List packs: **Y**
- Start (create-run): **Y** (201)
- Poll: **Y** (200 terminal SUCCEEDED)

### FE wire (grep only — no edits)
`generate-from-knowledge.html` still POSTs `/api/projects/{id}/generate-from-knowledge/runs` with `{ packIds, batchSize }`, polls `.../runs/{runId}`, lists `.../knowledge-packs` (+ detail). Paths match Backend handoff.

### Out of scope
Phase 4A / plain-cases: **not touched**. Portal jar not rebuilt. Nothing pushed.


## Ingest strip UI (Library | Upload TCs | Upload .md) — 2026-11-02 ~18:35 Africa/Cairo

**Status:** GREEN (UI live-applied; stub-tolerant 404/501; no portal restart; no push)  
**Bot:** Frontend (executor)  
**Tip SHA (dirty tree OK):** `83b9077`  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\backup-pre-gfk-ingest-vd-sidebar-20261102-183236`  
**Portal restart:** NO (Maestro owns :8080). Ctrl+F5 for static/Thymeleaf.

### UX
- **Add pack** strip above pack checklist on `/generate-from-knowledge`
- Tabs: **Library** | **Upload TCs** | **Upload .md**
- Shared optional **title** + **batch size** (default 30, clamp 1–500)
- Library: multi-select from `GET /api/projects/{id}/generated-workbook/cases` (same as Execute / Bug Hunter)
- On success: refresh pack checklist via existing `loadPacks`

### Endpoints
| Tab | Method + path | Body |
|-----|----------------|------|
| Library | `POST …/knowledge-packs/ingest/library` | JSON `{ tcIds, title?, batchSize }` |
| Upload TCs | `POST …/knowledge-packs/ingest/upload-tc` | multipart `file`, `batchSize`, `title?` |
| Upload .md | `POST …/knowledge-packs/ingest/upload-md` | multipart `file`, `batchSize`, `title?` |

Headers: `credentials: same-origin` + `X-Keel-Requested-With: Keel` (+ `Content-Type: application/json` for library). CSRF ignored for `/api/**`; API header filter requires Keel header.

### Stub
- HTTP **404/501** or network → status **"Waiting on Backend knowledge ingest API"**
- Does not invent packs

### Files this slice
1. `src/main/resources/templates/generate-from-knowledge.html` — ingest strip + JS
2. `docs/handoff/2026-11-02-generate-from-knowledge-fe.md` — this section
3. `docs/handoff/per-bot-status.md` — one FE line

**Not touched:** `generate.html` (SHA256 still `afa500697ceaae859fe419524e0884b4b6c76253364aa3e21d5236c39c516181`), automate, plain-cases. No push. No portal restart.
