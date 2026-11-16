# Phase 6.16 — Production readiness audit and controlled production rollout plan

**Date:** 2026-11-15  
**Deployment target:** **PRODUCTION** (plan only — no flags enabled, no live production repairs in this phase)  
**Explicit exclusions:** Phase 6.15 staging checklist/IT is **not** production evidence. Local 8/8 compile-on live qualification is **not** production traffic evidence.

---

## Final verdict

**READY AFTER SPECIFIED BLOCKERS**

The implementation supports a **controlled, evidence-monitored production rollout** only after operational blockers are cleared. It is **not** “production-ready” on the basis of local tests alone and **not** authorized for unscoped global enablement without the mitigations below.

---

## 1. Production execution path (verified by inspection)

### Conversion job (CONVERT) → framework publish

| Step | Code location | Notes |
|------|---------------|--------|
| Job claim + lease | `ConversionWorker.runOne` | `DurableJobClaim.Lease`; heartbeat during emit |
| Work dir | `JobWorkLayout` → `ScopePaths.createJobWorkDir` | `workRoot/tenants/{tenant}/jobs/{jobId}` |
| Prove (browser) | `ConversionJobRunner` → `ProvePhase` | Optional `ExecutionRecoveryWiring` when recovery flags on (separate from repair) |
| Template + overlay | `FrameworkPackager` | UPDATE overlays customer framework |
| Emit codegen | `EmitPhase` | Page cluster, `CodeWriter`, outcomes |
| Emit compile #1 | `EmitCompileCheck.runIfEnabled` | Pre-revise smoke |
| Phase 3 revise | `RevisePhase` | Static Excel vs emit (advisory notes in sources) |
| Locator map | `LocatorMapBuilder` | Written to project + staged state |
| Emit compile #2 | `EmitCompileCheck` | Post-revise |
| **Framework acceptance** | `AutomateFrameworkAcceptanceGate.run` | Pre-zip; failure → `FrameworkAcceptanceException` |
| Package ZIP | `FrameworkPackager.zip` | Only after acceptance passes |
| Persist version | `ProjectStore.saveVersion` | Under tenant `projectRoot`; `PublicationGuard` |
| Job completion | `ConversionWorker` | FAILED on `IllegalStateException` (includes acceptance) |

**Repair subgraph** (only when `repairEnabled`):

`AutomateFrameworkAcceptanceGate` → snapshot original → `repair-candidate` → `AutomateFrameworkRepairBridge.attemptRepair` → (Cursor) `FrameworkRepairWorkspaceReconciler` → emit compile on candidate → inventory v2 revalidation → `promoteRepairCandidate` → operational JSON.

**Not in production gate today:** `FrameworkRepairArchitectureAssessor` does **not** run inside `AutomateFrameworkAcceptanceGate`; architecture quality is qualification/advisory only (live evidence writer), **not** a publish blocker.

### Phase ordering vs operator diagram

Actual pre-zip order in `EmitPhase.emit` matches: **compile → revise → locator map → compile → acceptance (incl. repair) → zip → saveVersion**. Architecture assessment is **not** enforced before promotion in this path.

---

## 2. Production configuration and feature-flag matrix

Prefix: `delivery.agent-harness.*` (`AgentHarnessProperties`). **All security-sensitive defaults are false** in `application.properties`.

| Property | Default (repo) | Enables | Required with framework repair | Independent of repair? |
|----------|----------------|---------|-------------------------------|-------------------------|
| `enabled` | `false` | Harness coordinator | **Yes** | — |
| `cursor.enabled` | `false` | `AgentTaskRouter` selects `CURSOR` vs `FAKE` | **Yes** (real repair) | Yes |
| `automate-framework-repair.enabled` | `false` | Repair bridge in acceptance gate | **Yes** | Yes |
| `max-concurrent-runs` | `2` | Per-**parentJobId** running cap | Yes (per job) | Yes |
| `max-runtime-minutes` | `15` | Task policy + bridge budget input | Yes | Yes |
| `framework-validation.enabled` | `false` | `AgentAwarePublicationGuard` (Phase 3 advisory) | No | **Yes** |
| `failure-investigation.enabled` | `false` | Prove escalation | No | Yes |
| `tc-validation.*` / `execution-recovery.*` | `false` | Other harness bridges | No | Yes |

**Repair gate predicate** (`AutomateFrameworkAcceptanceGate.repairEnabled`):  
`enabled && automate-framework-repair.enabled` (does **not** require `framework-validation.enabled`).

