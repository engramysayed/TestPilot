package delivery.job;

import delivery.codegen.CodeWriter;
import delivery.codegen.DomainCatalogWriter;
import delivery.codegen.PageClusterer;
import delivery.codegen.ProvenStep;
import delivery.excel.ManualTestCase;
import delivery.ir.TcDraft;
import delivery.ir.TcDraftStatus;
import delivery.ir.TcDraftStore;
import delivery.packager.FrameworkPackager;
import delivery.revise.AgentRouterClient;
import delivery.revise.FinalRevisePhase;
import delivery.revise.FinalReviseResult;
import delivery.store.LocatorMapBuilder;
import delivery.store.LocatorMapStore;
import delivery.store.ProjectStore;
import delivery.store.TcDiffService;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 2: load IR drafts, cluster pages, write POM/tests, pack ZIP, persist locator map.
 */
public class EmitPhase {
    /** Fixed emit steps after prove: load, cluster, final revise slot, write, compile, static revise, zip. */
    public static final int PROGRESS_UNITS = 7;

    private final JobProgressTracker progress;
    private int proveBase;
    private int emitStep;

    public EmitPhase(JobProgressTracker progress) {
        this.progress = progress == null ? new JobProgressTracker() : progress;
    }

    /** Package-visible for progress reserve tests. */
    void bumpProgress(String message) {
        if (emitStep == 0) {
            proveBase = progress.current();
        }
        int jobTotal = progress.effectiveTotal(proveBase);
        emitStep++;
        progress.update(proveBase + emitStep, jobTotal, message);
    }

