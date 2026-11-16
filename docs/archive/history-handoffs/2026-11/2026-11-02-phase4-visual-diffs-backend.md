# Phase 4 — Visual diffs backend (baselines + compare + approve) — 2026-11-02

**Status:** Live-applied on tip (this commit). Cloud agents were unavailable; applied on Windows PC checkout.

**Product intent:** Keep baseline screens from a good Execute/Hunt run; later runs flag layout/CSS drift via `ScreenshotDiff` even when clicks pass; FE can approve/replace baselines. Storage lives on the **project disk** (same tree as evidence / design-references), not a second DB.

## Disk layout (locked with AE)

Under `PortalStore.projectDiskRoot(projectId)`:

```
visual-baselines/{caseKey}/{viewport}/baseline.png
visual-baselines/{caseKey}/{viewport}/meta.json
visual-diffs/{runId}/{caseKey}/{viewport}/actual.png
visual-diffs/{runId}/{caseKey}/{viewport}/diff.png   # only when compare fails (optional)
visual-diffs/{runId}/{caseKey}/{viewport}/result.json
```

- `caseKey` / `viewport` / `runId`: `[A-Za-z0-9][A-Za-z0-9_-]{0,127}`
- `meta.json`: caseKey, viewport, createdAt, updatedAt, sourceRunId?, width/height, block (default 8), lumaThreshold (default 8), maxChangedFraction (default 0.02)
- `result.json`: runId, caseKey, viewport, passed, changedBlocks, totalBlocks, changedFraction, maxChangedFraction, baselineRel, actualFile, diffFile?, comparedAt

## Compare engine

Reuses `delivery.effect.ScreenshotDiff` (block mean-luma). Extended with:

- `Result.passes(maxChangedFraction)`
- `renderDiffPng(...)` — magenta block highlight overlay for FE / evidence

## Service hook (Execute / Hunt)

`delivery.portal.service.VisualDiffService#compareActual(projectId, runId, caseKey, viewport, actualPng, writeDiffPng)`

Call after a screenshot lands (or from an explicit FE compare). Does **not** auto-wire into HuntWorker / Execute run loop in this slice (avoids fragile HuntWorker edits). AE can call the same disk paths via `VisualDiffDiskStore`.

Also: `replaceBaseline`, `approveFromRun`, `listBaselines`, `listDiffs(..., pendingOnly)`.

## REST APIs

Auth mirror of `DesignReferenceController`:

- **Reads:** owned project (`getOwnedProject`) → 404 if missing
- **Writes** (upload / approve / compare): `ProjectAccess.denyUnlessOperable`

### Baselines — `/api/projects/{projectId}/visual-baselines`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/projects/{projectId}/visual-baselines` | List baselines (meta) |
| GET | `/api/projects/{projectId}/visual-baselines/{caseKey}/{viewport}` | One baseline meta |
| GET | `/api/projects/{projectId}/visual-baselines/{caseKey}/{viewport}/image` | `baseline.png` |
| POST | `/api/projects/{projectId}/visual-baselines/{caseKey}/{viewport}` | Replace baseline (multipart `file`; optional `sourceRunId`, `block`, `lumaThreshold`, `maxChangedFraction`) |
| PUT | same | Same as POST replace |
| POST | `/api/projects/{projectId}/visual-baselines/{caseKey}/{viewport}/approve` | JSON `{"runId":"..."}` — promote that run’s `actual.png` → new baseline |

### Diffs — `/api/projects/{projectId}/visual-diffs`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/projects/{projectId}/visual-diffs?runId=&pending=` | List comparisons; `pending=true` → failed only |
| GET | `/api/projects/{projectId}/visual-diffs/{runId}/{caseKey}/{viewport}` | One `result.json` |
| GET | `/api/projects/{projectId}/visual-diffs/{runId}/{caseKey}/{viewport}/actual` | actual.png |
| GET | `/api/projects/{projectId}/visual-diffs/{runId}/{caseKey}/{viewport}/diff` | diff.png (404 if pass / absent) |
| POST | `/api/projects/{projectId}/visual-diffs/compare` | multipart: `file` (actual PNG) + `runId`, `caseKey`, `viewport`, optional `writeDiffPng` |

## Code paths

| Path | Role |
|------|------|
| `src/main/java/delivery/effect/ScreenshotDiff.java` | Compare + renderDiffPng |
| `src/main/java/delivery/portal/service/VisualDiffDiskStore.java` | Disk layout |
| `src/main/java/delivery/portal/service/VisualDiffService.java` | Portal facade / Execute hook |
| `src/main/java/delivery/portal/api/VisualBaselineController.java` | Baseline REST |
| `src/main/java/delivery/portal/api/VisualDiffController.java` | Diff REST |
| `src/test/java/delivery/portal/service/VisualDiffRoundTripTest.java` | Disk round-trip |
| `src/test/java/delivery/portal/api/VisualDiffApiTest.java` | MockMvc API round-trip |

**Not edited:** HuntWorker, META-INF/, portal DB, api-keys.local.bat.

## Verify

```bat
cd /d D:\Work\projects\Keel\keel\TestPilot
mvn -pl keel-portal -am -Dtest=VisualDiffRoundTripTest,VisualDiffApiTest,ScreenshotDiffTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expect round-trip: baseline save → compare change → fail → approve → compare pass.

## Ops / restart

- Portal classpath pick-up: rebuild/restart portal (`rebuild-portal.cmd` / `start-portal.cmd`) after pull/commit.
- HuntWorker: **no change** this slice; no HuntWorker restart required for visual-diff APIs.
- Backup before apply: `_backup/visual-diffs-20261102-1745`

## Next for FE

1. Approve UX: list `GET .../visual-diffs?pending=true` (or by runId).
2. Side-by-side: baseline `/visual-baselines/.../image`, actual `.../visual-diffs/.../actual`, optional `.../diff`.
3. Approve button → `POST .../visual-baselines/{case}/{viewport}/approve` with `{runId}`.
4. Replace upload → `POST/PUT .../visual-baselines/{case}/{viewport}` multipart.
5. Coordinate with AE to call `VisualDiffService.compareActual` (or write the same disk paths) when Execute/Hunt screenshots land.

## Out of scope

- Postman / API Execute
- Sonic
- Auto-failing Execute jobs on visual drift (policy later)
- HuntWorker auto-hook
