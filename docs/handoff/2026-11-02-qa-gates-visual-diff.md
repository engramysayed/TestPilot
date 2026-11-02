# QA gate: visual-diff (RoundTrip + Comparer + Api)

- **Date:** 2026-11-02 ~18:07 (Africa/Cairo, UTC+2)
- **Slice:** visual-diff gate (next after Backend unit claim 8/0; ApiTest previously not run)
- **Live tree:** `D:\Work\projects\Keel\keel\TestPilot` (machine Ramy / `96a3c81a-96a9-490d-a835-f6bf263f986b`)
- **Tip SHA:** `8c486cc` (`8c486cc785f4ba247ff0a9a3075dface5f536b73`) — matches expected tip
- **Dirty/drift:** yes — `main...keel/main [ahead 5]`; many modified tracked files (codegen, portal UI, props, start scripts, handoffs) and untracked (templates, scripts, `CodegenW1EmitTest.java`, prior handoffs). Worktree not clean relative to tip.
- **Concurrent Maven:** none. Only runtime `java -jar keel-portal-1.0-SNAPSHOT.jar --server.port=8080` (PID 6444). No portal start/restart by this gate. Sonic ignored. No push/commit.
- **Command:**
  ```
  mvn -B -pl keel-portal -am "-Dtest=VisualDiffRoundTripTest,VisualDiffComparerTest,VisualDiffApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  ```
- **VERDICT:** **RED**

## Surefire summary

| Metric | Value |
|--------|-------|
| Exit code | 1 |
| BUILD | FAILURE (`keel-portal`) |
| Tests run | 9 |
| Failures | 4 |
| Errors | 0 |
| Skipped | 0 |
| Elapsed (surefire suite) | 8.377 s |
| Total Maven time | ~24.0 s |
| Finished at | 2026-11-02 18:07:04 Africa/Cairo |

### Per class

| Class | Run | Fail | Pass | Notes |
|-------|-----|------|------|-------|
| `delivery.visual.VisualDiffComparerTest` | 6 | 4 | 2 | Unit; Backend claimed 8/0 earlier — **does not hold on this dirty tip** |
| `delivery.portal.service.VisualDiffRoundTripTest` | 2 | 0 | 2 | PASS |
| `delivery.portal.api.VisualDiffApiTest` | 1 | 0 | 1 | PASS (first run this gate) |

Comparer PASS: `featureFlagDefaultsOff`, `noBaselineWritesActualAndNoBaselineStatus`.

### Failures (Comparer)

1. `caseKeyUsesStorageKeyEncoding` — expected `TC~5F1`, found `TC_1` (storage-key encoding mismatch vs `TcIdentity.storageKey` / test expectation).
2. `identicalPassWithinThreshold` — expected `PASS`, found `NO_BASELINE` (baseline not found under path test wrote).
3. `realChangeFailsAboveThreshold` — expected `FAIL`, found `NO_BASELINE`.
4. `sizeMismatchFails` — expected `FAIL`, found `NO_BASELINE`.

Pattern: three compare paths return `NO_BASELINE` (path/key layout drift likely tied to #1 encoding), plus explicit caseKey encoding assert fail.

Reports: `keel-portal\target\surefire-reports\TestSuite.txt`, `testng-results.xml`.

## Ops / next

- Portal left as found (running jar on 8080). No restart.
- Maven serialize rule observed; tree clear for next reactor (Codegen W1).
- Blocker for GREEN: fix `VisualDiffComparer` / `VisualBaselinePaths` caseKey encoding + baseline path so Comparer unit suite matches RoundTrip/Api (which already pass via store/API path).

## One-line for Maestro

**RED** tip `8c486cc` (dirty) visual-diff gate 9/4/0 — RoundTrip 2/0 + Api 1/0 PASS; Comparer 6/4 (NO_BASELINE + `TC~5F1` vs `TC_1`); handoff written; standing by for Codegen W1.