    public ConversionJobResult emit(
            ConversionJobRequest request,
            List<ManualTestCase> allCases,
            Path workDir,
            Path projectDir,
            String workFolder,
            String mode
    ) throws Exception {
        proveBase = progress.current();
        emitStep = 0;
        bumpProgress("Phase2 emit: loading IR drafts");
        TcDraftStore draftStore = new TcDraftStore(workDir);
        List<TcDraft> drafts = draftStore.readAll();
        if (drafts.isEmpty()) {
            throw new IllegalStateException("Phase2 emit: no IR drafts under " + draftStore.irDir());
        }

        bumpProgress("Phase2 emit: clustering pages");
        List<TcDraft> clustered = new ArrayList<>();
        for (TcDraft d : drafts) {
            clustered.add(PageClusterer.reclusterDraft(d));
        }
        boolean clientDelivery = request.finalRevise();
        clustered = FinalRevisePhase.applyHonestyForClientDelivery(clustered, allCases, clientDelivery);

        FinalReviseResult reviseResult = FinalReviseResult.skipped(clustered);
        bumpProgress(clientDelivery
                ? "Phase2 emit: final revise (AgentRouter)"
                : "Phase2 emit: final revise (skipped)");
        if (clientDelivery) {
            AgentRouterClient reviseClient = AgentRouterClient.fromConfigOrNull();
            reviseResult = new FinalRevisePhase(reviseClient)
                    .revise(clustered, allCases, projectDir, true);
            clustered = reviseResult.drafts();
            // Persist demoted IR back to work dir before codegen
            for (TcDraft d : clustered) {
                draftStore.write(d);
            }
        }

        List<TcOutcome> outcomes = new ArrayList<>();
        List<TcOutcome> toCodegen = new ArrayList<>();
        for (TcDraft d : clustered) {
            TcOutcome outcome = toOutcome(d);
            outcomes.add(outcome);
            // UPDATE reuse: keep classes already copied from stored framework
            if (d.status() != TcDraftStatus.REUSED) {
                toCodegen.add(outcome);
            }
        }

        bumpProgress("Phase2 emit: writing pages and tests");
        new CodeWriter(request.templateRoot().resolve("templates")).write(projectDir, toCodegen);
        DomainCatalogWriter.write(projectDir, outcomes);

        FrameworkPackager packager = new FrameworkPackager();
        packager.writeScoreReport(projectDir, outcomes);
        appendPageReuseMetrics(projectDir, clustered);
        appendHealMetrics(projectDir, clustered);
        packager.writeTargetConfig(projectDir, request.baseUrl(), request.username(), request.password());

        bumpProgress("Phase2 emit: compile smoke check");
        EmitCompileCheck.runIfEnabled(projectDir);

        bumpProgress("Phase3 revise: static Excel vs emit check");
        new RevisePhase().revise(projectDir, clustered, allCases);

        JSONObject locatorMap = LocatorMapBuilder.build(clustered);
        Path mapInProject = projectDir.resolve("docs/locator-map.json");
        Files.createDirectories(mapInProject.getParent());
        Files.writeString(mapInProject, locatorMap.toString(2));

        ProjectStore store = new ProjectStore(request.storeRoot(), request.baseUrl());
        new LocatorMapStore(store.projectRoot(request.projectId()).resolve("locator-map.json"))
                .save(locatorMap);

        // Also keep IR copies under project store for UPDATE / review
        Path irStore = store.projectRoot(request.projectId()).resolve("ir");
        Files.createDirectories(irStore);
        for (TcDraft d : clustered) {
            Files.writeString(irStore.resolve(TcDraftStore.safeFileName(d.tcId()) + ".json"),
                    TcDraftStore.toJson(d).toString(2));
        }

        // Persist per-step screenshots for portal timeline
        Path evidenceSrc = workDir.resolve("evidence");
        Path evidenceDest = store.projectRoot(request.projectId()).resolve("evidence");
        if (Files.isDirectory(evidenceSrc)) {
            copyEvidenceTree(evidenceSrc, evidenceDest);
        }

        Path zip = workDir.resolve("package.zip");
        bumpProgress("Phase2 emit: packaging ZIP");
        packager.zip(projectDir, zip);

        TcDiffService diffService = new TcDiffService();
        Path hashFile = store.projectRoot(request.projectId()).resolve("tc-hashes.json");
        JSONObject hashes = new JSONObject(diffService.hashesOf(allCases));
        Files.createDirectories(hashFile.getParent());
        Files.writeString(hashFile, hashes.toString(2));

        store.saveVersion(request.projectId(), projectDir, zip);

        int passed = (int) outcomes.stream().filter(o -> o.status() == TcStatus.PASSED).count();
        int todo = (int) outcomes.stream()
                .filter(o -> o.status() == TcStatus.TODO || o.status() == TcStatus.PARTIAL)
                .count();
        Path score = projectDir.resolve("docs/AUTOMATION_SCORE.md");

        String portalStatus = reviseResult.softBlocked() ? "COMPLETED_WITH_BLOCK" : "COMPLETED";
        String reviseVerdict = reviseResult.jobVerdict().name();

        JSONObject lastJob = new JSONObject();
        lastJob.put("status", portalStatus);
        lastJob.put("reviseVerdict", reviseVerdict);
        lastJob.put("passedCount", passed);
        lastJob.put("todoCount", todo);
        lastJob.put("mode", mode);
        lastJob.put("workFolder", workFolder);
        lastJob.put("phase", "two-phase");
        lastJob.put("finalRevise", clientDelivery);
        lastJob.put("completedAt", java.time.Instant.now().toString());
        store.writeLastJob(request.projectId(), lastJob);

        int jobTotal = progress.effectiveTotal(proveBase);
        progress.update(jobTotal, jobTotal, reviseResult.softBlocked()
                ? "Phase2 emit complete (soft block — not client-ready)"
                : "Phase2 emit complete");
        String message = reviseResult.ran() ? reviseResult.summaryMessage() : "ok";
        return new ConversionJobResult(zip, passed, todo, score, message, portalStatus, reviseVerdict);
    }

    public static TcOutcome toOutcome(TcDraft d) {
        TcStatus status = switch (d.status()) {
            case PASSED, REUSED -> TcStatus.PASSED;
            case PARTIAL -> TcStatus.PARTIAL;
            case TODO -> TcStatus.TODO;
        };
        Path evidence = d.evidenceDir() == null || d.evidenceDir().isBlank()
                ? null : Path.of(d.evidenceDir());
        String reason = d.failureReason();
        if (d.status() == TcDraftStatus.PARTIAL && d.blockerIntent() != null && !d.blockerIntent().isBlank()) {
            reason = "Blocked at step " + d.blockerStepIndex() + " [" + d.blockerIntent() + "]: " + reason;
        }
        if (d.status() == TcDraftStatus.REUSED) {
            reason = "reused";
        }
        return new TcOutcome(
                d.tcId(), d.title() == null ? "" : d.title(), status, d.provenSteps(), reason, evidence,
                d.needsLoginBeforeMethod(), d.loginSteps());
    }

