# Portal session — 2026-10-15

Living notes for the uncommitted portal work that landed with the 2026-10-15 Keel commit. The machine handoff mirror is `D:\priv\testpilot\handoffs\` (`RESUME-2026-10-15.md` is the file to paste into a new chat).

Product name is **Keel**. The folder is still `TestPilot`. Push **keel** only. Do not push public `origin`.

## Record

`/record` is its own page (same permission as Generate). `/generate?record=1` redirects there.

Chrome opens headed in the portal process. The page hook (`src/main/resources/keel/record-hook.js`) keeps a locator with each action and draws a bar: **Steps**, **Check this**, **This page**. Passwords are not stored. One recording per user, 20 minutes.

Save writes the next `TC_` case through `GeneratedWorkbookService`. Blank KeelPath, source tag `RECORD`.

**Build the framework** starts a normal `CONVERT` job. The worker sees `work/recorded-jobs/{jobId}.json` and calls `ConversionJobRunner.runRecorded`. That writes IR and runs `EmitPhase` only. `ProvePhase` does not run. Other stored drafts are merged so an update does not delete earlier tests. Credits stay on; the job is not created when there is no usable locator or check. A missing Automate permission does not undo the saved case.

A generated id is dropped. Text and address checks do not need a locator. Open and Press Enter stay English. A secret type step is not written into the framework; sign-in sets `needsLoginBeforeMethod`.

## Alerts

Account switches persist on `portal_users.alert_prefs`. A job that newly becomes `FAILED` or `COMPLETED_WITH_BLOCK` sends Telegram, email, or Slack. Saving an enabled channel sends a confirmation first and does not persist if the send fails. Engine dead-man channels stay off and log-only.

## Portal fixes in the same tree

- Project cards: Open, then Archive and Delete. Generate, Execute, Automate, and Bug Hunter are not repeated on the project page.
- Roles permissions use one multi-select.
- Account vision radios are vision only. Generate writing stays Gemma.
- Generate, Execute, and Automate extra sections start open. Project test-case rows stay collapsed because each row loads its steps.
- Bug Hunter triage loads `GET /api/jobs?projectId=&kind=HUNT`.
- The users page no longer calls `GET /api/admin/users/approvals-stub` (that path collided with delete-user).

## Checks

- `RecordedDraftsTest`, recording tests, and `GenerateMvcTest` / `PlainLanguageNavTest` passed on 2026-10-15.
- Browser: Record wizard, Keel bar, Check this, This page, and the build card. A save on an account with 0 credits kept the test case and did not start the job (a build costs 25). The temporary project was deleted.

## Still parked

- Mobile is not ready for a phone. Unit commit `3ba9079` may be on `keel/main` with this push. Appium doctor stays on the personal machine.
- Do not force-push `main`. Do not turn product credits off.
