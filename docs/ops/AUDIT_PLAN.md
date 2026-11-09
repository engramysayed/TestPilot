# Keel audit — findings and fix plan

**Audit date:** 2026-11-09 (Opus 4.7 / ponytail lite)
**Load assumed:** one team (~10s of users, ~100s of runs/day), one server, one engine process. Not public SaaS.
**Branch:** `alerts-members-and-cleanup` (11 commits ahead of `main`).

**How to use this doc:** Each finding below is self-contained. Pick one, read the "Current code" and "Fix", open the file, apply the diff, add the test, run `mvn -pl keel-portal test -Dtest=<class>`, commit. Any AI session or a human developer can continue from here without this session's context.

## Legend

- 🟢 **Batch A** — tiny, mechanical, do them all in one commit. ~15 lines total.
- 🟡 **Batch B** — security + correctness, each its own commit.
- 🔴 **Batch C** — real refactors, one branch per item, one weekend each.
- ⚪ **Batch D** — housekeeping.

## Suggested order

1. **Batch A** (half an hour, one commit) → ships 6 small wins.
2. **Batch B** in the order listed — stop before a public-facing deploy.
3. **Batch C** one item per month — no behaviour change, each on its own branch.
4. **Batch D** whenever.

---

# Batch A — tiny, one commit (~15 lines, half an hour)

### 🟢 A1 — `webhookExecutor` silently drops alerts on queue full
**Finding #4**
**File:** `src/main/java/delivery/portal/config/AsyncConfig.java:57-72`

**Current:** Default `RejectedExecutionHandler` is `AbortPolicy`. A fail storm (50 suite failures × 20 project members = 1000 tasks) overflows the 200-slot queue; `@Async` throws `RejectedExecutionException`, Spring logs it, alert is lost silently.

**Fix:** Add one line in the `webhookExecutor` bean:
```java
executor.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
```
Place after `setThreadNamePrefix("webhook-")`, before `initialize()`.

**Test:** none (behaviour change is only visible under overflow).
**Risk:** The caller thread blocks until the send runs. Alert is slightly slower under storm but reliable.

### 🟢 A2 — `JobDeletionController` missing project-operate check
**Finding #6**
**File:** `src/main/java/delivery/portal/api/JobDeletionController.java:38-55`

**Current:** Only checks `store.getOwnedJob(jobId, ownerId)`. A user who created a job then lost `operate` rights on the project can still delete the run.

**Fix:** After the `getOwnedJob` ownership check, before `wipeArtifacts`, add:
```java
ResponseEntity<?> denied = ProjectAccess.denyUnlessOperable(
        store, job.getProjectId(), ownerId);
if (denied != null) return denied;
```
`ProjectAccess` is already imported (same package).

**Test:** MVC test for 403 when a user without operate rights calls DELETE.
**Risk:** None. Narrows who can delete; owners retain access.

### 🟢 A3 — `SuiteAlertNotifier.memberAlerts` has no unregister path
**Finding #9**
**File:** `src/main/java/delivery/portal/alerts/SuiteScheduleWiring.java`

**Current:** `@PostConstruct` sets `SuiteAlertNotifier.registerMemberAlerts(...)` and never clears it. A test that rebuilds the Spring context leaks the old `AccountAlertService`.

**Fix:** Add a `@PreDestroy` method to `SuiteScheduleWiring`:
```java
@jakarta.annotation.PreDestroy
void unwire() {
    SuiteAlertNotifier.registerMemberAlerts(null);
}
```

**Test:** none.
**Risk:** None.

### 🟢 A4 — Worker busy-poll at 200ms → 1000ms
**Finding #16**
**Files (4):**
- `src/main/java/delivery/portal/worker/ConversionWorker.java:208`
- `src/main/java/delivery/portal/worker/ExecuteWorker.java:209`
- `src/main/java/delivery/portal/worker/GenerateBatchWorker.java:135`
- `src/main/java/delivery/portal/worker/HuntWorker.java:183`

**Current:** `Thread.sleep(200);` in each worker's main loop → 4 × 5 polls/sec = 20 DB hits/sec at idle.

