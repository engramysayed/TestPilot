# 2026-11-02 — Phase 4 visual diffs (AE)

**Owner:** Keel Automation Engine  
**AE tip:** 6296112  
**Aligned to Backend tip:** `8c486cc` (`docs/handoff/2026-11-02-phase4-visual-diffs-backend.md`)  
**Backup before AE work:** `backup/phase4-visual-diffs-20261102-174936` @ `cfa220e`

## Product rules

- Disk SoT = same project root as Backend (`VisualDiffDiskStore` / portal `projectDiskRoot`).
- Execute keeps screenshots in existing `evidence/`; optional compare writes `visual-diffs/`.
- **No silent baseline rewrite / no auto-seed.** Approve = Backend/FE only (`POST .../approve`).
- Flag **off by default:** `-Dkeel.visual.diff.enabled=true` to activate ProvePhase hook.
- Prefer preferred-hook for plain-English → Execute bind (existing `PreferredHooksStore` + `StepIntentBinder`).

## Disk / key alignment

Same trees as Backend:

```
visual-baselines/{caseKey}/{viewport}/baseline.png + meta.json
visual-diffs/{runId}/{caseKey}/{viewport}/actual.png + result.json (+ diff.png on fail)
```

- **caseKey:** TC display id when it matches Backend KEY `^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$` (e.g. `TC_1`, `TC_LOGIN`). **Not** `TcIdentity.storageKey` (`TC~5F1` is invalid for portal store).
- **viewport:** default `desktop` (`keel.visual.diff.viewport`); same string AE ↔ Backend ↔ FE.
- **Compare:** `VisualDiffComparer` → `VisualDiffDiskStore.compare(..., writeDiffPng=true)` so `result.json` / `diff.png` match portal REST.

## Code

| Path | Role |
|------|------|
| `delivery.visual.VisualBaselinePaths` | KEY-safe caseKey/viewport/runId + path helpers |
| `delivery.visual.VisualDiffComparer` | AE compare facade (NO_BASELINE without throw; else DiskStore) |
| `delivery.visual.VisualDiffConfig` | Feature flags |
| `delivery.visual.VisualDiffSession` | ProvePhase ThreadLocal projectRoot + runId |
| `delivery.job.ProvePhase#applyVisualBaselineDiff` | After vision assert on PASSED; demotes **only** on `FAIL` |
| Backend (already on tip) | `VisualDiffDiskStore`, `VisualDiffService`, REST controllers |

`VisualBaselineStore.writeBaseline` remains for explicit seed/tests — **ProvePhase never calls it**.

## Flags

| Property | Default |
|----------|---------|
| `keel.visual.diff.enabled` | `false` |
| `keel.visual.diff.viewport` | `desktop` |
| `keel.visual.diff.fail-fraction` | `0.02` (DiskStore meta may override per baseline) |
| `keel.visual.diff.block` / `luma-threshold` | `8` / `8` (used when AE writes NO_BASELINE result; live compare uses baseline meta via DiskStore) |

## Plain-English → Execute (unchanged this slice)

| Piece | Path |
|-------|------|
| `StepIntentBinder` | bind / bindSingle + OutcomeKind |
| `AuthoringService` | binder + NestedPreferredHook rewrite |
| `PreferredHooksStore` | ProvePhase `activate` for site preferred-hooks |

No Postman. No silent heal.

## Verify

```bat
cd /d D:\Work\projects\Keel\keel\TestPilot
mvn -pl keel-portal -am clean test -Dtest=VisualDiffComparerTest,VisualDiffRoundTripTest,VisualDiffApiTest,ScreenshotDiffTest,ProvePhaseVisualAssertPolicyTest -Dsurefire.failIfNoSpecifiedTests=false
```

(Serialize Maven on this tree — concurrent runs truncated `JobRecord$JobKind.class` earlier; see `docs/handoff/2026-11-02-qa-testcompile-jobkind.md`.)

## Out of scope / next

- Auto-fail Execute job policy beyond per-TC PARTIAL demote (product later).
- HuntWorker auto-hook (Backend deferred).
- KnowledgePack* / Hunt HTTP reader — do not touch.

## Avoided

- KnowledgePack*, HttpKnowledgePackReader
- api-keys.local.bat, push, Co-authored-by
