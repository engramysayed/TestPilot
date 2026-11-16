# Engineering — how this repo is built

Stable reference for how Keel/TestPilot is structured, built, and run. Changes slowly.
For *what exists* see [`implementation.md`](implementation.md). For *what's in flight right now* see [`handoff.md`](handoff.md).

---

## 1. Stack

- **Java 21** (enforced via maven-enforcer: `[21,)`).
- **Spring Boot 3.3.5** — web, security, JPA, async workers, actuator.
- **Thymeleaf** server-rendered templates (no SPA, no bundler). Static JS/CSS under `src/main/resources/static/`.
- **H2** embedded JPA database by default; H2 PostgreSQL mode is used in tests.
- **TestNG** test framework (not JUnit). Spring context tests extend `AbstractTestNGSpringContextTests`.
- **Chart.js 4.4.6** (CDN) for dashboard charts.
- **Ollama** (local LLM) + optional **Cursor Auto** sidecar for heal/invent cascades. Both guarded by properties, both can be disabled.
- **Selenium / Appium** inside the generated customer framework (not in the portal itself).
- **Maven** 3.9.x; the root pom is multi-module.

## 2. Modules

Maven multi-module parent: `org.example:keel-parent:1.0-SNAPSHOT` (`pom.xml`, `packaging=pom`).

| Module | Role |
|--------|------|
| `keel-portal` | The Spring Boot web app. Sources currently live at the repo root (`./src`) — the module's own pom skips most sources for compat. |
| `keel-contract` | Interfaces / records shared between portal and engine. |
| `keel-common` | Cross-module utilities (pure Java). |
| `keel-engine` | The automation engine (locator ladders, settle, vision, heal cascade). |
| `mobile-contracts` | Mobile runner contracts. |
| `mobile-runner` | Appium runner (`delivery.mobile.runner.MobileRunner`). |
| `mobile-codegen` | Mobile codegen helpers. |
| `customer-framework-template` | **Not** in the parent pom. Sibling folder. Base Selenium project zipped into customer downloads. |
| `customer-mobile-framework-template` | Same, for Appium downloads. |

The portal and mobile runner are the two runnable entry points (floorplan/architecture.json:6-9).

## 3. Entry points & runtime model

- `delivery.portal.PortalApplication` (@SpringBootApplication). Scans `delivery.portal` + `delivery.mobile`.
  `@EnableAsync` — background workers are `@Async` Spring beans on a `ThreadPoolTaskExecutor`.
  `@EntityScan` + `@EnableJpaRepositories` cover both portal and mobile persistence packages.
- `delivery.mobile.runner.MobileRunner` for mobile runs.

At runtime there is one JVM for everything (portal web + API + workers + schedulers). Workers share the Spring thread pool.

## 4. Package map (`src/main/java/delivery/`)

Top-level packages under `delivery/`:

```
agent        authoring    cli          codegen       effect       env
evidence     excel        guard        heal          hunt         identity
ir           job          knowledge    locator       mobile       net
packager     portal       privacy      prompts       revise       routing
runner       settle       snapshot     store         util         vision      visual
```

Portal subpackages:
```
portal/
  web/        — @Controller for HTML pages (4 classes, PortalUiController is the main one)
  api/        — @RestController under /api/* (67 controllers)
  service/    — business logic (DashboardService, PortalStore, AccountService, …)
  worker/     — @Async job workers (see §5)
  jobs/       — DurableJobClaim, LeaseKeeper, JobLeaseReconciler
  security/   — Spring Security config, admin auth, runner filter
  persistence/, db/, model/  — JPA entities & repos
  config/, preflight/, cli/, engine/  — bootstrapping & env plumbing
  roles/      — role model + guards
```

Engine subpackages of interest:
- `delivery.heal` — heal cascade (`HealCascade`, `FreeInventHealer`, `CursorHealClient`, `HealProposal*`).
- `delivery.store` — on-disk memory. **Two separate locator-memory layers**: `DomainLocatorMemory` (proven selectors keyed by `host + path + intentKey`, auto-recorded after successful runs, site-scoped at `{siteRoot}/domain-locator-memory.json`; tried before bind, dropped on failure) and `RunMemoryHintsStore` (free-text hints fed to the heal AI, per-job at `{projectRoot}/run-memory/{memId}.json` → copied onto each execute job as `run-memory-approved.json`). Both exposed in the portal: `/locator-memory` edits the first, `/run-memory` ("Flaky locators") edits the second.
- `delivery.authoring` — story-to-step binding (`StepIntentBinder`, `IntentText`, `CandidateScorer`, `OutcomeKind`).
- `delivery.settle` — post-action settle (`Settle`, `SettlePolicy`, `EffectOutcome`, `ScreenshotDiff`).
- `delivery.locator` — locator policy + ranking (`LocatorStabilityPolicy`, `LocatorRanker`, `SemanticLocatorLadder`, `MultiLocatorBag`, `ElementGrounder`).
- `delivery.packager` — zip framework for customer download.
- `delivery.codegen` — generate Selenium/Appium code.
- `delivery.vision`, `delivery.visual` — screen vision + visual diffs.
- `delivery.hunt` — Bug Hunter planner + runner.
- `delivery.knowledge` — knowledge packs for GFK (generate from knowledge).

