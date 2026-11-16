# Phase 6.4 — Cursor repair workspace reconciliation (D-6.3-01)

**Date:** 2026-11-15  
**Defect:** D-6.3-01 — Cursor edits `taskDir/workspace/`; compile / v2 revalidation / promotion used `framework-acceptance/repair-candidate/` with no merge.

## Lifecycle (before change)

1. `AutomateFrameworkAcceptanceGate` snapshots `project/` → `framework-acceptance/original` and copies to `repair-candidate`.
2. `AutomateFrameworkRepairBridge` creates `FRAMEWORK_ADAPTATION_V1`, `ensureIsolatedCopy(taskDir, repair-candidate)` (early workspace seed).
3. **FAKE:** `task.workspacePath` = repair-candidate; agent writes there directly.
4. **CURSOR:** `AgentHarnessCoordinator.prepareCursorWorkspace` copies repair-candidate → `taskDir/workspace/`, repoints `workspacePath` and `allowedWorkspaceRoots` to the isolated path.
5. On `COMPLETED`, gate ran `EmitCompileCheck` and `FrameworkContractValidator` on **repair-candidate only** (stale for Cursor).

## Reconciliation design (Phase 6.4)

**Integration point:** `AutomateFrameworkAcceptanceGate`, immediately after confirmed `repair.succeeded()`, **before** `EmitCompileCheck`.

**Sequence (Cursor only):**

`COMPLETED` → identity checks → `FrameworkRepairWorkspaceReconciler.reconcile(...)` → compile repair-candidate → inventory v2 revalidation → promote on pass.

**Class:** `FrameworkRepairWorkspaceReconciler`

- Permitted relative paths only:
  - `src/main/java/project/pages/`
  - `src/test/java/project/tests/generated/`
  - `src/test/java/project/tests/todo/`
- Fail closed on: task not `COMPLETED`, provider not `CURSOR`, workspace / repair-candidate identity mismatch, symlinks, path escape.
- Manifest: copied / deleted / excluded / unchanged (deterministic diff on permitted tree).
- New status: `RECONCILIATION_FAILED` — no compile-as-success, no promotion.

**FAKE:** unchanged; reconciliation skipped when `providerId != CURSOR`.

**Tests:** `FrameworkRepairWorkspaceReconcilerTest`, `FrameworkAdaptationCursorReconcileIT` (Fake runtime registered as CURSOR).

## Security / boundaries

| Risk | Mitigation |
|------|------------|
| Arbitrary source path | Reconcile only from registered `taskDir/workspace` and `task.workspacePath` |
| Repair-candidate confusion | Must equal `workDir/framework-acceptance/repair-candidate` (real path) |
| Path traversal | Reject `..`; permitted-prefix allow list |
| Symlinks | `NOFOLLOW` + explicit reject on walk |
| Metadata / secrets | Paths outside permitted prefixes excluded |
| Cross-tenant | `AgentRunStore.taskDir(projectRoot, parentJobId, taskId)` + task.json provider/type/status |

## Milestones

| Milestone | Status |
|-----------|--------|
| **RECONCILIATION VERIFIED** | Yes — unit + `FrameworkAdaptationCursorReconcileIT` (deterministic) |
| **LIVE REPAIR VERIFIED** | Yes — `FrameworkAdaptationCursorLiveIT#missingExpectedPage_liveRepairLifecycle` (2026-11-15, ~107s): Cursor COMPLETED → reconcile → v2 pass → promotion; evidence `docs/handoff/phase6-3-live-evidence-MISSING_PAGE.json` |
| **PRODUCTION READY** | No — feature flags unchanged; live repair quality separate from reconciliation |

## Known limitations

- Reconciliation does not prove repair quality (invalid codegen → `REVALIDATION_FAILED` or `COMPILE_FAILED`, not `RECONCILIATION_FAILED`).
- Deletions only within permitted prefixes when removed from agent workspace.
- `pom.xml` and non-generated paths are never copied from agent workspace.