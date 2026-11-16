# Implementation — what the system does

What's actually built and running today. Companion to [`engineering.md`](engineering.md) (how it's built) and [`handoff.md`](handoff.md) (current state).

---

## 1. Product in one paragraph

Keel/TestPilot is a test-automation portal. Users describe what their web (or Android) app should do; Keel generates test cases with an LLM, converts them to Selenium/Appium code, runs them in a real browser with vision + healing, and reports results. The portal is a Spring Boot web app; the automation engine is a Java library (`delivery.*`) the workers call into. Customer-visible artifacts (Excel workbooks, framework zips) are stored per tenant/project under `delivery-store/`.

## 2. The six UX rules (portal)

The portal is being rebuilt against these (branch `ux-overhaul`, docs in `docs/handoff/2026-11-16-ux-overhaul-*`):

1. **One job per page.** Multi-action pages are split into sidebar children (no tabs).
2. **Choose before submit.** Settings visible above submit; no "Advanced" accordions.
3. **Kill helper prose.** Headings and field labels only; explanations go to tooltips or the Guide.
4. **Secondary actions in overflow `⋯` menu.** At most 1 primary + 1 secondary; the rest overflow.
5. **Empty states = one line of instruction + one button.** No decorative prose.
6. **Reuse existing `keel-*.css` tokens.** No new design systems.

## 3. Portal pages (templates)

All under `src/main/resources/templates/`. Route handlers in `delivery.portal.web.PortalUiController` unless noted.

| URL | Template | Purpose |
|-----|----------|---------|
| `/` → `/dashboard` | — | redirect |
| `/dashboard` | `dashboard.html` | Workspace KPIs, Execution trends chart, Needs attention, Recent activity. |
| `/projects` | `projects.html` | List / create / archive / delete projects. |
| `/projects/{projectId}` | `project-detail.html` | Per-project settings (tabs for summary / settings / locator / credentials). |
| `/generate` | `generate.html` | Write test cases from stories (bulk CSV, paste external AI, copy prompt). |
| `/generate/compare` | `generate-compare.html` | Multi-model side-by-side generate (new child page). |
| `/generate-from-knowledge` | `generate-from-knowledge.html` | Generate from knowledge packs. |
| `/record` (alias `/playback`) | `record.html` | Chrome record → steps → save as test case. |
| `/plain-cases` | `plain-cases.html` | Plain-English step authoring before formalising. |
| `/structured-plans` (alias `/test-blueprints`) | `structured-plans.html` | Action/assert plan editor with revisions. |
| `/run-memory` | `run-memory.html` | Approved locator hints that Execute pins. |
| `/suites` | `suites.html` | Compose test packs (Smoke / Regression / Nightly). |
| `/suites/schedule` | `suites-schedule.html` | Pack scheduling + alerts + monitor. |
| `/execute` | `execute.html` | Run library cases with vision + heal. |
| `/upload` → `/automate` | `upload.html` | Upload workbook → `CONVERT` job → framework zip. |
| `/bug-hunter` | `bug-hunter.html` | Explore a site to find bugs (planner-driven). |
| `/packages` | `packages.html` | Framework packages across projects (inspect, download, delete). |
| `/runs` (alias `/jobs`, `/history`) | `jobs.html` | Full run history with status, download, delete. |
| `/status?jobId=…` | `status.html` | Live run status + progress + evidence links. |
| `/evidence?jobId=…` | `evidence.html` | Evidence triage for a finished run. |
| `/pricing` | `pricing.html` | Credit costs per job kind. |
| `/guide` (alias `/tc-guide` → `#tc-authoring`) | `guide.html` | How to use Keel day to day. |
| `/account` | `account.html` | User account + credits. |
| `/login` | `login.html` | Sign in. |
| `/invite/{token}` | `invite.html` | Accept invite (set password). |
| `/request-access` | `request-access.html` | Pending-account landing. |
| `/admin/users` | `admin-users.html` | Users & role assignment (head-owner only). |
| `/admin/users/invite` (alias `/admin/invites`) | `admin-users-invite.html` | Invite form (split page per rule 1). |
| `/admin/pricing` | `admin-pricing.html` | Set credit prices per unit. |
| `/admin/refunds` | `admin-refunds.html` | Review refund requests. |
| `/admin/domains` | `admin-domains.html` | Per-site data store + delete site. |
| `/admin/roles` | `admin-roles.html` | Custom roles & permissions. |
| `/admin/llm-settings` | `admin-llm-settings.html` | Install-wide Ollama + heal API keys. |
| `/admin/prompts` | `admin-prompts.html` | Prompt inventory + overrides. |
| Any 4xx / 5xx | `error.html` | Styled error card — covers Whitelabel. |
| Shared fragments | `fragments.html` | sidebar, topbar, pageHead, featureHelpBtn. |

