# Slice F — portal schedule dispatch (Cursor)

Observed-at: 2026-11-07 ~16:24 Africa/Cairo

## Changed paths

- `SuiteRunService.java` (extracted from `SuiteController`)
- `SuiteScheduleDispatchService.java`, `SuitePortalScheduleWorker.java`
- `SuiteDiskStore.tryClaimDue`, `recordScheduleFailure`, schedule normalize fields
- `SuiteService.listDueForAllProjects`, `PortalStore.listProjectsForScheduleDispatch`
- `application.properties` worker flags (default disabled)

## Offline gate

`SuiteDiskStoreTest.tryClaimDue_advancesNextRunAt` + existing suite tests — PASS with E API tests.

## HOME live gate

- Portal started locally (PID in `target/portal-live.log`); **not** production debit QA.
- `GET /suites` → **200** with Basic auth (`engramisayed@localhost`).
- Automatic interval fire: enable `delivery.suites.portal-schedule-worker.enabled=true` and suite with workbook `tcIds` — manual follow-up.

## Release

NO — worker off by default.
