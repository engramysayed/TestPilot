package delivery.portal.service;

import delivery.portal.model.JobRecord;

import java.util.Optional;

/** Resolves live job status for pipeline stage transitions. */
public interface JobStatusLookup {

    Optional<JobRecord.Status> status(String jobId);

    default Optional<String> errorMessage(String jobId) {
        return Optional.empty();
    }
}
