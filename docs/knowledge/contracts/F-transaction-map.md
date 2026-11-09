# Slice F — transaction map (2026-11-07)

**Scope delivered in this slice:** in-portal schedule dispatch (not full durable occurrence DB).

## Claim → run → stamp

| Step | Component | Mutation? |
|------|-----------|------------|
| List due | `SuiteService.listDueForAllProjects` | Read-only |
| Reserve slot | `SuiteDiskStore.tryClaimDue` | Advances `nextRunAt`, sets `lastClaimedAt` |
| Enqueue | `SuiteRunService.startRun` (`trigger=schedule`) | Job row + optional `SUITES_MONITOR` debit |
| Success stamp | `SuiteDiskStore.recordSuiteRunFire` | `lastFiredAt`, `lastJobId`, recompute `nextRunAt` |
| Failure stamp | `SuiteDiskStore.recordScheduleFailure` | `schedule.lastFailure` |

## Workers

| Worker | Enable flag | Notes |
|--------|-------------|-------|
| AE `ScheduleClaimPoller` | `KEEL_SCHEDULE_CLAIM` | Existing; GET `/api/schedules/due` + POST runs |
| Portal `SuitePortalScheduleWorker` | `delivery.suites.portal-schedule-worker.enabled` | Default **false**; ticks `SuiteScheduleDispatchService` |

## Not in this slice

- SQL occurrence store, lease/fence multi-worker
- `occurrenceId` on run request body
- Automated live 5-minute schedule QA (manual/home only)

## HOME live gate

Portal :8080 smoke + `/suites` 200 documented in slice F handoff; scheduled fire requires enabled worker + due suite + library TCs.
