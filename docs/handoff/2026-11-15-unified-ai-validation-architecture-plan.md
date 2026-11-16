# Unified AI validation and autonomous recovery — implementation plan

**Date:** 2026-11-15  
**Baseline:** `keel/main` @ `b252a90` (Policy A reconcile on `4f61a14`)  
**Status:** Phase 1 in progress (shared contracts + Generate deterministic validation)

## Discovery summary (code truth)

| Area | What exists | Gap |
|------|-------------|-----|
| **Generate** | `TcGenerateService` → Ollama → `GenerateQualityGate` + `GenerateAuthoringRules`; `ManualTestCase` JSON/CSV | No versioned validation contract; `GENERATED_TEST_REVIEW_V1` enum only |
| **Execute** | `ProvePhase` → bind → `HealCascade` (Ollama pick → Cursor pick/solve via **`heal.mjs`**) | No Agent Harness; `failure-investigation.enabled` unused |
| **Automate** | `ConversionWorker` → Prove → `EmitPhase` → optional `AgentHarnessJobBridge` (READ_ONLY advisory) | No framework repair path; no shared execution recovery |
| **Harness** | `AgentHarnessCoordinator`, `CursorAgentRuntime`, Policy A reconcile | Execute/Generate not wired; `VALIDATION_REPAIR` tools defined but Convert forces READ_ONLY |

**Do not modify:** `tools/cursor-heal/heal.mjs`, heal integration in `HealCascade` / `CursorHealClient`. Cursor final layer for new flows uses **`agent.mjs`** via harness only.

**Preserve:** Policy A (`INTERRUPTED` without auto-`cancelRun`, persisted ids, no blind `Agent.create`).

## Task type and permission matrix (target)

Maps user intent to **existing** `AgentTaskType` where possible.

| User concept | `AgentTaskType` | Tools (default) | Write scope |
|--------------|-----------------|-----------------|-------------|
| TC validation / repair | `GENERATED_TEST_REVIEW_V1` | READ_ONLY → escalation: policy override for authorized TC patch files only (future) | Generation workbook / draft JSON only |
| Execution recovery | `EXECUTION_FAILURE_INVESTIGATION_V1` | READ_ONLY + optional browser tools when policy allows (future) | Authorized automation tree in job work dir |
| Framework validation (Automate) | `FRAMEWORK_VALIDATION_V1` | READ_ONLY at accept gate | Isolated zip workspace |
| Framework repair (Automate) | `FRAMEWORK_ADAPTATION_V1` | `VALIDATION_REPAIR` | Isolated copy only; publish only after compile + revalidate |
| Bug deep-dive | `BUG_INVESTIGATION_V1` | READ_ONLY | Evidence dirs |

Convert **pre-publish** `FRAMEWORK_VALIDATION_V1` stays **advisory** (Phase 3). Automate **acceptance** repair is a separate stage (Phase 4).

## Shared contracts (Phase 1)

Package: `delivery.agent.validation.contract`

- `ValidationContractIds` — `tc-contract/v1`, `framework-contract/v1`
- `ValidationSeverity` — `MANDATORY`, `RECOMMENDED`, `INFORMATIONAL`
- `ValidationFinding` — ruleId, description, severity, artifact, file, line, evidence, suggestedCorrection
- `ValidationReport` — contractId, version, passed, findings, metadata
- `TestCaseContractValidator` — structural (delegates `GenerateQualityGate.validateDetailed`), contextual, entity quoting
- `FrameworkContractValidator` — deterministic page/test mapping and naming under a workspace root
- `QuotedEntityExtractor` — quoted UI strings for entity extractability checks

Persistence format: JSON alongside existing `validation-report.json` pattern (`contract-validation-report.json`).

## Phased delivery

### Phase 1 — Shared contracts + Generate validation (this milestone)

| Item | Deliverable |
|------|-------------|
| Contracts | Java types + validators + unit tests |
| Flags | `delivery.agent-harness.tc-validation.enabled`, `tc-validation.cursor-escalation.enabled` (default **false**) |
| Generate | `GenerateTcContractValidation` + optional hook in `TcGenerateService` after existing quality gate |
| Cursor | `GenerateHarnessBridge` stub: creates `GENERATED_TEST_REVIEW_V1` task + objective manifest; **execute only when** master harness + cursor + escalation flags true |
| Docs | This plan + handoff updates |

**Tests:** `TestCaseContractValidatorTest`, `QuotedEntityExtractorTest`, `FrameworkContractValidatorTest`, `ValidationReportTest`.

**Risks:** Duplicating `GenerateQualityGate` rules — mitigated by delegation. Cursor escalation cost — gated behind triple flag.

### Phase 2 — Execute recovery harness layer (**foundation landed 2026-11-15**)

