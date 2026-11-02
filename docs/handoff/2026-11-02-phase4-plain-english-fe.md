# Phase 4A — Plain-English case author FE shell (2026-11-02)

**Status:** GREEN (shell navigable + stub-tolerant save; Backend API not on tip)  
**Bot:** Frontend (live-apply on Ramy)  
**Tip SHA (dirty tree OK):** `cfa220e3bc138dc888df9a0b63aef5056aa90f0c`  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\backup-pre-phase4a-plain-20261102-174801`  
**Time (Africa/Cairo):** 2026-11-02 ~17:48+

## IA decision (LOCKED)

- Sibling sidebar item **Plain cases** → `/plain-cases` (own page).
- Placed in Features nav **immediately before Execute** (after Record):  
  Generate → From knowledge → Record → **Plain cases** → Execute → Automate → …
- `navActive == 'plain-cases'`.
- Permission: `data-feature-perm="feature.execute"` (cases run via Execute).
- Classic **Generate**, **From knowledge**, **Automate** unchanged (bodies not edited).

## Files touched

1. `src/main/resources/templates/fragments.html` — nav link only (after Record, before Execute)
2. `src/main/java/delivery/portal/web/PortalUiController.java` — `@GetMapping("/plain-cases")`, `addNav(..., "plain-cases")`, `pageTitleForNav` → "Plain-English cases"
3. **NEW** `src/main/resources/templates/plain-cases.html` — shell UI
4. `src/main/resources/static/js/keel-sidebar-ia.js` — `featurePaths['/plain-cases'] = 'feature.execute'`
5. **NEW** `docs/handoff/2026-11-02-phase4-plain-english-fe.md` (this file)
6. `docs/handoff/per-bot-status.md` — one short FE line

**Not touched:** `generate.html`, `generate-from-knowledge.html`, Automate/`upload.html`, theme CSS stack. No portal restart. No GFK smoke. No push.

## DoD shell UI checklist

- [x] Project picker (launcher + pinned; session key `keel.plainCasesProjectId`)
- [x] Case list editor: textarea, one action/expectation per line; placeholders `click Login` / `expect Welcome`
- [x] Optional case title field
- [x] Save / Add to project library — stub-tolerant (probe + draft POST)
- [x] “Open in Execute” → `/execute?projectId=…` (note: Execute consumes saved cases when Backend wires source)
- [x] Copy: separate from Generate and From knowledge; no Java required
- [x] Visual diffs (B) **not** implemented this pass

## API stub contract assumed (Backend DRAFT — none found on tip)

**Probe (GET list, first 2xx wins):**

- `GET /api/projects/{projectId}/plain-cases`
- `GET /api/projects/{projectId}/nl-cases`
- `GET /api/projects/{projectId}/plain-english-cases`

**Documented draft create (used when no list API):**

- `POST /api/projects/{projectId}/plain-cases`
- Body: `{ "title"?: string, "lines": string[] }`
- On HTTP 404/501 or network: FE shows **“Waiting on Backend plain-cases API”** and keeps **local preview**.

**Source search this apply:** no portal REST controller for plain-cases / nl-cases found under `delivery.portal.api` — FE stub path remains correct.

## What Backend still owes

1. Persist plain-English cases (`POST` + list/get) under the draft path (or publish a different path in Backend handoff for FE rewire).
2. Wire Execute case-source selection so saved plain cases appear alongside existing sources.
3. (Optional) Return `caseId` / `id` on create for FE confirmation copy.

## Next: visual baseline approve UX

Phase 4 visual diffs (**B**) — **done** — see `docs/handoff/2026-11-02-phase4-visual-diffs-fe.md` (Execute panel + approve baseline).

## Verify notes

- Portal restart **held** (Maestro owns :8080 rebuild). Route may 404 until portal picks up new mapping/template.
- Hard refresh: **Ctrl+F5** after deploy so sidebar IA + new page JS cache clear.
- No Postman/Sonic. No GFK smoke HTTP from this bot.

## READY path (after Maestro restarts portal)

1. Open `/plain-cases` (sidebar **Plain cases** under Features, after Record / before Execute).
2. Pin a project → author lines → Save → expect waiting/stub message until Backend lands.
3. “Open in Execute” carries `projectId`.
4. Confirm `/generate` and `/generate-from-knowledge` unchanged.

## Verify LIVE eeb079e

- **2026-11-02 18:07 Africa/Cairo** — Tip at verify start: `eeb079e`; portal then moved to tip `8c486cc` PID **6444** on `:8080` (no rebuild/restart by this bot).
- Unauth `GET /plain-cases` → **302** `/login`; after product-admin session (`engramisayed@localhost`) → **200** (HTML ~33KB).
- Page markers OK: title **Plain-English cases**, steps `textarea`, placeholders `click Login` / `expect Welcome`, sidebar **Plain cases** `href="/plain-cases"` active + `feature.execute`.
- Stub: `GET /api/projects/.../plain-cases` → **404** (Backend absent); `POST` same path → **403** CSRF (route still unwired). Neighbors: `/generate-from-knowledge` **200**, `/generate` **200**.
- **Verdict: GREEN** — Phase 4A Plain cases shell live; sidebar Y; Backend API still 404.

## Wire LIVE 83b9077 — 2026-11-02 18:20 Africa/Cairo

**Status:** GREEN (FE source wired + API smoke measured)  
**Bot:** Frontend (live-apply on Ramy)  
**Tip:** `83b9077` (`83b9077f537693d24e473554179225e68cdb3756`) — PlainCaseController in live jar PID **7248**  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\backup-pre-plain-cases-wire-20261102-181749`  
**Portal restart by this bot:** NO (Maestro owns rebuild; template change needs Maestro pick-up for HTML, APIs already live)