**Fix:** Change each to `Thread.sleep(1000);`. Nothing else.

**Test:** none.
**Risk:** New job starts take up to 1 second longer (was 200ms). Acceptable for human-triggered runs.

### 🟢 A5 — Generated framework `.gitignore` should exclude local creds
**Finding #18**
**File:** `customer-framework-template/.gitignore` (create if missing) — check `customer-framework-template/` root for existing `.gitignore`.

**Current:** `FrameworkPackager.writeTargetConfig` writes `delivery-target.local.properties` beside the project with real creds. It's excluded from the ZIP. But if the user initialises git in the generated project, the file can get committed.

**Fix:** Append to `customer-framework-template/.gitignore`:
```
# Local, user-supplied credentials (never commit)
delivery-target.local.properties
```

**Test:** none.
**Risk:** None.

### 🟢 A6 — `EvidencePaths.resolve` path escape
**Finding #22**
**File:** `src/main/java/delivery/ir/EvidencePaths.java:11-44`

**Current:** Fallback `evidenceRoot.resolve(tcId)` uses raw `tcId`. If any caller forwards an unvalidated path segment, `evidenceRoot.resolve("../../etc/hosts")` escapes.

**Fix:** First line of `resolve(Path evidenceRoot, String tcId)`:
```java
TcIdentity.requireValid(tcId);
```
`TcIdentity` is already in the same package.

**Test:** add to `src/test/java/delivery/ir/EvidencePathsTest.java` (create if missing):
```java
@Test(expectedExceptions = IllegalArgumentException.class)
public void rejectsPathTraversalTcId() {
    EvidencePaths.resolve(java.nio.file.Path.of("."), "../../etc");
}
```
**Risk:** Callers that pass tcIds NOT matching `^TC_[A-Z0-9_]+$` will now throw. Grep for callers first; most already validate upstream.

---

# Batch B — security + correctness (each its own commit)

### 🟡 B1 — Default admin password `engramisayed` in committed properties
**Finding #1**
**File:** `src/main/resources/application.properties:18-19`

**Current:**
```
delivery.admin-email=engramisayed@localhost
delivery.admin-password=engramisayed
```

**Problem:** On first boot `AdminSeedRunner` uses these to create an admin account. Anyone who clones the repo and runs `start-portal.cmd` gets admin access with password `engramisayed`. Also the username is a real person's handle, which leaks ownership info from a public-looking repo.

**Fix:**
1. In `application.properties`, replace with `${KEEL_ADMIN_EMAIL:admin@localhost}` / `${KEEL_ADMIN_PASSWORD:please-change}`.
2. In `AdminSeedRunner.java`, if `password.equals("please-change")`, log a loud WARN and skip the seed:
```java
if ("please-change".equalsIgnoreCase(password.trim())) {
    log.warn("Admin seed skipped — set KEEL_ADMIN_PASSWORD before first boot");
    return;
}
```
3. Update `keel-env.local.bat.example` to show the two new env vars.

**Test:** Existing `AdminSeedRunnerTest` (if any). Add one case: with `please-change`, no admin is seeded.
**Risk:** Existing installs that relied on the committed defaults break. Mitigation: state in commit message that the new env vars must be set.

### 🟡 B2 — `ProjectStore.deleteRecursive` follows symlinks
**Finding #2**
**File:** `src/main/java/delivery/store/ProjectStore.java:218-230`