| Item | Deliverable | Status |
|------|-------------|--------|
| Escalation gate | `ExecutionRecoveryEscalationGate` at `ProvePhase` intent failure boundary (`HEAL_EXHAUSTED`) | Done |
| Bridge | `ExecutionRecoveryHarnessBridge` — `EXECUTION_FAILURE_INVESTIGATION_V1`, `execution-investigation-evidence.json` | Done |
| Classification | `ExecutionFailureClass` + `ExecutionFailureClassifier` | Done |
| Evidence spike | `docs/handoff/2026-11-15-execution-recovery-evidence-spike.md` | Done |
| Worker wiring | Execute + Automate prove → `ExecutionRecoveryWiring.optionalGate` + `cancelCheck` (+ `JobHarnessBudget` on Convert) | **Done (Phase 3)** |
| Flag | `delivery.agent-harness.enabled` + `execution-recovery.cursor-escalation.enabled` | Done (default false) |

**Tests:** `ExecutionRecoveryEscalationGateTest` (8), `ExecutionFailureClassifierTest` (2) — FakeAgentRuntime only.

### Phase 3 — Automate shares execution recovery

| Item | Deliverable |
|------|-------------|
| Reuse | Same bridge as Execute inside `ConversionJobRunner` prove failures |
| No duplicate engine | Single `ExecutionRecoveryHarnessBridge` |

### Phase 4 — Automate framework validation + repair (**landed 2026-11-15**)

| Item | Deliverable | Status |
|------|-------------|--------|
| Accept gate | `EmitPhase` after `EmitCompileCheck`: `AutomateFrameworkAcceptanceGate` (deterministic only by default) | Done |
| Contract | `keel.framework-contract/v1` + `FrameworkTemplateCatalog` helper heuristics | Done |
| Contract Phase 6 | Inventory-driven validation (`contractVersion=2`), `FrameworkExpectedInventory`, structure checks | Done — see `2026-11-15-framework-contract-phase6-completion.md` |
| Repair | `FRAMEWORK_ADAPTATION_V1` via `AutomateFrameworkRepairBridge` on isolated copy; compile + independent revalidate | Done (FakeAgentRuntime in tests) |
| Flag | `delivery.agent-harness.enabled` + `automate-framework-repair.enabled` | Default false |
| Convert advisory | Unchanged `AgentHarnessJobBridge` / `FRAMEWORK_VALIDATION_V1` now explicitly READ_ONLY tools | Preserved |

### Phase 5 — E2E + controlled rollout (**audit 2026-11-15**)

See `docs/handoff/2026-11-15-phase5-e2e-rollout-readiness.md`. Acceptance boundary moved **after RevisePhase** + post-revise compile, **before ZIP**.

## Execute escalation order (verified)

1. Selenium execute (`TcExecutionService`)  
2. DOM shortlist / bind (`StepIntentBinder`, `PrecisionBindService`)  
3. Ollama pick (`AuthoringService.healIntentWithOllama`) — skipped on precision / second heal  
4. UI-TARS bbox widen (`HealCascade.tryVisionBboxWiden`)  
5. Qwen (vision provider config)  
6. Cursor `heal.mjs` pick/solve/invent (`CursorHealClient`)  
7. Recovery JSON (`RecoveryPlanParser`, `tryRecoveryHeal`)  
8. **(New)** Harness `EXECUTION_FAILURE_INVESTIGATION_V1` when flags on and prior tiers fail

## Feature flags (all default false)

| Property | Phase |
|----------|-------|
| `delivery.agent-harness.enabled` | existing |
| `delivery.agent-harness.cursor.enabled` | existing |
| `delivery.agent-harness.framework-validation.enabled` | Phase 3 Convert (unchanged) |
| `delivery.agent-harness.failure-investigation.enabled` | Phase 2 wiring |
| `delivery.agent-harness.tc-validation.enabled` | Phase 1 |
| `delivery.agent-harness.tc-validation.cursor-escalation.enabled` | Phase 1 |
| `delivery.agent-harness.execution-recovery.cursor-escalation.enabled` | Phase 2 |
| `delivery.agent-harness.automate-framework-repair.enabled` | Phase 4 |

## Files touched by phase (planned)

| Phase | Production | Tests |
|-------|------------|-------|
| 1 | `delivery.agent.validation.*`, `AgentHarnessProperties`, `TcGenerateService`, `GenerateHarnessBridge` | `*Contract*Test` |
| 2 | `ProvePhase`, `ExecutionRecoveryHarnessBridge` | `ProvePhase*`, coordinator |
| 3 | `ConversionJobRunner` | Integration IT |
| 4 | `EmitPhase` hook, `AutomateFrameworkRepairBridge` | Framework validator IT |

## Open decisions / blockers

None for Phase 1. Phase 4 repair publish gate needs product sign-off on **blocking** vs **advisory** relative to Convert.

## Remaining limitations (current)

- Harness not on Execute/Generate production paths until Phases 1–2 land.
- No JSON Schema for TC payload; contract is Java + prompts.
- Maven repair in Cursor runtime still not mapped to SDK tools for live Cursor.