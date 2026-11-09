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
- Project sections (Summary, Test cases, Packages, Settings) are a pill row in `keel-theme-calm.css`. The open section is the filled pill.
- Execute punctuation (`—`, `·`, `…`) was stored double-encoded in `execute.html`. The credential dropdown’s first row is the placeholder “— Select profile —”.
- Bug Hunter, Execute, and Automate show a compact `.credit-quote` bar above the start button (not a hero card). Hunt quantity is steps. Execute and Automate quantity is the selected runnable cases, or every runnable case when none are picked. An uploaded workbook shows the unit price only.
- Execute **Design mockups** starts collapsed, teal, with a short “optional — click to add” hint.
- Project **Version history**: human source labels, Author hides `TC_*` / job ids, **Current** instead of head, Restore aligned on the filter row. `CASE_EDIT` and `RECORD` now store the signed-in email as `sourceRef`.
- Overview attention maps `error`/`fail` to a red card (JS no longer drops FAILED to grey `info`).
- The users page no longer calls `GET /api/admin/users/approvals-stub` (that path collided with delete-user).
- Execute click of a quoted name accepts that label on any tag, anywhere on the page. Vision keeps a box whose text is exactly the name. Ollama can pick that row because it is now on the candidate list. A longer wrapper is not the target. The portal jar must be rebuilt before a live run sees this.
- Overview chart and recent-run rows (progress bar, Done/Failed/Running, Status/Results buttons). Record no longer has the colleague hint.
- Execute results look up screenshots in `TC~5Fxx__occ_N`, so step thumbs actually load. A bind miss also shows the heal PNG from the evidence root.
- `exec_e03990ac7d1c` still missed Support because the 15:50 run used the 01:19 jar, not `c1f70c7`.
- Next AE pack (not coded yet): Tasks 1–9 in `docs/superpowers/plans/2026-10-16-candidate-scorer.md`. Fixture gate + `bind-quality.json` are in 7–8. TIE pick payload is Task 9. Parked: pixel distance, slim-HTML keep-nav, iframe/shadow, Precision groundRank neighborhood.
- Expected is no longer a trailing fake step. Parse order is action → that row’s expected (if any). Orphan expecteds fold onto the last action. Failed asserts do not enter HealCascade. `PageLocatorCatalog` caches locators until URL or DOM fingerprint changes; vision adopts or prefers the stable catalog locator. `exec_054e431e2d64` failed after two good clicks because Expected `"Upload Transaction List"` is not on screen (`Upload Transactions List`) and then heal ran for 151s — that heal path is closed.

## Checks

- `RecordedDraftsTest`, recording tests, and `GenerateMvcTest` / `PlainLanguageNavTest` passed on 2026-10-15.
- Browser: Record wizard, Keel bar, Check this, This page, and the build card. A save on an account with 0 credits kept the test case and did not start the job (a build costs 25). The temporary project was deleted.

## Prices and the account owner

`/pricing` is a read-only table for any signed-in user. The service names are Generate, Execute, Automation, and Bug Hunter. Charged as and What it covers are stored on the price row (`charged_as`, `covers`) and edited on `/admin/pricing`. Bug Hunter’s default line is “a step is one click or one thing typed.” `/admin/pricing` saves `service_prices` (code, label, charged as, covers, cost). `CreditCostTable` reads the cost. Hibernate `ddl-auto=update` adds the new columns. Blank rows, and the old hunt sentence, are filled once on the next request. Missing rows are seeded from `delivery.credits.cost.*` (10 / 5 / 25 / 15).

`BillableQuantity` decides the multiplier at debit time:

- Generate: 1 (one writing job).
- Execute and Automate: runnable workbook rows. `MANUAL` is excluded. An unreadable workbook counts as 1 so a broken file is not free.
- Hunt: `iterationCeiling × cycleCeiling × actionCapPerCycle` from `hunt-runs/{jobId}/request.json`, after `HuntRequest.normalize()`. The default is 80. The hunt form shows `iterations × cycles × actions × unit price`.

Only a user whose stored role name is `OWNER` (`RolesService.isHeadAdmin`) can open Prices, Administration, and Roles, or call the user, credit, and role admin APIs. A role named Admin is refused with 403. Domains stays available to `ROLE_ADMIN`.

Known defect: the stored Bug Hunter unit is still 15, so a default hunt of 80 steps costs 1,200 until the owner lowers it. Generate is still one charge per job. Both can be edited again.

## Finish each job

After a finished job, document it (this file, `CHANGELOG.md`, `docs/PRODUCT.md`, and `D:\priv\testpilot\handoffs\`) and commit it, even when it still has defects. The Cursor rule is `.cursor/rules/finish-job-document-and-commit.mdc`. Do not push unless asked.

## Still parked

- Mobile is not ready for a phone. Unit commit `3ba9079` may be on `keel/main` with this push. Appium doctor stays on the personal machine.
- Do not force-push `main`. Do not turn product credits off.
