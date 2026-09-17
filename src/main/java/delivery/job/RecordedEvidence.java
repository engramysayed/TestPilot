package delivery.job;

import delivery.ir.TcDraft;
import delivery.ir.TcDraftStore;
import delivery.portal.model.JobRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Loads recorded IR for a job without rewriting historical evidence. */
public final class RecordedEvidence {
    private RecordedEvidence() {
    }

    public static List<TcDraft> drafts(Path projectRoot, JobRecord job) {
        if (projectRoot == null || job == null) {
            return List.of();
        }
        Path execute = projectRoot.resolve("execute-runs").resolve(job.getJobId());
        List<TcDraft> fromExecute = read(execute);
        if (!fromExecute.isEmpty()) {
            return fromExecute;
        }
        return read(projectRoot.resolve("jobs").resolve(job.getJobId()));
    }

    private static List<TcDraft> read(Path workDir) {
        TcDraftStore store = new TcDraftStore(workDir);
        if (!Files.isDirectory(store.irDir())) {
            return List.of();
        }
        try {
            return store.readAll();
        } catch (Exception e) {
            return List.of();
        }
    }
}
