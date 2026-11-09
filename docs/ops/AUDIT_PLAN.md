# Keel audit — findings and fix plan

**Audit date:** 2026-11-09 (Opus 4.7 / ponytail lite)
**Load assumed:** one team (~10s of users, ~100s of runs/day), one server, one engine process. Not public SaaS.

Live list; add new findings at the bottom, promote through batches as they land.

## Legend

- 🟢 **Batch A** — tiny, mechanical, do them all in one commit.
- 🟡 **Batch B** — security + correctness, each its own commit. Do before any public-facing or multi-user deploy.
- 🔴 **Batch C** — real refactors, one weekend each, each on its own branch. One per month.
- ⚪ **Batch D** — housekeeping.

## Findings

| # | Title | File (key) | Batch |
|---|---|---|---|
| 1 | Default admin password `engramisayed` in committed `application.properties` | `src/main/resources/application.properties:19` | 🟡 |
| 2 | `ProjectStore.deleteRecursive` follows symlinks (escape on delete) | `src/main/java/delivery/store/ProjectStore.java:218` | 🟡 |
| 3 | H2 datasource blank password by default in dev | `src/main/resources/application.properties:86` | 🟡 |
| 4 | `webhookExecutor` silently drops alerts on queue full (AbortPolicy) | `src/main/java/delivery/portal/config/AsyncConfig.java:61` | 🟢 |
| 5 | `spring.jpa.hibernate.ddl-auto=update` risky on real DB | `src/main/resources/application.properties:87` | 🟡 |
| 6 | `JobDeletionController` missing `denyUnlessOperable` check | `src/main/java/delivery/portal/api/JobDeletionController.java:38` | 🟢 |
| 7 | `AccountAlertService.save` sends a test every click, no throttle | `src/main/java/delivery/portal/service/AccountAlertService.java:72` | 🟡 |
| 8 | No MVC tests for new `/api/runs/{jobId}` or runner-authed `/api/schedules/due` | — | 🟡 |
| 9 | `SuiteAlertNotifier.memberAlerts` has no `@PreDestroy` | `src/main/java/delivery/job/SuiteAlertNotifier.java:25` | 🟢 |
| 10 | `PortalStore` 1700 lines / 92 methods / 105 callers | `src/main/java/delivery/portal/service/PortalStore.java` | 🔴 |
| 11 | 109 of ~2918 tests fail (pre-existing, before live-smoke exclude) | — | ⚪ |
| 12 | Live-smokes OOM in default `mvn test` | **fixed in this branch** (pom exclude) | ✅ |
| 13 | `/api/schedules/due` runner path returns schedules across all tenants | `src/main/java/delivery/portal/api/ScheduleDueController.java:36` | 🟡 |
| 14 | Four worker classes are near-copies (ConversionWorker / ExecuteWorker / GenerateBatchWorker / HuntWorker) | `src/main/java/delivery/portal/worker/` | 🔴 |
| 15 | `StepIntentBinder.java` (2498L) + `ProvePhase.java` (2576L) mega-files | `src/main/java/delivery/authoring/`, `src/main/java/delivery/job/` | 🔴 |
| 16 | Workers busy-poll at 200ms | `src/main/java/delivery/portal/worker/*.java` | 🟢 |
| 17 | `ExcelTcReader` uses `XSSFWorkbook(InputStream)` full-load; 20MB cap | `src/main/java/delivery/excel/ExcelTcReader.java` | ⚪ |
| 18 | Generated framework `.gitignore` doesn't exclude `delivery-target.local.properties` | `customer-framework-template/` | 🟢 |
| 19 | Engine → portal is plain HTTP by default (runner bearer in clear text if ever non-loopback) | `mobile-runner MobileRunnerAgent`, `EngineAgent` | 🟡 |
| 20 | `MobileRunOrchestrator.runs` ConcurrentHashMap never evicted | `src/main/java/delivery/mobile/run/MobileRunOrchestrator.java:70` | 🟡 |
| 21 | `EngineAgent` has no shutdown hook — in-flight job isn't marked CANCELLED on Ctrl+C | `src/main/java/delivery/runner/EngineAgent.java` | 🟡 |
| 22 | `EvidencePaths.resolve` uses raw `tcId` as a path segment without validation | `src/main/java/delivery/ir/EvidencePaths.java:24` | 🟢 |
| 23 | Non-atomic file writes (`TcDraftStore.write`, `HealProposalWriter`, `SuiteRunContextFiles.write`) | multiple | 🟡 |
| 24 | V2 runner chunk uploads leak: `.part` files stay forever if the final `last=true` never arrives | `src/main/java/delivery/portal/api/v2/RunnerApiV2Controller.java:207-226` | 🟡 |
| 25 | V2 runner chunk root defaults to `java.io.tmpdir/keel-v2-chunks` — world-readable on Linux | `src/main/java/delivery/portal/api/v2/RunnerApiV2Controller.java:69-70` | 🟡 |