Generated customer frameworks (one per project) live outside `src/` at `delivery-store/tenants/{ws}/projects/{prj}/framework/` and inherit from `customer-framework-template/`.

## 5. Workers & job lifecycle

All workers extend `delivery.portal.worker.AbstractJobWorker` (C5 refactor; scaffold only — not every worker is migrated).

| Worker | Job kind | Triggered by |
|--------|----------|--------------|
| `ConversionWorker` | `CONVERT` | `/automate` upload (Excel → framework zip) |
| `ExecuteWorker` | `EXECUTE` | `/execute` run button |
| `GenerateBatchWorker` | `GENERATE_BATCH` | `/generate` (one job per story row) |
| `CompareGenerateWorker` | `GENERATE_COMPARE` | `/generate/compare` |
| `HuntWorker` | `HUNT` | `/bug-hunter` start |
| `SuitePortalScheduleWorker` | scheduled `EXECUTE` | `/suites/schedule` cron (flag: `delivery.suites.portal-schedule-worker.enabled`) |

Shared lifecycle pieces in `delivery.portal.jobs`:
- **`DurableJobClaim.Lease`** — a worker claims a job row, gets a lease; `LeaseKeeper` renews it.
- **`JobLeaseReconciler`** — periodically reclaims leases from dead workers.
- **`AgentAwarePublicationGuard`**, **`JobAttemptPublicationGuard`** — prevent partial publishes when a worker dies mid-run.

Job kinds enum: `JobRecord.JobKind { CONVERT, EXECUTE, GENERATE_BATCH, GENERATE_COMPARE, HUNT }` (src/main/java/delivery/portal/model/JobRecord.java:12).

## 6. Core patterns

- **No bundler, no SPA.** Each Thymeleaf page loads one or two `/css/keel-*.css` files, one `/js/pages/<page>.js`, and uses the `fragments.html` sidebar/topbar.
- **Background work is Spring `@Async`** — no Quartz, no Kafka, no external queue.
- **Durable leases**: workers must `claim`, hold `Lease`, renew via `LeaseKeeper`; reconciler picks up orphans.
- **Heal cascade** (locator fails → recover):
  1. Candidate shortlist (`CandidateScorer`, DOM + semantic).
  2. Local Ollama heal.
  3. Cursor sidecar (`CursorHealClient`, flag `delivery.cursor-heal.enabled`).
  4. Vision widen (`delivery.heal.vision-widen.enabled`).
  Fallback: `FreeInventHealer` (`delivery.heal.invent.*`).
- **StepIntentBinder** turns an English step line into a bound action; `IntentText.normalize` was Arabic-aware and was stripped in Nov 2026 (English-only authoring now).
- **Customer framework contract** (Phase 6) — pre-zip the generated project is validated against an expected inventory (`FrameworkExpectedInventory`, `FrameworkContractValidator`, `contractVersion=2`) at `EmitPhase`.

## 7. Config

Properties files:
- `src/main/resources/application.properties` — dev defaults.
- `src/main/resources/application-product.properties` — production profile.

Key knobs:
```
delivery.store-root=./delivery-store
delivery.work-dir=./delivery-work
delivery.template-root=./customer-framework-template
delivery.public-base-url=http://localhost:8080

delivery.llm-base-url=http://127.0.0.1:11434
delivery.llm-model=gemma4:26b
delivery.generate-model=gemma4:26b
delivery.generate-models=gemma4:26b,gemma4:e2b,qwen2.5:latest
delivery.generate-timeout-seconds=600
delivery.generate-num-predict=8192

delivery.provider.allowlist=ollama,cursor,agentrouter,vision
delivery.install.mode=shared          # shared | dedicated (+ private-cidrs)
delivery.jobs.max-queued-per-tenant=20
delivery.jobs.max-running-per-install=8
delivery.install.production=false     # must flip in prod

delivery.cursor-heal.enabled=true     # layer 3 heal sidecar (node tools/cursor-heal/heal.mjs)
delivery.heal.invent.enabled=true
delivery.heal.invent.max-per-tc=2
delivery.heal.vision-widen.enabled=true

delivery.agent-harness.enabled=false  # unified agent harness — off by default
```

Admin credentials:
- `delivery.admin-email=${KEEL_ADMIN_EMAIL:admin@localhost}`
- `delivery.admin-password=${KEEL_ADMIN_PASSWORD:please-change}`

Environment variables override the properties. Production installs **must** set `delivery.install.production=true` and a non-default admin password.

## 8. Secrets & auth

- Spring Security basic auth for admin pages + session cookies for user flows.
- Admin pages gate on head/owner role; `requireHeadPage()` runs before `admin/*` controllers.
- API keys for heal providers are loaded by `LocalApiKeyLoader` (local file + env override).
- CSRF tokens are present on API POST/DELETE calls; JS uses `window.KEEL_PAGE.csrf` + `X-Keel-Requested-With` header.
- User emails are private; never ship them to external services without approval.

