# Phase 3 interim API / disk contract — Backend lock for FE/AE (2026-11-02)

**Tip base:** e9ee274 (impl in flight)
**Status:** INTERIM LOCK — FE shells + AE tag filter / schedule hook against these paths. Do not invent a second store.
**Reuse:** delivery.portal.alerts.AlertChannel (+ Slack/Email/Telegram/Stub). No second channel stack.

## 1. Suites (tags + packs)

A suite is a named pack of portal TC ids with one or more tags. Built-in tags seed three packs per project on first read if missing: smoke, 
egression, 
ightly (empty 	cIds until filled). User-named packs use custom tags (not reserved).

### Disk

`
{projectRoot}/suites.json
`

Array of suite objects:

| Field | Type | Notes |
|-------|------|-------|
| suiteId | string | UUID |
| name | string | display name |
| tags | string[] | includes built-in or custom; at least one |
| tcIds | string[] | portal TC ids (e.g. TC_1) |
| builtIn | bool | true for smoke/regression/nightly seed packs |
| alertChannelId | string \| null | slack \| email \| 	elegram \| stub (channel id()) |
| alertMinSeverity | string \| null | optional override; else channel default |
| schedule | object \| null | see §2; null = unscheduled |
| monitor | object \| null | see §4; null = not promoted |
| createdAt | ISO-8601 | |
| updatedAt | ISO-8601 | |

Reserved built-in tag names (case-insensitive): smoke, 
egression, 
ightly. Creating a user pack with those tags → 400.

### Portal APIs

| Method | Path | Body / notes |
|--------|------|----------------|
| GET | /api/projects/{projectId}/suites | ?tag= optional filter (any match) |
| GET | /api/projects/{projectId}/suites/{suiteId} | |
| POST | /api/projects/{projectId}/suites | {name, tags[], tcIds[]} → 201; uiltIn=false |
| PUT | /api/projects/{projectId}/suites/{suiteId} | {name?, tags?, tcIds?} — built-in: name/tags immutable; 	cIds editable |
| DELETE | /api/projects/{projectId}/suites/{suiteId} | built-in → 409; else remove suite (+ clear schedule/monitor fields) |

Auth: ProjectAccess readable (GET) / operable (mutate). Same style as PlainCase / FlakyTag.

**Execute / prove filter (AE + portal):** callers may pass ?suiteId= or ?tag= on existing execute/prove start endpoints when Backend wires them; AE must honor the same filter when claiming a scheduled suite run (§3). Until wired, AE reads suite 	cIds from this store via GET suite.

---

## 2. Schedule (attached to a suite)

Schedule lives **inside** the suite object (schedule field), not a separate root file.

### schedule object

| Field | Type | Notes |
|-------|------|-------|
| enabled | bool | |
| kind | string | cron \| interval |
| cron | string \| null | 5-field cron when kind=cron (project/local zone; document as Africa/Cairo for Keel ops) |
| intervalMinutes | int \| null | when kind=interval; min 5 |
| nextRunAt | ISO-8601 \| null | portal-maintained |
| lastFiredAt | ISO-8601 \| null | |
| lastJobId | string \| null | last suite run job id |

### Portal APIs

| Method | Path | Body |
|--------|------|------|
| GET | /api/projects/{projectId}/suites/{suiteId}/schedule | |
| PUT | /api/projects/{projectId}/suites/{suiteId}/schedule | {enabled, kind, cron?, intervalMinutes?} |
| DELETE | /api/projects/{projectId}/suites/{suiteId}/schedule | sets schedule null |

---

## 3. Schedule due / claim (AE hook)

Portal is SoT for due calculation. AE (or portal tick) claims a run:

| Method | Path | Notes |
|--------|------|-------|
| GET | /api/schedules/due | optional ?withinSeconds=60; returns [{projectId, suiteId, schedule, tcIds, alertChannelId}] for operable projects |
| POST | /api/projects/{projectId}/suites/{suiteId}/runs | body optional {trigger: schedule\|manual\|monitor}; starts execute for suite 	cIds; returns job id (reuse ExecuteWorker / existing execute-run shape) |

On successful fire: update suite schedule.lastFiredAt, lastJobId, 
extRunAt.

---

## 4. Alert channel bind

Reuse existing channels — **do not** add new channel classes unless Stub webhook URL config already exists.

| Method | Path | Notes |
|--------|------|-------|
| GET | /api/alert-channels | lists {id, enabled, minSeverity} from registered AlertChannel beans |
| PUT | /api/projects/{projectId}/suites/{suiteId}/alerts | {alertChannelId, alertMinSeverity?} — id must be a known channel; unknown → 400 |
| DELETE | /api/projects/{projectId}/suites/{suiteId}/alerts | clears bind (lertChannelId=null) |

On suite run **fail** or **flake** (flaky-tag signal or failed TCs): portal/AE sends via bound channel using existing AlertChannel.send(Alert). Unbound suite → no send (log only). Stub channel OK for local/dev.

---

## 5. Promote green suite → always-on monitor

### monitor object (on suite)

| Field | Type | Notes |
|-------|------|-------|
| enabled | bool | always-on when true |
| target | string | prod \| staging |
| promotedAt | ISO-8601 | |
| promotedFromJobId | string \| null | green job that justified promote |
| lastStatus | string \| null | green \| 
ed \| lake \| unknown |
| lastCheckedAt | ISO-8601 \| null | |

### Portal APIs

| Method | Path | Body / notes |
|--------|------|----------------|
| POST | /api/projects/{projectId}/suites/{suiteId}/promote-monitor | {target: prod\|staging, fromJobId?} — requires last suite run green when romJobId given (else 409); sets monitor.enabled=true |
| DELETE | /api/projects/{projectId}/suites/{suiteId}/monitor | demote (monitor=null or enabled=false) |
| GET | /api/projects/{projectId}/monitors | suites where monitor.enabled==true |

Monitor runs use the same schedule machinery or an internal tick; failures/flakes fire the suite alert bind. Target selects which base URL / env the runner uses (AE reads monitor.target; exact env resolution is AE-owned).

---

## 6. Out of scope

Postman/API suites; Codegen; Sonic; second alert stack; merge/push without user OK.

## 7. FE / AE parallel notes

- FE: shell suite list/create, schedule form, channel dropdown from GET /api/alert-channels, promote control — stub-wait on 404/501 until Backend GREEN.
- AE: honor 	cIds / tag filter on suite runs; implement claim against GET /api/schedules/due + POST .../runs; read monitor.target when trigger=monitor.
- QA: gate after Backend Surefire + AE claim; serialize Maven.