    private static void copyEvidenceTree(Path src, Path dest) throws Exception {
        Files.walkFileTree(src, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws java.io.IOException {
                Path rel = src.relativize(dir);
                Files.createDirectories(dest.resolve(rel));
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws java.io.IOException {
                Path rel = src.relativize(file);
                Files.copy(file, dest.resolve(rel), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static void appendPageReuseMetrics(Path projectDir, List<TcDraft> drafts) throws Exception {
        Path score = projectDir.resolve("docs/AUTOMATION_SCORE.md");
        Set<String> pages = new HashSet<>();
        int proven = 0;
        int blocked = 0;
        for (TcDraft d : drafts) {
            for (ProvenStep s : d.provenSteps()) {
                if (s.pageName() != null && !s.pageName().isBlank()) {
                    pages.add(s.pageName());
                }
                proven++;
            }
            for (ProvenStep s : d.loginSteps()) {
                if (s.pageName() != null && !s.pageName().isBlank()) {
                    pages.add(s.pageName());
                }
            }
            if (d.status() == TcDraftStatus.PARTIAL || d.status() == TcDraftStatus.TODO) {
                blocked++;
            }
        }
        String extra = """

                ## Phase-2 page reuse

                - Distinct page classes: %d
                - Proven steps across suite: %d
                - Cases with blocker/TODO: %d
                - Pages: %s
                """.formatted(pages.size(), proven, blocked, String.join(", ", pages.stream().sorted().toList()));
        if (Files.exists(score)) {
            Files.writeString(score, Files.readString(score) + extra);
        }
        appendAutomationNotesDoc(projectDir, drafts);
    }

    /** Write heal recovery automationNotes into docs for Automate reviewers. */
    private static void appendAutomationNotesDoc(Path projectDir, List<TcDraft> drafts) throws Exception {
        StringBuilder body = new StringBuilder("# Heal automation notes\n\n");
        boolean any = false;
        for (TcDraft d : drafts) {
            if (d == null || d.evidenceDir() == null || d.evidenceDir().isBlank()) {
                continue;
            }
            Path notes = Path.of(d.evidenceDir()).resolve(d.tcId()).resolve("automation-notes.txt");
            if (!Files.isRegularFile(notes)) {
                notes = Path.of(d.evidenceDir()).resolve("automation-notes.txt");
            }
            if (!Files.isRegularFile(notes)) {
                continue;
            }
            any = true;
            body.append("## ").append(d.tcId()).append("\n\n");
            body.append(Files.readString(notes).trim()).append("\n\n");
        }
        if (!any) {
            return;
        }
        Path out = projectDir.resolve("docs/HEAL_AUTOMATION_NOTES.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, body.toString());
    }

    private static void appendHealMetrics(Path projectDir, List<TcDraft> drafts) throws Exception {
        Path score = projectDir.resolve("docs/AUTOMATION_SCORE.md");
        long ollama = drafts.stream().filter(d -> "ollama".equalsIgnoreCase(d.healTier())).count();
        long vision = drafts.stream().filter(d -> "vision".equalsIgnoreCase(d.healTier())).count();
        long cursor = drafts.stream().filter(d -> "cursor".equalsIgnoreCase(d.healTier())).count();
        long invent = drafts.stream().filter(d -> "invent".equalsIgnoreCase(d.healTier())).count();
        long none = drafts.stream().filter(d -> "none".equalsIgnoreCase(d.healTier())
                || d.healTier() == null || d.healTier().isBlank()).count();
        StringBuilder detail = new StringBuilder();
        for (TcDraft d : drafts) {
            if (!"none".equalsIgnoreCase(d.healTier()) && d.healTier() != null && !d.healTier().isBlank()) {
                detail.append("- ").append(d.tcId()).append(": tier=").append(d.healTier());
                if (d.healSkipReason() != null && !d.healSkipReason().isBlank()) {
                    detail.append(" skip=").append(d.healSkipReason());
                }
                detail.append('\n');
            }
        }
        String extra = """

                ## Heal cascade

                - none: %d
                - ollama: %d
                - vision: %d
                - cursor: %d
                - invent: %d
                %s""".formatted(none, ollama, vision, cursor, invent,
                detail.isEmpty() ? "" : "\n" + detail);
        if (Files.exists(score)) {
            Files.writeString(score, Files.readString(score) + extra);
        }
    }
}
