# Research: 001-delivery-platform

## 1. Local AI runtime for authoring

- **Decision**: Ollama HTTP API on the conversion server (`LOCAL_LLM_BASE_URL`, `LOCAL_LLM_MODEL`); vision-capable or text+DOM model as ops chooses (e.g. Qwen2.5-VL class).
- **Rationale**: Constitution requires local-only AI; Ollama is operationally simple and keeps customer HTML/screenshots off cloud vendors.
- **Alternatives considered**: Keep Gemini Flash (rejected — cost + data leave premises); Hermes-only harness without a local model (rejected — Hermes is not the cost fix); remote OpenRouter (rejected for MVP).

## 2. How AI relates to code generation

- **Decision**: Local AI authors structured step JSON only; Freemarker (or equivalent) templates render Page/Test Java. LocatorPolicy text is injected into the AI system prompt; LocatorValidator gates outputs.
- **Rationale**: Consistent editable customer frameworks; prevents free-form Java architecture drift.
- **Alternatives considered**: AI writes full `.java` files (rejected — inconsistent packages); rules-only locator finder without AI (rejected — product owner requires AI as author for new/changed TCs).

## 3. Customer package stack

- **Decision**: Java 24 + Maven + Selenium 4 + TestNG + CI workflow file + reporting-ready deps (Allure preferred).
- **Rationale**: Matches current TestPilot expertise and constitution stack constraint.
- **Alternatives considered**: Playwright/TS for customer ZIP (deferred post-MVP); Cucumber-first (YAGNI for MVP).

## 4. Project / artifact storage for Update

- **Decision**: Filesystem store under `DELIVERY_STORE_ROOT/{projectId}/` with `framework/`, `locator-map.json`, `versions/vN.zip`, job metadata JSON.
- **Rationale**: Fast MVP, easy backup, matches “framework on our server” requirement without Git.
- **Alternatives considered**: Customer re-uploads prior ZIP (rejected for MVP UX); GitHub App PRs (explicitly later); full PostgreSQL from day one (optional later if multi-tenant grows).

## 5. Portal vs engine sequencing

- **Decision**: Phases A–D produce CLI/API conversion + New/Update ZIP; Phase E thin Spring Boot portal.
- **Rationale**: Constitution phase discipline — no pretty UI over a weak engine.
- **Alternatives considered**: Portal-first SaaS shell (rejected in design workshop).

## 6. Excel contract

- **Decision**: Exact headers `TC_ID`, `Title`, `Preconditions`, `Steps`, `ExpectedResult`, `Priority`, `Tags`; required = TC_ID, Title, Steps, ExpectedResult; Apache POI reader; reject on schema failure.
- **Rationale**: Constitution Controlled Input Contract.
- **Alternatives considered**: Free-form Word/PDF (rejected).

## 7. Partial success packaging

- **Decision**: Always include all TCs; PASSED → `tests/generated`; FAIL → `tests/todo` with reason/evidence; `docs/AUTOMATION_SCORE.md`.
- **Rationale**: Spec FR-008 / constitution Principle I.
- **Alternatives considered**: Download blocked until 100% pass (rejected); omit failures (rejected).

## 8. Secrets handling

- **Decision**: Job uses URL/login in memory for authoring; ZIP ships `webapp.properties.example` only.
- **Rationale**: Spec SC-007 / constitution secrets rule.
- **Alternatives considered**: Embed credentials for “it just runs” (rejected — security).

## 9. Reuse of existing TestPilot

- **Decision**: Reuse driver factory, waits, action execution, HTML slimmer, JSON batch parsing patterns; introduce `delivery.*` path that never calls `llmLayer` Gemini client.
- **Rationale**: Faster delivery; clear constitution boundary on cloud AI.
- **Alternatives considered**: Greenfield rewrite (rejected — waste).

## 10. Alignment with Superpowers plan

- **Decision**: Spec Kit plan is the feature-level design; detailed bite-sized engineering tasks remain in `docs/superpowers/plans/2026-08-04-testpilot-delivery-platform.md` and will be mirrored/refined by `/speckit-tasks`.
- **Rationale**: Avoid duplicate conflicting task lists; single product direction.
- **Alternatives considered**: Ignore Superpowers plan (rejected — already owner-approved).
