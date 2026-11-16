# Phase 6.8 — Compile-on live repair requalification (recovered)

**Date:** 2026-11-15  
**Branch context:** `ux-overhaul` (local)  
**Template baseline:** Phase 6.7 POM / `Assertion.java` fixes (uncommitted)

## Process recovery

| Item | Value |
|------|--------|
| Wrapper PID | 3936 (PowerShell) — **terminated** after Maven finished |
| Maven/Java | Started ~17:37 local; **exit code 1** after ~3412 s (~57 min) |
| Log | `docs/handoff/phase6-8-compile-on-live-run.log` |
| Surefire | `keel-portal/target/surefire-reports/delivery.agent.validation.automate.FrameworkAdaptationCursorCompileOnLiveIT.txt` |
| Recovered matrix evidence | `docs/handoff/phase6-8-compile-on-scenario-recovery.json` (+ per-scenario temp work dirs) |

The earlier `Start-Sleep` monitor abort did **not** stop Maven. The matrix ran to completion.

**On-disk `phase6-6-compile-on-*.json`:** mostly unchanged since the **14:17–14:26** Phase 6.6 run. During 6.8, only **MISSING_PAGE_LEGACY** and **MISSING_PAGE_ASSERTIONS** were rewritten (repair failed before the compile/accepted assert). Six scenarios hit `REVALIDATION_FAILED` with `emit-compile-check.ok` present; the IT fails when `compilePassed != accepted` **before** `writeEvidence`, so their JSON files were not refreshed.

## Preflight (unchanged from stub)

| Check | Result |
|-------|--------|
| `CustomerFrameworkTemplatePomTest` | PASS (`forkCount=0`, `MAVEN_OPTS=-Xmx1024m`) |
| `FrameworkRepairCompileBaselineTest` | PASS |
| Template `mvn test-compile` | PASS |
| Safety batch | PASS |
| `delivery.emit-compile-check` | `true` |

## Matrix command (this run)

```text
call api-keys.local.bat
mvn -pl keel-portal test -Dkeel.agent.cursor.live.framework=true -Dkeel.agent.cursor.compile.on=true -Ddelivery.emit-compile-check=true -Dtest=FrameworkAdaptationCursorCompileOnLiveIT -Dsurefire.forkCount=0
```

**Surefire:** 8 run, **6 failures**, 0 errors. Failures are harness assertions: `compilePassed=true` but `accepted=false` (gate `REVALIDATION_FAILED`).

## `EMIT_COMPILE_CHECK ok` correlation (six lines, one scenario each)

| Log timestamp (local) | Scenario |
|----------------------|----------|
| 17:49:43 | `EMPTY_TEST_STUB` |
| 18:12:08 | `MISSING_TEST` |
| 18:14:02 | `MISSING_PAGE_ACTIONS` |
| 18:28:20 | `MISSING_PAGE_LOCATORS` |
| 18:31:19 | `MIXED_INVENTORY_BAD_IMPORT` |
| 18:34:31 | `MULTI_PAGE_BAD_IMPORT` |

Each line matches `framework-acceptance/emit-compile-check.ok` and reconciliation under the corresponding `qual-*` temp dir in `phase6-8-compile-on-scenario-recovery.json`.

### Four compile successes (original partial count)

The four lines observed mid-run map to **MISSING_TEST**, **MISSING_PAGE_ACTIONS**, **MISSING_PAGE_LOCATORS**, and **MIXED_INVENTORY_BAD_IMPORT**. For all four (and the two later scenarios):

- **Compilation:** repair-candidate `test-compile` succeeded (`emit-compile-check.ok`).
- **Reconciliation:** manifest written; staged merge applied.
- **Revalidation:** **failed** — sole remaining mandatory finding **`FW_ORPHAN_TEST_CLASS`** (`BaseTest` not in emit inventory).
- **Architecture:** not blocking acceptance; gate never reached architecture-only promotion checks.
- **Promotion:** **none** (revalidation failed before `promoteRepairCandidate`).
- **Published artifact:** project tree **not** promoted; digest match N/A.

Compile success alone does **not** imply acceptance.

## Eight-scenario result table (Phase 6.8 evidence)