**Current:**
```java
public static void deleteRecursive(Path root) throws Exception {
    if (!Files.exists(root)) return;
    Files.walk(root)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(p -> { try { Files.deleteIfExists(p); } catch (...) {} });
}
```
`Files.walk` follows symlinks by default. A project operator who writes a symlink inside `hunt-runs/<jobId>/` → `C:\` would have `C:\` deleted when the run is purged.

**Fix:** Switch to `Files.walkFileTree` with `SimpleFileVisitor` (walkFileTree does NOT follow symlinks by default):
```java
public static void deleteRecursive(Path root) throws Exception {
    if (!Files.exists(root)) return;
    Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
        @Override public java.nio.file.FileVisitResult visitFile(
                Path f, java.nio.file.attribute.BasicFileAttributes a) throws java.io.IOException {
            Files.deleteIfExists(f); return java.nio.file.FileVisitResult.CONTINUE;
        }
        @Override public java.nio.file.FileVisitResult postVisitDirectory(
                Path d, java.io.IOException exc) throws java.io.IOException {
            Files.deleteIfExists(d); return java.nio.file.FileVisitResult.CONTINUE;
        }
    });
}
```

**Test:** `src/test/java/delivery/store/ProjectStoreDeleteTest.java`:
```java
@Test public void doesNotFollowSymlinks() throws Exception {
    Path tmp = Files.createTempDirectory("pstest");
    Path outside = Files.createTempDirectory("outside");
    Files.writeString(outside.resolve("keep.txt"), "x");
    Files.createSymbolicLink(tmp.resolve("link"), outside); // may fail on Windows without privilege
    ProjectStore.deleteRecursive(tmp);
    Assert.assertTrue(Files.exists(outside.resolve("keep.txt")));
}
```
Guard with `@Test(enabled = false)` on Windows if symlink creation needs admin.

**Risk:** Low. Same delete semantics; only the symlink-escape case changes.

### 🟡 B3 — H2 datasource blank password in dev
**Finding #3**
**File:** `src/main/resources/application.properties:81-86`

**Current:** `spring.datasource.password=${KEEL_DB_PASSWORD:}` → sa/blank if env unset. File-system access = full DB read.

**Fix:** Refuse blank in `product` profile. Add to `ProductionSafetyGuard.violations` an early check: in `product`, `datasourcePassword` must be non-blank (the check already exists for production; expand it).

Alternative: `AdminSeedRunner` or a new `KeelBootstrap` runner generates a random 32-char password on first boot and writes it to `delivery-store/.db-password` (gitignored), then exits with a message asking the user to set `KEEL_DB_PASSWORD` to that value.

**Test:** `ProductionSafetyGuardTest` — add a case for `product` + blank password = violation.
**Risk:** First boot after this change needs the env var set.

### 🟡 B4 — `AccountAlertService.save` test-sends with no throttle
**Finding #7**
**File:** `src/main/java/delivery/portal/service/AccountAlertService.java:72-95`

**Current:** Every call to `save(userId, body)` triggers `sender.sendEach(prefs, CONFIRMATION, ...)`. No per-user rate limit.

**Fix:** In-memory per-user cooldown:
```java
private final java.util.concurrent.ConcurrentHashMap<Long, java.time.Instant> lastConfirm
        = new java.util.concurrent.ConcurrentHashMap<>();