**Related non-properties:**

| Item | Source | Missing / denied behavior |
|------|--------|---------------------------|
| `CURSOR_API_KEY` | `ApiKeySource` / env / settings | `AgentHarnessException` `MISSING_API_KEY` (sidecar never starts) |
| Provider allowlist | `ProviderPolicy` / `delivery.provider.allowlist` | `PROVIDER_DENIED` |
| `delivery.emit-compile-check` | System property (default true) | Compile gates in emit + repair path |
| Bridge wait cap | `ExecutionRecoveryHarnessBudget.waitBudgetMs(props, leaseCap)` | `min(maxRuntimeMinutes×60s, lease remainder)` |

**Credentials in reports:** `framework-repair-operational.json` carries timing, ids, terminal reasons, harness **message strings** — not API keys. Sidecar env uses `CURSOR_API_KEY` only in process env (`CursorSidecarClient`).

---

## 3. Production isolation assessment

### What is isolated (verified)

| Boundary | Mechanism | Evidence |
|----------|-----------|----------|
| Tenant store | `ScopePaths.tenantRoot` / `projectRoot` | `ConversionJobRequest` + `TenantResolver.require` |
| Job work | `ScopePaths.jobWorkDir(tenant, jobId)` | Ephemeral per job |
| Agent task store (repair) | `tenantProjectRoot` passed to `gate.run` as `reportRoot` | `agent-runs/{parentJobId}/{taskId}/` under **tenant project root**, not work dir |
| Repair workspace | `AgentPolicy` allowed roots = repair candidate path | Coordinator rejects out-of-policy paths |
| Reconciliation | `FrameworkRepairWorkspaceReconciler` | Wrong workspace → `FrameworkRepairReconciliationException` (tested) |
| Publication | `ProjectStore.saveVersion` + `PublicationLock` | Only current job’s `projectDir` after acceptance |

**Conclusion:** A repair task for tenant A **cannot** read/write tenant B’s project tree **if** `ConversionJobRequest.tenantId` and store roots are correct (normal worker path). This is **filesystem scoping**, not a separate per-tenant harness credential.

### What is **not** isolated (blocker for preferred rollout)

| Gap | Impact |
|-----|--------|
| **No per-tenant or per-project harness flags** | Enabling repair in `application.properties` affects **all** tenants on that portal instance |
| **Conversion pool concurrency** | `conversionExecutor` core=2, max=4 (`AsyncConfig`) — multiple tenants/jobs can run converts (and repairs) concurrently on one host |
| **Global sidecar / CPU** | No per-tenant fair-share beyond per-job `max-concurrent-runs` |

**Selective tenant/project rollout in config:** **Not supported** by current implementation.  
**Safest alternative without new code:**

1. **Pilot production instance** (single tenant on dedicated host) with flags on, **or**
2. **Global enable** on shared production with **strict operational monitoring** and agreed job allowlist maintained **outside** the product (ops process), accepting blast radius.

---

## 4. Acceptance, publication, and rollback safety

### Publication guarantees (verified — code + deterministic tests)

| Guarantee | Enforcement |
|-----------|-------------|
| Failed / interrupted repair does not publish | No `promoteRepairCandidate`; `FrameworkAcceptanceException` before zip/saveVersion |
| Compile failure does not publish | `COMPILE_FAILED` outcome; exception at emit |
| Mandatory validation failure does not publish | Revalidation failure / repair failure statuses |
| Revalidation independent of repair agent | `FrameworkContractValidator.validateWorkspace(repairRoot, …)` after repair, not agent self-report |
| Packaged artifact = validated tree | Zip built from `projectDir` **after** promotion; digest checks in qual tests |
| Prior published versions | `saveVersion` only on success; failed job leaves store version unchanged (`ConversionWorker` catch → FAILED) |

**Measured:** `FrameworkRepairAcceptanceSafetyTest`, `AutomateFrameworkAcceptanceGateTest`, `FrameworkRepairBridgeOperationalTest` — **pass** (this session).

**Unverified in production:** Promotion under real Cursor variance, concurrent jobs on production host, partial publish under crash during `saveVersion` (lock mitigates but not prod-drilled).

### Policy A (reconcile vs cancel)

