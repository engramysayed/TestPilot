# Phase 3 backend — suites + schedules + alerts + monitor (2026-11-02)

**Status:** GREEN (portal stores/REST + targeted Surefire)  
**Bot:** Backend (live-apply on Ramy)  
**Tip:** c008273  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\phase3-20261102-1953` (new files only; no prior Suite* overwrite)  
**Contract:** `docs/handoff/2026-11-02-phase3-api-contract.md` (`ce4bc2c`)  
**Time (Africa/Cairo):** 2026-11-02 ~19:55

## Disk (locked)

| Area | Path |
|------|------|
| Suites SoT | `{projectRoot}/suites.json` |

First read seeds built-in packs **smoke** / **regression** / **nightly** (`builtIn=true`, empty `tcIds`) when missing. Schedule + monitor + alert bind live **on the suite object** (not separate root files).

## API summary

Auth: `ProjectAccess` readable (GET) / operable (mutate). Same `ApiError` shape as Phase 2.

### Suites

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/projects/{projectId}/suites?tag=` | optional any-match tag filter |
| GET | `/api/projects/{projectId}/suites/{suiteId}` | |
| POST | `/api/projects/{projectId}/suites` | `{name, tags[], tcIds[]}` → 201; reserved tags → 400 |
| PUT | `/api/projects/{projectId}/suites/{suiteId}` | built-in: name/tags immutable; tcIds editable |
| DELETE | `/api/projects/{projectId}/suites/{suiteId}` | built-in → 409 |

### Schedule (nested)

| Method | Path |
|--------|------|
| GET/PUT/DELETE | `/api/projects/{projectId}/suites/{suiteId}/schedule` |

`kind`: `cron` (5-field; nextRunAt via Spring `CronExpression` in zone **Africa/Cairo**) or `interval` (`intervalMinutes` ≥ 5).

### Alerts

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/alert-channels` | `{id, enabled, minSeverity}` from registered `AlertChannel` beans |
| PUT/DELETE | `/api/projects/{projectId}/suites/{suiteId}/alerts` | unknown channel id → 400 |

**Reuse only** `delivery.portal.alerts.AlertChannel` beans (Slack/Email/Telegram — all extend `StubAlertChannel`). No second channel stack. There is **no** bean with `id=stub`; bind `slack` / `email` / `telegram`.

### Monitor

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/projects/{projectId}/suites/{suiteId}/promote-monitor` | `{target:prod|staging, fromJobId?}`; fromJobId must be owned COMPLETED green job or 409 |
| DELETE | `/api/projects/{projectId}/suites/{suiteId}/monitor` | |
| GET | `/api/projects/{projectId}/monitors` | `monitor.enabled==true` |

### Schedule due + suite run

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/schedules/due?withinSeconds=60` | operable, non-archived projects for current user |
| POST | `/api/projects/{projectId}/suites/{suiteId}/runs` | `{trigger?, credentialProfile?, runner?}` → ACCEPTED `{jobId,...}` |

Suite run materializes via `JobWorkbookResolver.resolve(..., useLibrary=true, suite.tcIds)` then same `ExecuteWorker` / `AuthoringJobRequestFiles` path as execute-runs. Empty `tcIds` → 400. On fire with an enabled schedule, stamps `lastFiredAt` / `lastJobId` / `nextRunAt`.

## Files (Backend)

- `src/main/java/delivery/portal/service/SuiteDiskStore.java`
- `src/main/java/delivery/portal/service/SuiteService.java`
- `src/main/java/delivery/portal/api/SuiteController.java`
- `src/main/java/delivery/portal/api/AlertChannelController.java`
- `src/main/java/delivery/portal/api/ScheduleDueController.java`
- `src/test/java/delivery/portal/service/SuiteDiskStoreTest.java`

**Not touched:** META-INF; portal DB; api-keys.local.bat; Codegen W2; HuntWorker; Postman/Sonic; Generate; AE ProvePhase/TcExecutionService.

## Verify

```bat
mvn -q -pl keel-portal -Dtest=SuiteDiskStoreTest test
```

Expect **6/0** (passed/failed). Also `mvn -q -pl keel-portal -DskipTests compile` SUCCESS.

## Deviations (minimal)

1. Contract mentions `alertChannelId` value `stub` — live beans are `slack`/`email`/`telegram` (stub implementations). GET `/api/alert-channels` returns those ids only.
2. Suite-run execute filter on legacy execute/prove start endpoints (`?suiteId=` / `?tag=`) not dual-wired this slice; AE should use GET suite + POST `.../suites/{id}/runs` / due claim as contracted.

## Portal restart

Maestro owns `:8080` rebuild/restart after tip advances (`rebuild-portal.cmd` / package). No `mvn clean` if jar locked. Hard-refresh FE after restart.
