# Phase 6 implementation plan

**Source of truth for expected pages:** `PageAccumulator` built from the same `List<TcOutcome>` as `CodeWriter` / `DomainCatalogWriter` (proven, login, setup steps).

**Source of truth for expected tests:** `CodegenTestClasses` / `CodegenNaming.testClassName` per outcome (generated vs todo tier).

**Integration point:** `EmitPhase` passes `FrameworkExpectedInventory.fromOutcomes(outcomes)` into `AutomateFrameworkAcceptanceGate.run` immediately before pre-zip validation (Phase 5 boundary preserved).

**Contract version:** Reports use `contractVersion=2` when inventory-driven validation runs; v1 file-only rules unchanged when inventory is null.

**Completed:** 2026-11-15 — see `2026-11-15-framework-contract-phase6-completion.md`.