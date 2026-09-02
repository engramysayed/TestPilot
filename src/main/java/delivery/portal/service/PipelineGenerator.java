package delivery.portal.service;

/** Generates and saves a workbook when a pipeline is started with stories. */
@FunctionalInterface
public interface PipelineGenerator {

    void generate(String projectId, Long ownerUserId, String stories) throws Exception;
}
