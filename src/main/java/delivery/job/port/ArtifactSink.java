package delivery.job.port;

import java.io.IOException;

/** Where a worker puts result artifacts. In-process: the job's run dir; split: chunked v2 upload. */
public interface ArtifactSink {
    void put(String relativeName, byte[] bytes) throws IOException;
}
