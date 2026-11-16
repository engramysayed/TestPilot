# Execute & verify E1–E2 — Implementation Plan

> **Do not commit unless the user asks.**  
> **Status:** Implemented 2026-08-23

**Spec:** `docs/superpowers/specs/2026-08-23-execute-verify-design.md`

## Tasks (complete)

1. JobKind model + schema patch + PortalStore
2. ExecuteJobRunner, DryRunExecuteService, ExecuteWorker
3. ExecuteRunService + ExecuteRunController + screenshot API
4. execute.html UI + dashboard Execute card live
5. ExecuteRunApiTest, ExecuteMvcTest, DashboardMvcTest updates

## Verify

`mvn -q "-Dmaven.compiler.release=21" "-Dtest=ExecuteMvcTest,ExecuteRunApiTest,DashboardMvcTest" test`
