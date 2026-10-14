package delivery.mobile.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import delivery.identity.ScopePaths;
import delivery.identity.TenantId;
import delivery.portal.DeliveryPortalProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Durable mobile case-requirement fields (M3-06 Final).
 * Path: {@code {storeRoot}/tenants/{tenant}/projects/{projectId}/mobile/case-requirements.json}.
 * Does not replace workbook {@code ManualTestCase.callBefore}; this store owns mobile
 * authMode / callBefore[] / authProfileRevisionId / startStateTcId / negativeLogin.
 */
@Component
public class MobileCaseRequirementsStore {
    public static final String FILE = "case-requirements.json";
    public static final String NEGATIVE_LOGIN_SETUP = "NEGATIVE_LOGIN_SETUP";

    private final DeliveryPortalProperties portalProperties;
    private final ObjectMapper mapper;

    public MobileCaseRequirementsStore(DeliveryPortalProperties portalProperties, ObjectMapper mapper) {
        this.portalProperties = portalProperties;
        this.mapper = mapper;
    }

    public Path storePath(String tenantId, String projectId) {
        Path storeRoot = Path.of(portalProperties.getStoreRoot());
        return ScopePaths.projectRoot(storeRoot, TenantId.parse(tenantId), projectId)
                .resolve("mobile").resolve(FILE);
    }

    public synchronized List<MobileCaseRequirementsView> list(String tenantId, String projectId) throws IOException {
        DurableState state = load(tenantId, projectId);
        return new ArrayList<>(state.cases.values());
    }

    public synchronized Optional<MobileCaseRequirementsView> get(
            String tenantId, String projectId, String tcId) throws IOException {
        DurableState state = load(tenantId, projectId);
        return Optional.ofNullable(state.cases.get(normalizeTc(tcId)));
    }

    public synchronized MobileCaseRequirementsView put(
            String tenantId, String projectId, MobileCaseRequirementsView view) throws IOException {
        if (view == null) {
            throw new AuthContractException("CASE_ID", "requirements body required");
        }
        DurableState state = load(tenantId, projectId);
        MobileCaseRequirementsView clean = new MobileCaseRequirementsView(
                view.tcId(), view.authMode(), view.callBefore(),
                view.authProfileRevisionId(), view.startStateTcId(), view.negativeLogin());
        validateNegativeLoginSetup(state, clean);
        state.cases.put(clean.tcId(), clean);
        save(tenantId, projectId, state);
        return clean;
    }

    /**
     * Reject when a negative-login case would serve as authenticated setup for another case
     * (callBefore / startStateTcId target) — mirrors AE M3-03 NEGATIVE_LOGIN_SETUP.
     */
    static void validateNegativeLoginSetup(DurableState state, MobileCaseRequirementsView candidate) {
        String tc = candidate.tcId();
        // If candidate is negative-login, it must not already be referenced as setup by others
        if (candidate.negativeLogin()) {
            for (MobileCaseRequirementsView other : state.cases.values()) {
                if (other.tcId().equals(tc)) {
                    continue;
                }
                if (other.callBefore().contains(tc) || tc.equals(other.startStateTcId())) {
                    throw new AuthContractException(
                            NEGATIVE_LOGIN_SETUP,
                            "negative login case cannot serve as authenticated setup: " + tc);
                }
            }
        }
        // If candidate references a known negative-login case as setup → reject
        for (String dep : candidate.callBefore()) {
            MobileCaseRequirementsView depView = state.cases.get(dep);
            if (depView != null && depView.negativeLogin()) {
                throw new AuthContractException(
                        NEGATIVE_LOGIN_SETUP,
                        "negative login case cannot serve as authenticated setup: " + dep);
            }
        }
        if (!candidate.startStateTcId().isBlank()) {
            MobileCaseRequirementsView ss = state.cases.get(candidate.startStateTcId());
            if (ss != null && ss.negativeLogin()) {
                throw new AuthContractException(
                        NEGATIVE_LOGIN_SETUP,
                        "negative login case cannot serve as authenticated setup: "
                                + candidate.startStateTcId());
            }
            // also: cannot set startStateTcId to self when negativeLogin
            if (candidate.negativeLogin() && candidate.startStateTcId().equals(tc)) {
                throw new AuthContractException(
                        NEGATIVE_LOGIN_SETUP,
                        "negative login case cannot serve as authenticated setup: " + tc);
            }
        }
    }

    private DurableState load(String tenantId, String projectId) throws IOException {
        Path path = storePath(tenantId, projectId);
        if (!Files.isRegularFile(path)) {
            return new DurableState();
        }
        DurableState state = mapper.readValue(Files.readAllBytes(path), DurableState.class);
        if (state.cases == null) {
            state.cases = new LinkedHashMap<>();
        }
        return state;
    }

    private void save(String tenantId, String projectId, DurableState state) throws IOException {
        Path path = storePath(tenantId, projectId);
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(FILE + ".tmp");
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(state);
        Files.write(tmp, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFail) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String normalizeTc(String tcId) {
        if (tcId == null || tcId.isBlank()) {
            throw new AuthContractException("CASE_ID", "tcId required");
        }
        return tcId.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class DurableState {
        public Map<String, MobileCaseRequirementsView> cases = new LinkedHashMap<>();
    }
}
