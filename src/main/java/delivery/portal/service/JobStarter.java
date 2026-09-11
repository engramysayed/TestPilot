package delivery.portal.service;

import delivery.authoring.AuthoringEngine;

/**
 * Starts Automate (CONVERT) and Execute jobs for pipeline orchestration.
 * Implemented by {@link PortalJobStarter} in production; faked in unit tests.
 */
public interface JobStarter {

    String startConvert(String projectId, Long ownerUserId, boolean useGenerated, AuthoringEngine authoringEngine);

    String startExecute(String projectId, Long ownerUserId, boolean useGenerated, AuthoringEngine authoringEngine);
}