## 9. Build & run

```bash
mvn -f pom.xml clean verify          # full build + tests (slow)
mvn -f pom.xml -DskipTests package   # just build
mvn -Dtest=DashboardMvcTest test     # single test (root pom routes to keel-portal)
mvn spring-boot:run                  # starts PortalApplication on :8080
```

Windows convenience script: `start-portal.cmd`.

Regenerate the floorplan:
```bash
# from repo root
claude /floorplan   # or the plugin's refresh skill
```
Floorplan output lands at `floorplan/index.html` and `floorplan/architecture.json`.

## 10. Testing conventions

- **TestNG**, not JUnit. Tests under `src/test/java/`, mirror the package of the class under test.
- Spring context tests use `AbstractTestNGSpringContextTests` + `@SpringBootTest(classes = PortalApplication.class, properties = {…})`.
- Common per-test properties: isolated H2 URL, `delivery.store-root=./target/test-delivery-store-*`, `delivery.dry-run=true`.
- MVC tests use `MockMvc` + `httpBasic("admin@testpilot.local", "ChangeMeAdmin1!")`.
- Framework contract has its own evidence JSON files under `docs/archive/handoffs/phase6-*-compile-on-*.json`.
- Live smokes (`SettleLiveSmoke`, `TunnelBrokerApiTest`) need real infra and are gated.

## 11. Branch model

- **`main`** — tracked by both `keel/main` (origin for Keel) and `origin/main` (TestPilot fork). Both kept in sync by direct push.
- **`ux-overhaul`** — long-running feature branch for the portal UX refactor (Nov 2026). Merges forward into main in large batches.
- **`audit-c1-*` through `audit-c5-*`** — one branch per audit refactor (`HealCall` record, `BindRequest` record, mega-method extracts, `PortalStore` split plan, `AbstractJobWorker` scaffold).
- **`alerts-members-and-cleanup`** — the other session's UI rebase branch.
- `backup/*` branches are pre-refactor safety nets; don't commit there.

Attribution lines for commits made by this assistant:
```
Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

## 11b. Agent harness + validation — where to read

Unified scaffold under `delivery.agent.harness.*` + `delivery.agent.validation.*` — 76 files, **off by default**. Provider-abstracted agent runtime (Fake + Cursor today), coordinator, five versioned task types, durable store, workspace prep, policy/budget, reconciliation. Bridges live under `convert/`, `recovery/`, `validation/automate/`, `validation/generate/`. Deterministic contract validators (`FrameworkContractValidator`, `TestCaseContractValidator`) share the `ValidationReport` shape with AI-driven bridges so callers consume both the same way. See `docs/implementation.md §6.10` for the inventory.

Start here when you resume this work:
- **Architecture plan** — [`docs/archive/handoffs/2026-11-15-unified-ai-validation-architecture-plan.md`](archive/handoffs/2026-11-15-unified-ai-validation-architecture-plan.md) (master doc, read first)
- **Phase 6 completion** — [`docs/archive/handoffs/2026-11-15-framework-contract-phase6-completion.md`](archive/handoffs/2026-11-15-framework-contract-phase6-completion.md)
- **Next-phase plan** — [`docs/archive/handoffs/2026-11-16-framework-contract-phase6-plan.md`](archive/handoffs/2026-11-16-framework-contract-phase6-plan.md)
- **Execution-recovery spike** — [`docs/archive/handoffs/2026-11-15-execution-recovery-evidence-spike.md`](archive/handoffs/2026-11-15-execution-recovery-evidence-spike.md)
- **Phase 3 plan/impl** — [`docs/archive/handoffs/2026-11-12-agent-harness-phase3-plan.md`](archive/handoffs/2026-11-12-agent-harness-phase3-plan.md) + [`-phase3-implementation.md`](archive/handoffs/2026-11-12-agent-harness-phase3-implementation.md)
- **Routing table** (all Phase 6 sub-handoffs 6.4 → 6.17 + evidence JSONs) — [`docs/ENGINEERING_HANDOFF.md`](ENGINEERING_HANDOFF.md) rows **Unified agent harness** and **Automate framework contract (Phase 6)**

## 12. Pointers into deeper docs

- `docs/PRODUCT.md` — full product feature list (487 lines).
- `docs/ENGINEERING_HANDOFF.md` — the long-form engineer onboarding doc (index into everything).
- `docs/knowledge/CURRENT.md` — architecture + status snapshot.
- `docs/knowledge/REVIEW-2026-11-07.md` — open risks / next work.
- `docs/archive/handoffs/` — dated handoff docs; see `docs/archive/handoffs/CLAUDE-RESUME-KEEL-KNOWLEDGE.md` for Claude-specific resume map.
- `docs/archive/handoffs/2026-11-16-ux-overhaul-master-plan.md` — the UX overhaul plan (Phases 1–11).
- `docs/archive/HISTORY.md` — session history, newest first.
- `floorplan/index.html` — interactive architecture map.
- `graphify-out/GRAPH_REPORT.md` — knowledge graph export.
