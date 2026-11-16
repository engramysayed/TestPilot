# Phase 6.3 — Live Cursor framework repair verification

**Date:** 2026-11-15  
**Assessment:** **LIVE VERIFIED WITH DOCUMENTED LIMITATIONS**

## 1. Preflight (code inspection — confirmed)

| Check | Evidence |
|-------|----------|
| Task type | `AgentTaskType.FRAMEWORK_ADAPTATION_V1` (`AutomateFrameworkRepairBridge`, live `task.json`) |
| Tool permission | `AgentToolRegistry.VALIDATION_REPAIR` — `MODIFY_GENERATED_SOURCES`, `RUN_APPROVED_MAVEN_COMMANDS`, read/search tools; validation stays `READ_ONLY` |
| Workspace isolation | `FrameworkAcceptanceWorkspace.snapshotOriginal` + `createRepairCandidate`; `AgentWorkspacePreparer.ensureIsolatedCopy` for Cursor (`taskDir/workspace/`) |
| Original preserved | Snapshot under `framework-acceptance/original/`; promotion only on acceptance |
| Inventory v2 to agent | `framework-repair-context.json` = full `ValidationReport.toApiMap()` (`contractVersion=2`, metadata `inventoryVersion=2`, `validationMode=inventory`) |
| Revalidation inventory | `AutomateFrameworkAcceptanceGate` passes same `FrameworkExpectedInventory` to initial + revalidation validators |
| Production flags | `application.properties`: `delivery.agent-harness.enabled=false`, `automate-framework-repair.enabled=false` (unchanged) |
| heal.mjs | Not modified |

**Cursor vs FAKE workspace behavior:** For `AgentProviderId.CURSOR`, `AgentHarnessCoordinator.prepareCursorWorkspace` copies the repair candidate into `taskDir/workspace/` and restricts `allowedWorkspaceRoots` to that path. **FAKE** tasks write directly to the repair-candidate path (no extra copy).

## 2. Live configuration

```text
call api-keys.local.bat
mvn -pl keel-portal test ^
  -Dkeel.agent.cursor.live.framework=true ^
  -Ddelivery.emit-compile-check=false ^
  -Dtest=FrameworkAdaptationCursorLiveIT#missingExpectedPage_liveRepairLifecycle

delivery.provider.allowlist=cursor  (set in-test only)
node tools/cursor-agent/agent.mjs    (absolute path from repo root)
AgentHarnessProperties: enabled=true, cursorEnabled=true, automateFrameworkRepairEnabled=true, maxRuntimeMinutes=10
```

Opt-in harness: `FrameworkAdaptationCursorLiveIT` (local; not required for CI).

Sidecar sanity: `CursorAgentRuntimeLiveIT` **passed** (~25s) with same key bootstrap.

## 3. Scenario results

| # | Scenario | Live executed? | Outcome |
|---|----------|----------------|---------|
| 1 | Missing `Login_Page` | **Yes** | See below |
| 2 | Missing `Login_Test` | No (timeboxed) | Harness ready; same command with `#missingExpectedTest_liveRepairLifecycle` |
| 3 | Multi-page bad import | No | `#missingPageImport_multiPage_liveRepairLifecycle` |
| 4 | Missing locator/action | No | — |
| 5 | Empty stub | No | — |
| 6 | Mixed inventory | No | Covered deterministically in `FrameworkContractPhase62RegressionTest.mixedFixture_*` |

### Scenario 1 — Missing expected Page (live)

| Item | Value |
|------|--------|
| Task ID | `bdbffa3ee81b46b1b04f6589e23474cc` |
| Agent ID / run | `agent-3f3c16bc-125f-4f94-84a9-7c8d322eaa51` / `run-954fa699-2154-47c7-8582-0a4391f08cb3` |
| Sidecar status | `COMPLETED` |
| Gate status | `REVALIDATION_FAILED` (**not** accepted) |
| Duration | ~98s |

**Initial v2 report** (pre-repair): mandatory `FW_EXPECTED_PAGE_PRESENT` for `Login_Page`; metadata `inventoryVersion=2`, `contractVersion=2`.

**Agent context:** `framework-repair-context.json` identical finding list (inventory v2).

**Agent action:** Created `Login_Page.java` in **task** `workspace/` (custom skeleton using `WebDriverFactory`, method `click_Login_Button` — not codegen-shaped).