private static final java.time.Duration COOLDOWN = java.time.Duration.ofSeconds(60);
// inside save(), right before sender.sendEach:
java.time.Instant now = java.time.Instant.now();
java.time.Instant last = lastConfirm.get(userId);
if (last != null && java.time.Duration.between(last, now).compareTo(COOLDOWN) < 0) {
    // skip confirmation, still save prefs
} else {
    AttentionAlertSender.Result result = sender.sendEach(prefs, CONFIRMATION, config(), transport);
    if (!result.ok()) throw new IllegalArgumentException(String.join(" ", result.errors()));
    lastConfirm.put(userId, now);
}
```

**Test:** `AccountAlertServiceTest` — two `save()` calls within 60s → only one send through a mock transport.
**Risk:** First save after a rejected confirmation won't re-send. Acceptable.

### 🟡 B5 — `/api/schedules/due` runner path leaks cross-tenant
**Finding #13**
**Files:**
- `src/main/java/delivery/portal/api/ScheduleDueController.java:30-45`
- `src/main/java/delivery/portal/service/SuiteService.java:206`
- `src/main/java/delivery/portal/service/PortalStore.java:361`

**Current:** My fix in `ScheduleDueController` calls `suites.listDueForAllProjects(within)` for any runner token. `PortalStore.listProjectsForScheduleDispatch()` returns every non-archived project across every workspace.

**Fix:**
1. Add to `PortalStore.java`:
```java
public List<ProjectRecord> listProjectsForScheduleDispatchByTenant(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) return List.of();
    return projectRepository.findAll().stream()
            .filter(p -> !p.isArchived())
            .filter(p -> tenantId.equals(p.getTenantId()))
            .map(this::toRecord).collect(Collectors.toList());
}
```
2. Add to `SuiteService.java`:
```java
public List<Map<String, Object>> listDueForAllProjectsOfTenant(String tenantId, int withinSeconds) throws IOException {
    // same body as listDueForAllProjects but use the tenant-scoped project list
}
```
3. In `ScheduleDueController.due()`, replace the runner branch:
```java
if (auth instanceof PrivateRunnerFilter.RunnerToken rt) {
    String tenantId = rt.enrollment().tenant().value();
    return ResponseEntity.ok(suites.listDueForAllProjectsOfTenant(tenantId, within));
}
```

**Test:** MVC test — enroll a runner in tenant A, create a suite in tenant B, GET /api/schedules/due with A's bearer → B's suite not returned.
**Risk:** Same-tenant behaviour unchanged.

### 🟡 B6 — Engine / mobile runner default to plain HTTP
**Finding #19**
**Files:**
- `mobile-runner/src/main/java/delivery/mobile/runner/agent/MobileRunnerAgent.java:47`
- `src/main/java/delivery/runner/EngineConfig.java:39-55`

**Current:** `KEEL_PORTAL_URL` defaults to `http://127.0.0.1:8080`. If someone sets it to a non-loopback `http://…` URL, the `tp_run_*` bearer goes over clear text.

**Fix:** In `EngineConfig` and `MobileRunnerAgent`, after reading the URL, refuse non-loopback HTTP:
```java
URI uri = URI.create(portal);
boolean loopback = switch (uri.getHost() == null ? "" : uri.getHost()) {
    case "127.0.0.1", "localhost", "::1", "[::1]" -> true;
    default -> false;
};
if ("http".equals(uri.getScheme()) && !loopback) {
    throw new IllegalStateException("ENGINE_CONFIG_REFUSED: non-loopback portal URL must use https");
}
```

