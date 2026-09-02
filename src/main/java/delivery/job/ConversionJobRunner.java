package delivery.job;

import delivery.excel.ExcelTcReader;
import delivery.excel.KeelPathCaseFilter;
import delivery.excel.ManualTestCase;
import delivery.packager.FrameworkPackager;
import delivery.store.ProjectStore;
import delivery.store.TcDiffService;
import delivery.util.ProjectNaming;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Orchestrates two-phase conversion:
 * <ol>
 *   <li>Phase 1 {@link ProvePhase} — browser prove + IR drafts (+ step retry)</li>
 *   <li>Phase 2 {@link EmitPhase} — page cluster + CodeWriter + ZIP + locator map</li>
 *   <li>Phase 3 {@link RevisePhase} — static Excel vs emit notes (no browser heal)</li>
 * </ol>
 */
public class ConversionJobRunner {
    private final JobProgressTracker progress = new JobProgressTracker();

    public JobProgressTracker progress() {
        return progress;
    }

    public ConversionJobResult run(ConversionJobRequest request) throws Exception {
        return run(request, () -> false);
    }

    public ConversionJobResult run(ConversionJobRequest request, BooleanSupplier cancelCheck) throws Exception {
        String mode = request.mode() == null ? "NEW" : request.mode().trim().toUpperCase();
        ProjectStore store = new ProjectStore(request.storeRoot(), request.baseUrl());
        if ("UPDATE".equals(mode) && !store.hasFramework(request.projectId())) {
            throw new IllegalStateException("UPDATE_WITHOUT_FRAMEWORK");
        }

        ExcelTcReader reader = new ExcelTcReader();
        List<ManualTestCase> allCases = KeelPathCaseFilter.requireForSurface(
                reader.read(request.excel()),
                KeelPathCaseFilter.Surface.AUTOMATE,
                "No AUTOMATE test cases in workbook — use Generate KeelPath=AUTOMATE, or leave KeelPath blank for legacy sheets"
        );

        Map<String, String> storedHashes = new HashMap<>();
        Path hashFile = store.projectRoot(request.projectId()).resolve("tc-hashes.json");
        if (Files.exists(hashFile)) {
            JSONObject json = new JSONObject(Files.readString(hashFile));
            for (String key : json.keySet()) {
                storedHashes.put(key, json.getString(key));
            }
        }

        TcDiffService diffService = new TcDiffService();
        List<ManualTestCase> toAuthor = allCases;
        if ("UPDATE".equals(mode)) {
            toAuthor = diffService.diff(allCases, storedHashes).toAuthor();
        }
        // UPDATE: only re-author changed TCs; NEW: null means author all
        Set<String> authorIds = "UPDATE".equals(mode)
                ? toAuthor.stream().map(ManualTestCase::tcId).collect(Collectors.toCollection(HashSet::new))
                : null;

        String workFolder = ProjectNaming.fromBaseUrl(request.baseUrl(), Instant.now());
        Path work = request.workDir().resolve(workFolder);
        Files.createDirectories(work);
        Path projectDir = work.resolve("project");
        FrameworkPackager packager = new FrameworkPackager();
        if ("UPDATE".equals(mode) && store.hasFramework(request.projectId())) {
            packager.copyTemplate(store.projectRoot(request.projectId()).resolve("framework"), projectDir);
        } else {
            packager.copyTemplate(request.templateRoot(), projectDir);
        }

        int proveUnits = allCases.size();
        int jobTotal = proveUnits + EmitPhase.PROGRESS_UNITS;
        progress.update(0, jobTotal, "Phase1 starting");
        ProvePhase prove = new ProvePhase(progress).withCancelCheck(cancelCheck);
        prove.prove(request, allCases, authorIds, work);

        progress.update(proveUnits, jobTotal, "Phase2 starting");
        EmitPhase emit = new EmitPhase(progress);
        return emit.emit(request, allCases, work, projectDir, workFolder, mode);
    }

    /** Strip login instructions so body authoring does not re-emit login clicks. */
    static ManualTestCase bodyOnlyCase(ManualTestCase tc) {
        String steps = tc.steps() == null ? "" : tc.steps();
        String cleaned = steps
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*(enter|type|fill).*?(username|user\\s*name|password|e-?mail).*$", "")
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*click\\s+(the\\s+)?(login|sign\\s*in).*button.*$", "")
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*(log\\s*in|sign\\s*in)\\b.*$", "")
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*(open|go to|navigate to|visit).*(login\\s*page|sign\\s*in\\s*page).*$", "")
                .replaceAll("(?im)^\\s*\\d+[.)]?\\s*(open|go to|navigate to|visit).*(base\\s*url|application\\s*url|website|home\\s*page).*$", "")
                .trim();
        return new ManualTestCase(
                tc.tcId(),
                tc.title(),
                tc.preconditions(),
                """
                        Post-login steps only (login runs in @BeforeMethod):
                        %s
                        Do NOT click login, type username, or type password.
                        """.formatted(cleaned.isBlank() ? steps : cleaned),
                tc.expectedResult(),
                tc.priority(),
                tc.tags(),
                tc.visualAssertion(),
                tc.testData(),
                tc.keelPath()
        );
    }
}