**Acceptance invariants:**

- Original `project/` still missing `Login_Page` (no promotion).
- `framework-acceptance/repair-candidate/` still **without** `Login_Page.java` (agent edits not merged into repair candidate — see §7).
- `COMPLETED` did **not** yield acceptance (`REVALIDATION_FAILED`).

Machine summary: [phase6-3-live-evidence-MISSING_PAGE.json](./phase6-3-live-evidence-MISSING_PAGE.json) (no secrets).

## 4. Failure / lifecycle (live + deterministic)

| Scenario | Verified how |
|----------|----------------|
| Mandatory violations block acceptance | Live: revalidation failed after COMPLETED |
| COMPLETED ≠ accepted | Live scenario 1 |
| Original workspace preserved | Live: `project/` unchanged when not accepted |
| FAKE repair success path | `AutomateFrameworkAcceptanceGateTest` + `inventoryV2_emitPath_*` |
| Repair disabled | `mandatoryViolation_repairDisabled_rejected` |
| Revalidation failed / no promotion | Live scenario 1; `repairFails_originalSnapshotPreserved` |
| Cancel / timeout / Policy A reconcile | Prior evidence `2026-11-12-cursor-live-recovery-evidence.json` (VALIDATION tasks; Policy A preserved) |

## 5. Security (preflight + live task.json)

- **Workspace roots:** single isolated path under `agent-runs/.../workspace` (live task budget).
- **Cross-tenant:** tasks scoped to disposable temp project root; no portal DB involved in IT.
- **Secrets:** objective uses finding counts only; full contract in JSON artifact (no API keys in objective).
- **Tool allow-list:** no blanket filesystem; `MODIFY_GENERATED_SOURCES` within workspace root only.
- **Original tree:** repair bridge passes `repairCandidateRoot`; Cursor cannot write `projectDir` until promotion (which did not occur).

## 6. Compilation

Live run: `delivery.emit-compile-check=false` in IT (faster iteration). Compile gate still invoked on **repair-candidate** path when enabled; live failure was **contract revalidation**, not compile timeout.

## 7. Known limitations and defect D-6.3-01

**D-6.3-01 — Cursor workspace merge gap (architecture)**

After a Cursor `FRAMEWORK_ADAPTATION_V1` run, modifications live under `agent-runs/{job}/{taskId}/workspace/`. **Compile, revalidation, and promotion** operate on `framework-acceptance/repair-candidate/` without copying agent workspace back. FAKE provider does not hit this because it does not use `prepareCursorWorkspace`.

**Impact:** Live repair can `COMPLETED` with plausible edits in the agent workspace while gate correctly rejects promotion — scenario 1 behaved as designed for safety, but successful Cursor repairs cannot promote until merge exists.

**Proposed fix (not implemented; needs approval):** On `COMPLETED`, sync `taskDir/workspace` → `repair-candidate` before `EmitCompileCheck` and inventory revalidation.

**Other limits**

- Live coverage is scenario 1 + prior Cursor SDK/coordinator ITs; not all six violation types exercised live.
- Cursor repair quality is nondeterministic; stub pages may fail `FW_PAGE_MISSING_*` even if a file exists.
- Repair objective text is terse (`mandatoryMessages().size()` only) — agent relied on context JSON for rule ids.

## 8. Final assessment

**LIVE VERIFIED WITH DOCUMENTED LIMITATIONS**

- Real Cursor executed `FRAMEWORK_ADAPTATION_V1` with correct tools and isolated workspace.
- Inventory v2 findings and metadata reached the agent.
- Acceptance invariants held: no spurious promotion; `COMPLETED` ≠ accepted when revalidation fails.
- End-to-end **successful** live repair + promotion is **not verified** until D-6.3-01 is addressed (and a repair producing codegen-valid pages is observed).

**Commands run**

```text
mvn -pl keel-portal test -Dkeel.agent.cursor.live=true -Dtest=CursorAgentRuntimeLiveIT
→ PASS (~25s)

mvn -pl keel-portal test -Dkeel.agent.cursor.live.framework=true -Ddelivery.emit-compile-check=false -Dtest=FrameworkAdaptationCursorLiveIT#missingExpectedPage_liveRepairLifecycle
→ PASS (verification assertions; outcome REVALIDATION_FAILED)
```