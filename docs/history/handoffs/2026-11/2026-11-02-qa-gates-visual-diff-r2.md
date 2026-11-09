# QA gate: visual-diff r2 (RoundTrip + Comparer + Api + ScreenshotDiff + ProvePhaseVisualAssertPolicy)

- **Date:** 2026-11-02 ~18:12 (Africa/Cairo, UTC+2)
- **Slice:** visual-diff re-gate r2 after AE GREEN claim (caseKey=portal KEY `TC_1`, not storageKey)
- **Feature:** `6296112` (`feat(ae): ProvePhase visual-diff compare via DiskStore (flagged)`) — visible in `git log` below tip
- **Live tree:** `D:\Work\projects\Keel\keel\TestPilot` (machine Ramy / `96a3c81a-96a9-490d-a835-f6bf263f986b`)
- **Tip SHA:** `adfc4e5` (`adfc4e5c598f015f9ed59b98153e698f470e2dac`) — matches expected tip (`docs(ae): record Phase 4 visual-diff tip SHA`)
- **Branch:** `main`
- **Concurrent Maven:** none before run. Only runtime `java -jar keel-portal-1.0-SNAPSHOT.jar --server.port=8080` (PID 2236). **No portal restart.** No push/commit. Maven serialize observed.
- **Command:**
  ```
  mvn -B -pl keel-portal -am "-Dtest=VisualDiffRoundTripTest,VisualDiffComparerTest,VisualDiffApiTest,ScreenshotDiffTest,ProvePhaseVisualAssertPolicyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  ```
- **VERDICT:** **GREEN**

## Surefire summary

| Metric | Value |
|--------|-------|
| Exit code | 0 |
| BUILD | SUCCESS (reactor: keel-parent, keel-contract, mobile-contracts, keel-portal) |
| Tests run | 18 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Elapsed (surefire suite) | 8.592 s |
| Total Maven time | 12.217 s |
| Finished at | 2026-11-02 18:12:37 Africa/Cairo |

### Per class (all five specified classes ran)

| Class | Run | Fail | Pass | Notes |
|-------|-----|------|------|-------|
| `delivery.visual.VisualDiffComparerTest` | 6 | 0 | 6 | includes `caseKeyMatchesPortalKeyNotStorageKey` → expects portal KEY `TC_1` |
| `delivery.portal.service.VisualDiffRoundTripTest` | 2 | 0 | 2 | PASS |
| `delivery.portal.api.VisualDiffApiTest` | 1 | 0 | 1 | PASS (Spring Boot test context) |
| `delivery.effect.ScreenshotDiffTest` | 2 | 0 | 2 | present; ran |
| `delivery.job.ProvePhaseVisualAssertPolicyTest` | 7 | 0 | 7 | present; ran |

**Totals:** 6+2+1+2+7 = **18/0**. No missing-class skips needed (`surefire.failIfNoSpecifiedTests=false` unused).

### caseKey expectation (AE claim)

- Comparer test `caseKeyMatchesPortalKeyNotStorageKey` asserts portal case KEY **`TC_1`** (not storageKey encoding such as `TC~5F1`).
- Aligns with AE claim GREEN 18/0 on tip `adfc4e5` / feature `6296112`.

Reports: `keel-portal\target\surefire-reports\TestSuite.txt`, `TEST-TestSuite.xml`.

## Ops / next

- Portal left as found (running jar on 8080; PID 2236). No restart mid-gate.
- Prior r1 handoff (`2026-11-02-qa-gates-visual-diff.md`) was **RED** 9/4 on tip `8c486cc` (Comparer NO_BASELINE + storageKey encoding). This **r2** supersedes for tip `adfc4e5`.
- Tree clear for next Maven serialize consumer.

## One-line for Maestro / Leader

**GREEN** tip `adfc4e5` (feature `6296112`) visual-diff r2 gate **18/0** — Comparer/RoundTrip/Api/ScreenshotDiff/ProvePhaseVisualAssertPolicy all PASS; caseKey=`TC_1` (portal KEY); portal not restarted; handoff `docs/history/handoffs/2026-11/2026-11-02-qa-gates-visual-diff-r2.md`.
