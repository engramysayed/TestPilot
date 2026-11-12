# Agent harness Phase 3 — implementation handoff

**Status:** Implemented behind flags (default off)  
**Plan:** `2026-11-12-agent-harness-phase3-plan.md`

## Enable (non-prod)

```properties
delivery.agent-harness.enabled=true
delivery.agent-harness.framework-validation.enabled=true
delivery.agent-harness.cursor.enabled=true   # optional; false uses FAKE runtime
```

## Key types

| Component | Package |
|-----------|---------|
| `AgentAwarePublicationGuard` | `delivery.portal.worker` |
| `AgentHarnessJobBridge` | `delivery.agent.harness.convert` |
| `PackageZipExtractor` | `delivery.agent.harness.convert` |
| `AgentStageIndexStore` | `delivery.agent.harness.convert` |
| `ArtifactIntegrityException` | `delivery.job` |

## Tests (deterministic)

**Focused Phase 3 + hardening suite** (from `keel-portal/`):

```bash
mvn test -Dtest=PackageZipExtractorTest,ProjectStorePublicationGuardTest,AgentHarnessPhase3Test,AgentHarnessCoordinatorTest,ValidationReportWriterTest,AgentStageIndexResilienceTest,ConversionWorkerHarnessLeaseIT
```

| Class | Tests |
|-------|------:|
| `PackageZipExtractorTest` | 5 |
| `ProjectStorePublicationGuardTest` | 3 |
| `AgentHarnessPhase3Test` | 4 |
| `AgentHarnessCoordinatorTest` | 5 |
| `ValidationReportWriterTest` | 2 |
| `AgentStageIndexResilienceTest` | 5 |
| `ConversionWorkerHarnessLeaseIT` | 1 |
| **Combined** | **25** (0 failures, 0 skipped — verified 2026-11-12) |

**Test-count reconciliation:** “15 passed” was the first four-class slice (4+3+4+4 zip/guard/phase3/coordinator). “16 across four classes” added `interrupted_withIds_reconciles`. Hardening (+symlink test, `ValidationReportWriterTest`, `AgentStageIndexResilienceTest`, `ConversionWorkerHarnessLeaseIT`) brings the total to **25**.

**Hardening additions**

- `ConversionWorkerHarnessLeaseIT` — convert job + harness + `SlowFakeAgentRuntime`; lease renewal across interval; single completion, no duplicate reclaim.
- `ValidationReportWriterTest` — primary + fallback persist both fail; WARN logs; `REPORT_PERSIST_FAILED` in `events.jsonl` when task dir exists; no `ArtifactIntegrityException`.
- `PackageZipExtractorTest.symlinkEntry_extractedAsRegularFile_notSymlink` + `SymlinkZipFixtures` — ZIP external-attribute symlink entry written as regular file under dest root (no OS symlink, no escape).
- `AgentStageIndexResilienceTest` — A active / B deferred / C evicts B; promotion on A terminal; disk restart; concurrent registry updates; fingerprint dedup.

## Known gaps

- Live Cursor poll-budget `INTERRUPTED` — verify with opt-in `CursorActiveRunRecoveryLiveIT` after policy A sidecar change.
- Job **message** warning when both report paths fail — not wired in bridge today; persistence fail-open is tested at `ValidationReportWriter` only.
- D10 staged `agent-validation.json` — not implemented (product optional).
