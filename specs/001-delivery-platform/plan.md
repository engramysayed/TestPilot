# Implementation Plan: Manual TC to Automation Delivery Platform

**Branch**: `001-delivery-platform` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-delivery-platform/spec.md`

**Note**: Aligns with constitution v1.0.0 and prior Superpowers plan at `docs/superpowers/plans/2026-08-04-testpilot-delivery-platform.md`. Implementation MUST NOT start until product owner explicitly says to build.

## Summary

Deliver a hosted MVP where customers without SDETs upload our Excel manual TCs, provide application URL/login, choose New or Update, and download a Java/Selenium/TestNG automation ZIP (reporting + CI) with passed tests and TODO stubs. Conversion uses live browser validation and **local AI only**; durable code is emitted by deterministic templates into one adaptable framework core. Thin portal handles upload/status/download; engine/CLI must produce New ZIP before portal work.

## Technical Context

**Language/Version**: Java 24 (engine + customer ZIP); portal API Java 24 (Spring Boot 3.x)

**Primary Dependencies**: Maven, Selenium 4, TestNG 7.x, Apache POI (Excel), Ollama HTTP API (local LLM), Freemarker (codegen templates), Spring Boot (portal/control plane), existing TestPilot layers (`OrchestratorBuilder`, `actionExecute`, `JsonMapper`, drivers). Delivery path MUST NOT call Gemini/cloud LLM APIs.

**Storage**: Filesystem project store (`DELIVERY_STORE_ROOT/{projectId}/`: framework snapshot, locator-map.json, versioned ZIPs, job metadata). MVP may use H2 or JSON files for portal job records—no mandatory external DB.

**Testing**: TestNG (existing) + JUnit/TestNG unit tests for `delivery.*`; contract tests for Excel/API; manual SauceDemo smoke for full browser jobs.

**Target Platform**: Windows/Linux server hosting portal + workers + Ollama + headless Chrome/Edge; customers run downloaded ZIP on their machines/CI.

**Project Type**: Monorepo web-service + conversion engine + customer-framework-template artifact

**Performance Goals**: Excel validation feedback under 30s for files up to 5 MB; job status visible within 10s after submit; authoring throughput depends on local model/GPU (document expected minutes per TC in ops runbook).

**Constraints**: Local AI only on delivery path; Excel template-only ingest; secrets not in ZIP; validate-before-persist; portal after CLI New ZIP; phase discipline per constitution.

**Scale/Scope**: MVP single-tenant or few projects; async jobs; one automation stack for customer packages.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| I. Ready automation packages (ZIP, all TCs, TODO for fails) | PASS | Packager + code writer design |
| II. Excel template-only ingest | PASS | ExcelTcReader + reject path |
| III. Local AI + deterministic codegen | PASS | Ollama client; Freemarker writer; no Gemini on `delivery.*` |
| IV. Validate before persist | PASS | TcExecutionService before CodeWriter |
| V. Adaptable core, thin portal, phases | PASS | Template + phased A→E; portal after CLI |
| Secrets not in ZIP | PASS | example properties only |
| Server store for Update | PASS | ProjectStore |
| No unjustified YAGNI (Git/billing/cloud AI) | PASS | Out of MVP |

**Post–Phase 1 re-check**: PASS — data-model/contracts/quickstart do not introduce cloud AI, multi-stack, or Git sync.

## Project Structure

### Documentation (this feature)

```text
specs/001-delivery-platform/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md             # NOT created by /speckit-plan (use /speckit-tasks)
```

### Source Code (repository root)

```text
customer-framework-template/     # Core shipped inside every customer ZIP
  pom.xml
  .github/workflows/ci.yml
  README.md
  templates/                     # PageClass / GeneratedTest / TodoTest Freemarker
  src/main/java/com/testpilot/core/
  src/test/java/com/testpilot/pages/
  src/test/java/com/testpilot/tests/generated/
  src/test/java/com/testpilot/tests/todo/
  src/test/resources/config/webapp.properties.example

src/main/java/delivery/          # Conversion engine (new)
  excel/
  authoring/                     # LocalLlmClient, LocatorPolicy, AuthoringService
  codegen/                       # ProvenStep, CodeWriter, PageAccumulator
  job/                           # ConversionJobRunner, TcExecutionService
  packager/
  store/
  cli/

src/main/java/                   # Existing TestPilot (reuse; Gemini not on delivery path)
  buildersLayer/, drivers/, executionLayer/, llmLayer/, ...

portal/                          # Phase E — Spring Boot thin UI + API
  src/main/java/.../api/
  src/main/resources/templates/ or static/

src/test/java/delivery/          # Unit/contract tests for delivery
docs/superpowers/                # Prior design + detailed task plan (reference)
```

**Structure Decision**: Extend the existing TestPilot Maven repo with a `delivery` package and `customer-framework-template` directory; add a `portal` module/app in Phase E only after CLI New ZIP works. Avoid a premature multi-repo split.

## Complexity Tracking

> No constitution violations requiring justification.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
