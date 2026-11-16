# Phase 5 — E2E verification and rollout readiness

**Date:** 2026-11-15  
**Status:** READY WITH DOCUMENTED LIMITATIONS

## 1. Final artifact lifecycle (Automate)

```
ConversionJobRunner (KeelPath AUTOMATE)
  → ProvePhase (+ optional ExecutionRecoveryEscalationGate)
  → EmitPhase:
      codegen (CodeWriter, DomainCatalog)
      docs/metrics (score, heal, target config)
      EmitCompileCheck #1
      RevisePhase (may edit generated *Test.java — REVIEW comments only)
      docs/locator-map.json
      EmitCompileCheck #2 (post-revise)
      AutomateFrameworkAcceptanceGate (deterministic ± repair)
      package.zip
  → ProjectStore.saveVersion(frameworkDir=projectDir, guard=PublicationGuard)
      → AgentAwarePublicationGuard.beforeLockedPublish (optional advisory FRAMEWORK_VALIDATION_V1 on ZIP)
```

**Acceptance invariant (after Phase 5 fix):** Validator + compiler run on `projectDir` **after** all emit-side mutations and **before** `packager.zip`. Published framework is `copyRecursive(projectDir → store/framework)` and versioned zip from the same tree.

**RevisePhase impact:** Does **not** change locators, POM, or page classes. **Does** inject `// REVIEW:` / `// STOPPED HERE:` comment lines into generated test `.java` files when static honesty finds residual defects. Acceptance was moved **after** RevisePhase to avoid validating a pre-revise tree.

**Post-publish:** No code path rewrites `framework/` after `saveVersion` except user UPDATE overlay on next job.

## 2. Framework contract completeness (audit)

See completion report matrix in unified plan — gaps documented; no severity changes in Phase 5.

## 3. Live Cursor

`FRAMEWORK_ADAPTATION_V1` live path **not executed** in Phase 5 (no approved live run). FakeAgentRuntime + FixingFakeRuntime cover orchestration only.

## 4. Rollout

All `delivery.agent-harness.*` flags default **false** in `application.properties`. Enable per-environment with checklist in completion report.