| Scenario | Cursor task | Reconciliation | Compile-on | Revalidation (inventory v2) | Promotion | Published = candidate | Failure class |
|----------|-------------|----------------|------------|-----------------------------|-----------|-------------------------|---------------|
| `EMPTY_TEST_STUB` | COMPLETED `01c42125…` / `run-abdcda17…` | Yes | **Pass** | **Fail** (`FW_ORPHAN_TEST_CLASS`) | No | No | REPAIR_QUALITY_OR_VALIDATION |
| `MISSING_PAGE_LEGACY` | INTERRUPTED `0b0b22d2…` | No | Fail (no marker) | N/A | No | No | REPAIR |
| `MISSING_TEST` | COMPLETED `16a9c1f6…` / `run-62bf4b6b…` | Yes | **Pass** | **Fail** (`FW_ORPHAN_TEST_CLASS`) | No | No | REPAIR_QUALITY_OR_VALIDATION |
| `MULTI_PAGE_BAD_IMPORT` | COMPLETED `b6e10585…` / `run-08c74865…` | Yes | **Pass** | **Fail** (`FW_ORPHAN_TEST_CLASS`) | No | No | REPAIR_QUALITY_OR_VALIDATION |
| `MISSING_PAGE_LOCATORS` | COMPLETED `2830c749…` / `run-f87ceac2…` | Yes | **Pass** | **Fail** (`FW_ORPHAN_TEST_CLASS`) | No | No | REPAIR_QUALITY_OR_VALIDATION |
| `MISSING_PAGE_ACTIONS` | COMPLETED `f5593282…` / `run-03a9548a…` | Yes | **Pass** | **Fail** (`FW_ORPHAN_TEST_CLASS`) | No | No | REPAIR_QUALITY_OR_VALIDATION |
| `MISSING_PAGE_ASSERTIONS` | INTERRUPTED `264f6d20…` | No | Fail (no marker) | N/A | No | No | REPAIR |
| `MIXED_INVENTORY_BAD_IMPORT` | COMPLETED `2207cecd…` / `run-ee4ea804…` | Yes | **Pass** | **Fail** (`FW_ORPHAN_TEST_CLASS`) | No | No | REPAIR_QUALITY_OR_VALIDATION |

**Work-dir evidence:** `%LOCALAPPDATA%\Temp\qual-<scenario>-<suffix>\` paths listed in `phase6-8-compile-on-scenario-recovery.json` (`acceptance-revalidate/contract-validation-report.json`, `reconciliation-manifest.json`, task JSON).

**Ambiguity / staleness:** six scenarios lack refreshed `phase6-6-compile-on-*.json`; use recovery JSON + temp dirs as source of truth for 6.8.

## vs Phase 6.6

| Dimension | Phase 6.6 (pre–6.7 template) | Phase 6.8 (post–6.7 template) |
|-----------|------------------------------|----------------------------------|
| Repair-candidate compile | 0/8 passed (`COMPILE_FAILED`, template/POM defects) | **6/8** passed `EMIT_COMPILE_CHECK` |
| Cursor repair completed | Most scenarios reached compile stage | **6/8** `COMPLETED`; **2/8** `INTERRUPTED` |
| Inventory v2 acceptance | 0/8 | **0/8** |
| Dominant post-compile blocker | Missing/invalid `pom.xml`, compile errors | **`FW_ORPHAN_TEST_CLASS`** on template `BaseTest` in validated tree |
| Erroneous promotion | None observed | None observed |

## Known defects (this qualification)

1. **Inventory vs template surface:** Full template copy leaves `BaseTest` on the repair-candidate tree; inventory v2 revalidation treats it as mandatory orphan after otherwise successful Cursor repair + compile.
2. **Evidence IT gap:** `FrameworkAdaptationCursorCompileOnLiveIT` does not write `phase6-6-compile-on-*.json` when compile passes but acceptance fails (assertion before `writeEvidence`).
3. **Live repair reliability:** `MISSING_PAGE_LEGACY` and `MISSING_PAGE_ASSERTIONS` Cursor tasks `INTERRUPTED` (no reconciliation/compile).

## Verdicts

| Verdict | Result | Rationale |
|---------|--------|-----------|
| **1. LIVE CURSOR REPAIR** | **PARTIAL / FAIL** | 6/8 tasks completed; 2 interrupted; none produced accepted repair |
| **2. COMPILE-ON ACCEPTANCE** | **FAIL** | 0/8 accepted; 6/8 compile-on succeeded on repair-candidate only |
| **3. ARCHITECTURE QUALITY** | **INCONCLUSIVE (non-blocking)** | Gate blocked on contract revalidation before architecture-gated promotion |
| **4. RECONCILIATION & PROMOTION SAFETY** | **PASS** | Reconciliation ran for all compile-success paths; **no** promotion on failure |
| **5. COMPLETE MATRIX COVERAGE** | **PASS** | All 8 scenarios executed with correlated temp-dir + Surefire evidence |
| **6. PRODUCTION READINESS** | **NOT READY** | Zero compile-on acceptances; systematic revalidation blocker |

## Continuation (if re-run needed)

- **Do not rerun** the six scenarios already conclusively verified in temp dirs unless evidence is copied offline first.
- **Smallest continuation:** fix `FW_ORPHAN_TEST_CLASS` / qualification tree policy (template vs inventory), then re-run only scenarios still on stale JSON **or** add IT change to persist evidence on `REVALIDATION_FAILED`.
- Optional: re-attempt only `MISSING_PAGE_LEGACY` and `MISSING_PAGE_ASSERTIONS` for Cursor interrupt flake (not required for compile-on acceptance conclusion).

**No production flags enabled. No commit in this recovery step.**