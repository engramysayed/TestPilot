# Execution recovery — investigation evidence payload (Phase 2)

**Schema:** `keel.execution-investigation-evidence/v1`  
**Date:** 2026-11-15

## Purpose

Structured, read-only context for `EXECUTION_FAILURE_INVESTIGATION_V1` harness tasks after the Execute heal pipeline (`HealCascade` + `heal.mjs`) is exhausted. The payload is **not** proof of pass and does not replace the original `StepAttempt` failure.

## Data sources

| Field | Source | Missing behavior |
|-------|--------|------------------|
| `testCaseId`, `expectedResult`, `steps` | `ManualTestCase` | Empty string + `present: false` |
| `intentIndex`, `intentText`, `intentKind` | `StepIntentBinder.IntentLine` | Nullable index only when absent |
| `originalFailure`, `healSkipReason` | ProvePhase loop | `null` if blank |
| `failureClass` | `ExecutionFailureClassifier` | `UNKNOWN` |
| `currentUrl` | `ProvePhase.currentUrl(driver)` | Omitted if driver dead |
| `evidenceDir` | Step outcome / prove evidence tree | Reference path only |
| `screenshotPath` | Heal screenshot under evidence | Omitted if not written |
| `failedLocators` | `FailedLocator` list | Empty array |
| `healTier`, `healPipelineExhausted` | ProvePhase | Tier `none` when unused |
| `healingAttempts` | Derived from tier + flags | Explicit `ollamaAttempted` / `cursorHealAttempted` booleans |
| `visionLayerAttempted` | `VisionGroundingConfig.enabled()` at build time | Boolean only (no fabricated vision hits) |
| `stackTrace` | Not captured in ProvePhase today | **Always omitted** in v1 |

## Security

- Failure strings pass through `ProvePhase.scrubReason` when a `ConversionJobRequest` is supplied (credential scrubbing consistent with existing prove messages).
- No raw DOM HTML in v1 payload (path references only) to avoid large/secret leakage.
- Workspace path must stay under job `workDir`; harness policy uses READ_ONLY tools for investigation type.

## Persistence

`ExecutionRecoveryHarnessBridge` writes `execution-investigation-evidence.json` under `agent-runs/{jobId}/{taskId}/` before coordinator `execute()`.

## Worker lifecycle (Phase 2c)

| Concern | Behavior |
|---------|----------|
| **Where wired** | Portal `ExecuteWorker` → `ExecuteJobRunner(harnessProps)` + shared `cancelCheck`; `ConversionWorker` → `ConversionJobRunner(harnessProps, leaseBudget)` |
| **Remote runners** | `EngineAgent` / `PrivateRunnerAgent` use `new ExecuteJobRunner()` — **no** harness props; recovery intentionally off until remote config exists |
| **Lease renewal** | `PortalStore.beginWork` → `LeaseKeeper` (15s); continues on a background thread while prove blocks on synchronous investigation |
| **Cancel before harness** | `ProvePhase` + gate + bridge skip with `JOB_CANCELLED`; no coordinator `execute()` |
| **Cancel during harness** | `JobCancelSupport.awaitOrCancel` polls job cancel; on abort → `requestCancel` + `AgentHarnessCoordinator.cancel` (Policy A path), then `JobCancelledException` propagates to worker |
| **Harness timeout** | `min(max-runtime-minutes, leaseBudget)` on Automate; Execute uses property cap only unless a lease budget is passed later |
| **Pass/fail** | Investigation never flips `StepAttempt.ok`; original `lastReason` preserved |

## Versioning

Top-level `schemaVersion: 1` and `contractId: keel.execution-investigation-evidence/v1`. Increment only when fields are added or semantics change.

## Example (truncated)

```json
{
  "schemaVersion": 1,
  "contractId": "keel.execution-investigation-evidence/v1",
  "testCaseId": "TC_01",
  "intentIndex": 2,
  "intentKind": "CLICK",
  "failureClass": "LOCATOR_RESOLUTION",
  "originalFailure": "HEAL_EXHAUSTED: execute still failed after Cursor heal: element not found",
  "healPipelineExhausted": true,
  "healTier": "cursor",
  "healingAttempts": {
    "ollamaAttempted": true,
    "cursorHealAttempted": true,
    "visionLayerEnabled": false
  },
  "failedLocators": [
    { "strategy": "xpath", "value": "//button[@id='login']", "errorSummary": "not found" }
  ],
  "evidenceDir": "evidence/TC_01/step-2",
  "missingFields": ["stackTrace", "consoleLog", "networkErrors"]
}
```