Other UI controllers:
- `AccessRequestUiController` — pending-account flows.
- `PublicPromptController` — public prompt share links.
- `FaviconController` — favicon.

## 4. REST API surface

67 controllers under `src/main/java/delivery/portal/api/`. Each handles one feature; URLs are typically `/api/<feature>/…`. Grouped by purpose:

**Account & auth**
- `AccountController`, `AccountCreditsController`, `AccountJobsController`, `MePermissionsController`, `AccessRequestController`.

**Projects & workspace**
- `ProjectController`, `ProjectPreflightController`, `WorkspaceCollaborationController`, `EnvironmentController`.

**Jobs lifecycle**
- `JobController`, `JobTriageController`, `JobCompareController`, `JobRerunController`, `JobDeletionController`, `JobRefundController`, `PortalRunnerController` (status stream).

**Generate**
- `GenerateBatchController`, `GenerateTcController`, `GenerateConfigController`, `GeneratedWorkbookController`, `GenerateFromKnowledgeController`, `KnowledgePackController`, `WorkbookUploadPreviewController`.

**Execute & Runs**
- `ExecuteRunController`, `FailureReplayController`, `FailurePackController`, `PreRunAuthoringReviewController`, `RequirementCoverageController`, `MobileExecuteResultController`, `MobileRunController`.

**Suites**
- `SuiteController`, `ScheduleDueController`, `AlertChannelController`, `FlakyTagController`.

**Record, plain cases, blueprints**
- `RecordingController`, `PlainCaseController`, `StructuredPlanController`, `RunMemoryController`.

**Hunt**
- `HuntController`, `HuntTriageController`.

**Heal & visuals**
- `HealProposalController`, `VisualDiffController`, `VisualBaselineController`, `VisionPolicyController`.

**Pipeline & webhooks**
- `PipelineController`, `WebhookController`.

**Admin**
- `AdminUsersController`, `AdminRolesController`, `AdminDomainsController`, `AdminCreditsController`, `AdminAlertsController`, `AdminLlmSettingsController`, `AdminPromptsController`, `AdminRefundsController`, `AdminOpsController`, `PricingController`.

**Tunnels (for customer sites behind firewalls)**
- `TunnelController`, `TunnelAgentController`, `TunnelBadgeController`, `TunnelHeartbeatController`, `TunnelPublicProxyController`.

**Mobile**
- `MobileAppDeviceController`, `MobileCaseRequirementsController`, `MobileAuthProfileController`.

**Nav search**
- `PortalNavSearchController` — powers the top-bar search over pages + projects.

**Health / design**
- `HealthController`, `DesignReferenceController`, `UsageController`.

## 5. Job kinds (`JobRecord.JobKind`)

Enum values in `src/main/java/delivery/portal/model/JobRecord.java:12`:

| Kind | Worker | Triggered from | Output |
|------|--------|----------------|--------|
| `CONVERT` | `ConversionWorker` | `/automate` upload | Framework zip (Selenium/Appium) |
| `EXECUTE` | `ExecuteWorker` | `/execute` run | Evidence folder + results |
| `GENERATE_BATCH` | `GenerateBatchWorker` | `/generate` bulk | New workbook rows |
| `GENERATE_COMPARE` | `CompareGenerateWorker` | `/generate/compare` | Two-model workbook compare |
| `HUNT` | `HuntWorker` | `/bug-hunter` | Hunt report (bugs, not library cases) |

Scheduled suite dispatch (`SuitePortalScheduleWorker`) emits `EXECUTE` jobs when a schedule is due; it's flagged off by default via `delivery.suites.portal-schedule-worker.enabled`.

Status values that matter for the UI (`JobRecord`): `QUEUED`, `RUNNING`, `COMPLETED`, `COMPLETED_WITH_BLOCK`, `FAILED`. Downloadable = `COMPLETED` or `COMPLETED_WITH_BLOCK` (for CONVERT the zip; for HUNT even `FAILED` yields a report).

