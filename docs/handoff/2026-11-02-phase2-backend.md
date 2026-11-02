# Phase 2 backend — failure replay + heal Accept/Reject + flaky tags (2026-11-02)

**Status:** GREEN (portal stores/REST + AE writers aligned + targeted tests)  
**Bot:** Backend (live-apply on Ramy)  
**Tip:** `0a38215` (from base `83b9077`)  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\phase2-20261102-1900`  
**Contract:** `docs/handoff/2026-11-02-phase2-api-contract.md` (exact disk + paths)  
**Time (Africa/Cairo):** 2026-11-02 ~19:10

## Disk (locked — shared with AE writers)

| Area | Path |
|------|------|
| Failure evidence | `{projectRoot}/execute-runs/{jobId}/evidence/{tcFolder}/failure/{stepIndex}/` (`meta.json`, optional `screen.png` / `network.json` / `console.json`) |
| Heal proposals | `{projectRoot}/heal-proposals/{proposalId}.json` |
| Flaky tags | `{projectRoot}/flaky-tags.json` |

AE already emits via `FailureReplayWriter`, `HealProposalWriter`, `FlakyTagStore`. Portal is SoT for list/get/accept/reject/clear.

Generated Java Accept target: `{projectRoot}/src/main/java/project/pages/{PageClass}.java` only — **409** if missing (never create). Also resolves `CodegenNaming.pageClassName` when AE stores a page stem.

## API summary

### Failure replay (job-owned like existing screenshots)

| Method | Path |
|--------|------|
| GET | `/api/execute-runs/{jobId}/failures` |
| GET | `/api/execute-runs/{jobId}/failures/{tcId}` |
| GET | `/api/execute-runs/{jobId}/failures/{tcId}/{stepIndex}` |
| GET | `…/screen` \| `…/network` \| `…/console` |

### Heal proposals (`ProjectAccess` readable / operable)

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/projects/{projectId}/heal-proposals?status=pending` | |
| GET | `/api/projects/{projectId}/heal-proposals/{proposalId}` | |
| POST | `/api/projects/{projectId}/heal-proposals` | AE create → `pending` (disk emit also OK) |
| POST | `…/{proposalId}/accept` | patch existing page locator only |
| POST | `…/{proposalId}/reject` | discard; no code change |

### Flaky tags

| Method | Path |
|--------|------|
| GET | `/api/projects/{projectId}/flaky-tags` |
| POST | `/api/projects/{projectId}/flaky-tags` body `{tcId, stepIndex?, reason?, sourceJobId?, signal?}` |
| DELETE | `/api/projects/{projectId}/flaky-tags/{tagId}` | soft-clear (`clearedAt`) |

Auth: KnowledgePack / PlainCase style — `denyUnlessReadable` (GET) / `denyUnlessOperable` (mutate). Failure GETs use `ExecuteRunService.requireOwnedExecuteJob`.

## Files (Backend)

- `FailureEvidenceDiskStore.java` + `FailureEvidenceService.java` + `FailureReplayController.java`
- `HealProposalDiskStore.java` + `HealJavaLocatorPatcher.java` + `HealProposalService.java` + `HealProposalController.java`
- `FlakyTagDiskStore.java` + `FlakyTagService.java` + `FlakyTagController.java`
- Portal tests: `FailureEvidenceDiskStoreTest`, `HealProposalDiskStoreTest`, `FlakyTagDiskStoreTest`
- Converted AE JUnit→TestNG (compile gate): `FailureReplayWriterTest`, `HealProposalWriterTest`, `FlakyTagStoreTest`

**Not touched:** CodeWriter / PageAccumulator / EmitStepNormalizer / W2 ladder; PlainCase*; Visual*; META-INF; portal DB; api-keys.local.bat.

## Verify

```bat
mvn -q -pl keel-portal -Dtest=FailureEvidenceDiskStoreTest,HealProposalDiskStoreTest,FlakyTagDiskStoreTest,FailureReplayWriterTest,HealProposalWriterTest,FlakyTagStoreTest test
```

Expect **14/0**.

## Portal restart

Maestro owns `:8080` rebuild/restart after tip advances. No `mvn clean` if jar locked. Hard-refresh FE after restart.

## P3

Suite schedules spike deferred (no spare time after P2 GREEN).
