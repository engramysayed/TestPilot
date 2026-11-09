# Scheduled suites audit (regression / smoke / nightly / monitors) — session handoff (2026-11-08)

**Nothing committed. Build/tests not run** (Maven Central unreachable from the sandbox).

## Defects found and fixed

| # | Defect | Fix |
|---|--------|-----|
| 1 | Suite context (alert channel, monitor target, trigger) was a ThreadLocal on the dispatcher; Execute runs on the async worker, so **no suite run ever alerted or used its monitor target** | Persisted as `execute-runs/{jobId}/suite-context.json` (`SuiteRunContextFiles`); `ExecuteWorker` restores it on its thread |
| 2 | Portal never called `SuiteAlertNotifier.registerChannels` → alerts logged only | `SuiteScheduleWiring` registers the Slack/Email/Telegram beans at startup |
| 3 | Alert only when **all** cases failed; crashes never alerted | Any failing case alerts (all = CRITICAL, some = WARNING); crashed runs alert `SUITE_RUN_ERROR`; refused scheduled starts alert `SUITE_SCHEDULE_FAILED` |
| 4 | Engine path (`GET /api/schedules/due` + `POST …/runs`) never claimed the slot → duplicate runs (poll overlap, several engines, portal worker + engine — the portal worker is now enabled by default) | `startRun` with trigger schedule/monitor claims the slot (`409 SCHEDULE_NOT_DUE` if already started); portal worker passes `preClaimed=true` |
| 5 | `suites.json` read-modify-write without a lock, non-atomic write | Per-file lock on every mutation + claim; temp file + atomic move |
| 6 | A manual run of a scheduled suite overwrote `lastJobId` and pushed `nextRunAt` | Only schedule/monitor runs touch the schedule; fire no longer re-advances `nextRunAt` (the claim did) |
| 7 | No overlap guard → pile-up of long regressions | Previous scheduled job QUEUED/RUNNING → slot skipped, `SKIPPED_OVERLAP` recorded + alert |
| 8 | Cron allowed every minute (intervals ≥ 5 min) | Cron rejected if any of the next 50 firings are < 5 min apart |
| 9 | After downtime the nightly fired at whatever time Keel came back | Slot older than `delivery.suites.misfire-grace-minutes` (60) → skipped, `MISSED` recorded |
| 10 | Scheduled runs used the first credential profile in DB order; engine path sent none → `CREDENTIAL_PROFILE_REQUIRED` | Schedule has `credentialProfile`; fallback = first by name |
| 11 | `lastFailure` never cleared; monitor `lastStatus` always "unknown" | Cleared on a successful fire; `recordRunOutcome` writes `lastJobStatus`, `lastResult`, monitor `lastStatus` (green/degraded/red) + `lastCheckedAt` |
| 12 | Cron zone hard-coded Africa/Cairo | Per-schedule `timezone` (validated) + `delivery.suites.schedule-zone` default |

UI: Suites → Schedule has *Time zone* and *Login profile*, and shows next run, last run (status, passed/failed), problem, and monitor status.
Guide → Suites updated.

## Tests

`SuiteDiskStoreTest` (+6: cron gap, timezone, profile kept, misfire, 16-thread claim race, outcome/monitor),
`SuiteAlertNotifierTest` (+2: partial failure warning, context file round trip).

## Known remaining

- Runs that require a **private runner** are not executed by `ExecuteWorker`, so their suite outcome/alerts are not recorded by the portal.
- `alertMinSeverity` on a suite is stored but only the channel's own minimum severity filters alerts.
- Claim windows: the portal worker claims up to `within-seconds` (60) early; the API claim allows 120 s — runs may start up to a minute or two before the slot.

## Verify

```bat
mvn -pl keel-portal "-Dtest=SuiteDiskStoreTest,SuiteAlertNotifierTest,SuiteSliceEApiTest" test
```
Then: put a 5-minute interval schedule on a pack with an alert channel, make one case fail, and confirm the alert,
the "Last run" line, and that pressing *Run* manually does not change "Next run".
