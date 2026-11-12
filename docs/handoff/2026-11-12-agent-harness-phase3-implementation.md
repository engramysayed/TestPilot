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

## Live Cursor verification (2026-11-12, opt-in)

Machine evidence: `docs/handoff/2026-11-12-cursor-live-recovery-evidence.json`

| Tier | Test | Observed |
|------|------|----------|
| 0 | `AgentStoreLockCrossProcessIT`, `AgentStoreLockTest` | **Pass** (3 tests) |
| 2 | `CursorActiveRunRecoveryLiveIT` | **Pass** (~140s, post-fix): execute + kill + fresh JVM `coordinator.execute` → terminal `INTERRUPTED`, child exit 0, ids stable, `agents.ndjson` 1→1, no auto-cancel. Pre-fix fail (~134s, `run.started` required) recorded in evidence JSON. |
| 3 | `CursorReconcilePollBudgetLiveIT` (opt-in) | **Pass** (~25s): direct `op=reconcile`, 15s budget → `INTERRUPTED`, 20 `reconcile.poll` events, no cancel lifecycle, `agents.ndjson` count unchanged (1). |

**Policy A Java fix (committed with this handoff):** `CursorAgentRuntime.start()` — on reconcile with persisted ids, accept terminal sidecar without `run.started` (use task ids); when sidecar emits `run.started`, verify ids match. Unit: `CursorAgentRuntimeTest`; live Tier 2 re-verified.

## Known gaps

- Poll-budget `INTERRUPTED` does not write `result.json` or complete the agent task as success — callers must treat `INTERRUPTED` as a first-class terminal (retries / human follow-up per product policy).
- Job **message** warning when both report paths fail — not wired in bridge today; persistence fail-open is tested at `ValidationReportWriter` only.
- D10 staged `agent-validation.json` — not implemented (product optional).