### FE changes (`plain-cases.html` only)

1. On project pin: `GET /api/projects/{id}/plain-cases` → render **Saved cases** library; select loads title/lines.
2. Save: `POST …/plain-cases` `{ title?, lines[] }` (newline-split, trim empties); on success show `caseId` + refresh list. Still stub-tolerant on **404/501**.
3. **Run in Execute** button: `POST …/plain-cases/{caseId}/execute` for selected/last-saved; on **202** show `jobId` + links to `/status?jobId=` and `/execute`.
4. Headers unchanged vs portal POSTs: `credentials: same-origin`, `X-Keel-Requested-With: Keel`, `Content-Type: application/json`.
5. Dropped multi-path probe (`nl-cases` / `plain-english-cases`); contract path is `/plain-cases` only. Waiting note only when list/save still 404.

**Not touched:** `generate.html`, GFK, Execute visual panel. No push. No portal restart.

### Smoke VERIFY (session + CSRF) — measured

Auth: form POST `/login` (`engramisayed@localhost` from `application.properties`) → **302** `/dashboard`; `GET /api/account` → **200** id=205 ADMIN.  
Mutating calls: cookie session + form `_csrf` / `X-CSRF-TOKEN` + `X-Keel-Requested-With: Keel` (same pattern as GFK smoke).

| Step | Method / path | HTTP | Notes |
|------|----------------|------|--------|
| List (pre) | `GET …/prj_afdde06f424e/plain-cases` | **200** | empty → then 1 after create |
| Save | `POST …/plain-cases` `{title,lines}` | **201** | `caseId=pc_263e4cf00cc9` |
| List | same GET | **200** | 1 case, ids aliased caseId/plainCaseId/id |
| Execute (no baseUrl) | `POST …/plain-cases/pc_263e4cf00cc9/execute` | **400** | `MISSING_BASE_URL` (precondition) |
| PATCH project | `PATCH /api/projects/prj_afdde06f424e` `{baseUrl}` | **200** | set `https://example.com` for smoke only |
| Execute (0 credits) | same execute | **402** | `INSUFFICIENT_CREDITS` need 150 have 0 |
| Credits | `POST /api/admin/credits/205` `SET_ABSOLUTE` 500 | **200** | balance 500 (smoke unlock) |
| Execute | same execute | **202** | `jobId=exec_06a30b89ee3b` status QUEUED source plain-case |

### Smoke Y/N
- Save POST: **Y** (201)
- GET list: **Y** (200)
- Execute 202: **Y** (after baseUrl + credits unlock)

### CSRF note
`X-Keel-Requested-With: Keel` alone is used by page JS (same as GFK/Execute). Smoke curl also sent `X-CSRF-TOKEN` from login `_csrf` / XSRF cookie — both accepted; no 403 on wired routes.

### Next
Maestro: portal rebuild/refresh so jar/templates pick up wired `plain-cases.html` (APIs already on tip). Hard-refresh Ctrl+F5 after rebuild.