**Test:** `EngineConfigTest` — `KEEL_PORTAL_URL=http://portal.example.com` throws.
**Risk:** Breaks any deploy that currently relies on plain HTTP to a remote portal (there shouldn't be any).

### 🟡 B7 — `MobileRunOrchestrator.runs` never evicts completed runs
**Finding #20**
**File:** `src/main/java/delivery/mobile/run/MobileRunOrchestrator.java:70, 141`

**Current:** `runs.put(run.runId, run)` is the only mutation. The ConcurrentHashMap grows for the JVM's lifetime.

**Fix:** When a run reaches a terminal state (FAILED / COMPLETED / CANCELLED), evict it. Grep `MobileRun` for the status-change path, add `runs.remove(runId)` after terminal state is set. If there is no single such code point, wrap a `completeRun(runId)` helper and call it.

Also add a scheduled sweep that removes any run with a terminal state older than N hours:
```java
@org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3_600_000L)
void sweepCompletedRuns() {
    java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofHours(6));
    runs.values().removeIf(r -> r.terminalAt() != null && r.terminalAt().isBefore(cutoff));
}
```

**Test:** `MobileRunOrchestratorTest` — complete a run, assert `runs` is empty.
**Risk:** Status lookups for ancient runs now miss. Fine — status is persisted.

### 🟡 B8 — `EngineAgent` has no shutdown hook
**Finding #21**
**File:** `src/main/java/delivery/runner/EngineAgent.java` (main method)

**Current:** Ctrl+C mid-run kills the JVM without marking the in-flight job CANCELLED. The lease reclaimer picks it up later, but the engine window logs no graceful exit.

**Fix:** In the main method, register a shutdown hook that calls an in-memory flag the main loop checks:
```java
static volatile boolean shuttingDown = false;
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    shuttingDown = true;
    LogsManager.info("ENGINE_SHUTDOWN: draining");
    // best-effort: if a job is in flight, POST cancel to portal
}));
// main loop:
while (!Thread.currentThread().isInterrupted() && !shuttingDown) { ... }
```

**Test:** none (signal handling is hard to unit-test).
**Risk:** None. Worst case the hook adds a short drain delay on exit.

### 🟡 B9 — Non-atomic file writes
**Finding #23**
**Files:**
- `src/main/java/delivery/ir/TcDraftStore.java:29`
- `src/main/java/delivery/heal/HealProposalWriter.java:76`
- `src/main/java/delivery/job/SuiteRunContextFiles.java:44`

**Current:** `Files.writeString(file, json, UTF_8)` truncates then writes. JVM crash mid-write → 0-byte or partial file → next read throws JSONException.

**Fix:** Add helper to `src/main/java/delivery/store/ProjectStore.java` (or a new `Atomic.java`):
```java
public static void writeStringAtomic(Path file, String content, Charset cs) throws IOException {
    Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
    Files.writeString(tmp, content, cs);
    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
}
```
Replace the three `Files.writeString(...)` calls with `Atomic.writeStringAtomic(...)`.

**Test:** one unit test that a partial crash leaves the previous file intact (simulate by throwing between tmp write and move).
**Risk:** `ATOMIC_MOVE` may not be supported on every filesystem; fall back to `REPLACE_EXISTING` alone on `AtomicMoveNotSupportedException`.

### 🟡 B10 — V2 runner chunks leak `.part` files
**Finding #24**
**File:** `src/main/java/delivery/portal/api/v2/RunnerApiV2Controller.java:184-228`

**Current:** `PUT /api/v2/runners/jobs/{jobId}/artifacts/{artifactId}/chunks/{index}` writes `.part` files under `chunkRoot/jobId/attemptId/artifactId/`. `deleteTree(dir)` runs only on the final `last=true` chunk's happy path. If the runner dies mid-upload, the `.part` files stay forever.

**Fix:** Scheduled sweeper (new method in the same controller or a new bean):
```java
@org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3_600_000L)
void sweepStaleChunks() throws IOException {
    if (!Files.isDirectory(chunkRoot)) return;
    java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofHours(6));
    try (var stream = Files.walk(chunkRoot)) {
        stream.filter(Files::isDirectory)
              .filter(p -> !p.equals(chunkRoot))
              .filter(p -> {
                  try { return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff); }
                  catch (IOException e) { return false; }
              })
              .forEach(p -> deleteTree(p));
    }
}
```

**Test:** unit test — create a fake stale chunk dir, run sweep, assert deleted.
**Risk:** None.

### 🟡 B11 — V2 chunk root defaults to world-readable `/tmp`
**Finding #25**
**File:** `src/main/java/delivery/portal/api/v2/RunnerApiV2Controller.java:68-70`

**Current:**
```java
this.chunkRoot = chunkDir == null || chunkDir.isBlank()
        ? Path.of(System.getProperty("java.io.tmpdir"), "keel-v2-chunks")
        : Path.of(chunkDir);
```
On Linux `/tmp` is world-readable → another local user can read artifact bytes mid-upload.

**Fix:** Default under the portal's own store root:
```java
this.chunkRoot = chunkDir == null || chunkDir.isBlank()
        ? Path.of(deliveryProps.getStoreRoot()).resolve("chunks")
        : Path.of(chunkDir);
```
Needs `DeliveryPortalProperties deliveryProps` injected into the constructor.

**Test:** none.
**Risk:** Existing deploys that relied on tmp for chunk staging now write to the store root. If disk space on the store partition is tight, consider documenting.

### 🟡 B12 — `scrubSecrets` uses exact equality
**Finding #26**
**File:** `src/main/java/delivery/job/ProvePhase.java:2251-2282`

**Current:**
```java
if (!user.isBlank() && user.equals(v)) v = "${TARGET_USERNAME}";
else if (pass != null && !pass.isEmpty() && pass.equals(v)) v = "${TARGET_PASSWORD}";
```
Misses the password wrapped with whitespace, URL-encoded, or embedded in a longer value (`"https://user:p@ss@host"`, `" p@ss "`, `"p%40ss"`).

**Fix:** Use `SecretSanitizer.scrubText(v, List.of(user, pass))` (which already exists in `delivery.privacy`) on the `v` string:
```java
String scrubbed = delivery.privacy.SecretSanitizer.scrubText(v,
        java.util.List.of(user, pass));
if (!scrubbed.equals(v)) {
    secret = true;
    v = scrubbed.replace(user, "${TARGET_USERNAME}").replace(pass, "${TARGET_PASSWORD}");
}
```

**Test:** `ProvePhaseScrubTest` — feed `" " + pass + " "`, `URLEncoder.encode(pass)`, `"prefix" + pass + "suffix"`, assert each no longer contains pass.
**Risk:** The `SecretSanitizer` already has logic to not leak partial matches; reuse it.

### 🟡 B13 — Failure-reason / message strings never scrubbed
**Finding #27**
**File:** `src/main/java/delivery/job/ProvePhase.java:538, 541, 676, 1551`

**Current:**
```java
return todoDraft(tc, ..., "Login prelude error: " + e.getMessage(), ev, 0, ...);
```
If `e.getMessage()` ever contains the typed password (some WebDriver exceptions carry the element value), the password lands unscrubbed in `todoDraft.reason`, which is persisted and rendered on the status page.

**Fix:** Wrap every `reason` string construction in ProvePhase with:
```java
private static String reason(String text, ConversionJobRequest request) {
    if (text == null) return "";
    String user = request == null || request.username() == null ? "" : request.username();
    String pass = request == null || request.password() == null ? "" : request.password();
    return delivery.privacy.SecretSanitizer.scrubText(text, java.util.List.of(user, pass));
}
```
Replace all `"Login prelude error: " + e.getMessage()` with `reason("Login prelude error: " + e.getMessage(), request)`.

**Test:** Feed an exception with message containing `request.password()`, assert the resulting `reason` does not contain it.
**Risk:** None — only redacts.

### 🟡 B14 — `ddl-auto=update`
**Finding #5**
**File:** `src/main/resources/application.properties:87`

**Current:** `spring.jpa.hibernate.ddl-auto=update`. Safe on H2 file; dangerous on a real DB with data (renamed column = silent data drop).

**Fix:** Create `src/main/resources/application-product.properties` (or add to the existing one) with `spring.jpa.hibernate.ddl-auto=validate`. Keep `update` for the default/dev profile. Make sure every schema change goes through an explicit migration class (`FencingTokenColumnMigration`, `RolesSchemaMigration` are existing examples).

**Test:** rerun the Spring boot tests; validate doesn't allow "missing column" so any drift surfaces loudly.
**Risk:** First boot after this change on an un-migrated DB fails to start — which is the whole point.

### 🟡 B15 — No MVC tests for new endpoints
**Finding #8**
**Files:**
- `src/main/java/delivery/portal/api/JobDeletionController.java` (new)
- `src/main/java/delivery/portal/api/ScheduleDueController.java` (modified for runner auth)

**Fix:** Two new MVC tests:
```java
// JobDeletionControllerTest
@Test public void delete404ForNonOwner();
@Test public void delete409WhenRunning();
@Test public void delete200OnSuccessAndRowRemoved();

// ScheduleDueControllerTest
@Test public void runnerBearerReturnsTenantScopedList();   // after B5
@Test public void userSessionReturnsPerUserList();
@Test public void noAuthReturns401();
```
Follow `ExecuteRunControllerTest` for setup patterns.

**Risk:** None — tests only.

---

# Batch C — real refactors (one branch per item, weekend-sized)

Order to attack them in: **C1 → C2 → C3 → C4 → C5**. Each is behaviour-preserving.

### 🔴 C1 — Collapse `HealCascade` constructor + heal() overloads
**Finding #28**
**File:** `src/main/java/delivery/heal/HealCascade.java:70-228`

**Current:** 6 constructor overloads + 7 `heal()` overloads, each chaining to the next with defaults. Every new parameter means 7 touch points.

**Fix:** Replace with:
```java
public record HealConfig(
        AuthoringService authoring,
        CursorHealClient cursor,
        FreeInventHealer freeInvent,       // nullable → defaults to FreeInventHealer.fromConfig(cursor)
        boolean visionWidenEnabled,
        VisionGroundingProvider vision,    // nullable
        java.util.function.Supplier<GroundingBrowser> browserSupplier // nullable
) {
    public static HealConfig of(AuthoringService a, CursorHealClient c) { ... }
}

public record HealCall(
        String tcId, StepIntentBinder.IntentLine intent, String slimHtml,
        byte[] png, String failureReason, Path screenshot, boolean tryOllama,
        List<String> priorSteps, boolean allowInvent, List<ProvenStep> spent,
        List<FailedLocator> failed, CandidateLivenessProbe probe
) {
    public static HealCall minimal(String tcId, IntentLine intent, String slimHtml,
            byte[] png, String failureReason, Path screenshot, boolean tryOllama) { ... }
}

public HealCascade(HealConfig cfg) { ... }
public HealResult heal(HealCall call) { ... }
```
Grep all callers (`grep -rn "new HealCascade\|healCascade.heal(" src/main src/test`) and migrate each.

**Test:** every existing test should still pass. No behaviour change.
**Risk:** Big diff, zero runtime change. Deploy with full test suite green.

### 🔴 C2 — Collapse `StepIntentBinder` bind overloads
**Finding #29**
**File:** `src/main/java/delivery/authoring/StepIntentBinder.java:180-250`

**Fix:** Same shape as C1:
```java
public record BindRequest(
        ManualTestCase tc, List<DomCandidate> candidates,
        List<String> preferOnTie,    // nullable → List.of()
        BindQualityMetrics metrics,  // nullable
        Set<String> spentFillIds     // nullable → Set.of()
) {}

public static BindResult bind(BindRequest req) { ... }
```
Migrate all callers of `bind(...)` and `bindSingle(...)`.

**Risk:** Same as C1.

### 🔴 C3 — Split the three 250-line methods
**Finding #30**
**Files:**
- `src/main/java/delivery/job/ProvePhase.java:attemptIntentWithRetry` (435L)
- `src/main/java/delivery/heal/HealCascade.java:heal 7-param` (256L)
- `src/main/java/delivery/authoring/StepIntentBinder.java:bindSingleInternal` (263L)

**Fix:** Split by **phase**, not line count. Suggested phases:
- `attemptIntentWithRetry` → `bindPhase()`, `executePhase()`, `verifyPhase()`, `retryPhase()`. Each returns a small record.
- `heal` → `tryInvent()`, `tryVision()`, `tryCursor()`, `tryFallback()`. Each returns `Optional<HealResult>`; the top method just tries each.
- `bindSingleInternal` → `classify()`, `score()`, `pickBest()`, `handleTie()`. Compose at the top.

**Risk:** Easiest-first order: start with `HealCascade.heal` (clear tiers), then `bindSingleInternal` (stateless), then `attemptIntentWithRetry` (shares state with the enclosing `prove`).

### 🔴 C4 — Split `PortalStore`
**Finding #10**
**File:** `src/main/java/delivery/portal/service/PortalStore.java` (1700L, 92 methods, 105 callers)

**Fix:** Group methods by concern:
- `ProjectManagementStore` — project CRUD, archive
- `JobLifecycleStore` — job entity CRUD, lease, delete
- `WorkspaceAccessStore` — canOperate / canAdminister / member listing
- `PromptOverrideStore` — prompt overrides

Keep one `PortalStore` as a thin facade that delegates (so the 105 callers don't all need to change at once) → then migrate callers one controller at a time.

**Risk:** Big diff. Run full test suite between every slice.

### 🔴 C5 — `AbstractJobWorker` for the 4 worker copies
**Finding #14**
**Files (4):**
- `src/main/java/delivery/portal/worker/ConversionWorker.java`
- `src/main/java/delivery/portal/worker/ExecuteWorker.java`
- `src/main/java/delivery/portal/worker/GenerateBatchWorker.java`
- `src/main/java/delivery/portal/worker/HuntWorker.java`

**Fix:** Extract shared polling + lease + cancel plumbing into `AbstractJobWorker<T>`. Each subclass overrides `JobRecord.JobKind kind()` and `void runOne(JobRecord job)`.

**Risk:** One of the four already has a quirk the others lack (I suspect `HuntWorker` — the hunt pipeline differs). Factor only the unanimous parts.

### 🔴 (deferred) Split `StepIntentBinder.java` and `ProvePhase.java`
**Finding #15** (same work as C3)

Covered by C3 for the method split. A full file split is a second pass: once methods are extracted into private statics, move groups into separate classes (`IntentClassifier`, `CandidateScorer`, `IntentBinder`). Not needed if C3 lands cleanly.

---

# Batch D — housekeeping

### ⚪ D1 — Triage 109 pre-existing failing tests
**Finding #11**

After this branch lands (the pom now excludes `**/*LiveSmoke*` from default `mvn test`), rerun the full suite:
```bash
rebuild-portal.cmd
mvn -pl keel-portal test
```
Count real failures vs noise. Fix in groups by subsystem.

### ⚪ D2 — Monitor Excel upload memory
**Finding #17**
**File:** `src/main/java/delivery/excel/ExcelTcReader.java`

Current `XSSFWorkbook(InputStream)` loads the whole workbook. At the 20MB upload cap it can spike memory. Only act if a real user hits it; the lazy path is `SXSSFWorkbook`, which streams.

---

# Confirmations (scanned and clean — no finding, for future auditors)

- **FreeMarker templates** (`customer-framework-template/templates/*.ftl`) escape user content with `?j_string`; identifiers come from `CodegenNaming` (Java-identifier-safe).
- **ZIP ingest** (`ProjectArtifactService.normalizeZipEntry`, `MobileInputZipValidator.rejectUnsafeName`) correctly reject `..` and absolute paths.
- **Credit debit** (`CreditService.tryDebitForJob`) uses `findByIdForUpdate` pessimistic lock + idempotency key — safe under concurrent charges.
- **Subprocess launches** (adb, apk, appium, cursor heal, nvidia-smi) use `ProcessBuilder` with a `List<String>` — no shell injection.
- **Secrets** are redacted via `SecretSanitizer` at outbound LLM calls (20 call sites confirmed).
- **`LocalLlmClient`** refuses cloud-vendor base URLs on the delivery path; **`AgentRouterClient`** passes the API key in `x-api-key` header and scrubs user text.
- **TLS trust**: no `TrustAll`/`AllowAllHosts` override anywhere; no `ObjectInputStream`; no XXE surface.
- **Static caches**: `PublicationLock.LOCAL`, `HostDeviceLock.LOCAL`, `CredentialRevision.MEMORY`, `PromptCatalog.CACHE` are keyed on bounded sets (project id, device id, prompt id, token hash) — do not leak.
- **Snapshot `ReplayCache`** is fully `synchronized`.
- **v1/v2 runner API controllers** all enforce runner / service-identity tokens; `RateLimitPolicy` throttles public API at 60/tenant/min.
- **TunnelPublicProxyController** validates the project token inside the handler; `TunnelAgentController` uses `TunnelAgentAuth`.
- **Admin controllers** without explicit auth inside the file (12 of them) are correctly covered by `.hasRole("ADMIN")` on `/api/admin/**` in SecurityConfig.
- **`net/TargetNetworkPolicy`** blocks loopback/private/link-local by default; dedicated installs must opt in via CIDRs.
- **Mega-files `StepIntentBinder`, `ProvePhase`, `HealCascade`**: no mutable static state, no swallowed exceptions (every `catch (Exception ignored)` is intentional driver cleanup or best-effort I/O), no obvious wrong-branch bug in the main happy paths. They are **expensive to change**, not buggy today.

# What was not audited

Per-line semantic review of the mega-files (`StepIntentBinder.bindSingleInternal`, `ProvePhase.attemptIntentWithRetry`, `HealCascade.heal`). The structural problems are captured in #28–#30; a line-by-line correctness pass is its own future task.

---

**Audit continues; new findings get appended with the next `#N`.**