| Rule | Inspection |
|------|------------|
| Reconcile poll exhaustion → `INTERRUPTED` | Coordinator + sidecar result handling; live Policy A ITs on host (historical), not prod |
| No `cancelRun` on poll exhaustion alone | Reconcile `getResult` path; explicit cancel only via `cancel` op / confirmed ids |
| `INTERRUPTED` ≠ `CANCELLED` | `AgentHarnessCoordinator.cancel` |

**No changes** proposed to timeouts or Policy A in this phase.

### Rollback procedure (production)

| Step | Effect | In-flight jobs |
|------|--------|----------------|
| Set `delivery.agent-harness.automate-framework-repair.enabled=false` | New repairs → `REPAIR_NOT_ENABLED` | Jobs already past gate keep outcome |
| Set `delivery.agent-harness.enabled=false` | Coordinator throws on execute | Bridge may still be waiting until timeout unless job cancelled |
| Set `delivery.agent-harness.cursor.enabled=false` | Router selects FAKE | Do **not** rely on this mid-repair — inconsistent provider |
| Restart portal (recommended) | Refresh Spring `AgentHarnessProperties` bean | **Required** to stop long-running bridge waits cleanly |
| User cancel job | `JobCancelSupport` / lease ownership | Cooperative abort |

**Disabling flags alone is not sufficient for all in-flight repairs** without job cancel and/or process restart. Evidence preserved under `agent-runs/` and `framework-acceptance/` — rollback does not delete them.

**Published frameworks:** Disabling flags does **not** roll back already saved versions; operator must use existing portal version management.

---

## 5. Reliability and operations (measured vs unavailable)

| Topic | Status |
|-------|--------|
| Lease renewal vs repair | **Verified pattern** — `ConversionWorker.runWithProgressMirror` heartbeats independent of harness; not re-run on prod in this phase |
| Bridge vs lease budget | **Documented** (6.13) — `min(12m props cap, lease)` on worker |
| Sidecar startup duration | **Unavailable** — `sidecarStartupMs` not populated (6.14); do not infer from `bridgeElapsedMs` |
| Run ID persistence | **Verified** — task.json + START events when sidecar succeeds (qual + telemetry) |
| `AgentStoreLock` | **Verified** — cross-process IT exists; prod contention unmeasured |
| Operational JSON | **Verified** deterministic wiring (6.14) |
| Terminal reasons | **Verified** classifier + bridge tests |
| Prod interrupt/timeout rates | **Unmeasured** |
| Restart recovery | **Partially verified** — reconcile path with persisted ids; prod drill required |

---

## 6. Controlled production rollout plan

**Prerequisite:** Resolve **B1** (tenant scoping strategy) before broad production enablement.

### Phase P0 — Deploy code, flags off

- Ship 6.14 observability + acceptance chain (already in tree).
- Confirm production `application.properties`: all harness flags **false**.
- Confirm `CURSOR_API_KEY` provisioning process (secret store, rotation).

### Phase P1 — Pilot (recommended)

**Option A (preferred):** Dedicated production portal instance **or** single-tenant deployment with flags:

```properties
delivery.agent-harness.enabled=true
delivery.agent-harness.cursor.enabled=true
delivery.agent-harness.automate-framework-repair.enabled=true
delivery.agent-harness.max-concurrent-runs=2
delivery.provider.allowlist=cursor
```

**Option B (shared multi-tenant prod):** Same flags globally + **ops allowlist** of project IDs permitted to run CONVERT until confidence built (process control, not code).

**Per-job monitoring (mandatory):**

- Collect `framework-acceptance/framework-repair-operational.json` from job work dir (or export to log pipeline).
- Review `acceptanceTerminalReason`, `bridgeTerminalReason`, `agentRunStarted`, ids.
- Reject manual republish if `accepted=false`.

**Manual publication boundary:** Existing `AgentAwarePublicationGuard` applies to **framework-validation** flag, not automate repair. Repair boundary is **acceptance gate only** — no extra human gate in code.

### Phase P2 — Expand

- Increase tenant/project coverage only after pilot gates (below) met with **documented** production samples (not staging, not local live IT).

### Rollout gates (proposed — thresholds need prod data)

| Gate | Proposal | Evidence type |
|------|----------|----------------|
| Repair completion rate | Track % `ACCEPTED` after repair triggered | Operational JSON per job |
| INTERRUPTED / bridge timeout rate | Alert on `BRIDGE_WAIT_TIMEOUT` | Operational JSON |
| Compile success after repair | `COMPILE_FAILURE` count | Gate status |
| Revalidation success | `REVALIDATION_FAILURE` count | Gate status |
| Promotion success | `accepted=true` + version bump | Job + store |
| Cross-tenant isolation | Zero cross-tenant artifact paths in audits | Ops checklist |
| Rollback drill | Flags off + in-flight cancel | Runbook drill once |

