package delivery.portal.service;

import delivery.portal.model.JobRecord;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StoreJobStatusLookup implements JobStatusLookup {

    private final PortalStore store;

    public StoreJobStatusLookup(PortalStore store) {
        this.store = store;
    }

    @Override
    public Optional<JobRecord.Status> status(String jobId) {
        return store.getJob(jobId).map(JobRecord::getStatus);
    }

    @Override
    public Optional<String> errorMessage(String jobId) {
        return store.getJob(jobId)
                .map(j -> {
                    String err = j.getError();
                    if (err != null && !err.isBlank()) {
                        return err;
                    }
                    return j.getMessage();
                })
                .filter(s -> s != null && !s.isBlank());
    }
}