## 6. Core engine subsystems

Under `src/main/java/delivery/`:

### 6.1 Heal (`delivery.heal`)
Cascade when a locator fails on a step:
- `HealCascade` → entry point, takes a `HealCascade.HealCall` record (C1 refactor).
- Layer 1: `CandidateScorer` shortlist (DOM + semantic, see §6.2).
- Layer 2: local Ollama heal.
- Layer 3: `CursorHealClient` sidecar (`node tools/cursor-heal/heal.mjs`, flag `delivery.cursor-heal.enabled`).
- Layer 4: vision-widen (`delivery.heal.vision-widen.enabled`).
- Fallback: `FreeInventHealer` invents candidates (`delivery.heal.invent.*`, provider `cursor` or `agentrouter`).
- Output: `HealResult` + `HealProposal` (persisted via `HealProposalDiskStore`/`HealProposalWriter`).

### 6.2 Authoring (`delivery.authoring`)
Story-text → bound step:
- `IntentText.normalize` — NFKC + lowercase + whitespace-collapse (Arabic verb-annotation and `TASHKEEL`/`ARABIC` regexes were removed Nov 2026).
- `StepIntentBinder.classify` + `StepIntentBinder.BindRequest` (C2 refactor).
- `CandidateScorer` — DOM shortlist + semantic rank.
- `OutcomeKind` — confirm-line classifier (toast tokens, OTP/Logout without a field/button).
- `AuthoringReviewService` — pre-run review gate.

### 6.3 Settle (`delivery.settle`)
Post-action convergence check:
- `Settle`, `SettlePolicy`, `EffectOutcome`, `Result`.
- `ScreenshotDiff` for per-step visual change detection.
- Mobile variants under `mobile-runner/src/main/java/delivery/mobile/runner/settle/`: `MobileSettleService`, `HierarchyProbe`, `ActionOutcomeClassifier`.

### 6.4 Locator (`delivery.locator`)
Picking a resilient locator for an element:
- `LocatorStabilityPolicy` — ladder: hook → test id → id → name → role+name → label → text → CSS → ordinal.
- `LocatorRanker`, `SemanticLocatorLadder`, `MultiLocatorBag`, `ElementGrounder`.
- Per-project mode on Projects → Settings → Locator ranking.

### 6.5 Hunt (`delivery.hunt`)
Bug Hunter runner — planner picks next action per round using Ollama or Cursor; `HuntTriageController` lets a reviewer accept/dismiss findings before promoting any TC candidates.

### 6.6 Codegen & packager (`delivery.codegen`, `delivery.packager`)
Convert test cases to Java code and emit a runnable Maven project:
- `FrameworkExpectedInventory` + `FrameworkContractValidator` (contract version `2`) validate the generated project at `EmitPhase` before zipping (Phase 6 completion).
- `FrameworkRepairWorkspaceReconciler` reconciles Cursor repair candidates (Phase 6.4).
- Staged reconciliation + compile-on qualification (Phase 6.6+).

### 6.7 Knowledge packs (`delivery.knowledge`)
Feature notes / case packs that feed `generate-from-knowledge`. `KnowledgePackService` + `KnowledgePackDiskStore`.

### 6.8 Vision / Visual (`delivery.vision`, `delivery.visual`)
Screen-vision (annotated screenshots the models see) + visual diffs between runs (`VisualBaselineController`, `VisualDiffController`).

### 6.9 Mobile (`delivery.mobile`, modules `mobile-*`)
Appium runner with its own settle service. Enabled via `delivery.mobile.enabled`.

### 6.10 Agent harness (`delivery.agent`)
Unified scaffold for driving Cursor sidecar workflows (framework repair, failure investigation, TC validation, execution recovery). **Off by default** (`delivery.agent-harness.enabled=false`); each capability has its own flag.

## 7. Services (`delivery.portal.service`)

Business-logic layer the controllers and workers call into. Notables:

- **`PortalStore`** — the main job/project/user persistence facade. A C4 split plan lives as code-anchored javadoc on `PortalStore`.
- `DashboardService` — stats for `/dashboard`.
- `AccountService`, `AdminUserService`, `AdminDomainService`, `AdminPromptsService`.
- `InviteService`, `InviteMailService`, `AccessRequestService`.
- `GeneratedWorkbookService`, `GenerateModelService`, `GeneratePayloadEnricher`.
- `ExecuteRunService`, `ExecuteResultsExcelService`.
- `FailureEvidenceService` (+ `FailureEvidenceDiskStore`), `FailurePackService`, `FailurePackSanitizer`, `FailureReasonHumanizer`.
- `HealProposalService` (+ `HealProposalDiskStore`), `HealJavaLocatorPatcher`.
- `HuntTriageService`.
- `JobStarter`, `JobStatusLookup`, `JobLinkEnricher`, `JobUploadCleanup`, `JobWorkbookResolver`, `JobVisionSummaryService`.
- `FlakyTagService` + `FlakyTagDiskStore`.
- `BugReportService`, `AccountAlertService`, `DesignReferenceService`.
- `KnowledgePackService` + `KnowledgePackDiskStore`.
- `GenerateFromKnowledgeRunStore`, `GeneratedTcScopeFilter`.
- `AdminSeedRunner` — bootstraps the admin user.

## 8. Persistence

- H2 embedded by default (`jdbc:h2:file:./delivery-store/h2`), PostgreSQL mode in tests.
- JPA entities in `delivery.portal.persistence` + `delivery.mobile.persistence`.
- Durable job rows: `JobRecord` (+ `JobKind` enum).
- Lease rows: `DurableJobClaim` + `Lease`.
- Users, projects, credits, alerts, refunds — each has its own entity under `portal.persistence`.
- File artifacts (workbooks, evidence folders, framework zips, heal proposals, flaky tags, knowledge packs) live on disk under `delivery-store/tenants/{workspace}/projects/{project}/…` — not in H2.

## 9. Security & roles

- Spring Security basic auth for admin; session cookie for users.
- `delivery.portal.security` — config, filters, `requireHeadPage()` guard on admin controllers.
- `delivery.portal.roles` — role model + feature perms; flag `delivery.roles.ui-enabled`.
- Feature perms used by sidebar: `feature.generate`, `feature.execute`, `feature.automate`, `feature.hunt`.

## 10. Static assets

### CSS (`src/main/resources/static/css/`)
- `portal.css` — base/table/button/typography.
- `keel-theme-calm.css` — the theme.
- `keel-layout.css`, `keel-portal-enhancements.css`, `keel-portal-a11y.css`.
- `keel-sidebar-roles.css` — sidebar, KPI row, nav-groups, overflow menu.
- `keel-dashboard.css` — dashboard-specific tiles, chart wraps, filter bar.
- `keel-feature-help.css` — the `?` popover + hover reveal.
- `keel-credits.css`, `keel-file-input.css`, `keel-generate-layout.css`, `keel-mobile-ui.css`, `keel-reskin.css`, `keel-run-memory.css`, `keel-suites-schedule.css`.

### JS pages (`src/main/resources/static/js/pages/`)
One per page: `bug-hunter.js`, `evidence.js`, `execute.js` (+ `execute-2.js` helpers), `generate.js`, `guide.js` (+ `guide-2.js`), `project-detail.js`, `status.js`, `suites.js`. Shared code sits in `src/main/resources/static/js/` siblings (`keel-sidebar-ia.js`, `keel-feature-help.js`, etc.).

## 11. Framework packager output

`CONVERT` jobs emit a zip whose structure mirrors `customer-framework-template/`:

```
project-framework/
  pom.xml
  src/main/java/project/pages/*.java          — page objects per feature
  src/main/java/project/utils/                 — Actions, locators, settle, reports, retry, dataReader
  src/test/java/project/tests/*.java           — generated TestNG classes
  testng.xml
  README.md
```

Mobile variant: `customer-mobile-framework-template/` zipped the same way for Appium runs.

Per-project generated frameworks live at `delivery-store/tenants/{ws}/projects/{prj}/framework/` and are what the user downloads.

## 12. Floorplan & graphs

- `floorplan/architecture.json` — hand-curated subsystem map (15 top-level nodes, 2 flows, 9-stop tour).
- `floorplan/recent-work.json` — recent-work cards for the UX overhaul.
- `floorplan/index.html` — interactive viewer.
- `graphify-out/` — persistent knowledge-graph exports (regenerate via `scripts/graphify-update.cmd`).