**Do not treat local 8/8 or 6.15 staging artifacts as thresholds.**

---

## 7. Unresolved blockers

| ID | Severity | Blocker | Evidence |
|----|----------|---------|----------|
| B1 | **Critical** | No per-tenant/project repair flag | `AgentHarnessProperties` is global Spring config |
| B2 | **High** | No production-sidecar startup / concurrent load measurements | 6.15 not run; `sidecarStartupMs` null |
| B3 | **High** | `CURSOR_API_KEY` + provider allowlist prod readiness | Code fails closed; ops config unverified here |
| B4 | **Medium** | Architecture assessor not in publish gate | Promotion may omit functional architecture checks |
| B5 | **Medium** | In-flight rollback needs restart/cancel procedure | Spring bean not refreshable per job |
| B6 | **Medium** | Up to 4 concurrent CONVERT jobs | `AsyncConfig` vs harness per-job cap=2 |
| B7 | **Low** | Phase 6.15 staging plan not executed | Explicitly **not** prod evidence |

---

## 8. Production verification checklist (owners)

| # | Check | Evidence required | Owner |
|---|--------|-------------------|--------|
| 1 | Prod config: flags default off at deploy | Config screenshot / `application-prod.properties` | Ops |
| 2 | Pilot flags applied only on approved instance/tenant | Change ticket | Ops |
| 3 | First pilot CONVERT: operational JSON archived | File path in job work dir | Eng/Ops |
| 4 | Failed repair: job FAILED, no version bump | Portal job + store version | QA |
| 5 | Rollback: flags off + restart; new job no repair | Job status `REPAIR_NOT_ENABLED` | Ops |
| 6 | In-flight cancel during repair | Task artifacts + terminal reason | Eng |
| 7 | `CURSOR_API_KEY` missing → clear failure | Job log / operational reason | Ops |
| 8 | Two simultaneous converts (if multi-tenant) | Distinct work dirs + task ids | Eng |

---

## 9. Tests run (this phase — deterministic only)

```
mvn -pl keel-portal test -Dsurefire.forkCount=0 \
  -Dtest=FrameworkRepairAcceptanceSafetyTest,FrameworkRepairBridgeOperationalTest,AutomateFrameworkAcceptanceGateTest,FrameworkContractGeneratedTestDiscoveryTest
```

**Result:** All **passed** (2026-11-15). These prove **safety logic**, not production reliability.

**Not run:** Live Cursor production jobs; Phase 6.15; staging qualification.

---

## 10. Files inspected (no code changes in 6.16)

| Area | Files |
|------|--------|
| Worker / job | `ConversionWorker.java`, `ConversionJobRunner.java`, `EmitPhase.java`, `JobWorkLayout.java` |
| Acceptance / repair | `AutomateFrameworkAcceptanceGate.java`, `AutomateFrameworkRepairBridge.java`, `FrameworkAcceptanceWorkspace.java` |
| Harness | `AgentHarnessCoordinator.java`, `AgentTaskRouter.java`, `AgentHarnessProperties.java`, `CursorSidecarClient.java`, `CursorAgentRuntime.java` |
| Identity / store | `ScopePaths.java`, `ProjectStore.java` |
| Publication | `PublicationGuard.java`, `AgentAwarePublicationGuard.java`, `FrameworkAcceptanceException.java` |
| Ops config | `application.properties`, `AsyncConfig.java` |
| Observability | `FrameworkRepairOperationalReportWriter.java`, `FrameworkRepairTaskTelemetry.java` |

---

## 11. Next action (exact)

1. **Product/Ops decision** on pilot model: dedicated prod instance (B1 mitigation) vs shared prod with external project allowlist.  
2. **Configure** production secrets: `CURSOR_API_KEY`, `delivery.provider.allowlist`, Node sidecar path.  
3. **Enable** the three repair flags on **pilot scope only** (per decision).  
4. Run **first monitored CONVERT** with mandatory violation requiring repair; archive operational JSON; verify version bump only on `ACCEPTED`.  
5. **Do not** enable globally until gates in §6 pass on production samples.

**This document does not authorize production rollout by itself.**