## Suggested order

### 🟢 Batch A — half a day, one commit
#4, #6, #9, #16, #18, #22. Around 15 lines total.

### 🟡 Batch B — security + correctness, each its own commit
#1 → #2 → #3 → #7 → #13 → #19 → #20 → #21 → #23 → #24 → #25 → #5 → #8. Each 30 min to 2 hours; tests required for #2, #7, #20, #23, #24.

### 🔴 Batch C — refactors, one branch per item
#10 PortalStore split → #14 AbstractJobWorker → #15 StepIntentBinder/ProvePhase split.

### ⚪ Batch D — housekeeping
#11 triage failing tests after live-smoke exclude lands. #17 only if real users hit the 20MB cap.

## Confirmations (not findings — scanned and clean)

- **FreeMarker templates** escape user content with `?j_string`; identifiers are name-sanitised in `CodegenNaming`.
- **ZIP ingest** (`ProjectArtifactService.normalizeZipEntry`, `MobileInputZipValidator.rejectUnsafeName`) correctly reject `..` and absolute paths.
- **Credit debit** uses pessimistic lock (`findByIdForUpdate`) + idempotency key — safe under concurrent charges.
- **Subprocess launches** (adb, apk, appium, cursor heal, nvidia-smi) use `ProcessBuilder` with a `List<String>` — no shell injection.
- **Secrets** are redacted via `SecretSanitizer` at every outbound LLM call (20 call sites confirmed).
- **`LocalLlmClient`** refuses cloud-vendor base URLs on the delivery path; **`AgentRouterClient`** passes the API key in a header (not the URL) and scrubs text first.
- **TLS trust**: no `TrustAll`/`AllowAllHosts` override anywhere; no `ObjectInputStream`; no XXE surface.
- **Static caches**: `PublicationLock.LOCAL`, `HostDeviceLock.LOCAL`, `CredentialRevision.MEMORY`, `PromptCatalog.CACHE` are keyed on bounded sets (project id, device id, prompt id) — do not leak.
- **Snapshot `ReplayCache`** is fully `synchronized`.
- **v1/v2 runner API controllers** all enforce runner / service-identity tokens; `RateLimitPolicy` throttles public API at 60/tenant/min.
- **TunnelPublicProxyController** validates the project token inside the handler; `TunnelAgentController` uses `TunnelAgentAuth`.
- **Admin controllers** without explicit auth inside the file (12 of them) are correctly covered by `.hasRole("ADMIN")` on `/api/admin/**` in SecurityConfig.
- **`net/TargetNetworkPolicy`** blocks loopback/private/link-local by default; dedicated installs must opt in via CIDRs.

## Not checked yet

Nothing material — the whole repo has been swept. Further depth would be per-file reviews (`StepIntentBinder.java`, `ProvePhase.java`, `HealCascade.java`) which are already on the Batch C split list.

Audit continues; new findings get appended with the next `#N`.
